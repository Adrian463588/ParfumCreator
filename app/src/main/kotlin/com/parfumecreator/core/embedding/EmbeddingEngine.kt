package com.parfumecreator.core.embedding

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

private const val TAG = "EmbeddingEngine"

@Singleton
class AssetLoader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    @Volatile
    private var cachedModelSha256: String? = null

    fun loadModel(): ByteBuffer? {
        return try {
            context.assets.openFd("embedding/all-MiniLM-L6-v2.tflite").use { fileDescriptor ->
                FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                    val startOffset = fileDescriptor.startOffset
                    val declaredLength = fileDescriptor.declaredLength
                    inputStream.channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Embedding model file not found in assets: ${e.message}")
            null
        }
    }

    fun loadVocab(): Map<String, Int> {
        val vocab = mutableMapOf<String, Int>()
        try {
            context.assets.open("embedding/vocab.txt").bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    vocab[line.trim()] = index
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Embedding vocab.txt not found in assets: ${e.message}")
        }
        return vocab
    }

    /** Returns the immutable asset fingerprint used by the catalog index key. */
    fun modelSha256(): String? {
        cachedModelSha256?.let { return it }
        val calculated = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open("embedding/all-MiniLM-L6-v2.tflite").use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }.getOrNull()
        if (calculated != null) cachedModelSha256 = calculated
        return calculated
    }
}

@Singleton
class BertTokenizer @Inject constructor(
    private val assetLoader: AssetLoader
) {
    private val vocab: Map<String, Int> by lazy { assetLoader.loadVocab() }

    private val clsId: Int get() = vocab["[CLS]"] ?: 101
    private val sepId: Int get() = vocab["[SEP]"] ?: 102
    private val unkId: Int get() = vocab["[UNK]"] ?: 100
    private val padId: Int get() = vocab["[PAD]"] ?: 0

    data class TokenizedInput(
        val inputIds: IntArray,
        val attentionMask: IntArray,
        val tokenTypeIds: IntArray
    )

    fun tokenize(text: String, maxLen: Int = 128): TokenizedInput {
        require(maxLen >= 3) { "Tokenizer maxLen must leave room for CLS and SEP" }
        val tokens = mutableListOf(clsId)
        val cleanText = text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}\\s\\p{Punct}]"), " ")
        val words = cleanText.split(Regex("[\\s\\p{Punct}]+")).filter { it.isNotEmpty() }

        for (word in words) {
            if (tokens.size >= maxLen - 1) break
            if (vocab.containsKey(word)) {
                tokens.add(vocab[word] ?: unkId)
            } else {
                // WordPiece subword fallback
                var subword = word
                var isFirst = true
                while (subword.isNotEmpty() && tokens.size < maxLen - 1) {
                    var found = false
                    for (end in subword.length downTo 1) {
                        val piece = (if (isFirst) "" else "##") + subword.substring(0, end)
                        if (vocab.containsKey(piece)) {
                            tokens.add(vocab[piece] ?: unkId)
                            subword = subword.substring(end)
                            isFirst = false
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        tokens.add(unkId)
                        break
                    }
                }
            }
        }

        tokens.add(sepId)

        val inputIds = IntArray(maxLen) { padId }
        val attentionMask = IntArray(maxLen) { 0 }
        val tokenTypeIds = IntArray(maxLen) { 0 }

        val actualLen = minOf(tokens.size, maxLen)
        for (i in 0 until actualLen) {
            inputIds[i] = tokens[i]
            attentionMask[i] = 1
        }

        return TokenizedInput(inputIds, attentionMask, tokenTypeIds)
    }
}

@Singleton
class TfLiteEmbeddingService @Inject constructor(
    private val assetLoader: AssetLoader,
    private val tokenizer: BertTokenizer
) {
    private var interpreter: Interpreter? = null
    private var lastInitAttemptAt = 0L
    private val inferenceMutex = Mutex()
    private val cacheLock = Any()
    private val embeddingCache = object : LinkedHashMap<String, FloatArray>(CACHE_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > CACHE_CAPACITY
    }

    @Synchronized
    private fun getInterpreter(): Interpreter? {
        if (interpreter == null && System.currentTimeMillis() - lastInitAttemptAt >= INIT_RETRY_COOLDOWN_MS) {
            lastInitAttemptAt = System.currentTimeMillis()
            val modelBuffer = assetLoader.loadModel()
            if (modelBuffer != null) {
                try {
                    val options = Interpreter.Options().apply {
                        setNumThreads(4)
                    }
                    val candidate = Interpreter(modelBuffer, options)
                    if (!hasExpectedSignature(candidate)) {
                        candidate.close()
                        throw IllegalArgumentException("MiniLM TFLite signature tidak sesuai kontrak 384 dimensi")
                    }
                    interpreter = candidate
                    lastInitAttemptAt = 0L
                    Log.i(TAG, "TFLite MiniLM embedding model loaded successfully. Inputs: ${interpreter?.inputTensorCount}, Outputs: ${interpreter?.outputTensorCount}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create TFLite Interpreter: ${e.message}", e)
                }
            }
        }
        return interpreter
    }

    private fun hasExpectedSignature(interpreter: Interpreter): Boolean {
        if (interpreter.inputTensorCount !in 1..3 || interpreter.outputTensorCount < 1) return false
        for (index in 0 until interpreter.inputTensorCount) {
            val tensor = interpreter.getInputTensor(index)
            val shape = tensor.shape()
            if (tensor.dataType() != DataType.INT32 || shape.size != 2 || shape[0] != 1 || shape[1] != MAX_SEQ_LEN) {
                Log.w(TAG, "Unexpected MiniLM input signature at $index: ${shape.contentToString()} ${tensor.dataType()}")
                return false
            }
        }
        val output = interpreter.getOutputTensor(0)
        val outputShape = output.shape()
        val supportedOutput = output.dataType() == DataType.FLOAT32 && (
            outputShape.size == 2 && outputShape[0] == 1 && outputShape[1] == EMBEDDING_DIM ||
                outputShape.size == 3 && outputShape[0] == 1 && outputShape[1] > 0 && outputShape[2] == EMBEDDING_DIM
            )
        if (!supportedOutput) {
            Log.w(TAG, "Unexpected MiniLM output signature: ${outputShape.contentToString()} ${output.dataType()}")
        }
        return supportedOutput
    }

    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        val cacheKey = normalizeCacheKey(text)
        if (cacheKey.isBlank()) return@withContext null

        cached(cacheKey)?.let { return@withContext it }

        val computed = inferenceMutex.withLock {
            // A second coroutine may have populated the cache while this call
            // waited for the interpreter. Avoid duplicate TFLite inference.
            cached(cacheKey) ?: infer(text)
        }
        computed?.let { store(cacheKey, it) }
        computed
    }

    /** Stable corpus-cache component; it is independent from query text. */
    fun modelCacheKey(): String = assetLoader.modelSha256() ?: "unavailable"

    private fun infer(text: String): FloatArray? {
        val interp = getInterpreter() ?: return null
        return try {
            val tokenized = tokenizer.tokenize(text, MAX_SEQ_LEN)
            val inputCount = interp.inputTensorCount
            val outputShape = interp.getOutputTensor(0).shape()

            val inputIds2D = arrayOf(tokenized.inputIds)
            val attentionMask2D = arrayOf(tokenized.attentionMask)
            val tokenTypeIds2D = arrayOf(tokenized.tokenTypeIds)

            val inputs = when (inputCount) {
                3 -> arrayOf(inputIds2D, attentionMask2D, tokenTypeIds2D)
                2 -> arrayOf(inputIds2D, attentionMask2D)
                else -> arrayOf(inputIds2D)
            }

            // Check output tensor rank
            val rawVector = if (outputShape.size == 3 && outputShape[2] == EMBEDDING_DIM) {
                // Shape: [1, seq_len, 384] -> Requires Attention-Weighted Mean Pooling
                val seqLen = outputShape[1]
                val dim = outputShape[2]
                val output3D = Array(1) { Array(seqLen) { FloatArray(dim) } }
                val outputs = mapOf(0 to output3D)

                interp.runForMultipleInputsOutputs(inputs, outputs)

                // Mean pooling with attention mask
                val pooled = FloatArray(dim)
                var maskSum = 0.0f
                for (i in 0 until minOf(seqLen, MAX_SEQ_LEN)) {
                    val mask = tokenized.attentionMask[i].toFloat()
                    if (mask > 0.0f) {
                        maskSum += mask
                        for (d in 0 until dim) {
                            pooled[d] += output3D[0][i][d] * mask
                        }
                    }
                }
                val divisor = if (maskSum > 1e-9f) maskSum else 1.0f
                for (d in 0 until dim) {
                    pooled[d] /= divisor
                }
                pooled
            } else if (outputShape.size == 2 && outputShape[1] == EMBEDDING_DIM) {
                // Shape: [1, 384] -> Direct sentence embedding
                val output2D = Array(1) { FloatArray(EMBEDDING_DIM) }
                val outputs = mapOf(0 to output2D)
                interp.runForMultipleInputsOutputs(inputs, outputs)
                output2D[0]
            } else {
                Log.w(TAG, "Unsupported embedding output shape: ${outputShape.contentToString()}")
                return null
            }

            if (rawVector.size != EMBEDDING_DIM || rawVector.any { !it.isFinite() }) {
                Log.w(TAG, "Embedding output did not match expected finite $EMBEDDING_DIM dimensions")
                return null
            }
            l2Normalize(rawVector).takeIf { vector -> vector.any { it != 0.0f } }
        } catch (e: Exception) {
            Log.w(TAG, "TFLite embedding inference failed: ${e.message}")
            null
        }
    }

    private fun normalizeCacheKey(text: String): String =
        text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    @Synchronized
    fun close() {
        interpreter?.close()
        interpreter = null
        lastInitAttemptAt = 0L
        synchronized(cacheLock) { embeddingCache.clear() }
    }

    private fun cached(key: String): FloatArray? = synchronized(cacheLock) {
        embeddingCache[key]?.copyOf()
    }

    private fun store(key: String, vector: FloatArray) {
        synchronized(cacheLock) {
            embeddingCache[key] = vector.copyOf()
        }
    }

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sumSquares = 0.0f
        for (v in vec) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares).coerceAtLeast(1e-9f)
        val normalized = FloatArray(vec.size)
        for (i in vec.indices) {
            normalized[i] = vec[i] / norm
        }
        return normalized
    }

    companion object {
        const val MAX_SEQ_LEN = 128
        const val EMBEDDING_DIM = 384
        private const val CACHE_CAPACITY = 2_048
        private const val INIT_RETRY_COOLDOWN_MS = 5_000L
    }
}

object CosineSimilarity {
    fun calculate(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0.0f
        var dot = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in vecA.indices) {
            dot += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 1e-9f) (dot / denominator).coerceIn(-1.0f, 1.0f) else 0.0f
    }
}
