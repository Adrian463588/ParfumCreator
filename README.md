# ParfumCreator

Local-first Android companion for formulating perfumes from natural aromatics and catalog fragrance oils, with on-device RAG, LiteRT-LM inference, native C++17 calculation hardening, and opt-in cited web lookup.

Versi terbaru: **v1.3.1** (versionCode 5) | Android 8.0+ (API 26) | Kotlin, Jetpack Compose, Material 3, Android NDK (C++17)

## Unduh APK terbaru (v1.3.1)

Unduh langsung APK debug terbaru dari rilis publik GitHub tanpa perlu kompilasi lokal:

- **Unduh APK langsung (v1.3.1 Debug)**: [ParfumCreator-v1.3.1-debug.apk](https://github.com/Adrian463588/ParfumCreator/releases/download/v1.3.1/ParfumCreator-v1.3.1-debug.apk)
- **Halaman rilis terbaru (v1.3.1)**: [GitHub Release v1.3.1](https://github.com/Adrian463588/ParfumCreator/releases/tag/v1.3.1)
- **Semua rilis**: [GitHub Releases](https://github.com/Adrian463588/ParfumCreator/releases)

Cara pasang di perangkat Android (sideloading):
1. Unduh berkas `ParfumCreator-v1.3.1-debug.apk` melalui tautan di atas dan cocokkan checksum SHA-256 yang tercantum pada halaman rilis `v1.3.1`.
2. Buka berkas APK dari pengelola berkas Android, lalu izinkan opsi **Install unknown apps** bila sistem meminta izin pemasangan.
3. Selesaikan pemasangan dan jalankan aplikasi. Build ini adalah **Debug APK** untuk pengujian dan pemasangan mandiri, bukan build bertanda tangan Play Store.

## Riwayat versi

| Versi | Tanggal | Ringkasan perubahan | Unduh / Referensi |
| --- | --- | --- | --- |
| **v1.3.1** | 2026-11-25 | Rilis publik pertama, audit DevSecOps sebelum publikasi, sinkronisasi `User-Agent` dengan `BuildConfig.VERSION_NAME`, dan rilis penuh hardening NDK C++17 & OWASP MASVS | [ParfumCreator-v1.3.1-debug.apk](https://github.com/Adrian463588/ParfumCreator/releases/download/v1.3.1/ParfumCreator-v1.3.1-debug.apk) |
| **v1.3.0** | 2026-11-14 | Milestone internal hardening OWASP MASVS-RESILIENCE, library native `libparfume_core.so` (C++17), `AndroidKeyStoreCrypto` AES-256 GCM, R8 minification, dan headless emulator harness | Digabungkan ke rilis publik v1.3.1 |
| **v1.2.0** | 2026-10-26 | Integrasi 200 studi template resep (100 natural & 100 bibit sintetis), browser resep dinamis, binding produk katalog eksplisit, dan penskalaan batch | [Unduh APK v1.2.0](https://github.com/Adrian463588/ParfumCreator/releases/download/v1.2.0/app-debug.apk) |
| **v1.1.0** | 2026-10-23 | Perbaikan gerbang relevansi RAG lokal, internet fallback opt-in otomatis, validasi formula katalog, dan provenance dataset | [Detail rilis v1.1.0](https://github.com/Adrian463588/ParfumCreator/releases/tag/v1.1.0) |
| **v1.0.0** | 2026-08-20 | Baseline awal kalkulator formula, katalog bahan natural/sintetis, RAG lokal, dan AI companion | Baseline internal (tanpa aset rilis) |

## Fitur utama

- **Formula Creator & kalkulator terpadu**: Buat dan simpan formula memakai ID bahan katalog nyata (natural essential oil dan fragrance oil merek Luzi, Parfarome, Macrabame) dalam satu mesin kalkulasi. Konversi gram, miligram, dan mililiter berdasarkan densitas katalog; hitung konsentrasi massa, proporsi note (top, middle, base), dan diagram radar fragrance wheel 4-sumbu.
- **232 template resep**: Jelajahi 100 studi konsentrat natural (`NAT-001` sampai `NAT-100`), 100 studi konsentrat bibit (`BIB-001` sampai `BIB-100`), dan 32 template bawaan/starter. Pratinjau komposisi, skala gram batch, pilih produk katalog nyata untuk accord yang belum terikat, setujui catatan adaptasi, lalu terapkan ke draft Formula Creator tanpa substitusi diam-diam atau auto-save.
- **RAG lokal (MiniLM 384-d & Room)**: Unggah dokumen PDF, TXT, MD, CSV, atau JSON melalui Storage Access Framework (SAF) Android. Dokumen dipotong dengan batas Unicode aman, di-embed lokal memakai `all-MiniLM-L6-v2.tflite`, dan dicari dengan pemeringkatan hybrid dense + lexical disertai sitasi halaman dan nama berkas.
- **AI lokal Gemma E2B/E4B (LiteRT-LM)**: Unduh model pinned dari Hugging Face melalui WorkManager resumable dengan verifikasi ukuran byte, revisi, dan SHA-256 sebelum status menjadi siap. Jika model lokal belum terpasang atau belum terverifikasi, aplikasi tetap memberikan jawaban ekstraktif bersumber dari katalog, template, dan dokumen lokal.
- **Internet fallback opt-in**: Mati secara default (`allowInternetFallback = false`). Jika diaktifkan di Settings, aplikasi mencari otomatis ke host HTTPS dalam allowlist (`id.wikipedia.org`, `en.wikipedia.org`, IFRA, FDA, EU CosIng) saat bukti lokal tidak mencukupi. Query disanitasi dan riwayat chat atau dokumen lokal tidak dikirim.
- **Keamanan aplikasi (OWASP MASVS-RESILIENCE & NDK C++17)**: Kalkulasi SPL/konsentrasi, anti-debugging (`ptrace` & `/proc/self/status` `TracerPid`), dan inspeksi hooking (`/proc/self/maps`) dikompilasi ke `libparfume_core.so` lintas ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`), dilengkapi `AndroidKeyStoreCrypto` AES-256 GCM, `allowBackup="false"`, `usesCleartextTraffic="false"`, serta R8 minification pada build rilis.

Catatan formulasi: SPL adalah heuristik aplikasi, bukan ukuran daya tahan, proyeksi, atau sillage di kulit. Kategori konsentrasi dan keluarga aroma berfungsi sebagai alat bantu peracikan. Dokumentasi pemasok dan densitas bahan aktual selalu menjadi acuan utama.

## Bangun dan verifikasi

Prasyarat: JDK 17+ (teruji pada JDK 21), Android SDK platform 35 dan NDK/CMake 3.22.1, serta koneksi jaringan untuk resolusi dependensi Gradle pertama kali. Atur SDK melalui `ANDROID_HOME` atau berkas `local.properties` yang tidak dilacak Git. Dependensi dipin pada `gradle/libs.versions.toml`.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
# Dengan perangkat fisik atau emulator terhubung:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Output APK debug berada di `app/build/outputs/apk/debug/app-debug.apk`. Laporan pengujian dan lint berada di `app/build/reports/`. Untuk alur pengujian emulator headless, lihat [panduan headless dev](android/HEADLESS_DEV_GUIDE.md).

## Cara penggunaan

1. Buka **Library** untuk menelusuri bahan, merek, note, dan metadata. Untuk membuka template resep, masuk ke **Settings > Template Resep** atau tombol template di tab **Formula**.
2. Pilih **Detail & gunakan** pada resep, sesuaikan target gram, dan ikat setiap bahan yang masih belum terpetakan ke produk katalog nyata. Adaptasi bahan dicatat eksplisit sebelum tombol **Gunakan di Formula Creator** aktif.
3. Buka **Documents** untuk mengunggah dokumen referensi via SAF dan tunggu pengindeksan selesai.
4. Buka **AI Chat** untuk bertanya tentang dokumen atau meminta racikan, misalnya: `Buatkan parfum natural citrus 100 g EDP`, `Buatkan parfum dari bibit Luzi 50 g 20%`, atau `Jelaskan perbedaan EDP dan EDT menurut dokumen saya`.
5. Aktifkan **Internet Fallback** di **Settings** jika ingin pencarian pengetahuan publik otomatis saat sumber lokal belum mencukupi.
6. Untuk inferensi LLM lokal, pilih folder penyimpanan SAF permanen, unduh model Gemma E2B atau E4B, lalu tunggu verifikasi SHA-256 dan runtime selesai.

## Arsitektur dan pengembangan

- `presentation`: layar Compose, Material 3, ViewModel, dan state UI.
- `domain`: model murni Kotlin, use case, dan mesin kalkulasi bersama (`FormulaCalculationEngine`).
- `data`: database Room, DataStore Preferences, dan implementasi repository.
- `core`: ekstraksi dokumen, embedding TFLite, klien web allowlisted, adapter LiteRT-LM, `AndroidKeyStoreCrypto`, `SecurityHardeningManager`, dan `NativeSecurityBridge` (JNI ke `app/src/main/cpp/parfume_core.cpp`).

Pengembangan mengikuti metodologi BMAD dan OpenSpec sebagai artefak perancangan (bukan dependensi runtime). Dokumen pendukung:

- [Product Requirements Document](docs/PRD.md)
- [Design System](docs/DESIGN.md)
- [Data and Model Provenance](DATA_PROVENANCE.md)
- [Changelog](CHANGELOG.md)
- [Implementation and Verification Audit](IMPLEMENTATION_AUDIT.md)
- OpenSpec changes: [recipe-template-library](openspec/changes/recipe-template-library/proposal.md) ([spec](openspec/changes/recipe-template-library/specs/recipe-template-library/spec.md), [tasks](openspec/changes/recipe-template-library/tasks.md)), [mobile-app-hardening](openspec/changes/mobile-app-hardening/proposal.md), dan [native-ndk-hardening](openspec/changes/native-ndk-hardening/proposal.md).

## Keamanan, privasi, dan sumber data

Pengindeksan dokumen, pencarian vektor, dan kalkulasi berjalan secara lokal di perangkat. Saat Internet Fallback aktif, hanya teks pertanyaan saat ini yang telah disanitasi (tanpa URL, email, nomor telepon, atau pola kredensial) yang dikirim ke endpoint HTTPS allowlisted tanpa redirect otomatis; dokumen unggahan dan riwayat percakapan tidak pernah keluar dari perangkat.

200 studi resep bawaan merupakan konsentrat eksperimental (bukan parfum siap pakai di kulit, formula asli pemasok, atau klaim penghargaan terverifikasi). Slot accord LUZI dan Parfarome tanpa SKU selalu memerlukan pemilihan produk katalog secara manual, sedangkan bahan natural tidak diganti otomatis. Aplikasi ini tidak menetapkan batas dilusi aman atau izin IFRA; rincian asal aset dan batas metadata tercatat di [DATA_PROVENANCE.md](DATA_PROVENANCE.md).

## Status verifikasi

Hasil gerbang otomatis (unit test, Android lint, build APK) serta keterbatasan uji perangkat keras untuk setiap versi dicatat di [IMPLEMENTATION_AUDIT.md](IMPLEMENTATION_AUDIT.md). Build yang lolos kompilasi tidak menggantikan uji unduhan model multi-gigabita dan inferensi langsung di perangkat target.
