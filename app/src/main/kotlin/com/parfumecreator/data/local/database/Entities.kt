package com.parfumecreator.data.local.database

import androidx.room.*
import com.parfumecreator.domain.model.*

@Entity(tableName = "ingredients")
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // NATURAL | SYNTHETIC
    val brand: String? = null,
    @ColumnInfo(name = "note_type") val noteType: String,
    @ColumnInfo(name = "fragrance_family") val fragranceFamily: String,
    @ColumnInfo(name = "density_g_ml") val densityGml: Double = 1.0,
    @ColumnInfo(name = "cas_number") val casNumber: String? = null,
    @ColumnInfo(name = "inspired_by") val inspiredBy: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "source_plant") val sourcePlant: String? = null,
    @ColumnInfo(name = "ifra_limit") val ifraLimit: Double? = null,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean = false
) {
    fun toDomain(): Ingredient = Ingredient(
        id = id,
        name = IngredientNameCleaner.clean(name),
        type = if (type.equals("SYNTHETIC", ignoreCase = true)) IngredientType.SYNTHETIC else IngredientType.NATURAL,
        brand = brand,
        noteType = NoteType.fromString(noteType),
        fragranceFamily = FragranceFamily.fromString(fragranceFamily),
        densityGml = densityGml,
        casNumber = casNumber,
        inspiredBy = inspiredBy,
        description = description,
        sourcePlant = sourcePlant,
        ifraLimit = ifraLimit,
        isCustom = isCustom
    )

    companion object {
        fun fromDomain(domain: Ingredient): IngredientEntity = IngredientEntity(
            id = domain.id,
            name = domain.name,
            type = domain.type.name,
            brand = domain.brand,
            noteType = domain.noteType.name,
            fragranceFamily = domain.fragranceFamily.name,
            densityGml = domain.densityGml,
            casNumber = domain.casNumber,
            inspiredBy = domain.inspiredBy,
            description = domain.description,
            sourcePlant = domain.sourcePlant,
            ifraLimit = domain.ifraLimit,
            isCustom = domain.isCustom
        )
    }
}

@Entity(tableName = "formulas")
data class FormulaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "total_weight_g") val totalWeightG: Double,
    @ColumnInfo(name = "concentration_pct") val concentrationPct: Double,
    @ColumnInfo(name = "concentration_type") val concentrationType: String,
    val spl: Double,
    @ColumnInfo(name = "spl_label") val splLabel: String,
    @ColumnInfo(name = "fragrance_wheel_primary") val fragranceWheelPrimary: String,
    @ColumnInfo(name = "fragrance_wheel_sub") val fragranceWheelSub: String,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "formula_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["id"],
            childColumns = ["formula_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["formula_id"]), Index(value = ["ingredient_id"])]
)
data class FormulaIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "formula_id") val formulaId: Long,
    @ColumnInfo(name = "ingredient_id") val ingredientId: Long,
    val amount: Double,
    val unit: String,
    @ColumnInfo(name = "amount_in_gram") val amountInGram: Double,
    val percentage: Double = 0.0
)

data class FormulaWithIngredientsRelation(
    @Embedded val formula: FormulaEntity,
    @Relation(
        entity = FormulaIngredientEntity::class,
        parentColumn = "id",
        entityColumn = "formula_id"
    )
    val formulaIngredients: List<FormulaIngredientWithDetail>
)

data class FormulaIngredientWithDetail(
    @Embedded val link: FormulaIngredientEntity,
    @Relation(
        parentColumn = "ingredient_id",
        entityColumn = "id"
    )
    val ingredient: IngredientEntity
)

@Entity(
    tableName = "rag_documents",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["source_url", "content_hash"], unique = true)
    ]
)
data class RagDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "file_name") val fileName: String,
    val uri: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "chunk_count") val chunkCount: Int = 0,
    @ColumnInfo(name = "indexed_at") val indexedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "source_type") val sourceType: String = SourceType.UPLOAD.name,
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
    @ColumnInfo(name = "content_hash") val contentHash: String? = null,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long? = null,
    @ColumnInfo(name = "expires_at") val expiresAt: Long? = null,
    @ColumnInfo(name = "trust_level") val trustLevel: String = TrustLevel.USER_DOCUMENT.name,
    val availability: String = DocumentAvailability.AVAILABLE.name
) {
    fun toDomain(): RagDocument = RagDocument(
        id = id,
        fileName = fileName,
        uri = uri,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        chunkCount = chunkCount,
        indexedAt = indexedAt,
        isActive = isActive,
        sourceType = SourceType.fromStorage(sourceType),
        sourceUrl = sourceUrl,
        contentHash = contentHash,
        fetchedAt = fetchedAt,
        expiresAt = expiresAt,
        trustLevel = TrustLevel.fromStorage(trustLevel),
        availability = DocumentAvailability.fromStorage(availability)
    )
}

@Entity(
    tableName = "rag_chunks",
    foreignKeys = [
        ForeignKey(
            entity = RagDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["document_id"]),
        Index(value = ["document_id", "chunk_index"], unique = true)
    ]
)
data class RagChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Int,
    val text: String,
    val embedding: ByteArray,
    @ColumnInfo(name = "page_start") val pageStart: Int? = null,
    @ColumnInfo(name = "page_end") val pageEnd: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RagChunkEntity
        return id == other.id && documentId == other.documentId && chunkIndex == other.chunkIndex && text == other.text && embedding.contentEquals(other.embedding) && pageStart == other.pageStart && pageEnd == other.pageEnd
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + (pageStart ?: 0)
        result = 31 * result + (pageEnd ?: 0)
        return result
    }
}

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "model_name") val modelName: String? = null
) {
    fun toDomain(): ChatSession = ChatSession(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        modelName = modelName
    )
}

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_id"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val role: String, // USER | ASSISTANT | SYSTEM
    val content: String,
    @ColumnInfo(name = "rag_sources") val ragSources: String? = null,
    @ColumnInfo(name = "citations_json") val citationsJson: String? = null,
    @ColumnInfo(name = "retrieval_mode") val retrievalMode: String? = null,
    @ColumnInfo(name = "model_status") val modelStatus: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_streaming") val isStreaming: Boolean = false,
    @ColumnInfo(name = "recommended_formula_json") val recommendedFormulaJson: String? = null
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val url: String,
    @ColumnInfo(name = "destination_uri") val destinationUri: String?,
    @ColumnInfo(name = "total_bytes") val totalBytes: Long,
    @ColumnInfo(name = "downloaded_bytes") val downloadedBytes: Long = 0,
    val status: String, // NOT_DOWNLOADED | QUEUED | DOWNLOADING | PAUSED | COMPLETED | FAILED
    val description: String = "",
    @ColumnInfo(name = "is_required") val isRequired: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

object IngredientNameCleaner {
    private val NON_MATERIAL_LISTING = Regex(
        "(?i)\\b(?:kaleng kosong|botol kosong|botol parfum|sumbu lilin|candle wick|silicone mold|cetakan lilin|" +
            "cetakan silikon|pot cream|spray bottle|holder wood|wood centering tool|" +
            "all product|all luzi packing|all parfarome packing|all macrabame packing)\\b"
    )
    // Matches any leading parentheses/bracket groups like (&), ( & ), (10 pcs), (new version), (A-B), (Part 1), [REPACK], etc.
    private val LEADING_GARBAGE_BRACKETS = Regex("""^[\(\[\{][^\)\]\}]*[\)\]\}]\s*""", RegexOption.IGNORE_CASE)
    private val LEADING_PUNCTUATION = Regex("""^[/\s\-|–—:;,.*#+&]+""")
    private val REMOVE_PIPE_AND_AFTER = Regex("""\|.*$""")
    private val REMOVE_SELLER_BOILERPLATE = Regex("""\s*-\s*NOIN(?:\s+SCENT)?\b.*$|\s+Biang\s+Bibit.*$|\s+Segel\s+\d+.*$|\s*-\s*Aroma\s+Ingredients.*$|\s*-\s*Single\s+Note.*$|\s*-\s*Pure\s+Isolates.*$|\s*-\s*Grade\s+A.*$""", RegexOption.IGNORE_CASE)
    private val REMOVE_BRAND_SUFFIX = Regex("""\s*(?:Product\s+By|Product\s+BY|By|Produk\s+By)\s+(?:PARFAROME|LUZI|MACBRAME|MACRABAME|BELL|SFA|IFF|LABOR|PARFEX|BENBERG|SEKAWAN|EXPRESSIONS|TRIEFTA|ZAPHIR)(?:\s+(?:PREMIUM|EXCLUSIVE|PLATINUM|RESERVE))?\s*.*$""", RegexOption.IGNORE_CASE)
    private val REMOVE_GENDER_BOILERPLATE = Regex("""\s+(?:FOR\s+(?:WOMEN\s+AND\s+MEN|MEN\s+AND\s+WOMEN|WOMEN|MEN|UNISEX))\b.*$""", RegexOption.IGNORE_CASE)
    private val REMOVE_PACKAGING_SUFFIX = Regex("""\s*(?:Segel|Repack|Packing|Kemasan|Botol|Kaleng)\s+\d+.*$|\s+\d+\s*(?:gr|gram|kg|ml|l|liter)\b.*$""", RegexOption.IGNORE_CASE)
    private val REMOVE_GENERIC_FRAGRANCE_SUFFIX = Regex("""\s+(?:FINE\s+)?FRAGRANCE(?:\s+OIL)?(?:\s*-\s*NOIN)?\s*$|\s+ESSENTIAL\s+OIL\s*$""", RegexOption.IGNORE_CASE)
    private val TRAILING_SYMBOLS = Regex("""[/\s\-|–—:;,.*#+&]+$""")

    fun clean(raw: String): String {
        if (raw.isBlank() || NON_MATERIAL_LISTING.containsMatchIn(raw)) return ""
        var text = raw.trim()

        // 1. Loop to strip leading brackets / symbols e.g. "(&) ", "( & ) ", "/ (new version) "
        var changed = true
        var loops = 0
        while (changed && loops < 5) {
            val before = text
            text = text.replace(LEADING_PUNCTUATION, "").trim()
            text = text.replace(LEADING_GARBAGE_BRACKETS, "").trim()
            text = text.replace(LEADING_PUNCTUATION, "").trim()
            changed = (before != text)
            loops++
        }

        // 2. Remove seller description after pipe '|'
        text = text.replace(REMOVE_PIPE_AND_AFTER, "")

        // 3. Remove seller brand boilerplate like "- NOIN", "Biang Bibit..."
        text = text.replace(REMOVE_SELLER_BOILERPLATE, "")

        // 4. Remove gender suffixes like "FOR WOMEN AND MEN"
        text = text.replace(REMOVE_GENDER_BOILERPLATE, "")

        // 5. Remove "By PARFAROME PREMIUM", "Product By LUZI", etc.
        text = text.replace(REMOVE_BRAND_SUFFIX, "")

        // 6. Remove packaging suffixes like "Segel 1Kg", "Repack 100ml"
        text = text.replace(REMOVE_PACKAGING_SUFFIX, "")

        // 7. Remove redundant trailing "FRAGRANCE OIL" or "FRAGRANCE" if name has specific scent
        val cleanedFragrance = text.replace(REMOVE_GENERIC_FRAGRANCE_SUFFIX, "").trim()
        if (cleanedFragrance.isNotBlank() && cleanedFragrance.length > 2) {
            text = cleanedFragrance
        }

        // 8. Remove trailing hyphens, symbols or dangling punctuation
        text = text.replace(TRAILING_SYMBOLS, "").trim()

        if (text.isBlank()) return raw.trim()

        // 9. Format Title Case for UI/UX typography if all uppercase or lowercase
        val letters = text.filter { it.isLetter() }
        if (letters.isNotEmpty() && (letters.all { it.isUpperCase() } || text == text.lowercase())) {
            text = toTitleCase(text)
        }

        return text
    }

    fun toTitleCase(input: String): String {
        return input.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            when (word.uppercase()) {
                "EDP", "EDT", "EDC", "IFRA", "SPL", "CAS", "VS", "LV", "DPG", "ISO", "CK" -> word.uppercase()
                "&", "AND", "OR", "OF", "THE", "BY", "FOR" -> word.lowercase()
                else -> word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
