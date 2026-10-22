# ParfumCreator

Android companion for creating perfumes from natural aromatics and catalog fragrance oils, with local document retrieval and optional automatic internet fallback.

Version **1.1.0** · Android 8.0+ (API 26) · Kotlin / Jetpack Compose / Material 3

## Capabilities

- Create and save formulas using real catalog ingredient IDs; natural and branded fragrance-oil blends share one calculation engine.
- Use two explicitly labeled catalog-based starter concentrates; legacy templates with missing materials remain unavailable until their ingredients are resolved.
- Convert g, mg and mL using catalog density; calculate mass concentration, note proportions and a weighted fragrance wheel.
- Upload PDF, TXT, MD, CSV or JSON documents through Android's document picker and answer from indexed local passages with page/file citations.
- Enable **Internet Fallback** in Settings to search automatically when local evidence is insufficient. There is no per-question confirmation. Explicit OFF keeps lookup and cached web evidence disabled.
- Search MediaWiki and curated IFRA/FDA/EU sources. Fetched pages have clickable provenance and are cached as untrusted references; they cannot add ingredients or grant safety clearance.
- Run local Gemma E2B/E4B through LiteRT-LM after the user downloads a pinned artifact. SHA-256 and runtime verification determine readiness. The runtime cache is separate from permanent model storage; conversations reuse in-process KV state.
- Retain useful, cited extractive answers and deterministic catalog formulas when a local LLM is unavailable.

SPL is an application heuristic, not a measured prediction of longevity, projection or sillage. Concentration categories and wheel families are formulation aids. Supplier documentation and actual material density take precedence over incomplete legacy metadata.

## Build and verify

Requirements: JDK 17+, Android SDK platform 35/build tools, and network access for the first Gradle dependency resolution. Configure the SDK through `ANDROID_HOME` or an untracked `local.properties` file. Use the checked-in Gradle wrapper; dependencies are pinned in `gradle/libs.versions.toml`.

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
# With a connected emulator or physical device:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Test and lint reports are under `app/build/reports/`. Build outputs and downloaded LLMs are excluded from Git.

## Use

1. Open Library to inspect materials, brands, notes and metadata.
2. Open Documents and upload a readable document; wait for indexing to finish.
3. Ask AI Chat a question about the document. Sources remain visible even without an LLM.
4. Enable Internet Fallback in Settings if automatic public knowledge lookup is wanted.
5. Ask for a natural blend or specify a catalog brand/material, batch and concentration. Review the calculated formula before importing it into Formula Creator.
6. For model-generated explanations, select a persistent SAF download folder, download a model, and wait for checksum/runtime verification.

Examples: `Buatkan parfum natural citrus 100 g EDP`; `Buatkan parfum dari bibit Luzi 50 g 20%`; `Jelaskan perbedaan EDP dan EDT menurut dokumen saya`.

## Architecture and development

`presentation` renders Compose state; `domain` owns models, use cases and calculations; `data` owns Room/DataStore/repositories; `core` owns embedding, parsing, network and LiteRT adapters. Reuse shared policies rather than adding parallel implementations. Follow SOLID, DRY and YAGNI.

Development uses BMAD analysis/planning/implementation/review/QA and OpenSpec change artifacts. The active contract and scenarios are in [OpenSpec change](openspec/changes/perfumery-companion-reliability/proposal.md), [scenario specification](openspec/changes/perfumery-companion-reliability/specs/perfumery-companion/spec.md), and [task/evidence checklist](openspec/changes/perfumery-companion-reliability/tasks.md).

See [product requirements](docs/PRD.md), [design](docs/DESIGN.md), [data provenance](DATA_PROVENANCE.md), [changelog](CHANGELOG.md), and [implementation/verification audit](IMPLEMENTATION_AUDIT.md). BMAD/OpenSpec are authoring methodology, not runtime dependencies.

## Privacy and data

Inference and document indexing run locally. Only sanitized current-question text is sent for enabled knowledge lookup; uploaded text and chat history are not included in search requests. Network fetches require HTTPS and exact host allowlisting, bounded responses and timeouts. Downloaded LLMs require pinned revision, size and checksum verification.

Catalog and embedding assets were already present in the supplied project. Some legacy product-family/density metadata and safety fields lack supplier provenance; treat them as catalog metadata, not verified scientific or regulatory facts. See DATA_PROVENANCE.md. No blanket license or redistribution rights are asserted for third-party datasets, models or references.

## Acceptance status

Automated gates and device limitations for this version are recorded in the dated section of IMPLEMENTATION_AUDIT.md. A passing build does not establish real model inference, model-download completion or full device UI acceptance. Follow that checklist when testing a release on hardware.
