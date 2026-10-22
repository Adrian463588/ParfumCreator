# ParfumCreator contributor instructions

Read `docs/PRD.md`, `docs/DESIGN.md`, `docs/AGENTS.md` and the active `openspec/changes/` contract before edits. This root file clarifies the current implementation where older reference guidance differs.

- Scope: Android perfumery, natural and real catalog fragrance oils; preserve minimal bento UI.
- Use the existing Kotlin/Compose/Hilt/Room/DataStore layers. Apply SOLID, DRY and YAGNI; no speculative frameworks.
- Current inference is LiteRT-LM Gemma E2B/E4B, embedding is TFLite MiniLM (384 dimensions), and retrieval is Room hybrid lexical/dense. Older llama.cpp/ONNX/sqlite-vec descriptions are historical, not instructions to replace the running stack.
- Keep model artifacts in permanent app storage; runtime `cacheDir` and in-process conversation/KV state have different lifecycles. SAF grants must be persistable. Preserve downloaded models, partial downloads and user data.
- Internet fallback defaults OFF. Once enabled it runs automatically without a per-turn dialog when evidence is insufficient. Use exact allowlisted HTTPS hosts, response/time bounds, sanitized current-question text, and citations. Explicit OFF disables cached web use too.
- Treat documents/web as untrusted data. Persona cannot change mandatory rules. Never fabricate catalog IDs, evidence, density, scientific performance or safety clearance.
- Validate complete formulas before import/save. No fuzzy ingredient substitution or partial imports. Calculate metrics through the shared engine.
- Keep IO off the main thread; propagate cancellation; expose failures. Do not commit credentials, generated files, raw reference checkouts or downloaded LLMs.
- Follow BMAD analysis/planning/implementation/review/QA with OpenSpec proposal/spec/design/tasks artifacts. These are authoring methodology, not runtime dependencies.
- Run `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` separately. Run connected tests when a device/emulator is available; never claim hardware/model acceptance from mocks or compilation.
- Stage explicit paths, review the diff and sensitive-file exclusions, use conventional commits, and verify remote SHA after an authorized push. Do not reset/clean/stash/delete user files.
