# AGENTS.md — Parfume Creator

Panduan ini mendefinisikan rules, konvensi, dan behavior untuk semua coding agent (AI assistant) yang bekerja pada project ini. Baca seluruh dokumen ini sebelum membuat perubahan apapun.

---

## 1. Project Identity

- **Nama Aplikasi:** Parfume Creator
- **Package:** `com.parfumecreator`
- **Platform:** Android (minSdk 26, targetSdk 34)
- **Language:** Kotlin (100%)
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Clean Architecture + MVVM
- **Paradigm:** Offline-First, Local AI

---

## 2. Core Behavior Rules

### 2.1 Anti-Slop / Controlled Output
- Jangan buat kode yang tidak diminta oleh task/PRD.
- Jangan ganti implementasi yang sudah berjalan tanpa bukti kerusakan.
- Jangan duplikasi kode atau buat file alternatif tanpa instruksi eksplisit.
- Modifikasi file yang sudah ada, bukan buat ulang.
- Ikuti DRY, SOLID, KISS, YAGNI, dan arsitektur project yang sudah ada.

### 2.2 Before Modifying Code
1. Inspect existing implementation terlebih dahulu.
2. Identifikasi root cause masalah.
3. Tentukan perubahan minimum yang diperlukan.
4. Modifikasi hanya file yang terdampak.
5. Jalankan lint/test yang relevan.
6. Stop ketika acceptance criteria terpenuhi.

### 2.3 Loop Prevention
- Maksimal 3 attempt untuk pendekatan yang gagal.
- Jika error yang sama muncul 2 kali berturut-turut, STOP dan reassess root cause.
- Jangan ulangi command yang sama dengan harapan hasil berbeda.

---

## 3. Architecture Conventions

### 3.1 Layer Separation (Clean Architecture)

```
Presentation Layer  -->  Domain Layer  -->  Data Layer
(Compose + VM)           (Use Cases)        (Repos + DAOs)
```

**Aturan:**
- Domain layer TIDAK boleh bergantung pada Android framework (no Context, no Room, no SAF).
- Data layer implements interface dari domain layer.
- Presentation layer hanya boleh memanggil Use Cases via ViewModel.
- ViewModel tidak boleh langsung memanggil Repository (harus via Use Case).

### 3.2 Package Structure

```
com.parfumecreator/
├── data/
│   ├── local/
│   │   ├── database/       # Room entities
│   │   ├── dao/            # Room DAOs
│   │   └── converters/     # TypeConverters
│   ├── repository/         # Repository impl
│   └── datasource/         # Local data sources
├── domain/
│   ├── model/              # Domain models (pure Kotlin)
│   ├── repository/         # Repository interfaces
│   └── usecase/            # Use cases (1 file = 1 use case)
├── presentation/
│   └── ui/
│       ├── theme/          # Material 3 theme
│       ├── components/     # Reusable Compose components
│       ├── chat/           # Chat screen + VM + state
│       ├── formula/        # Formula creator screen + VM
│       ├── ingredients/    # Ingredient library screen + VM
│       ├── documents/      # RAG document management
│       ├── download/       # Model download screen + VM
│       └── settings/       # Settings screen + VM
├── di/                     # Hilt modules
└── core/
    ├── embedding/          # ONNX embedding inference
    ├── llm/                # llama.cpp wrapper
    ├── download/           # WorkManager download logic
    ├── document/           # PDF/TXT parsers
    └── ocr/                # ML Kit OCR
```

### 3.3 Naming Conventions

| Element | Convention | Contoh |
|---------|-----------|--------|
| Entity (Room) | `[Name]Entity` | `IngredientEntity` |
| Domain Model | `[Name]` | `Ingredient` |
| DAO | `[Name]Dao` | `IngredientDao` |
| Repository Interface | `[Name]Repository` | `IngredientRepository` |
| Repository Impl | `[Name]RepositoryImpl` | `IngredientRepositoryImpl` |
| Use Case | `[Verb][Noun]UseCase` | `CreateFormulaUseCase` |
| ViewModel | `[Screen]ViewModel` | `ChatViewModel` |
| UI State | `[Screen]UiState` | `ChatUiState` |
| Hilt Module | `[Feature]Module` | `DatabaseModule` |
| Compose Screen | `[Name]Screen` | `ChatScreen` |

---

## 4. Kotlin & Compose Standards

### 4.1 Kotlin
- Gunakan `data class` untuk domain models dan UI state.
- Gunakan `sealed interface` untuk UiState: `Idle`, `Loading`, `Success<T>`, `Error`.
- Hindari `!!` operator. Gunakan `?: return`, `?: throw`, atau `requireNotNull`.
- Semua IO operations harus di `Dispatchers.IO` via coroutine atau suspend fun.
- Gunakan `StateFlow` untuk state yang perlu diobserve oleh UI, bukan `LiveData`.
- Gunakan `Flow` untuk stream data dari database (Room auto-update).

### 4.2 Jetpack Compose
- Setiap screen adalah `@Composable` function yang menerima ViewModel via `hiltViewModel()`.
- State diambil dengan `collectAsStateWithLifecycle()` bukan `collectAsState()`.
- Gunakan `LaunchedEffect` untuk one-time side effects.
- Hindari business logic di dalam Composable; semua logic di ViewModel.
- Gunakan `remember { mutableStateOf() }` hanya untuk local UI state (transient).
- Setiap Composable yang bisa dipakai ulang diletakkan di `components/`.

### 4.3 Error Handling
- Wrap semua Repository calls dalam `try-catch` dan map ke `Result<T>` atau sealed class.
- Tampilkan error ke user via `SnackbarHostState` atau dedicated error UI state.
- Jangan swallow exception secara diam-diam.

---

## 5. Database (Room) Standards

### 5.1 Entity Rules
- Setiap entity WAJIB punya `@PrimaryKey(autoGenerate = true) val id: Long = 0`.
- Timestamp disimpan sebagai `Long` (epoch milliseconds).
- Boolean disimpan sebagai `Int` (0/1) di SQLite, gunakan `@ColumnInfo(typeAffinity = ColumnInfo.INTEGER)`.
- FloatArray untuk embedding disimpan sebagai `BLOB`, gunakan TypeConverter.
- Jangan simpan relational list di dalam entity; gunakan join table.

### 5.2 DAO Rules
- Semua DAO methods harus `suspend fun` (untuk one-shot) atau `fun ... : Flow<...>` (untuk reactive).
- Query yang mengembalikan banyak data harus menggunakan `Flow<List<T>>`.
- Gunakan `@Transaction` untuk operasi yang menyentuh lebih dari satu tabel.

### 5.3 sqlite-vec Integration
- Virtual table `vec_chunks` diinisialisasi di `RoomDatabase.Callback.onCreate()`.
- Insert ke `vec_chunks` HARUS sync dengan insert ke `rag_chunks` dalam satu transaksi.
- Cosine similarity search menggunakan native sqlite-vec SQL: `SELECT id, distance FROM vec_chunks WHERE embedding MATCH ? ORDER BY distance LIMIT ?`

---

## 6. AI & ML Standards

### 6.1 LLM (llama.cpp)
- Model path diambil dari `DataStore` (user-selected SAF URI).
- Model loading dilakukan di `Dispatchers.IO`, hasilnya di-cache di singleton.
- Inference menggunakan `CoroutineScope` dan emits via `Flow<String>` untuk streaming.
- System prompt dikonfigurasi dari `DataStore` (user-editable).
- Context window default: 2048 tokens. Max new tokens: 512.
- Unload model saat aplikasi ke background untuk menghemat RAM (via `ProcessLifecycleOwner`).

### 6.2 Embedding (ONNX Runtime)
- Model `all-MiniLM-L6-v2.onnx` di-load sekali saat aplikasi start.
- Output: 384-dimensi `FloatArray`, dinormalisasi (L2 norm).
- Embedding generation HARUS di `Dispatchers.IO`.
- Tokenizer menggunakan WordPiece tokenizer yang di-bundle dengan model.

### 6.3 RAG Pipeline
```
User Query
   |
   v
[EmbeddingGenerator] -- query embedding (384-dim)
   |
   v
[sqlite-vec] -- cosine similarity search, top-K=5 chunks
   |
   v
[PromptBuilder] -- system_prompt + RAG_context + user_query
   |
   v
[LlmInference] -- streaming generation
   |
   v
ChatUiState.Success (stream ke UI)
```

---

## 7. SAF (Storage Access Framework) Standards

- Gunakan `ACTION_OPEN_DOCUMENT_TREE` untuk memilih folder RAG source.
- Gunakan `ACTION_OPEN_DOCUMENT` untuk memilih file individual.
- Setelah user memilih, panggil `takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`.
- Simpan URI ke `DataStore` untuk akses berikutnya.
- Semua file read/write via `ContentResolver`, bukan `File` API.
- Jangan gunakan absolute path; selalu gunakan `Uri` dari SAF.

---

## 8. Download Manager Standards

- Gunakan `WorkManager` dengan `CoroutineWorker` untuk semua download.
- Penamaan work: `"download_{bundle_id}"` untuk uniqueness.
- Policy: `ExistingWorkPolicy.APPEND_OR_REPLACE`.
- Download ke file `.tmp` terlebih dahulu; rename setelah complete.
- Resume: baca `Content-Range` response header; jika server support, send `Range: bytes={downloaded}-`.
- Progress di-update via `setProgressAsync()` di Worker, di-observe via `WorkManager.getWorkInfoByIdLiveData()`.
- Constraints: `NetworkType.CONNECTED`, `setRequiresStorageNotLow(true)`.
- Backoff: `BackoffPolicy.EXPONENTIAL`, initial delay 10 detik.

---

## 9. Fragrance Calculation Logic

### 9.1 Unit Conversion
```kotlin
fun convertToGrams(amount: Double, unit: MeasurementUnit, density: Double): Double {
    return when (unit) {
        MeasurementUnit.GRAM -> amount
        MeasurementUnit.MILLIGRAM -> amount / 1000.0
        MeasurementUnit.MILLILITER -> amount * density
    }
}
```

### 9.2 Concentration Calculation
```kotlin
fun calculateConcentration(fragranceWeightG: Double, totalWeightG: Double): Double {
    if (totalWeightG <= 0.0) return 0.0
    return (fragranceWeightG / totalWeightG) * 100.0
}

fun getConcentrationType(concentration: Double): ConcentrationType = when {
    concentration >= 20.0 -> ConcentrationType.EXTRAIT
    concentration >= 15.0 -> ConcentrationType.EDP
    concentration >= 8.0  -> ConcentrationType.EDT
    concentration >= 4.0  -> ConcentrationType.EDC
    else                  -> ConcentrationType.EAU_FRAICHE
}
```

### 9.3 SPL Calculation
```kotlin
fun calculateSpl(
    concentration: Double,
    topRatio: Double,
    middleRatio: Double,
    baseRatio: Double
): Double {
    val noteWeightFactor = ((baseRatio * 1.5) + (middleRatio * 1.0) + (topRatio * 0.5)) / 3.0
    val spl = concentration * noteWeightFactor
    return spl.coerceIn(0.0, 100.0)
}

fun getSplLabel(spl: Double): SplLabel = when {
    spl <= 25.0 -> SplLabel.SUBTLE
    spl <= 50.0 -> SplLabel.MODERATE
    spl <= 75.0 -> SplLabel.STRONG
    else        -> SplLabel.INTENSE
}
```

### 9.4 Fragrance Wheel Prediction
```kotlin
fun predictFragranceWheel(ingredients: List<FormulaIngredient>): FragranceWheelResult {
    val scores = mutableMapOf<FragranceFamily, Double>()
    ingredients.forEach { item ->
        val weightG = item.amountInGram
        val family = item.ingredient.fragranceFamily
        scores[family] = (scores[family] ?: 0.0) + weightG
    }
    val dominant = scores.maxByOrNull { it.value }?.key ?: FragranceFamily.FRESH
    return FragranceWheelResult(primary = dominant.toPrimaryWheel(), scores = scores)
}
```

---

## 10. Testing Standards

### 10.1 Unit Tests
- Lokasi: `src/test/java/com/parfumecreator/`
- Framework: JUnit 4 + Mockito/MockK + Turbine (Flow testing)
- Target coverage: Use Cases 100%, Calculators 100%, Repositories 80%
- Setiap Use Case HARUS punya unit test terpisah.
- Gunakan `kotlinx.coroutines.test.runTest` untuk suspend functions.

### 10.2 Integration Tests
- Lokasi: `src/androidTest/java/com/parfumecreator/`
- Test Room DAOs menggunakan `in-memory` database.
- Framework: Espresso + Compose Testing (`createComposeRule`)

### 10.3 What NOT to Test
- Jangan test ViewModel secara isolated; test Use Case + Repository saja.
- Jangan test boilerplate Hilt modules.

---

## 11. Pre-populated Data Standards

### 11.1 Lokasi Data
- File: `src/main/assets/data/ingredients_natural.json`
- File: `src/main/assets/data/ingredients_luzi.json`
- File: `src/main/assets/data/ingredients_parfarome.json`
- File: `src/main/assets/data/ingredients_macrabame.json`

### 11.2 JSON Schema
```json
{
  "version": 1,
  "ingredients": [
    {
      "name": "Bergamot Essential Oil",
      "type": "NATURAL",
      "brand": null,
      "note_type": "TOP",
      "fragrance_family": "CITRUS",
      "density_g_ml": 0.882,
      "cas_number": "8007-75-8",
      "description": "Citrusy, fresh, slightly floral with herbal undertones",
      "source_plant": "Citrus bergamia"
    }
  ]
}
```

### 11.3 Pre-population Strategy
- Gunakan `RoomDatabase.Callback.onCreate()` untuk insert data dari assets JSON.
- Parse JSON menggunakan `Gson` atau Kotlin serialization.
- Insert secara batch menggunakan `DAO.insertAll()`.
- Data bawaan memiliki flag `is_custom = 0` dan tidak bisa dihapus oleh user.

---

## 12. Commit & Branch Standards

### 12.1 Branch Naming
```
feature/[feature-name]     # misal: feature/rag-pipeline
fix/[bug-description]      # misal: fix/saf-uri-persistence
chore/[task]               # misal: chore/add-room-entities
```

### 12.2 Commit Message Format (Conventional Commits)
```
feat(rag): implement PDF chunking with pdfbox-android
fix(formula): correct unit conversion for milliliter input
chore(db): add rag_chunks entity and DAO
test(usecase): add unit tests for CreateFormulaUseCase
```

---

## 13. DO and DON'T

### DO
- Selalu baca file yang akan dimodifikasi sebelum mengedit.
- Gunakan `hiltViewModel()` di Composable, bukan konstruksi manual.
- Emit error state ke UI via `UiState.Error`, jangan crash.
- Gunakan `takePersistableUriPermission` untuk semua SAF URI yang perlu persisted.
- Load model AI di background thread dan cache hasilnya.

### DON'T
- Jangan gunakan `GlobalScope` — selalu gunakan `viewModelScope` atau `lifecycleScope`.
- Jangan hard-code path file — gunakan SAF URI.
- Jangan block main thread dengan operasi IO.
- Jangan gunakan `Thread.sleep()` — gunakan `delay()`.
- Jangan simpan model AI di internal storage app (hilang saat uninstall) — gunakan SAF.
- Jangan gunakan `startActivityForResult` (deprecated) — gunakan `ActivityResultLauncher`.
- Jangan buat singleton sendiri — gunakan Hilt `@Singleton`.
