# BMAD implementation and acceptance checklist

## Analysis / planning
- [x] Inspect both source schemas, metadata limitations and existing template-to-editor path.
- [x] Write proposal, design and scenario requirements before implementation.

## Implementation
- [x] Bundle unchanged JSON with provenance/checksums; preserve all 200 source IDs.
- [x] Implement pure validated parser and background cached loading with explicit errors.
- [x] Add complete catalog binding/scaling/application validation shared through a use case.
- [x] Expose searchable natural/bibit menu, preview, explicit mapping and safe apply.
- [x] Keep existing templates, draft cancellation, saved formulas and RAG source paths compatible.
- [x] Update version, README, changelog, product/design and audit docs.

## Review / QA
- [x] Real-asset counts, percentages, grams, IDs and provenance tests pass.
- [x] Application tests cover matching, manual consent, invalid IDs/types/brands, mass scaling and concentration.
- [x] OpenSpec strict validation passes.
- [x] Unit test gate passes (command/result/count recorded).
- [x] Android lint gate passes (errors/warnings recorded).
- [x] Debug assemble gate passes.
- [x] Connected-device gate attempted when available; missing device reported explicitly.
- [x] Explicit staging/secret/generated-file review, commit, push and remote SHA verification complete.
