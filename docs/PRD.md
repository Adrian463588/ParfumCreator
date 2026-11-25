# 📋 Product Requirements Document (PRD)
# Parfume Creator — AI-Powered Perfume Formulation App

**Version:** 1.3.1
**Date:** 2026-11-25  
**Platform:** Android (Jetpack Compose)  
**Paradigm:** Local-First / Local AI dengan Internet Fallback Opt-In  

---

## 1. Overview

**Parfume Creator** adalah aplikasi Android local-first yang menggabungkan kalkulator formulasi parfum presisi, database bahan yang komprehensif, dan AI Companion berbasis RAG (Retrieval-Augmented Generation). Tujuan utamanya adalah membantu para peracik parfum—dari hobbyist hingga profesional—untuk meramu, menghitung, dan mendapatkan rekomendasi AI dalam meracik parfum dari **natural essential oil** maupun **fragrance oil sintetis** dari brand seperti Parfarome, Luzi, dan Macrabame.

Fungsi utama tetap berjalan **offline** dengan katalog, template, knowledge snapshot, embedding, dan inference lokal. Pencarian internet hanya tersedia setelah pengguna mengaktifkan `allowInternetFallback`; query disanitasi dan dibatasi ke MediaWiki serta registry resmi yang di-allowlist. Dokumen upload, history chat, credential, dan data pribadi tidak dikirim ke internet. Hasil web selalu berstatus `WEB_UNTRUSTED`, memiliki URL, hash, waktu, TTL, dan provenance.

Setelah fallback diaktifkan, pencarian berjalan otomatis tanpa dialog per pertanyaan jika sumber lokal belum cukup. Hanya paragraf yang relevan dari halaman yang benar-benar diambil boleh menjadi konteks jawaban. Kesamaan topik generik atau skor embedding tinggi saja tidak membuktikan bahwa pertanyaan terjawab. Pengaturan OFF yang dipilih pengguna tetap dihormati, termasuk untuk cache web.

---

## 2. Target User

| Segmen | Deskripsi |
|--------|-----------|
| **Hobbyist Parfum** | Individu yang meracik parfum untuk kebutuhan pribadi |
| **Perfumer Indie** | Pembuat parfum skala kecil yang menjual produk |
| **Seller Fragrance Oil** | Penjual yang butuh panduan mixing untuk konsumen |
| **Mahasiswa/Peneliti** | Pelajar bidang kimia kosmetik atau aromatik |

---

## 3. Goals & Non-Goals

### Goals
- Menyediakan kalkulator formulasi parfum yang presisi (g, mg, ml)
- Database bahan parfum yang lengkap termasuk brand sintetis lokal
- AI Chatbot lokal dengan kemampuan RAG dari dokumen pengguna
- Download model AI yang resumable, diverifikasi SHA-256, dan disimpan permanen di `filesDir/parfume-models`; SAF dapat dipakai sebagai sumber file
- Prediksi arah fragrance wheel dan kalkulasi SPL
- Formula baru hanya memakai ID bahan katalog nyata dan total batch tervalidasi
- Citation terstruktur dan clickable untuk dataset, dokumen upload, template, knowledge, serta web fallback

### Non-Goals
- Tidak menggunakan API cloud untuk inference AI
- Tidak memerlukan koneksi internet untuk fungsi utama; internet hanyalah fallback opt-in untuk knowledge
- Tidak menjual atau mendistribusikan bahan parfum
- Tidak menjadi e-commerce platform

---

## 4. Functional Requirements

### FR-01: AI RAG Lokal dengan SAF Folder

**Prioritas:** P0 (Critical)

**Deskripsi:**  
Pengguna dapat mengupload dokumen (PDF, TXT) ke folder yang dipilih via SAF (Storage Access Framework). Sistem akan memproses dokumen tersebut menjadi chunk, menghasilkan embedding, dan menyimpannya di database vektor lokal. AI Chatbot dapat menjawab pertanyaan berdasarkan referensi dari dokumen-dokumen tersebut.

**User Stories:**
- `US-01.1` Sebagai pengguna, saya dapat memilih folder di storage untuk menjadi RAG source melalui SAF picker.
- `US-01.2` Sebagai pengguna, saya dapat menambahkan file PDF/TXT ke dalam RAG folder dan sistem akan otomatis mengindeks dokumen tersebut.
- `US-01.3` Sebagai pengguna, saya dapat menghapus dokumen dari RAG source dan embedding-nya akan ikut terhapus.
- `US-01.4` Sebagai pengguna, saya dapat bertanya kepada AI Chatbot dan AI akan menjawab dengan referensi dari dokumen RAG yang relevan beserta sumbernya.

**Acceptance Criteria:**
- SAF folder picker dapat dipanggil dan URI tersimpan dengan `takePersistableUriPermission`
- PDF berhasil di-parse menggunakan pdfbox-android
- Text di-chunk dengan ukuran 512 token, overlap 50 token
- Embedding 384-dimensi dihasilkan oleh interpreter LiteRT/TFLite lokal (all-MiniLM-L6-v2)
- Embedding tersimpan sebagai BLOB FloatArray di Room dan dicari dengan hybrid lexical+dense similarity
- Cosine similarity search mengembalikan top-K chunk yang relevan
- Response AI menyertakan nama file sumber sebagai referensi

**Technical Notes:**
- Parser: `pdfbox-android` untuk PDF, native reader untuk TXT
- Embedder: LiteRT/TFLite `all-MiniLM-L6-v2.tflite` (384 dimensi) + tokenizer Unicode
- Vector DB: Room (`rag_documents`/`rag_chunks`) dengan deduplikasi URI/hash dan reindex atomic
- Chunk strategy: bounded Unicode-safe chunks dengan overlap tervalidasi dan metadata halaman

---

### FR-02: Database Semua Bahan Parfum (Natural)

**Prioritas:** P0 (Critical)

**Deskripsi:**  
Aplikasi menyediakan database bahan parfum natural yang sudah ter-preload, mencakup essential oil, absolut, resinoid, dan isolat. Setiap bahan memiliki metadata lengkap termasuk note classification, fragrance family, dan densitas.

**User Stories:**
- `US-02.1` Sebagai pengguna, saya dapat menelusuri semua bahan parfum natural berdasarkan nama, note, atau family.
- `US-02.2` Sebagai pengguna, saya dapat melihat detail setiap bahan: nama, tipe, note (top/middle/base), fragrance family, densitas (g/ml), deskripsi aroma.
- `US-02.3` Sebagai pengguna, saya dapat menambahkan bahan custom ke library.
- `US-02.4` Sebagai pengguna, saya dapat mengimpor data bahan dari CSV via SAF.

**Data Fields per Bahan:**
```
id, name (EN/ID), type (NATURAL/SYNTHETIC)
note_type: TOP | MIDDLE | BASE | HEART
fragrance_family: CITRUS | FLORAL | WOODY | ORIENTAL | FRESH | FOUGERE | CHYPRE | GOURMAND | AQUATIC | SPICY | EARTHY | MUSKY
density_g_ml: Float
cas_number: String (optional)
description: String
source_plant: String (optional)
is_custom: Boolean
```

**Pre-loaded Categories:**
- Essential Oils: Bergamot, Lavender, Rose Otto, Sandalwood, Vetiver, Oud, Patchouli, dll. (~200 bahan)
- Absoluts: Jasmine Absolute, Rose Absolute, Orris, dll.
- Isolates & Aroma Chemicals: Linalool, Limonene, Geraniol, dll.
- Resins & Balsams: Benzoin, Labdanum, Myrrh, dll.

---

### FR-03: Database Fragrance Oil Sintetis (Brand)

**Prioritas:** P0 (Critical)

**Deskripsi:**  
Database fragrance oil sintetis dari brand-brand yang populer di kalangan peracik parfum Indonesia, yaitu **Luzi**, **Parfarome**, dan **Macrabame**. Data ini di-preload dan dapat diperluas oleh pengguna.

**Brand Data:**

| Brand | Asal | Fokus | Jumlah Produk Est. |
|-------|------|-------|-------------------|
| **Luzi** | Swiss | Fragrance ingredient premium | ~150+ produk |
| **Parfarome** | Spanyol | Halal/alcohol-free, inspired designer | ~200+ produk |
| **Macrabame** | Indonesia | Inspired designer, harga terjangkau | ~100+ produk |

**Data Fields per Fragrance Oil:**
```
id, name, brand: LUZI | PARFAROME | MACRABAME | CUSTOM
inspired_by: String (nama parfum yang diinspirasi, jika ada)
note_type: TOP | MIDDLE | BASE
fragrance_family: FragranceFamily enum
density_g_ml: Float (default 0.95)
concentration_ratio: Float (kekuatan, 0.0-1.0)
ifra_limit: Float (% max usage, jika tersedia)
description: String
is_custom: Boolean
```

**User Stories:**
- `US-03.1` Sebagai pengguna, saya dapat filter fragrance oil berdasarkan brand, note, atau aroma family.
- `US-03.2` Sebagai pengguna, saya dapat mencari fragrance oil berdasarkan nama parfum yang diinspirasi.
- `US-03.3` Sebagai pengguna, saya dapat menambahkan produk custom dari brand lain.

---

### FR-04: Database Raw Material & Natural Fragrance Oil

**Prioritas:** P1 (High)

**Deskripsi:**  
Data raw material melengkapi database dengan bahan-bahan yang lebih spesifik dari sisi kimiawi, serta fragrance oil natural (non-sintetis tapi tetap processed, seperti Bergamot FCF, Lavender 40/42).

**User Stories:**
- `US-04.1` Sebagai pengguna, saya dapat melihat raw material data termasuk komposisi kimiawi utama.
- `US-04.2` Sebagai pengguna, saya dapat membedakan antara bahan natural murni vs. natural yang telah diproses.

---

### FR-05: Kalkulator Formulasi Manual

**Prioritas:** P0 (Critical)

**Deskripsi:**  
Pengguna dapat membuat resep/formula parfum secara manual dengan menambahkan bahan-bahan dari library, menentukan jumlahnya dalam g, mg, atau ml, dan mendapatkan kalkulasi otomatis.

**User Stories:**
- `US-05.1` Sebagai pengguna, saya dapat membuat formula baru dan memberi nama.
- `US-05.2` Sebagai pengguna, saya dapat menambahkan bahan ke formula dengan amount dalam gram (g), miligram (mg), atau mililiter (ml).
- `US-05.3` Sebagai pengguna, saya dapat mengubah satuan setiap bahan secara independen; sistem akan mengkonversi ke gram menggunakan densitas.
- `US-05.4` Sebagai pengguna, saya dapat melihat total berat formula dan persentase setiap bahan.
- `US-05.5` Sebagai pengguna, saya dapat menyimpan formula dan melihat riwayatnya.
- `US-05.6` Sebagai pengguna, saya dapat menduplikasi formula yang ada sebagai base untuk formula baru.

**Kalkulasi Otomatis:**
```
Total Weight (g) = Sum(amount_i x density_i)  // jika input dalam ml
Total Weight (g) = Sum(amount_i)               // jika input dalam g
Concentration (%) = (Sum fragrance_weight / total_weight) x 100
Note Breakdown: Top/Middle/Base masing-masing sebagai % dari total fragrance
```

**Fragrance Wheel Prediction:**  
Berdasarkan fragrance family bahan aroma, sistem menjumlahkan bobot seluruh subfamily pada 4 sumbu FLORAL, WOODY, ORIENTAL, FRESH. Sumbu dengan total terbesar menjadi primary; dominant subfamily harus berada pada sumbu tersebut. Pelarut tidak masuk skor. Formula kosong atau hanya pelarut tidak memiliki arah olfaktif. Metadata family katalog merupakan indikasi, bukan hasil pengujian aroma.

---

### FR-05A: Library Template Resep Natural dan Bibit

**Prioritas:** P0

Menu Formula dan Pengaturan menyediakan Template Resep: 100 studi natural NAT-001–NAT-100 dan 100 studi bibit BIB-001–BIB-100 dari JSON pengguna, ditambah 32 template lama/starter. Nama/ID sumber, keluarga aroma asli, komposisi dan provenance dipertahankan. Pencarian mencakup resep, ID, bahan, brand dan kode supplier; filter kategori/aroma memiliki jumlah dinamis.

Preview menampilkan persen massa, gram referensi/target, tautan sumber dan status eksperimental. Semua resep baru adalah konsentrat 100% tanpa pelarut, bukan produk siap dipakai di kulit. Aplikasi tidak menentukan dilusi aman atau mengesahkan klaim penghargaan/SKU dari file.

Pencocokan otomatis hanya berdasarkan identitas unik, tipe dan brand. EO adalah alias essential oil; grade FCF/absolute/botanical tetap dibedakan. Accord LUZI/Parfarome tanpa SKU wajib dipilih dari katalog nyata. Bahan belum tersedia dapat dipetakan secara eksplisit ke bahan katalog sesuai tipe/brand/peran pelarut; ini merupakan adaptasi yang wajib disetujui dan dicatat dalam notes. Tidak ada pembuatan produk fiktif atau impor parsial.

Penggunaan resep memvalidasi seluruh ID katalog, jumlah, total batch (toleransi 0,01 g), konsentrasi dan kesesuaian peran bahan melalui kalkulator bersama sebelum mengganti draft. Batal/gagal mempertahankan draft lama; sukses tidak otomatis menyimpan. Parsing aset berjalan pada IO, file invalid ditolak utuh dengan error terlihat, sedangkan sumber valid lain tetap tersedia.

### FR-06: Kalkulasi SPL & Konsentrasi

**Prioritas:** P0 (Critical)

**Deskripsi:**  
Sistem menghitung SPL (Scent Profile Level) dan tipe konsentrasi parfum secara otomatis berdasarkan formula yang dibuat.

**Concentration Type:**

| Tipe | Band konsentrasi massa aplikasi |
|------|------------|
| Parfum Extrait | >= 20% |
| EDP (Eau de Parfum) | 15 sampai < 20% |
| EDT (Eau de Toilette) | 8 sampai < 15% |
| EDC (Eau de Cologne) | 4 sampai < 8% |
| Eau Fraiche | < 4% |

Band ini klasifikasi perkiraan aplikasi, bukan prediksi ketahanan. Formula tanpa pelarut dilabeli konsentrat. Tidak ada estimasi jam atau jarak dari konsentrasi/SPL saja.

**SPL (Scent Profile Level):**
```
SPL = concentration(%) x note_weight_factor x longevity_modifier
note_weight_factor = ((base_ratio x 1.5) + (middle_ratio x 1.0) + (top_ratio x 0.5)) / 3.0
SPL range 0-100: 0-25 Subtle | 26-50 Moderate | 51-75 Strong | 76-100 Intense
```

**User Stories:**
- `US-06.1` Sebagai pengguna, saya dapat melihat tipe konsentrasi parfum secara real-time saat mengedit formula.
- `US-06.2` Sebagai pengguna, saya dapat melihat SPL score dengan deskripsi verbal (Subtle/Moderate/Strong/Intense).
- `US-06.3` Sebagai pengguna, saya dapat melihat breakdown note (top/middle/base) dalam formula.
- `US-06.4` Sebagai pengguna, saya melihat bahwa SPL merupakan indeks heuristik aplikasi; aplikasi tidak menyimpulkannya sebagai jam ketahanan, jarak proyeksi atau hasil uji fisik.

---

### FR-07: AI Companion Perfumery (Local RAG, Formula, dan Guardrail)

**Prioritas:** P0 (Critical)

**Deskripsi:**  
AI Companion menggunakan model Gemma E2B/E4B `.litertlm` melalui LiteRT-LM untuk percakapan lokal, RAG, rekomendasi katalog, template, formula baru, serta perhitungan SPL/fragrance wheel. Jika runtime LLM belum siap, deterministic fallback tetap menjawab dari katalog/template/knowledge lokal dengan citation; tidak ada jawaban rekaan. Persona pengguna hanya mengubah gaya bahasa, sementara guardrail inti tetap immutable.

**User Stories:**
- `US-07.1` Sebagai pengguna, saya dapat mengajukan pertanyaan umum seputar parfum dan mendapatkan jawaban kontekstual.
- `US-07.2` Sebagai pengguna, saya dapat meminta AI untuk membuat rekomendasi formula parfum berdasarkan preferensi aroma.
- `US-07.3` Sebagai pengguna, saya dapat meminta AI untuk menghitung SPL dan prediksi fragrance wheel dari formula yang saya berikan.
- `US-07.4` Sebagai pengguna, saya dapat mengatur system prompt / persona AI melalui Settings.
- `US-07.5` Sebagai pengguna, AI akan merujuk dokumen RAG yang relevan saat menjawab pertanyaan teknis.
- `US-07.6` Sebagai pengguna, saya dapat melihat respon AI secara streaming (token per token).
- `US-07.7` Sebagai pengguna, formula yang direkomendasikan AI dapat langsung di-import ke Formula Creator.
- `US-07.8` Sebagai pengguna, saya dapat mengaktifkan internet fallback satu kali di Settings; saat evidence lokal tidak cukup, pencarian berjalan otomatis tanpa dialog per pertanyaan dan citation tetap clickable.
- `US-07.9` Sebagai pengguna, saya menerima status eksplisit untuk model invalid, dataset kosong, dokumen rusak, atau web provider gagal.

**Default System Prompt:**
```
Kamu adalah ParfumAI, asisten perfumer berpengalaman dengan keahlian dalam meracik parfum
dari natural essential oil dan fragrance oil sintetis (Luzi, Parfarome, Macrabame).
Saat merekomendasikan formula, selalu sertakan: nama dan jumlah bahan (gram), tipe konsentrasi,
prediksi SPL (0-100), prediksi fragrance wheel, dan deskripsi olfactory profile.
```

---

### FR-08: Download Model AI & Bundle (Resumable, Terverifikasi)

**Prioritas:** P0 (Critical)

**Deskripsi:**  
Pengguna dapat mendownload model LLM lokal (`.litertlm`) dan bundle data tambahan. Download mendukung resume otomatis saat koneksi terputus, HTTPS host allowlist, batas response, redirect control, nama file, ukuran, dan SHA-256. Model final dimaterialisasi ke `filesDir/parfume-models`; `cacheDir/parfume-litert-runtime` hanya untuk cache runtime/compiled dan tidak dianggap sebagai storage model permanen.

**User Stories:**
- `US-08.1` Sebagai pengguna, saya dapat memilih folder tujuan download melalui SAF folder picker (disimpan permanen).
- `US-08.2` Sebagai pengguna, saya dapat melihat daftar model/bundle tersedia beserta ukuran file.
- `US-08.3` Sebagai pengguna, saya dapat melihat progress download real-time (percentage, speed, ETA).
- `US-08.4` Sebagai pengguna, download akan otomatis resume jika koneksi terputus atau aplikasi ditutup.
- `US-08.5` Sebagai pengguna, file tetap ada di SAF folder meskipun aplikasi di-uninstall dan diinstall ulang.
- `US-08.6` Sebagai pengguna, saya dapat menghapus bundle yang tidak diperlukan.

**Available Bundles:**

| Bundle | Ukuran Est. | Keterangan |
|--------|------------|------------|
| LLM: Gemma 4 E2B-it LiteRT-LM | 2,588,147,712 byte | Registry revision/SHA-256 terpin |
| LLM: Gemma 4 E4B-it LiteRT-LM | 3,659,530,240 byte | Registry revision/SHA-256 terpin |
| Embedding: all-MiniLM-L6-v2 | ~22 MB | Wajib untuk RAG |
| DB Bundle: Luzi Products | ~5 MB | Data produk Luzi |
| DB Bundle: Parfarome Products | ~5 MB | Data produk Parfarome |
| DB Bundle: Macrabame Products | ~3 MB | Data produk Macrabame |

**Technical Implementation:**
- WorkManager dengan CoroutineWorker untuk background download
- HTTP Range header untuk resume: `Range: bytes={downloaded}-`
- Download ke file `.tmp`, rename ke final name setelah selesai
- Progress tracking via WorkManager Progress API + StateFlow
- Constraints: NetworkType.CONNECTED, StorageNotLow
- Backoff policy: Exponential, min 10s

---

## 5. Non-Functional Requirements

| Kategori | Requirement |
|----------|------------|
| **Performance** | LLM TTFT <= 5s pada perangkat mid-range (Snapdragon 700 series) |
| **Local-first** | Katalog, formula, embedding, RAG dokumen, dan fallback deterministic berjalan tanpa internet |
| **Privacy** | Tidak ada pengiriman dokumen upload, history, credential, atau data pribadi; web hanya opt-in dengan query tersanitasi |
| **App Size** | App <= 50 MB (tanpa model); model di SAF |
| **Min SDK** | Android 8.0 (API 26) |
| **Target SDK** | Android 14 (API 34) |
| **ABI** | arm64-v8a, armeabi-v7a |

---

## 6. Tech Stack

| Layer | Teknologi |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt |
| Database | Room (SQLite) + FloatArray BLOB hybrid retrieval |
| LLM Inference | LiteRT-LM Gemma E2B/E4B (`.litertlm`) |
| Embedding | LiteRT/TFLite Mobile MiniLM 384-d |
| PDF Parsing | pdfbox-android dengan batas ukuran/halaman |
| OCR | Google ML Kit Text Recognition v2 |
| File Access | SAF (Storage Access Framework) |
| Background | WorkManager + Kotlin Coroutines |
| Navigation | Compose Navigation |
| Preferences | DataStore Preferences |

---

## 7. Database Schema (Room)

```sql
CREATE TABLE ingredients (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  type TEXT NOT NULL,           -- NATURAL | SYNTHETIC
  brand TEXT,                   -- NULL | LUZI | PARFAROME | MACRABAME | CUSTOM
  note_type TEXT NOT NULL,      -- TOP | MIDDLE | BASE | HEART
  fragrance_family TEXT NOT NULL,
  density_g_ml REAL DEFAULT 1.0,
  cas_number TEXT,
  inspired_by TEXT,
  description TEXT,
  ifra_limit REAL,
  is_custom INTEGER DEFAULT 0
);

CREATE TABLE formulas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  total_weight_g REAL,
  concentration_pct REAL,
  concentration_type TEXT,      -- EXTRAIT|EDP|EDT|EDC|EAU_FRAICHE
  spl REAL,
  spl_label TEXT,               -- SUBTLE|MODERATE|STRONG|INTENSE
  fragrance_wheel_primary TEXT,
  fragrance_wheel_sub TEXT,
  notes TEXT,
  created_at INTEGER,
  updated_at INTEGER
);

CREATE TABLE formula_ingredients (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  formula_id INTEGER REFERENCES formulas(id) ON DELETE CASCADE,
  ingredient_id INTEGER REFERENCES ingredients(id),
  amount REAL NOT NULL,
  unit TEXT NOT NULL,           -- GRAM | MILLIGRAM | MILLILITER
  amount_in_gram REAL
);

CREATE TABLE rag_documents (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  file_name TEXT NOT NULL,
  uri TEXT NOT NULL UNIQUE,
  mime_type TEXT,
  size_bytes INTEGER,
  chunk_count INTEGER DEFAULT 0,
  indexed_at INTEGER,
  is_active INTEGER DEFAULT 1
);

CREATE TABLE rag_chunks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  document_id INTEGER REFERENCES rag_documents(id) ON DELETE CASCADE,
  chunk_index INTEGER,
  text TEXT NOT NULL,
  embedding BLOB NOT NULL       -- FloatArray 384-dim
);

CREATE VIRTUAL TABLE vec_chunks USING vec0(
  id INTEGER PRIMARY KEY,
  embedding FLOAT[384]
);

CREATE TABLE chat_sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT,
  created_at INTEGER,
  updated_at INTEGER,
  model_name TEXT
);

CREATE TABLE chat_messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id INTEGER REFERENCES chat_sessions(id) ON DELETE CASCADE,
  role TEXT NOT NULL,           -- USER | ASSISTANT | SYSTEM
  content TEXT NOT NULL,
  rag_sources TEXT,             -- JSON array of doc names
  timestamp INTEGER
);

CREATE TABLE downloads (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  bundle_id TEXT NOT NULL UNIQUE,
  file_name TEXT NOT NULL,
  url TEXT NOT NULL,
  destination_uri TEXT,
  total_bytes INTEGER,
  downloaded_bytes INTEGER DEFAULT 0,
  status TEXT,                  -- PENDING|DOWNLOADING|PAUSED|COMPLETED|FAILED
  work_request_id TEXT,
  created_at INTEGER
);
```

---

## 8. Development Milestones

| Fase | Durasi | Deliverable |
|------|--------|------------|
| Phase 1: Foundation | Week 1-2 | Project setup, Hilt, Navigation, Theme, Room DB |
| Phase 2: Data Layer | Week 3-4 | Entities, DAOs, pre-populated ingredient data |
| Phase 3: RAG Pipeline | Week 5-6 | SAF, PDF parsing, LiteRT/TFLite embedding 384-d, Room hybrid retrieval |
| Phase 4: LLM Integration | Week 7-8 | LiteRT-LM Gemma, streaming chat, persona guardrail, citation contract |
| Phase 5: Calculator | Week 9-10 | Formula Creator UI, kalkulasi SPL, fragrance wheel |
| Phase 6: Download Manager | Week 11 | WorkManager downloads, resume, progress UI |
| Phase 7: Polish & Test | Week 12 | UI polish, testing, performance optimization |

---

## 9. Open Questions

1. Format model LLM yang dipakai: `.litertlm` Gemma E2B/E4B dari registry revision dan SHA-256 yang dipin.
2. Apakah database Luzi, Parfarome, Macrabame akan di-curate manual atau discrape dari katalog online?
3. Apakah ada bahasa utama UI: Bahasa Indonesia atau Bilingual (ID/EN)?
4. Apakah OCR untuk gambar bahan (ingredient label) termasuk dalam v1.0?
5. Apakah cloud backup formula/chat history diperlukan di versi mendatang?
