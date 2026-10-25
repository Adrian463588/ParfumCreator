# IMPLEMENTATION AUDIT — PARFUME CREATOR

**Date**: 2026-08-20  
**Target Platform**: Android (API 26–35)  
**Hardware Tested**: Samsung Galaxy S20 Ultra (`SM-G988B`, Android 13)  
**Architecture**: Clean Architecture + MVVM + Jetpack Compose (M3) + Room + Hilt + Coroutines/Flow  
**Design System**: Luxury Apothecary Dark Palette (`#12100E`, `#E5A93C`, `#2D6A4F`, `#1C1917`)  

---

## 1. Executive Summary & Verification Matrix

The repository has been audited against the canonical specifications (`PRD.md`, `AGENTS.md`, `DESIGN.md`). All mockups, fake implementations, and UI clipping bugs have been removed and replaced with production-grade business logic, Room persistence, and mathematical calculation models.

| Requirement ID | Specification | Status | Implementation Details & Evidence |
|---|---|---|---|
| **FR-01** | AI RAG Lokal (SAF, Chunking, TfLite 384-d, Cosine Similarity) | **IMPLEMENTED** | `PdfTextExtractor`, `TextChunker` (512-word sliding window, 50-word overlap), `TfLiteEmbeddingService` (`all-MiniLM-L6-v2.tflite`), `RagRepositoryImpl` vector similarity search, `DocumentsScreen` with SAF file picker. |
| **FR-02** | Database Bahan Parfum Natural | **IMPLEMENTED** | 2,047 real items preloaded in Room DB (`ingredients_natural.json`, `ingredients_luzi.json`, `ingredients_parfarome.json`, `ingredients_macrabame.json`), filtered by note & family, detail bottom sheet. |
| **FR-03** | Database Synthetic / Brand (Luzi, Parfarome, Macrabame) | **IMPLEMENTED** | Pre-populated brand datasets, brand filter chips in `IngredientLibraryScreen`, inspired-by search, density, note classification. |
| **FR-04** | Data Raw Material / Natural Fragrance | **IMPLEMENTED** | CAS numbers, plant sources, olfactory descriptions, IFRA limits, custom ingredient creation and CSV import (`importIngredientsFromCsv`). |
| **FR-05** | Formula Creator, Satuan Ukur, Visualisasi Fragrance Wheel | **IMPLEMENTED** | Pure Kotlin `FormulaCalculationEngine`, live weight/percentage calculations, multi-unit conversions (g, mg, ml with density), custom Canvas-rendered 4-axis Michael Edwards Radar Chart (`FLORAL`, `WOODY`, `ORIENTAL`, `FRESH`), 12-family breakdown sheet, Room CRUD with auto-increment ID handling. |
| **FR-06** | Konsentrasi & SPL Engine (Sillage, Projection, Longevity) | **IMPLEMENTED** | IFRA concentration tiers (Parfum Extrait `>=20%`, EDP `15-20%`, EDT `8-15%`, EDC `4-8%`, Eau Fraîche `<4%`), SPL formula with weighted base (1.5x), heart (1.0x), and top (0.5x) note factors. |
| **FR-07** | Local AI Chatbot (LiteRT-LM & GGUF, Zero Telemetry) | **IMPLEMENTED** | `LiteRtLmRunner` streaming inference, RAG context injection, `StructuredFormulaParser` for JSON recipe detection with 1-click import into Formula Creator, offline model status fallback. |
| **FR-08** | Model & Bundle Download Manager (Resumable WorkManager) | **IMPLEMENTED** | `DownloadWorker` supporting HTTP `Range: bytes={x}-` resume, `.tmp` file staging, SAF external folder storage selector, progress state machine (`NOT_DOWNLOADED`, `DOWNLOADING`, `COMPLETED`, `FAILED`). |

---

## 2. Codebase Architecture & Integrity

```
com.parfumecreator
├── core
│   ├── document          # PdfTextExtractor, TextChunker
│   ├── download          # DownloadWorker (Resumable WorkManager)
│   ├── embedding         # TfLiteEmbeddingService, BertTokenizer, CosineSimilarity
│   └── llm               # ModelManager, LiteRtLmRunner, StructuredFormulaParser
├── data
│   ├── local
│   │   ├── dao           # IngredientDao, FormulaDao, RagDao, ChatDao, DownloadDao
│   │   └── database      # ParfumeDatabase, Room Entities, Converters, Prepopulate Callbacks
│   └── repository        # RepositoriesImpl, SettingsRepositoryImpl
├── domain
│   ├── engine            # FormulaCalculationEngine (Pure Kotlin, Math & Wheel Logic)
│   ├── model             # Formula, Ingredient, Rag, Chat, Download models & Enums
│   ├── repository        # Domain Repository Interfaces
│   └── usecase           # Single-responsibility UseCases (CRUD, RAG, Calculation, Chat)
└── presentation
    └── ui
        ├── chat          # ChatScreen, ChatViewModel, Bubble & Citation Cards
        ├── components    # FragranceWheelCard (Custom Canvas Radar), BottomSheets, Dialogs
        ├── documents     # DocumentsScreen, DocumentsViewModel (SAF Document Indexing)
        ├── download      # DownloadScreen, DownloadViewModel (Bundle Manager)
        ├── formula       # FormulaScreen, FormulaViewModel (Adaptive formulation editor)
        ├── ingredients   # IngredientLibraryScreen, IngredientViewModel (Catalog, CSV Import)
        ├── navigation    # AppNavigation, BottomNav, TopAppBar Routing
        ├── settings      # SettingsScreen, SettingsViewModel (Persona & Unit Config)
        └── theme         # Luxury Apothecary Theme, Palette, Typography, Dimens
```

---

## 3. Detailed Verification & Test Evidence

### 3.1 Automated Test Execution
- **Command**: `./gradlew.bat test`
- **Result**: `BUILD SUCCESSFUL in 2m 24s`
- **Tasks Executed**: 67 tasks executed across debug and release unit test suites with **0 failures**.
- **Coverage**:
  - `FormulaCalculationEngineTest`: Unit conversion (g, mg, ml with density), concentration classification, SPL calculations, Michael Edwards 4-axis radar scores, primary/secondary fragrance wheel predictions.
  - `FormulaUseCasesTest`: Formula CRUD, custom ingredient persistence, CSV file stream parsing.
  - `SendChatMessageUseCaseTest`: Message session orchestration, RAG retrieval injection, structured recipe JSON parsing.
  - `SettingsUseCaseTest`: User settings persistence, system prompt customization, default units.
  - `TextChunkerAndParserTest`: Text sliding-window chunking, structured LLM JSON extraction, Cosine similarity math.
  - `DataIngestionTest`: Noise filter exclusion, brand regex detection (Luzi, Parfarome, Macrabame).

### 3.2 Physical Hardware Testing (Samsung Galaxy S20 Ultra)
- **APK Build**: `./gradlew.bat assembleDebug` -> `BUILD SUCCESSFUL in 53s`.
- **Streamed Installation**: `adb install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`.
- **Live Device Validations**:
  1. **Formula Creation**: Added natural and synthetic ingredients, modified amounts, verified real-time calculation of total weight, concentration percentage, SPL score, and dynamic polygon radar chart.
  2. **Formula Persistence**: Saved formula with name & description, verified database auto-increment ID (`id=0L` sanitized), opened saved formulas bottom sheet, loaded formula back into editor.
  3. **Settings Navigation**: Accessed Settings from TopAppBar gear icon across screens, adjusted persona and default units, navigated back seamlessly.
  4. **Catalog Navigation**: Browsed 2,047 real items across Luzi, Parfarome, Macrabame, and Natural categories with multi-filter chips.
  5. **RAG & Download UI**: Tested document indexing interface and offline bundle download manager.

---

## 4. Spec Discrepancy & Technical Decisions

1. **Embedding Model**: PRD mentioned ONNX or LiteRT-LM for embeddings. The codebase utilizes Google TensorFlow Lite with bundled `all-MiniLM-L6-v2.tflite` (384-dimensional vector embeddings) and `vocab.txt` tokenizer. This provides superior Android CPU/NNAPI throughput with zero runtime download requirement for embeddings.
2. **Vector Database**: Instead of external `sqlite-vec` native binary dependencies, vector chunks are stored as binary FloatArray BLOBs in Room (`rag_chunks`) and scored via fast in-memory cosine similarity matrix vectorization in `RagRepositoryImpl`. This guarantees 100% Android NDK binary compatibility across ARM64, ARMv7, and x86_64 devices.
3. **Formula Entity Auto-Increment ID**: Resolved Room `PrimaryKey(autoGenerate = true)` collision where passing non-zero initial entity IDs prevented auto-generation. Added domain-to-entity ID mapping ensuring `id = 0L` is used for new inserts.

---

## 5. Historical Baseline Verdict (2026-08-20)

Status: historical and superseded by the current AI Companion audit below. The
statements in this section are not evidence that the current model, retrieval,
upload, or internet-fallback path is ready. In particular, a compiled APK or a
UI smoke test cannot prove that a multi-gigabyte LiteRT-LM artifact passes its
checksum and runtime verification.

- **Historical Quality Gates**: PASS for the baseline checks recorded at that time.
- **Historical Offline Scope**: the baseline was documented as on-device only;
  the current contract is local-first with opt-in, allowlisted web fallback.
- **Current production status**: see Section 8; real model and end-to-end
  inference acceptance remain separate from compilation and UI evidence.

---

## 6. Incremental AI/RAG Repair — 2026-08-25

Bagian ini adalah status teknis terbaru untuk chatbot perfumery dan mengoreksi klaim kesiapan model lokal pada audit historis di atas.

- `PerfumeIntentClassifier` sekarang memisahkan pertanyaan knowledge, template, racikan baru, dan rekomendasi katalog. Token penting seperti `buatkan`, `EDP`, `EDT`, dan `extrait` dipertahankan dalam normalisasi query.
- `PerfumeryKnowledgeLoader` memuat snapshot pengetahuan offline ber-provenance dari sumber publik. Snapshot ini diringkas/parafrasekan, menyimpan URL dan tanggal pengambilan, dan tidak melakukan scraping live di perangkat.
- `GroundedFormulaPlanner` dapat membuat formula baru di luar template. Setiap bahan dan pelarut harus berasal dari baris katalog Room dengan ID nyata; bila kandidat atau pelarut tidak tersedia, jawaban fail-closed dan tidak mengarang bahan.
- Formula template tetap memakai baris template yang tersimpan. Rekomendasi produk mengembalikan produk katalog aktual beserta brand, note, family, dan ID.
- `RagRepositoryImpl` menggunakan normalizer yang sama dengan router dan menggabungkan kandidat knowledge, template, dan katalog bahan ke pencarian hybrid dense/lexical.
- Untuk intent formula, planner deterministik digunakan sebelum LLM agar jumlah, ID bahan, konsentrasi, dan total batch tidak diserahkan kepada halusinasi model kecil. LLM lokal tetap dipakai untuk jawaban bebas/knowledge bila artefak model final tersedia.

### Batas dan bukti terbaru

- `cacheDir` pada LiteRT-LM adalah cache artefak/runtime. Reuse attention KV dilakukan oleh `Conversation` yang dipertahankan per sesi, bukan oleh `cacheDir` saja.
- Artefak Gemma E2B pada perangkat terakhir masih berupa file sementara/incomplete; karena itu status inferensi LLM lokal tetap `UNAVAILABLE`. Fallback grounded bukan bukti bahwa model lokal sudah berhasil dimuat.
- Snapshot knowledge saat ini berisi 13 entri terkurasi untuk dasar parfum, konsentrasi, keluarga aroma, piramida note, IFRA, CosIng, FDA, sensitivitas, PubChem, penyimpanan, serta maturasi/macerasi. Dataset produk lokal tetap menjadi sumber utama formula dan rekomendasi.
- Klaim keselamatan, IFRA, masa simpan, dan maturasi selalu diberi caveat; formula yang dihasilkan adalah proposal berbasis metadata katalog, bukan sertifikat keamanan atau hasil uji stabilitas.

### Verification run 2026-08-25

- `./gradlew.bat testDebugUnitTest lintDebug assembleDebug`: **PASS**. Lint report: `app/build/reports/lint-results-debug.html`.
- Final APK installation with `adb -s RRCN3008VYE install -r -d`: **Success**. Device: Samsung `SM-G988B`, Android 13.
- Prior functional device smoke on the same device/build line verified novel formula, knowledge answer, catalog recommendation, and template formula. The final UI retest was not claimed because an unrelated Android permission dialog for `Quran Plus` covered the foreground window; no permission or data of that app was changed.
- Local model acceptance remains separate: the selected Gemma E2B artifact previously inspected was incomplete, so the app must continue to report local LLM `UNAVAILABLE` until the exact final file passes the byte-size/name validation.

---

## 7. AI Companion: Local RAG → Internet Fallback — 2026-08-25

- Chat sekarang memprioritaskan katalog, knowledge snapshot, dan dokumen PDF/TXT/MD/CSV/JSON yang di-upload melalui SAF. Chunk dokumen di-embed dengan MiniLM lokal dan dicari memakai hybrid dense/lexical retrieval.
- Untuk intent knowledge, `CompanionRetrievalPolicy` mengizinkan internet hanya ketika evidence lokal kosong atau skor terbaik di bawah ambang. Intent template, formula baru, dan rekomendasi produk tidak boleh memakai web snippet sebagai sumber bahan atau ID katalog.
- Fallback internet memakai Wikimedia REST search pada host allowlist `id.wikipedia.org` lalu `en.wikipedia.org`. Query dipotong, URL/email/nomor telepon dihapus, request memakai HTTPS, timeout, response-size cap, rate spacing, cache kecil, User-Agent, dan redirect dimatikan.
- Web snippet diperlakukan sebagai data tidak tepercaya: prompt-injection pattern dibuang, hasil tidak dimasukkan permanen ke dataset lokal, URL dan judul sumber ditampilkan pada jawaban, dan dokumen upload tidak dikirim ke internet.
- UI chat sekarang menampilkan bento card status `Local RAG`, `Internet fallback`, dan inference. Chip sumber membedakan dokumen lokal (`📄`) dari internet (`🌐`).

### Research basis

- MediaWiki REST API mendokumentasikan endpoint pencarian `/search/page?q=...` dan JSON result pages: https://www.mediawiki.org/wiki/API:REST_API/Reference/en
- Android mendokumentasikan HTTPS clients, timeout, repository encapsulation, dan network work di luar main thread: https://developer.android.com/develop/connectivity/network-ops/connecting
- OWASP merekomendasikan allowlist destination, validasi redirect, HTTPS-only schemes, response/time limits, dan tidak menerima URL arbitrary untuk outbound fetch: https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html

### Verification companion final 2026-08-25

- `./gradlew.bat testDebugUnitTest lintDebug assembleDebug`: **PASS** (`BUILD SUCCESSFUL`, 62 actionable tasks); report lint: `app/build/reports/lint-results-debug.html`.
- Endpoint Wikimedia yang dipakai adapter diverifikasi dari host dengan HTTPS `200` dan JSON result page; tidak ada scraping arbitrary atau unggah dokumen lokal.
- APK terbaru dipasang ke Samsung `RRCN3008VYE` dengan `adb install -r -d`: **Success**. Activity foreground terverifikasi dan logcat 300 baris terakhir tidak menemukan fatal exception `com.parfumecreator`.
- Smoke UI final menampilkan bento `AI companion parfum`, `Local-first • Local RAG`, tile `Dokumen`, `Internet`, `Inference`, badge `RAG`, dan input companion. Query knowledge di perangkat tetap memilih evidence lokal ketika cukup; web fallback tetap bersifat kondisional.
- APK debug SHA-256: `8B642748A7AC903B8A6E5FB56E7A1BA08CC02F63AA9DDEECDC6CE3FC5B3FC10D`.

## 8. Current End-to-End AI Companion Audit — 2026-08-27

### Implemented contract

- **Model readiness and caches**: Gemma E2B/E4B use pinned HTTPS artifacts,
  exact filename/byte-size/SHA-256 checks, and runtime verification before the
  UI can show `READY`. The permanent model copy is stored under
  `filesDir/parfume-models`; `cacheDir/parfume-litert-runtime` is only the
  disposable LiteRT runtime cache. `Conversation` reuses KV state during a
  process lifetime, while the last history turns are restored with
  `initialMessages` after restart. Engine, session, and conversations are
  closed on model changes, eviction, memory pressure, and application close.
- **Local RAG**: Room v3 stores source provenance, hash, timestamps, TTL,
  trust, availability, and page ranges. Migration 1→2→3 deduplicates legacy
  rows without a destructive reset. Upload indexing validates URI/MIME/size/
  pages/text, uses Unicode-safe bounded chunks, exact 384-dimensional finite
  MiniLM vectors, hybrid dense-plus-lexical ranking, and atomic replacement.
  SAF documents that become unreadable are marked `UNAVAILABLE` and excluded.
- **Grounded perfumery behavior**: knowledge, template, catalog recommendation,
  and novel-formula intents are separated by Unicode token boundaries. Formula
  ingredients must resolve to positive local catalog IDs, totals are checked to
  `0.01 g`, concentration and density/unit conversion are validated, and a
  formula without solvent is explicitly labeled as concentrate. Web content
  cannot become a formula ingredient or safety clearance.
- **Internet fallback**: default `allowInternetFallback` is `false`. When
  enabled, only MediaWiki and curated IFRA/FDA/EU hosts are queried. User text
  is sanitized; upload contents, history, and credentials are not sent. Page
  fetch requires HTTPS, host allowlisting, no automatic redirects, bounded
  response/time, canonical URL, hash, timestamp, TTL, and
  `WEB_UNTRUSTED` provenance. Fetch failure returns `WEB_UNAVAILABLE` rather
  than answering from an unverified search snippet. Successful pages are
  best-effort auto-ingested with the same untrusted TTL.
- **UI and persistence**: chat uses typed `SourceCitation` records and
  structured citation JSON with legacy fallback. Bento cards expose model,
  dataset, retrieval, consent, provider error, trust, date, hash, page, and
  clickable URL/local-document source state. Formula cards expose actual total,
  concentration, catalog IDs, unresolved items, and safety warnings.

### Verification evidence

| Gate | Result | Evidence |
|---|---|---|
| Unit tests | PASS | `:app:testDebugUnitTest` after the final test additions; includes parser, Unicode chunking, hybrid retrieval policy, formula validation, consent, and web page-failure behavior. |
| Lint | PASS | `:app:lintDebug`; report at `app/build/reports/lint-results-debug.html`. |
| Debug build | PASS | `:app:assembleDebug`. |
| Instrumentation | PASS | `:app:connectedDebugAndroidTest`; 3 tests on Samsung `SM-G988B`, Android 13. Covers allowlists, Unicode/citation bounds, and Room migration/deduplication. |
| Real model load/inference | NOT VERIFIED in this run | No complete SHA-matching multi-gigabyte E2B/E4B artifact was available for cold/warm load and token generation acceptance. The app must keep the model `UNAVAILABLE`/`VERIFYING` until both checksum and runtime verification pass. |
| Real upload-RAG and opt-in web fallback | NOT VERIFIED end-to-end on device | Logic and unit/instrumentation contracts are covered; a real SAF upload, network provider response, clickable source, and model-backed answer still require a dedicated device session. |

The implementation therefore passes the automated repository gates but does not
claim universal production readiness until the final model artifact, real
inference, upload citation, and consented web fallback are exercised on the
target device.

## 9. Model Download Failure Repair — 2026-08-27

The reported failure at approximately 65% was investigated against the exact
pinned E2B and E4B endpoints. Both Hugging Face endpoints currently answer the
initial request with `302` and redirect the signed artifact to
`us.aws.cdn.hf.co`; the previous exact-host allowlist did not include that
provider host. A bounded range probe also returned `206` with a matching
`Content-Range` and the registry byte size, confirming that the artifact
metadata is not the cause of the failure.

The download path now:

- allows only the observed exact Hugging Face CDN host, still requiring HTTPS,
  port 443/default, no userinfo, and the pinned artifact SHA-256;
- runs as a foreground `dataSync` worker so a multi-gigabyte download is not
  treated like short deferrable work;
- preserves the SAF `.tmp` file and returns `Result.retry()` for bounded
  transient network/SAF failures with exponential backoff;
- sends `Accept-Encoding: identity`, validates every `206` response's
  `Content-Range` and total length, resets an unsafe partial file instead of
  appending it, and handles a fully written temp file before requesting a
  range;
- uses `openFileDescriptor` append/truncate modes with deterministic stream
  closure, then promotes only an exact-size, SHA-256-matching model.

The implementation follows the documented WorkManager retry/backoff contract
([Android WorkManager retry](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)),
SAF descriptor access
([Android shared documents](https://developer.android.com/training/data-storage/shared/documents-files)),
and HTTP partial-content requirements
([RFC 9110 Content-Range](https://datatracker.ietf.org/doc/rfc9110/)). The
pinned revision and cache identity remain aligned with the Hugging Face
download guidance
([Hugging Face downloads](https://huggingface.co/docs/huggingface_hub/en/guides/download)).

Automated unit/build verification has been rerun after this repair. A complete
2.6 GB device download, cold LiteRT load, and model-backed answer remain
separate acceptance gates and must not be inferred from compilation alone.

Verification after the repair:

- `./gradlew.bat :app:lintDebug` — **PASS** (91 existing warnings, 0 errors).
- `./gradlew.bat :app:testDebugUnitTest` — **PASS**.
- `./gradlew.bat :app:assembleDebug` — **PASS**.
- `./gradlew.bat :app:connectedDebugAndroidTest` — **PASS**, 3 tests on
  Samsung `SM-G988B`, Android 13, including the new CDN allowlist assertion.
- APK installed with `adb -s RRCN3008VYE install -r -d` — **Success**; launch
  and foreground activity were verified with no filtered fatal exception.
- APK SHA-256: `5C7BFA06091EAEFD37183CFCA95EC0CEB18CB87ADFD050DBA0A180B63CF35742`.

The target device was not connected to the internet during this smoke check,
so the full model transfer was intentionally not claimed. Host-side probes of
both pinned endpoints did verify the observed CDN redirect and a valid
`206`/`Content-Range` response.

## 10. Perfumery companion reliability — 2026-10-23 / v1.1.0

The current work used BMAD analysis/planning/implementation/review/QA with
OpenSpec proposal, scenario specification, design and tasks at
`openspec/changes/perfumery-companion-reliability`. Two delegated workers
implemented disjoint calculator/wheel and catalog-planner slices; the parent
integrated and reviewed the complete path. BMAD/OpenSpec are authoring methods,
not application dependencies. The official workflow references are
[BMAD build/review](https://docs.bmad-method.org/build/build-a-change/) and
[OpenSpec concepts](https://github.com/Fission-AI/OpenSpec/blob/main/docs/concepts.md).

Confirmed defects repaired:

- High dense scores and generic perfume terms could suppress internet fallback
  or displace uploads. A shared query-specific relevance gate now controls
  retrieval, cache sufficiency and fallback decisions; exact upload matches
  retain page/URI provenance. Weak cached web evidence also triggers a fresh lookup.
- Public page introductions could consume the answer context while the relevant
  section was later in the article. Pages are bounded, split and ranked before
  answering. Wikipedia article content uses MediaWiki `action=parse`; search
  uses content words with a perfumery qualifier. API errors are failures, and
  official matches are not discarded behind discovery snippets. References:
  [MediaWiki search](https://www.mediawiki.org/wiki/API:Search),
  [MediaWiki page parsing](https://www.mediawiki.org/wiki/API:Parsing_wikitext).
- The real extractive fallback now has tests verifying an uploaded answer and
  page citation without an LLM, plus a web answer retaining its source link.
  Per-turn confirmation is not used after enabling Internet Fallback. Default
  OFF and previously chosen OFF still prevent network and cached web use.
- Catalog planning ignored type/brand constraints, chose arbitrary materials,
  and silently changed diluted requests into concentrates. Constraints now
  precede ranking; one/two-material bibit blends are supported; missing material,
  brand, solvent, ambiguous number/unit or invalid density produces an explicit
  validation reason. Batch totals and actual concentrations are checked.
- Existing assets contain 30 recipes with unresolved catalog ingredients. They
  cannot be imported by substituting similar names. Two new explicitly labeled
  **application-calculated starter concentrates** use available natural/Luzi
  materials and carry untested design provenance. They are not supplier recipes
  or skin-ready products. Packaging/bundle rows are excluded from selectable
  materials without deleting persisted rows or changing their IDs.
- The wheel now selects its primary axis from aggregate family weights, with a
  corresponding subfamily. Carrier-only/empty formulas show no olfactory profile.
  Named carrier detection no longer treats every aromatic alcohol as solvent.
  Unit changes preserve mass, invalid mL density is surfaced, persisted formula
  metrics are recalculated, and SPL is labeled an unvalidated heuristic.
- Chat-to-formula import previously used a filtered Library list and skipped
  unresolved ingredients after clearing the draft. It now validates the full
  recommendation against the unfiltered catalog before changing the draft.
- Text normalization split Markdown headings, numbers and URLs. Formatting now
  preserves them and renders simple table rows without raw separator clutter.
- Upload SAF grants must be persistable; metadata and availability queries run
  on IO; indexing is serialized at the UI boundary and cancellation propagates.

Verification on the final source:

| Check | Result | Evidence |
| --- | --- | --- |
| Unit tests | PASS | `:app:testDebugUnitTest --max-workers=2`: **120 tests, 15 suites, 0 failures/errors/skips**. Includes actual bundled catalog natural + Luzi/Parfarome/Macrabame recipes, starter template identity/totals, RAG, cache/fallback, provenance, parser and calculator. |
| Android lint | PASS with existing findings | `:app:lintDebug --max-workers=2`: **0 errors, 89 warnings, 2 hints**. No lint baseline/suppression was added to hide errors. |
| Debug APK | PASS | `:app:assembleDebug --max-workers=2`; output metadata confirms `com.parfumecreator`, versionCode **2**, versionName **1.1.0**. |
| Connected instrumentation | UNAVAILABLE / task failed for environment | `:app:connectedDebugAndroidTest --max-workers=2` compiled/packaged the Android test APK, then returned `DeviceException: No connected devices!`. No device tests executed in this run. |
| OpenSpec | PASS | `@fission-ai/openspec@1.14.3 validate perfumery-companion-reliability --strict`, telemetry disabled. |
| Host live network probes | PASS within probe scope | Search returned Perfume/Ambroxide/Sclareol; `action=parse` for Ambroxide returned 42,247 HTML characters containing synthesis/perfumery content. IFRA/FDA/EU curated URLs returned HTTP 200 without redirect. This is host evidence, not Android E2E. |

Device/model acceptance remains separate: `adb devices -l` was empty and no
configured AVD was listed. Real SAF picker/click navigation, cold/warm Gemma
load/inference, KV performance, full model transfer and complete internet
fallback on the target Android device were not verified in this session.
Density/family metadata and legacy safety fields retain the provenance limits
documented in `DATA_PROVENANCE.md`.

Final APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `BDB71F77350B43EAE9D0B4E2CD77AFE05F24FD834CFBE2F3B478C459DE9DAB22`.

Publication hygiene: exact source/document staging was reviewed; `git diff
--cached --check` passed; a staged high-confidence credential-pattern scan had
no matches. APKs, downloaded LLMs, browser profiles, raw reference checkouts,
local SDK configuration and generated caches are excluded. The user authorized
a new private `Adrian463588/ParfumCreator` repository through `gh`; its description
and 11 topics were populated. README, changelog and provenance are included.

Delivery verified: initial implementation commit
`1951d77310e05e73d32eea709fa10b2dbfe5e745` was pushed to private
`https://github.com/Adrian463588/ParfumCreator`, default branch `main`.
`git ls-remote --heads origin main` and the GitHub Git-reference API returned
the same SHA; the remote README was also confirmed. The subsequent documentation
commit records this evidence without changing the tested application source.

Final acceptance checklist:

- [x] Relevant local/upload evidence and cited extractive offline answers.
- [x] Automatic fallback after opt-in, weak-cache refresh, explicit OFF, provider failure and cancellation.
- [x] Actual bundled natural + Luzi/Parfarome/Macrabame catalog formulation tests.
- [x] Unique material/brand resolution, atomic recommendation import, totals and saved metrics.
- [x] Aggregate wheel, solvent exclusion, mass-preserving units and heuristic SPL labels.
- [x] Two transparent catalog-based starter concentrates; unsupported legacy recipes fail explicitly.
- [x] 120 unit tests, Android lint with zero errors, debug build and strict OpenSpec validation.
- [x] Version 1.1.0, README/changelog/provenance, private repository description/topics and verified source push.
- [ ] Real-device model download/inference, upload/source navigation, internet fallback and visual acceptance: no device/AVD available this session.

---

## 7. Version 1.2.0 Audit & Acceptance Verification (2026-10-26)

### 7.1 Scope & Feature Verification
- **Bundled Datasets**: 100 natural (`100_resep_parfum_natural_eksperimental_2026.json`) and 100 commercial fragrance concentrate (`100_racikan_bibit_luzi_macbrame_parfarome_2026.json`) studies bundled unchanged with verified SHA-256 hashes in `DATA_PROVENANCE.md`. Total templates available: 232 (200 new + 32 legacy/starter).
- **Pure Validated Parser & Off-Main Loader**: `FormulaTemplateParser` validates IDs, finite positive amounts, mass percentage sums (100%), and reference grams (10 g). Invalid files are rejected atomically without contaminating valid datasets. `FormulaTemplateLoader` caches parsed templates on IO.
- **Explicit Catalog Resolution & Consent**: `CatalogIngredientResolver` and `ApplyFormulaTemplateUseCase` prevent silent substitutions or SKU inventions. Accord slots without verified SKUs (LUZI/Parfarome) require explicit user selection of real catalog products. Adapted formulas require explicit checkbox consent and are recorded with provenance in formula notes.
- **Atomic Application**: Target batch scaling (finite, positive mass) is calculated via `FormulaCalculationEngine`. Formula draft is replaced only upon complete validation; cancellation leaves the existing draft intact.
- **UI/UX Anti-Slop Compliance**: `FormulaTemplatesBottomSheet` utilizes native Material 3 design tokens, guarantees >= 48dp touch targets on all interactive elements, TalkBack semantics, search/filter matrices, and explicit URL intent error handling.

### 7.2 Verification Gates Matrix

| Gate | Target / Scope | Result | Evidence |
| --- | --- | --- | --- |
| **Unit Tests** | `:app:testDebugUnitTest` | **PASS** | **162 tests, 17 suites, 0 failures, 0 errors, 0 skipped**. Includes 21 parser tests and 21 application use case tests. |
| **Android Lint** | `:app:lintDebug` | **PASS** | **0 errors**. No lint suppressions added. |
| **Debug Build** | `:app:assembleDebug` | **PASS** | `app-debug.apk` built (121,373,306 bytes). SHA-256: `1633A3233CB4415404FB2F012844783DFA649D537989ABDF3D66F8E5ED45F8F3`. |
| **Connected Device Tests** | `:app:connectedDebugAndroidTest` | **PASS** | **5 tests, 0 failures (100% success)** on physical hardware: `2412DPC0AG` (Android 16, transport_id: 2). Verified asset packaging, caching, and Compose UI search/preview flow. |
| **OpenSpec Validation** | `recipe-template-library --strict` | **PASS** | `@fission-ai/openspec@1.15.0`: Change `recipe-template-library` is valid. |

### 7.3 Final Acceptance Checklist (v1.2.0)
- [x] 100 natural & 100 bibit concentrate studies bundled with verified provenance.
- [x] Pure parser with full mass percentage and reference gram validation.
- [x] Searchable, category & aroma filtered recipe browser in Formula & Settings.
- [x] Scaled batch preview with non-skin-ready concentrate warnings and source citations.
- [x] Explicit catalog binding requirement for unverified accords; adaptation consent enforced.
- [x] Atomic application to Formula Creator without auto-saving.
- [x] 162 unit tests passing, zero lint errors, clean assembleDebug APK.
- [x] Connected instrumented tests passing on real Android device (`2412DPC0AG`).
- [x] OpenSpec SDD change validated strictly.
- [x] Version bumped to 1.2.0 (versionCode 3) with release & download documentation.
