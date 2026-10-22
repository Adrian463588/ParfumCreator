package com.parfumecreator.data.local.dao

import androidx.room.*
import com.parfumecreator.data.local.database.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients ORDER BY is_custom DESC, name ASC")
    fun getAllIngredients(): Flow<List<IngredientEntity>>

    @Query("""
        SELECT * FROM ingredients
        WHERE (:query = '' OR name LIKE '%' || :query || '%' OR inspired_by LIKE '%' || :query || '%')
        AND (
            :brand IS NULL
            OR (:brand = 'NATURAL' AND type = 'NATURAL')
            OR (:brand = 'CUSTOM' AND is_custom = 1)
            OR (:brand = 'MACRABAME' AND (brand = 'MACRABAME' OR brand = 'MACBRAME' OR name LIKE '%MACBRAME%'))
            OR (brand = :brand)
        )
        AND (
            :noteType IS NULL
            OR note_type = :noteType
            OR (:noteType = 'TOP' AND note_type = 'TOP')
            OR (:noteType = 'HEART' AND (note_type = 'HEART' OR note_type = 'MIDDLE'))
            OR (:noteType = 'MIDDLE' AND (note_type = 'HEART' OR note_type = 'MIDDLE'))
            OR (:noteType = 'BASE' AND note_type = 'BASE')
        )
        AND (
            :family IS NULL
            OR fragrance_family = :family
            OR (:family = 'FLORAL' AND (fragrance_family = 'FLORAL' OR fragrance_family = 'ALDEHYDIC'))
            OR (:family = 'WOODY' AND (fragrance_family = 'WOODY' OR fragrance_family = 'EARTHY' OR fragrance_family = 'CHYPRE' OR fragrance_family = 'LEATHER'))
            OR (:family = 'ORIENTAL' AND (fragrance_family = 'ORIENTAL' OR fragrance_family = 'GOURMAND' OR fragrance_family = 'SPICY' OR fragrance_family = 'MUSKY'))
            OR (:family = 'FRESH' AND (fragrance_family = 'FRESH' OR fragrance_family = 'CITRUS' OR fragrance_family = 'FOUGERE' OR fragrance_family = 'AQUATIC' OR fragrance_family = 'GREEN' OR fragrance_family = 'FRUITY'))
        )
        ORDER BY is_custom DESC, name ASC
    """)
    fun searchIngredients(
        query: String,
        brand: String?,
        noteType: String?,
        family: String?
    ): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients ORDER BY is_custom DESC, name ASC")
    suspend fun getAllIngredientsList(): List<IngredientEntity>

    @Query("""
        SELECT * FROM ingredients
        WHERE (:query = '' OR name LIKE '%' || :query || '%' OR inspired_by LIKE '%' || :query || '%')
        ORDER BY is_custom DESC, name ASC
    """)
    suspend fun searchIngredientsByQueryList(query: String): List<IngredientEntity>

    @Query("SELECT * FROM ingredients WHERE id = :id LIMIT 1")
    suspend fun getIngredientById(id: Long): IngredientEntity?

    @Query("SELECT * FROM ingredients WHERE name = :name LIMIT 1")
    suspend fun getIngredientByName(name: String): IngredientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: IngredientEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIngredients(ingredients: List<IngredientEntity>): List<Long>

    @Delete
    suspend fun deleteIngredient(ingredient: IngredientEntity)

    @Query("SELECT COUNT(*) FROM ingredients")
    suspend fun getCount(): Int
}

@Dao
interface FormulaDao {
    @Transaction
    @Query("SELECT * FROM formulas ORDER BY updated_at DESC")
    fun getAllFormulasWithIngredients(): Flow<List<FormulaWithIngredientsRelation>>

    @Transaction
    @Query("SELECT * FROM formulas WHERE id = :id LIMIT 1")
    suspend fun getFormulaWithIngredientsById(id: Long): FormulaWithIngredientsRelation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormula(formula: FormulaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormulaIngredients(items: List<FormulaIngredientEntity>)

    @Query("DELETE FROM formula_ingredients WHERE formula_id = :formulaId")
    suspend fun deleteFormulaIngredientsByFormulaId(formulaId: Long)

    @Query("DELETE FROM formulas WHERE id = :id")
    suspend fun deleteFormulaById(id: Long)

    @Transaction
    suspend fun saveFormulaWithIngredients(
        formula: FormulaEntity,
        items: List<FormulaIngredientEntity>
    ): Long {
        val formulaId = insertFormula(formula)
        deleteFormulaIngredientsByFormulaId(formulaId)
        val linkedItems = items.map { it.copy(formulaId = formulaId) }
        insertFormulaIngredients(linkedItems)
        return formulaId
    }
}

@Dao
interface RagDao {
    @Query("SELECT * FROM rag_documents WHERE is_active = 1 ORDER BY indexed_at DESC")
    fun getAllDocuments(): Flow<List<RagDocumentEntity>>

    @Query("SELECT * FROM rag_documents WHERE uri = :uri LIMIT 1")
    suspend fun getDocumentByUri(uri: String): RagDocumentEntity?

    @Query("DELETE FROM rag_documents WHERE uri = :uri")
    suspend fun deleteDocumentByUri(uri: String)

    @Query("DELETE FROM rag_documents WHERE source_url = :sourceUrl AND content_hash = :contentHash")
    suspend fun deleteDocumentBySourceUrlAndHash(sourceUrl: String, contentHash: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: RagDocumentEntity): Long

    @Query("DELETE FROM rag_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("UPDATE rag_documents SET availability = :availability WHERE id = :id")
    suspend fun updateDocumentAvailability(id: Long, availability: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<RagChunkEntity>)

    @Query("UPDATE rag_documents SET chunk_count = :chunkCount, indexed_at = :indexedAt, is_active = 1 WHERE id = :docId")
    suspend fun updateDocumentChunkCount(docId: Long, chunkCount: Int, indexedAt: Long)

    @Query("SELECT * FROM rag_chunks WHERE document_id = :docId ORDER BY chunk_index ASC")
    suspend fun getChunksByDocId(docId: Long): List<RagChunkEntity>

    @Transaction
    suspend fun replaceDocument(
        document: RagDocumentEntity,
        chunks: List<RagChunkEntity>
    ): RagDocumentEntity {
        deleteDocumentByUri(document.uri)
        if (!document.sourceUrl.isNullOrBlank() && !document.contentHash.isNullOrBlank()) {
            deleteDocumentBySourceUrlAndHash(document.sourceUrl, document.contentHash)
        }
        val documentId = insertDocument(document.copy(id = 0L))
        val linkedChunks = chunks.map { it.copy(documentId = documentId) }
        insertChunks(linkedChunks)
        updateDocumentChunkCount(documentId, linkedChunks.size, document.indexedAt)
        return document.copy(
            id = documentId,
            chunkCount = linkedChunks.size
        )
    }

    @Query("""
        SELECT c.*, d.file_name AS docFileName,
            d.uri AS docUri,
            d.source_type AS docSourceType,
            d.source_url AS docSourceUrl,
            d.content_hash AS docContentHash,
            d.fetched_at AS docFetchedAt,
            d.expires_at AS docExpiresAt,
            d.trust_level AS docTrustLevel,
            d.availability AS docAvailability
        FROM rag_chunks c
        INNER JOIN rag_documents d ON c.document_id = d.id
        WHERE d.is_active = 1
    """)
    suspend fun getAllActiveChunks(): List<RagChunkWithDocName>
}

data class RagChunkWithDocName(
    @Embedded val chunk: RagChunkEntity,
    val docFileName: String,
    val docUri: String,
    val docSourceType: String,
    val docSourceUrl: String?,
    val docContentHash: String?,
    val docFetchedAt: Long?,
    val docExpiresAt: Long?,
    val docTrustLevel: String,
    val docAvailability: String
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updated_at DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

    @Query("UPDATE chat_sessions SET title = :title, updated_at = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: Long, title: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySessionId(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET content = :content, is_streaming = :isStreaming WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String, isStreaming: Boolean)

    @Query("UPDATE chat_sessions SET updated_at = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionTimestamp(sessionId: Long, timestamp: Long)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY is_required DESC, name ASC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(downloads: List<DownloadEntity>)

    @Query("UPDATE downloads SET downloaded_bytes = :downloaded, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, downloaded: Long, status: String)

    @Query("UPDATE downloads SET status = :status, destination_uri = :destUri WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, destUri: String?)

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloadsList(): List<DownloadEntity>
}
