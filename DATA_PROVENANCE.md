# Data and model provenance

Inventory inspected 2026-10-23. These assets predate this repair and were supplied in the local project; this inventory does not create missing supplier verification or distribution rights.

| Asset | Raw records | Status |
| --- | ---: | --- |
| `ingredients_natural.json` | 42 | Existing natural-material and carrier metadata; not a safety certificate. |
| `ingredients_luzi.json` | 154 | Existing branded catalog metadata. |
| `ingredients_parfarome.json` | 1,590 | Existing catalog metadata, including incomplete/default family/density classifications. |
| `ingredients_macrabame.json` | 261 | Existing branded catalog metadata. |
| `formula_templates.json` | 32 | 30 legacy reference recipes plus 2 explicitly labeled application-calculated starter concentrates using available catalog materials. |
| `perfumery_knowledge.json` | 13 | Paraphrased knowledge snapshot with per-entry source URLs and retrieval dates. |

Raw ingredient count is 2,047; the application may filter packaging/noise during ingestion. It must not claim that all records are supplier-verified. Existing IFRA numbers lack category/version/source provenance and must not be treated as verified limits. Family and density metadata can be incomplete or inferred; check the actual product before relying on mL conversion or family prediction.

Legacy templates contain materials absent from this inventory and remain unavailable for import until fully resolved. Starter templates 31/32 are new, transparent calculation designs: a 25/45/30 g natural top/heart/base concentrate and a 10 g single-bibit Luzi weighing template. They are not supplier recipes, tested perfumes or skin-ready products. Their descriptions explicitly preserve those limits. Packaging/bundle listings are hidden from material selection without deleting persisted rows or changing existing IDs.

The bundled MiniLM TFLite embedding and WordPiece vocabulary were already present. TFLite SHA-256: `0aac5b0b76be23ab94f065a7fab6e0daead5e57f6ff7d55e19a2641d6a81f276`. Its conversion source/license has not been established by this audit; do not infer a new license from this repository. Output vectors must be finite and 384-dimensional. A catalog index is keyed by corpus version and embedding hash.

LLM artifacts are downloaded separately from the pinned Hugging Face LiteRT-community Gemma endpoints in `ModelArtifactRegistry.kt`, with exact revision, byte count and SHA-256 checks. They are excluded from Git. Model usage remains subject to the upstream model terms.

Web references are discovered through the MediaWiki Action API or curated official IFRA/FDA/EU URLs. The article content is fetched under bounded response/time/host rules, relevant passages are ranked, and successful content may be cached locally with URL/hash/timestamp/TTL as `WEB_UNTRUSTED`. Wikipedia attribution remains with its article and applicable upstream terms. Other pages retain their own terms; source citation is not permission for bulk redistribution.

Third-party source checkouts, browser profiles and raw scraping workspaces under `docs/` are excluded from Git. The product/design/agent documents and application assets are included explicitly. The old preprocessing script is retained as development history, not evidence that its legacy IFRA/default-density constants are authoritative; review those inputs before regenerating assets.
