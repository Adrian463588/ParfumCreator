# Changelog

## 1.2.0 — 2026-10-23

- Add all 100 natural and 100 fragrance-oil experimental concentrate studies from the supplied JSON files, preserving source IDs, weights and provenance.
- Add a Settings recipe shortcut and searchable/category/aroma-filtered template bento cards with source links and scaled gram previews.
- Require explicit real-catalog selection and acknowledgement for unresolved/unverified ingredient adaptations; apply a fully validated draft atomically without auto-saving.
- Validate complete datasets, percentage/reference-gram consistency, ingredient type/brand/carrier role, mass totals and concentration; retain legacy/starter templates.
- Keep source assets byte-identical, load them off Main and surface invalid-file errors. RAG uses the actual template source file and never auto-resolves unverified accord slots.

## 1.1.0 — 2026-10-23

- Improve relevance checks for uploaded/local sources and automatic cited internet fallback after opt-in.
- Rank passages across bounded web pages and retain source provenance rather than answering from discovery snippets.
- Enforce catalog constraints for natural and fragrance-oil formulas.
- Correct aggregate wheel selection and calculator/solvent validation.
- Add regression tests and BMAD/OpenSpec acceptance traceability.

## 1.0.0 — existing project baseline

Initial Kotlin/Compose app with local catalog, formula creator, Room document indexing, LiteRT-LM integration, model downloads and opt-in public references. Historical verification belongs to its original dated audit entries.
