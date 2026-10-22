package com.parfumecreator

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.parfumecreator.core.document.TextChunker
import com.parfumecreator.core.download.ModelArtifactRegistry
import com.parfumecreator.core.web.WikimediaInternetClient
import com.parfumecreator.data.local.database.ParfumeDatabase
import com.parfumecreator.domain.model.SourceCitation
import com.parfumecreator.domain.model.SourceType
import com.parfumecreator.domain.model.TrustLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerfumeryCompanionAndroidTest {

    @Test
    fun webAndModelAllowListsRejectUnsafeTargets() {
        val web = WikimediaInternetClient()
        assertTrue(web.isAllowedPublicUrl("https://en.wikipedia.org/wiki/Perfume"))
        assertTrue(web.isAllowedPublicUrl("https://ifrafragrance.org/initiatives-positions/safe-use-fragrance-science/ifra-standards"))
        assertFalse(web.isAllowedPublicUrl("http://en.wikipedia.org/wiki/Perfume"))
        assertFalse(web.isAllowedPublicUrl("https://evil.example/wiki/Perfume"))
        assertFalse(web.isAllowedPublicUrl("https://user:password@en.wikipedia.org/wiki/Perfume"))
        assertFalse(web.isAllowedPublicUrl("https://en.wikipedia.org:8443/wiki/Perfume"))

        assertTrue(ModelArtifactRegistry.isAllowedDownloadUrl(ModelArtifactRegistry.all().first().pinnedUrl))
        // Hugging Face currently serves these pinned Xet artifacts through
        // this exact signed CDN host after the initial 302 response.
        assertTrue(ModelArtifactRegistry.isAllowedDownloadUrl("https://us.aws.cdn.hf.co/xet-bridge-us/model"))
        assertFalse(ModelArtifactRegistry.isAllowedDownloadUrl("http://huggingface.co/model"))
        assertFalse(ModelArtifactRegistry.isAllowedDownloadUrl("https://example.com/model"))
    }

    @Test
    fun unicodeChunkingAndCitationJsonRemainBounded() {
        val chunks = TextChunker.chunkText("parfum ${"香水🌿".repeat(100)} selesai", maxChars = 80)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.length <= 80 })
        assertTrue(chunks.joinToString(" ").contains("selesai"))

        val citation = SourceCitation(
            sourceType = SourceType.WEB,
            title = "Perfume",
            excerpt = "bounded excerpt",
            score = 0.8f,
            url = "https://en.wikipedia.org/wiki/Perfume",
            contentHash = "a".repeat(64),
            trustLevel = TrustLevel.WEB_UNTRUSTED,
            pageStart = 2,
            pageEnd = 2
        )
        val json = Gson().toJson(listOf(citation))
        val type = object : TypeToken<List<SourceCitation>>() {}.type
        val restored: List<SourceCitation> = Gson().fromJson(json, type)
        assertEquals(citation, restored.single())
        assertNotNull(restored.single().url)
    }

    @Test
    fun roomMigrationAddsMetadataAndDeduplicatesLegacyRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "parfume_migration_${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(LegacySchemaCallback())
                .build()
        )

        try {
            val db = helper.writableDatabase
            seedLegacyRows(db)
            ParfumeDatabase.MIGRATION_1_2.migrate(db)
            ParfumeDatabase.MIGRATION_2_3.migrate(db)

            assertEquals(1, scalarInt(db, "SELECT COUNT(*) FROM rag_documents"))
            assertEquals(1, scalarInt(db, "SELECT COUNT(*) FROM rag_chunks"))
            assertTrue(tableColumns(db, "rag_documents").containsAll(listOf(
                "source_type", "source_url", "content_hash", "fetched_at", "expires_at",
                "trust_level", "availability"
            )))
            assertTrue(tableColumns(db, "chat_messages").containsAll(listOf(
                "citations_json", "retrieval_mode", "model_status"
            )))
            assertTrue(indexNames(db, "rag_documents").contains("index_rag_documents_uri"))
            assertTrue(indexNames(db, "rag_documents").contains("index_rag_documents_source_url_content_hash"))
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun seedLegacyRows(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("CREATE TABLE chat_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, model_name TEXT)")
        db.execSQL("CREATE TABLE rag_documents (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, file_name TEXT NOT NULL, uri TEXT NOT NULL, mime_type TEXT, size_bytes INTEGER NOT NULL, chunk_count INTEGER NOT NULL, indexed_at INTEGER NOT NULL, is_active INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE rag_chunks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, document_id INTEGER NOT NULL, chunk_index INTEGER NOT NULL, text TEXT NOT NULL, embedding BLOB NOT NULL, FOREIGN KEY(document_id) REFERENCES rag_documents(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE chat_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, session_id INTEGER NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, rag_sources TEXT, timestamp INTEGER NOT NULL, is_streaming INTEGER NOT NULL, recommended_formula_json TEXT, FOREIGN KEY(session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE)")
        db.execSQL("INSERT INTO chat_sessions(title, created_at, updated_at, model_name) VALUES ('legacy', 1, 1, NULL)")
        db.execSQL("INSERT INTO rag_documents(file_name, uri, mime_type, size_bytes, chunk_count, indexed_at, is_active) VALUES ('old-a.txt', 'content://same', 'text/plain', 4, 1, 1, 1)")
        db.execSQL("INSERT INTO rag_documents(file_name, uri, mime_type, size_bytes, chunk_count, indexed_at, is_active) VALUES ('old-b.txt', 'content://same', 'text/plain', 4, 1, 2, 1)")
        db.execSQL("INSERT INTO rag_chunks(document_id, chunk_index, text, embedding) VALUES (1, 0, 'old', X'00000000')")
        db.execSQL("INSERT INTO rag_chunks(document_id, chunk_index, text, embedding) VALUES (2, 0, 'new', X'00000000')")
        db.execSQL("INSERT INTO chat_messages(session_id, role, content, rag_sources, timestamp, is_streaming, recommended_formula_json) VALUES (1, 'USER', 'legacy', NULL, 1, 0, NULL)")
    }

    private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> = buildSet {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }

    private fun indexNames(db: SupportSQLiteDatabase, table: String): Set<String> = buildSet {
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }

    private fun scalarInt(db: SupportSQLiteDatabase, query: String): Int = db.query(query).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private class LegacySchemaCallback : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) = Unit

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
