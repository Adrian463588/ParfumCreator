# DESIGN.md — Parfume Creator
# UI/UX Design System & Screen Specifications

---

## 1. Design Philosophy

Kontrak 1.2.0: Template Resep tersedia dari Formula dan Pengaturan, memakai bento card sederhana tanpa tab baru. Daftar memiliki pencarian, kategori Natural/Bibit, aroma dan jumlah dinamis. Detail scrollable memuat komposisi persen/gram, target batch, status binding, link sumber serta warning eksperimen/konsentrat. Pemilihan bahan memakai picker katalog yang sama. Tombol gunakan hanya aktif untuk resep lengkap/valid dan adaptasi yang disetujui; mengganti draft tidak sama dengan menyimpan. Semua kontrol baru minimal 48 dp dan warna berasal dari MaterialTheme.

Kontrak 1.1.0 (2026-10-23): pertahankan layout bento minimalis. Fallback internet yang sudah diaktifkan berjalan otomatis tanpa dialog per pertanyaan. Card sumber menampilkan dokumen/halaman atau tautan publik. Kesalahan retrieval tetap terlihat, sedangkan hash/istilah teknis tidak mendominasi isi jawaban. Formula/wheel memakai hasil kalkulator bersama; kosong/hanya pelarut tidak mengklaim family aroma. SPL dilabeli heuristik, tanpa janji longevity/sillage.

Parfume Creator menggunakan estetika **"Luxury Apothecary"** — memadukan keanggunan botol parfum premium dengan nuansa lab ilmiah yang modern. Tampilan dark-first dengan aksen warna amber/gold dan hint hijau botanikal.

**Prinsip Desain:**
- **Sensory-First:** Visual yang merangsang indera; asosiasikan warna dengan aroma (amber = oriental, lavender = floral, sage = herbal)
- **Precision & Clarity:** UI kalkulator harus jelas dan akurat; tidak ada ambiguitas pada angka
- **Warm Dark Mode:** Bukan hitam dingin — gunakan dark amber undertone (#0F0C08, bukan #000000)
- **Glassmorphism Subtle:** Card dengan frosted glass effect, bukan flat opaque
- **Micro-interaction Rich:** Setiap input/action memberikan feedback animasi yang halus

---

## 2. Color System

### 2.1 Primary Palette (Material 3)

```kotlin
// Theme.kt
val PerfumePrimary = Color(0xFFD4A853)        // Amber Gold — brand color
val PerfumeOnPrimary = Color(0xFF3D2800)       // Dark Brown on primary
val PerfumePrimaryContainer = Color(0xFF5A3C00) // Deep Amber container
val PerfumeOnPrimaryContainer = Color(0xFFFFDEA0) // Light Cream on container

val PerfumeSecondary = Color(0xFFB5C8A8)       // Sage Green — botanical accent
val PerfumeOnSecondary = Color(0xFF213320)
val PerfumeSecondaryContainer = Color(0xFF374935)
val PerfumeOnSecondaryContainer = Color(0xFFD1E4C3)

val PerfumeTertiary = Color(0xFFD4B8C7)        // Dusty Rose — floral accent
val PerfumeOnTertiary = Color(0xFF3D2333)

val PerfumeError = Color(0xFFFFB4AB)
val PerfumeOnError = Color(0xFF690005)
```

### 2.2 Surface & Background (Dark Theme)

```kotlin
val PerfumeBackground = Color(0xFF100E09)       // Warm near-black (amber undertone)
val PerfumeOnBackground = Color(0xFFEDE1D0)     // Warm cream
val PerfumeSurface = Color(0xFF1C190F)          // Slightly lighter than background
val PerfumeOnSurface = Color(0xFFEDE1D0)
val PerfumeSurfaceVariant = Color(0xFF4D4435)   // Card surfaces
val PerfumeOutline = Color(0xFF998977)          // Borders & dividers

// Glassmorphism overlay color
val GlassOverlay = Color(0x1AFFF8E7)            // Warm white at 10% opacity
val GlassBorder = Color(0x33D4A853)             // Gold at 20% opacity
```

### 2.3 Fragrance Family Color Mapping

| Family | Color HEX | Usage |
|--------|-----------|-------|
| FLORAL | `#E8B4D0` | Dusty pink — Rose, Jasmine, Ylang |
| WOODY | `#8B6914` | Deep amber — Sandalwood, Cedar, Oud |
| ORIENTAL | `#C4612A` | Burnt orange — Vanilla, Musk, Resins |
| FRESH | `#7BC8A4` | Mint green — Citrus, Aquatic, Green |
| CITRUS | `#F5C842` | Bright yellow — Bergamot, Lemon |
| FOUGERE | `#5E8A6E` | Forest green — Lavender, Oakmoss |
| CHYPRE | `#8B7355` | Earth brown — Patchouli, Vetiver |
| GOURMAND | `#D4874E` | Caramel — Vanilla, Chocolate, Coffee |
| SPICY | `#C0392B` | Deep red — Pepper, Clove, Cinnamon |
| MUSKY | `#A89180` | Warm taupe — White Musk, Ambrette |

---

## 3. Typography

```kotlin
// Menggunakan Google Fonts via Compose
// Cormorant Garamond = serif untuk headings (luxury feel)
// Inter = sans-serif untuk body text (readability)
// Fira Code = monospace untuk angka kalkulasi

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Light,
        fontSize = 57.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = CormorantGaramond,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    // Untuk angka kalkulasi formula
    // Gunakan: MaterialTheme.typography.bodyLarge.copy(fontFamily = FiraCode)
)
```

---

## 4. Shape System

```kotlin
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),    // Chips, tags
    small = RoundedCornerShape(8.dp),         // Input fields
    medium = RoundedCornerShape(12.dp),       // Cards
    large = RoundedCornerShape(16.dp),        // Bottom sheets
    extraLarge = RoundedCornerShape(28.dp)    // FAB, main action buttons
)

// Glassmorphism Card modifier
fun Modifier.glassCard(): Modifier = this
    .background(
        brush = Brush.linearGradient(
            colors = listOf(Color(0x1AFFF8E7), Color(0x0DFFF8E7))
        ),
        shape = RoundedCornerShape(12.dp)
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(Color(0x33D4A853), Color(0x11D4A853))
        ),
        shape = RoundedCornerShape(12.dp)
    )
```

---

## 5. Navigation Structure

### 5.1 Bottom Navigation Bar

5 tab utama dengan icon dan label:

| Tab | Icon | Label | Route |
|-----|------|-------|-------|
| 1 | `flask` / Outline.Science | **Formula** | `formula_creator` |
| 2 | `chat_bubble` | **AI Chat** | `chat` |
| 3 | `inventory_2` | **Library** | `ingredient_library` |
| 4 | `folder_open` | **Dokumen** | `rag_documents` |
| 5 | `download` | **Bundle** | `model_download` |

Settings diakses via icon di TopAppBar.

### 5.2 Navigation Graph

```
NavHost
├── formula_creator/
│   ├── formula_list        (default)
│   ├── formula_editor/{id}
│   └── ingredient_picker
├── chat/
│   ├── session_list        (default)
│   └── chat_session/{id}
├── ingredient_library/
│   ├── ingredient_list     (default)
│   └── ingredient_detail/{id}
├── rag_documents/
│   └── document_list       (default)
├── model_download/
│   └── bundle_list         (default)
└── settings/
    └── settings_screen
```

---

## 6. Screen Specifications

### 6.1 Formula Creator Screen

**Layout:** Scaffold dengan TopAppBar + FAB

**TopAppBar:**
- Title: "Formula Creator" (Cormorant Garamond, headlineMedium)
- Actions: Share icon, Delete icon (muncul jika formula sudah disimpan)

**Body:**

```
┌─────────────────────────────────────┐
│ [TextField] Nama Formula            │  ← outlined, placeholder "Nama formula..."
├─────────────────────────────────────┤
│                                     │
│  RINGKASAN FORMULA          [card]  │
│  ┌─────────┬──────────┬──────────┐  │
│  │Total    │Konsentrasi│   SPL   │  │
│  │23.5 g   │  18.2%   │  ████   │  │
│  │         │   EDP    │  67/100 │  │  ← progress bar dengan warna dinamis
│  └─────────┴──────────┴──────────┘  │
│                                     │
│  FRAGRANCE WHEEL          [chip]    │
│  [●] Floral Oriental               │  ← animated wheel visualization
│                                     │
│  NOTE BREAKDOWN                     │
│  Top ▓▓▓░░░░ 32%                   │
│  Mid ▓▓▓▓▓░░ 48%                   │
│  Base▓▓░░░░░ 20%                   │
│                                     │
│  ──────── BAHAN-BAHAN ────────      │
│  ┌────────────────────────────────┐ │
│  │ 🌸 Jasmine Absolute    [FLORAL]│ │
│  │    2.500 g  ────→  [g] [mg][ml]│ │  ← segmented control untuk satuan
│  │    10.6% dari formula    [×]   │ │
│  └────────────────────────────────┘ │
│  ┌────────────────────────────────┐ │
│  │ 🌲 Cedarwood EO       [WOODY] │ │
│  │    5.000 g            [g][mg][ml│ │
│  │    21.3% dari formula    [×]   │ │
│  └────────────────────────────────┘ │
│                                     │
│  [+ Tambah Bahan]  ← outlined button│
└─────────────────────────────────────┘
        [💾 Simpan Formula]  ← FAB
```

**Interactions:**
- Setiap perubahan amount → recalculate real-time (debounced 300ms)
- Swipe bahan ke kiri → delete dengan undo snackbar
- Tap nama bahan → buka ingredient detail sheet
- Long press bahan → reorder mode
- Fragrance wheel → tap untuk expand ke full visualization

---

### 6.2 AI Chat Screen

**Layout:** Full-screen chat interface

**TopAppBar:**
- Back button (ke session list)
- Title: nama session (atau "New Chat")
- Actions: [📁 RAG] toggle indicator, [⚙️] persona settings shortcut

**Body:**

```
┌─────────────────────────────────────┐
│ ← New Chat             [📁RAG:ON][⚙] │  ← RAG badge (green dot = active)
├─────────────────────────────────────┤
│                                     │
│  ┌─────────────────────┐           │
│  │ 📄 Ref: luzi_catalog │           │  ← RAG source chip (jika ada)
│  └─────────────────────┘           │
│                                     │
│  [AI BUBBLE]                        │
│  ┌─────────────────────────┐        │
│  │ 🤖 Berdasarkan dokumen  │        │
│  │ yang Anda upload, saya  │        │
│  │ menyarankan formula:    │        │
│  │ ─────────────────────  │        │
│  │ • Jasmine Absolute 2.5g │        │
│  │ • Bergamot EO 1.0g      │        │
│  │ • Cedarwood EO 3.0g     │        │
│  │ ─────────────────────  │        │
│  │ SPL: 72/100 (Strong)    │        │
│  │ Arah: Floral Woody      │        │
│  │ [Import ke Formula →]   │        │  ← action button di dalam bubble
│  └─────────────────────────┘        │
│                         ▲           │
│              [USER BUBBLE]          │
│  ┌───────────────────┐              │
│  │ Buat formula EDP  │              │
│  │ dengan nuansa     │              │
│  │ floral dan woody  │              │
│  └───────────────────┘              │
│                                     │
│  [⌛ AI sedang berpikir...]          │  ← typing indicator (3 dots animasi)
│                                     │
├─────────────────────────────────────┤
│ [📎] [Ask about formula...]    [➤] │  ← input bar
└─────────────────────────────────────┘
```

**Chat Bubble Styling:**
- User bubble: Background `PerfumePrimary` (amber), align kanan, border radius 16dp (kecuali kanan-bawah 4dp)
- AI bubble: Background `GlassOverlay` + border `GlassBorder`, align kiri, border radius 16dp (kecuali kiri-bawah 4dp)
- Streaming text: animasi appear token per token dengan fade-in

**Special AI Response Cards:**
- Ketika AI output formula → render sebagai **Formula Card** dengan tombol "Import ke Formula Creator"
- Ketika AI menyebut bahan → highlight nama bahan sebagai tappable chip

**AI Companion Bento Contract (implementasi 2026-08-27):**
- Header menampilkan tiga status ringkas: `Model` (`SELECTED`, `VERIFYING`, `READY`, `FAILED`), `Dataset` (`SEEDING`, `READY`, `EMPTY`, `FAILED`), dan `Internet` (`OFF`/`ON` dengan consent eksplisit).
- Setiap jawaban menyimpan `RetrievalReport` dan citation bertipe: katalog, template, knowledge, upload, atau web. Card sumber menampilkan judul, excerpt, skor bila ada, waktu retrieval, hash/trust, halaman/chunk, serta membuka URI dokumen atau URL HTTPS melalui `LocalUriHandler`/intent.
- Web fallback hanya dijalankan saat toggle aktif dan evidence lokal tidak cukup. UI wajib menampilkan `WEB_UNAVAILABLE`/provider error bila request gagal; tidak boleh menampilkan hasil seolah-olah berhasil.
- Formula card menampilkan total aktual dalam gram, konsentrasi aktual, ID katalog setiap bahan, bahan unresolved, serta peringatan bahwa SPL/IFRA bukan jaminan keselamatan atau hasil uji fisik.
- Dokumen dan cuplikan web diperlakukan sebagai data tidak tepercaya; teks instruksi di dalamnya tidak boleh mengubah persona inti atau guardrail.

---

### 6.3 Ingredient Library Screen

**Layout:** Scaffold + SearchBar + LazyColumn

```
┌─────────────────────────────────────┐
│ 📚 Library Bahan                    │
│ ┌─────────────────────────────────┐ │
│ │ 🔍 Cari bahan...               │ │  ← search bar (active = full width)
│ └─────────────────────────────────┘ │
│                                     │
│ [Semua][Natural][Sintetis][Custom]  │  ← filter chips (horizontal scroll)
│ [Floral][Woody][Oriental][Fresh]... │  ← fragrance family chips
│                                     │
│ Brand: [Semua][Luzi][Parfarome][Mac]│  ← brand filter chips
│                                     │
│ ─────── NATURAL ──────────────(45) │
│ ┌────────────────────────────────┐  │
│ │ 🟡 Bergamot EO          [TOP] │  │
│ │    Citrus | 0.882 g/ml        │  │
│ │    "Fresh, citrusy, herbal"   │  │
│ └────────────────────────────────┘  │
│ ┌────────────────────────────────┐  │
│ │ 🟣 Lavender EO         [TOP]  │  │  ← warna dot = fragrance family
│ │    Fresh | 0.885 g/ml         │  │
│ └────────────────────────────────┘  │
│                                     │
│ ─────── LUZI ─────────────────(32) │
│ ┌────────────────────────────────┐  │
│ │ 🟠 Rose 300 (Luzi)    [HEART] │  │
│ │    Floral | Inspired: —       │  │
│ └────────────────────────────────┘  │
└─────────────────────────────────────┘
                              [+] FAB ← tambah custom bahan
```

**Ingredient Detail Bottom Sheet:**
```
┌─────────────────────────────────────┐
│                  ▬                  │  ← drag handle
│ 🌸 Jasmine Absolute                 │
│ NATURAL • HEART NOTE • FLORAL       │  ← chips
│ ─────────────────────────────────── │
│ Densitas: 0.976 g/ml               │
│ CAS: 8022-96-6                      │
│ Sumber: Jasminum sambac             │
│ Batas penggunaan: lihat SDS/IFRA   │  ← kategori, tanggal, dan sumber wajib tampil
│                                     │
│ "Rich, warm, intensely floral       │
│  with waxy and fruity undertones"   │
│                                     │
│ [+ Tambah ke Formula Aktif]         │
└─────────────────────────────────────┘
```

---

### 6.4 RAG Documents Screen

**Layout:** Scaffold dengan EmptyState jika belum ada dokumen

```
┌─────────────────────────────────────┐
│ 📂 Dokumen Referensi                │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ 📁 Folder RAG                   │ │
│ │ /storage/emulated/0/PerfumeDocs │ │
│ │ [Ubah Folder]              [✓]  │ │  ← green checkmark = configured
│ └─────────────────────────────────┘ │
│                                     │
│ 3 dokumen terindeks                 │
│                                     │
│ ┌────────────────────────────────┐  │
│ │ 📄 luzi_catalog_2024.pdf       │  │
│ │    2.4 MB • 124 chunks         │  │
│ │    Diindeks: 19 Agu 2026       │  │
│ │    ████████████ 100%  ✓        │  │  ← indexing progress
│ └────────────────────────────────┘  │
│ ┌────────────────────────────────┐  │
│ │ 📄 perfume_formulation_guide.pdf│ │
│ │    1.1 MB • 67 chunks          │  │
│ │    ████████░░░  75%  ⟳          │  │  ← masih indexing (spinning)
│ └────────────────────────────────┘  │
│                                     │
│         [+ Tambah Dokumen]          │  ← outlined button, buka SAF
└─────────────────────────────────────┘
```

**Empty State:**
```
        [Ilustrasi: open book dengan partikel aroma]
         Belum ada dokumen referensi
         Tambahkan PDF, TXT untuk digunakan
         sebagai referensi AI Chatbot
              [+ Pilih Folder RAG]
```

---

### 6.5 Model Download Screen

**Layout:** LazyColumn dengan section per kategori

```
┌─────────────────────────────────────┐
│ 📦 Bundle & Model AI                │
│ ──────────────────────────────────  │
│ 📁 Folder Download:                 │
│ /storage/emulated/0/PerfumeAI/      │
│ [Ubah]                              │
│ ──────────────────────────────────  │
│                                     │
│ ── MODEL LLM ──────────────────     │
│ ┌────────────────────────────────┐  │
│ │ ⭐ Gemma 4 E2B-it     RECOMMENDED│ │
│ │    2.41 GiB • `.litertlm`       │ │
│ │    [VERIFYING / READY / FAILED] │ │  ← SHA-256 + runtime check
│ │    [Aktif ✓]    [Hapus]          │ │
│ └────────────────────────────────┘  │
│ ┌────────────────────────────────┐  │
│ │ Gemma 4 E4B-it                  │  │
│ │    3.41 GiB • kemampuan lebih tinggi│ │
│ │    [Download]                  │  │
│ └────────────────────────────────┘  │
│ ┌────────────────────────────────┐  │
│ │ Status: model aktif hanya setelah│ │
│ │ nama, ukuran, SHA-256, dan      │ │
│ │ LiteRT runtime verification OK  │ │
│ └────────────────────────────────┘  │
│                                     │
│ ── EMBEDDING MODEL (WAJIB) ─────   │
│ ┌────────────────────────────────┐  │
│ │ all-MiniLM-L6-v2 LiteRT/TFLite │  │
│ │    384-d • bundled untuk RAG    │  │
│ │    [Download]                  │  │
│ └────────────────────────────────┘  │
│                                     │
│ ── DATABASE BUNDLE ─────────────    │
│ ┌─────────┐ ┌─────────┐ ┌────────┐ │
│ │ 🇨🇭 Luzi │ │🇪🇸 Parfa│ │🇮🇩 Macr│ │
│ │ 5 MB    │ │ 5 MB    │ │ 3 MB   │ │
│ │[Download│ │[✓ Aktif]│ │[Downld]│ │
│ └─────────┘ └─────────┘ └────────┘ │
└─────────────────────────────────────┘
```

---

### 6.6 Settings Screen

```
┌─────────────────────────────────────┐
│ ← Pengaturan                        │
├─────────────────────────────────────┤
│ ── AI CHATBOT ───────────────────   │
│                                     │
│ Model LLM Aktif                     │
│ [Gemma 4 E2B-it         ▼]         │  ← dropdown; aktif hanya READY
│                                     │
│ Persona AI (System Prompt)          │
│ ┌─────────────────────────────────┐ │
│ │ Kamu adalah ParfumAI, asisten   │ │
│ │ perfumer berpengalaman...       │ │
│ │                                 │ │
│ │                         [Edit]  │ │
│ └─────────────────────────────────┘ │
│ [Reset ke Default]                  │
│                                     │
│ Temperatur AI                       │
│ Kreatif ──○────────── Presisi       │  ← slider 0.0–1.0
│                 0.7                 │
│                                     │
│ Max Token Output                    │
│ [256]  [512 ✓]  [1024]  [2048]     │  ← segmented button
│                                     │
│ ── RAG ──────────────────────────   │
│ Jumlah Chunk (Top-K)               │
│ ●──────○─────────────              │  ← slider 3–10, default 5
│              5 chunks               │
│                                     │
│ ── INTERNET FALLBACK ───────────   │
│ [OFF ✓] Cari web jika lokal kurang │  │  ← default OFF + consent
│ MediaWiki / registry resmi saja    │
│ Query lokal disanitasi; dokumen    │
│ upload dan history tidak dikirim   │
│                                     │
│ ── FORMULA ──────────────────────   │
│ Satuan Default                      │
│ [Gram ✓]  [Miligram]  [Mililiter]  │
│                                     │
│ ── TENTANG ───────────────────────  │
│ Versi: 1.0.0                       │
│ Lisensi Open Source                │
└─────────────────────────────────────┘
```

---

## 7. Component Library

### 7.1 FragranceFamilyChip
```kotlin
@Composable
fun FragranceFamilyChip(
    family: FragranceFamily,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val color = family.toColor() // mapping dari section 2.3
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(family.label) },
        leadingIcon = { 
            Box(Modifier.size(8.dp).background(color, CircleShape)) 
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color
        )
    )
}
```

### 7.2 SplGauge
```kotlin
@Composable
fun SplGauge(
    spl: Float,         // 0–100
    label: SplLabel,
    modifier: Modifier = Modifier
) {
    // Animated arc gauge
    // Warna: 0-25 sage, 26-50 amber, 51-75 orange, 76-100 red
    val color = when (label) {
        SplLabel.SUBTLE -> PerfumeSecondary
        SplLabel.MODERATE -> PerfumePrimary
        SplLabel.STRONG -> Color(0xFFE07020)
        SplLabel.INTENSE -> Color(0xFFE04040)
    }
    // Canvas arc dengan animasi progress
}
```

### 7.3 FormulaIngredientRow
```kotlin
@Composable
fun FormulaIngredientRow(
    ingredient: FormulaIngredient,
    onAmountChange: (Double, MeasurementUnit) -> Unit,
    onRemove: () -> Unit
) {
    // SwipeToDismiss wrapper
    // Left: ingredient info + percentage badge
    // Right: amount input + unit segmented control
}
```

### 7.4 ChatBubble
```kotlin
@Composable
fun ChatBubble(
    message: ChatMessage,
    onImportFormula: ((Formula) -> Unit)? = null
) {
    // USER bubble: trailing align, amber background
    // ASSISTANT bubble: leading align, glass background
    // Jika message mengandung formula JSON → render FormulaCard di dalam bubble
}
```

### 7.5 DownloadBundleCard
```kotlin
@Composable
fun DownloadBundleCard(
    bundle: DownloadBundle,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit
) {
    // State machine: NOT_DOWNLOADED | DOWNLOADING | PAUSED | COMPLETED
    // DOWNLOADING: animated linear progress + speed + ETA
    // COMPLETED: green check + "Aktif" chip
}
```

### 7.6 FragranceWheelVisualization
```kotlin
@Composable
fun FragranceWheelVisualization(
    scores: Map<FragranceFamily, Double>,
    primaryFamily: FragranceWheelPrimary,
    modifier: Modifier = Modifier
) {
    // Canvas-based radar/spider chart
    // 4 primary axes: Floral, Woody, Oriental, Fresh
    // Animasi fill menggunakan Animatable
    // Tap to expand ke fullscreen overlay
}
```

---

## 8. Animation Specs

| Element | Animation | Duration | Easing |
|---------|-----------|----------|--------|
| Screen transition | Slide + Fade | 300ms | FastOutSlowIn |
| Card appear | Scale 0.95→1.0 + Fade | 250ms | EaseOutBack |
| SPL gauge fill | Arc sweep | 600ms | EaseInOut |
| Formula recalculate | Number count-up | 400ms | FastOutLinearIn |
| Chat bubble appear | Slide from bottom | 200ms | EaseOut |
| AI streaming token | Fade in | 50ms/token | Linear |
| Download progress | Linear | Continuous | Linear |
| Fragrance wheel | Radar fill | 500ms | EaseOut |
| Bottom sheet open | Spring animation | — | Spring(0.8f) |

---

## 9. Empty States

Setiap screen yang bisa kosong HARUS punya illustrated empty state:

| Screen | Ilustrasi | Tagline | CTA |
|--------|-----------|---------|-----|
| Formula List | Botol parfum kosong | "Mulai racik parfum pertamamu" | "+ Buat Formula" |
| Chat Session List | Botol dengan gelembung chat | "Tanya ParfumAI soal apapun" | "Mulai Chat" |
| RAG Documents | Buku terbuka dengan aroma | "Tambah referensi untuk AI lebih pintar" | "+ Pilih Folder" |
| Ingredient Search | Pipet lab kosong | "Bahan tidak ditemukan" | "Tambah Sendiri?" |

---

## 10. Dark Mode & Light Mode

Aplikasi menggunakan **Dark Mode sebagai default** (karena estetika luxury parfum lebih cocok dengan dark).

Light mode tersedia sebagai opsi di Settings, menggunakan token Material 3 yang sesuai. Di light mode:
- Background: warm cream `#FFF8EC`
- Surface: white `#FFFFFF`
- Primary: tetap amber `#8B5E00` (adjusted for light)

---

## 11. Accessibility

- Minimum contrast ratio: 4.5:1 untuk teks body, 3:1 untuk large text
- Semua interactive element: min touch target 48×48dp
- Semua icon harus punya `contentDescription`
- Screen reader: pastikan semua state perubahan di-announce via `LiveRegion` atau Semantics modifier
- Support `reduceMotion`: disable animasi jika accessibility setting aktif

---

## 12. Responsive Design

Aplikasi didesain untuk **phone portrait** sebagai layout utama. Pertimbangan tablet (landscape):

- **Tablet 2-pane layout:** Formula Creator + Ingredient Library side-by-side
- **Chat + RAG Documents:** 2-pane, chat di kiri, document list di kanan
- Breakpoint: lebar >= 600dp → gunakan 2-pane layout via `AdaptiveNavigationSuite`

---

## 13. Asset Guidelines

### 13.1 App Icon
- Foreground: Botol parfum stylized dengan silhouette minimalis, warna amber
- Background: Dark brown gradient `#1C0A00` → `#3D2000`
- Adaptive icon dengan safe zone 66% dari total size

### 13.2 Onboarding Illustrations
- Style: Line art dengan warna amber/sage aksen, background transparan
- Konten: Botol parfum, pipet, bahan-bahan alam (lavender, rose, cedarwood)
- Format: SVG di-bundle dalam drawable resource

### 13.3 Fragrance Family Icons
- Set icon 24dp dan 48dp untuk setiap fragrance family
- Style konsisten: outline icons dengan warna family masing-masing
