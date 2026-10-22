package com.parfumecreator.data.local.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.parfumecreator.data.local.converters.Converters
import com.parfumecreator.data.local.dao.*
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStreamReader

@Database(
    entities = [
        IngredientEntity::class,
        FormulaEntity::class,
        FormulaIngredientEntity::class,
        RagDocumentEntity::class,
        RagChunkEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        DownloadEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ParfumeDatabase : RoomDatabase() {

    abstract fun ingredientDao(): IngredientDao
    abstract fun formulaDao(): FormulaDao
    abstract fun ragDao(): RagDao
    abstract fun chatDao(): ChatDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val DATABASE_NAME = "parfume_creator.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN source_type TEXT NOT NULL DEFAULT 'UPLOAD'")
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN source_url TEXT")
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN content_hash TEXT")
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN fetched_at INTEGER")
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN expires_at INTEGER")
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN trust_level TEXT NOT NULL DEFAULT 'USER_DOCUMENT'")
                db.execSQL("ALTER TABLE rag_chunks ADD COLUMN page_start INTEGER")
                db.execSQL("ALTER TABLE rag_chunks ADD COLUMN page_end INTEGER")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN citations_json TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN retrieval_mode TEXT")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN model_status TEXT")

                // Keep the newest row for each URI before adding the unique index.
                db.execSQL(
                    "DELETE FROM rag_documents WHERE id NOT IN " +
                        "(SELECT MAX(id) FROM rag_documents GROUP BY uri)"
                )
                db.execSQL(
                    "DELETE FROM rag_chunks WHERE id NOT IN " +
                        "(SELECT MAX(id) FROM rag_chunks GROUP BY document_id, chunk_index)"
                )
                db.execSQL(
                    "DELETE FROM rag_documents WHERE source_url IS NOT NULL AND content_hash IS NOT NULL " +
                        "AND id NOT IN (SELECT MAX(id) FROM rag_documents " +
                        "WHERE source_url IS NOT NULL AND content_hash IS NOT NULL " +
                        "GROUP BY source_url, content_hash)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rag_documents_uri ON rag_documents(uri)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_rag_documents_source_url_content_hash " +
                        "ON rag_documents(source_url, content_hash)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_rag_chunks_document_id_chunk_index ON rag_chunks(document_id, chunk_index)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rag_documents ADD COLUMN availability TEXT NOT NULL DEFAULT 'AVAILABLE'")
            }
        }

        fun buildDatabase(
            context: Context,
            scope: CoroutineScope,
            readiness: DatasetReadinessStore
        ): ParfumeDatabase {
            lateinit var instance: ParfumeDatabase
            instance = Room.databaseBuilder(
                context.applicationContext,
                ParfumeDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch(Dispatchers.IO) {
                            try {
                                val seedResult = prepopulateIngredients(
                                    context.applicationContext,
                                    instance.ingredientDao()
                                )
                                prepopulateDefaultBundles(instance.downloadDao())
                                if (seedResult.expectedCount > 0 &&
                                    seedResult.storedCount >= seedResult.expectedCount
                                ) {
                                    readiness.markReady()
                                } else if (seedResult.expectedCount == 0) {
                                    readiness.markEmpty()
                                } else {
                                    readiness.markSeeding()
                                }
                            } catch (error: Exception) {
                                Log.e(TAG, "Bundled catalogue seeding failed", error)
                                readiness.markFailed()
                            }
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        scope.launch(Dispatchers.IO) {
                            sanitizeExistingIngredients(db)
                            val expectedCount = readBundledIngredientCount(context.applicationContext)
                            runCatching { instance.ingredientDao().getCount() }
                                .onSuccess { count ->
                                    when {
                                        expectedCount < 0 -> readiness.markFailed()
                                        expectedCount == 0 -> readiness.markEmpty()
                                        count >= expectedCount -> readiness.markReady()
                                        else -> readiness.markSeeding()
                                    }
                                }
                                .onFailure { error ->
                                    Log.e(TAG, "Unable to determine catalogue readiness", error)
                                    readiness.markFailed()
                                }
                        }
                    }
                })
                .build()
            return instance
        }

        private fun sanitizeExistingIngredients(db: SupportSQLiteDatabase) {
            try {
                val cursor = db.query("SELECT id, name FROM ingredients")
                cursor.use { c ->
                    val idCol = c.getColumnIndex("id")
                    val nameCol = c.getColumnIndex("name")
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val rawName = c.getString(nameCol) ?: continue
                        val cleanName = IngredientNameCleaner.clean(rawName)
                        if (cleanName != rawName) {
                            db.execSQL(
                                "UPDATE ingredients SET name = ? WHERE id = ?",
                                arrayOf<Any?>(cleanName, id)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to sanitize existing ingredient names", e)
            }
        }

        private data class IngredientSeedResult(
            val expectedCount: Int,
            val storedCount: Int
        )

        private fun ingredientAssetPaths(): List<String> = listOf(
            "data/ingredients_natural.json",
            "data/ingredients_luzi.json",
            "data/ingredients_parfarome.json",
            "data/ingredients_macrabame.json"
        )

        private suspend fun prepopulateIngredients(
            context: Context,
            ingredientDao: IngredientDao
        ): IngredientSeedResult {
            val assetManager = context.assets
            val files = ingredientAssetPaths()

            val gson = Gson()
            val listType = object : TypeToken<IngredientJsonWrapper>() {}.type
            val allEntities = mutableListOf<IngredientEntity>()

            files.forEach { filePath ->
                assetManager.open(filePath).use { stream ->
                    InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                        val wrapper: IngredientJsonWrapper = gson.fromJson(reader, listType)
                        wrapper.ingredients.forEach { raw ->
                            val cleanName = IngredientNameCleaner.clean(raw.name)
                            if (cleanName.isNotBlank()) {
                                allEntities.add(
                                    IngredientEntity(
                                        name = cleanName,
                                        type = raw.type,
                                        brand = raw.brand,
                                        noteType = raw.noteType ?: "MIDDLE",
                                        fragranceFamily = raw.fragranceFamily ?: "FRESH",
                                        densityGml = raw.densityGml ?: 0.95,
                                        casNumber = raw.casNumber,
                                        inspiredBy = raw.inspiredBy?.toString()?.let { value -> IngredientNameCleaner.clean(value) },
                                        description = raw.description,
                                        sourcePlant = raw.sourcePlant,
                                        ifraLimit = raw.ifraLimit,
                                        isCustom = false
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (allEntities.isNotEmpty()) {
                ingredientDao.insertAllIngredients(allEntities)
            }
            return IngredientSeedResult(
                expectedCount = allEntities.size,
                storedCount = ingredientDao.getCount()
            )
        }

        private fun readBundledIngredientCount(context: Context): Int {
            return try {
                val gson = Gson()
                val listType = object : TypeToken<IngredientJsonWrapper>() {}.type
                ingredientAssetPaths().sumOf { filePath ->
                    context.assets.open(filePath).use { stream ->
                        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                            val wrapper: IngredientJsonWrapper = gson.fromJson(reader, listType)
                            wrapper.ingredients.count {
                                IngredientNameCleaner.clean(it.name).isNotBlank()
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Unable to read bundled ingredient count", error)
                -1
            }
        }

        private suspend fun prepopulateDefaultBundles(downloadDao: DownloadDao) {
            try {
                val defaultBundles = listOf(
                    DownloadEntity(
                        id = "llm_gemma_2b",
                        name = "Gemma 4 E2B-it (LiteRT-LM)",
                        fileName = "gemma-4-E2B-it.litertlm",
                        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm",
                        destinationUri = null,
                        totalBytes = 2_588_147_712L,
                        downloadedBytes = 0,
                        status = "NOT_DOWNLOADED",
                        description = "Official Google Gemma 4 on-device AI model for perfume formulation & guidance",
                        isRequired = true
                    ),
                    DownloadEntity(
                        id = "embed_all_minilm",
                        name = "Embedding: all-MiniLM-L6-v2",
                        fileName = "all-MiniLM-L6-v2.tflite",
                        url = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/model.tflite",
                        destinationUri = null,
                        totalBytes = 23_000_000L,
                        downloadedBytes = 23_000_000L,
                        status = "COMPLETED",
                        description = "Pre-bundled 384-dimensional vector embedding model for RAG",
                        isRequired = true
                    ),
                    DownloadEntity(
                        id = "llm_gemma_4b",
                        name = "Gemma 4 E4B-it (High-Capability)",
                        fileName = "gemma-4-E4B-it.litertlm",
                        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/28299f30ee4d43294517a4ac93abd6163412f07f/gemma-4-E4B-it.litertlm",
                        destinationUri = null,
                        totalBytes = 3_659_530_240L,
                        downloadedBytes = 0,
                        status = "NOT_DOWNLOADED",
                        description = "High-precision formulation assistant for high-RAM devices",
                        isRequired = false
                    )
                )
                downloadDao.insertAll(defaultBundles)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to seed default download metadata", e)
            }
        }

        private const val TAG = "ParfumeDatabase"
    }
}

data class IngredientJsonWrapper(
    val version: Int = 1,
    val ingredients: List<RawJsonIngredient> = emptyList()
)

data class RawJsonIngredient(
    val itemId: String? = null,
    val name: String,
    val type: String = "NATURAL",
    val brand: String? = null,
    val noteType: String? = null,
    val fragranceFamily: String? = null,
    val densityGml: Double? = null,
    val casNumber: String? = null,
    val inspiredBy: String? = null,
    val description: String? = null,
    val sourcePlant: String? = null,
    val ifraLimit: Double? = null
)
