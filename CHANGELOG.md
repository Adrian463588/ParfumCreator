# Changelog

## 1.3.0 — 2026-11-14

- Implement Mobile App Hardening aligned with OWASP MASVS-RESILIENCE for 100% offline security.
- Add `SecurityHardeningManager` covering runtime Java & native ptrace anti-debugging (`TracerPid`), package ID integrity, SHA-256 certificate signature verification, root detection, and dynamic hooking defense (`/proc/self/maps` Frida/Xposed scanning and socket detection).
- Add `AndroidKeyStoreCrypto` for hardware-backed AES-256 GCM authenticated encryption with dynamic IV and 128-bit AEAD integrity tag validation against tampering.
- Mask proprietary SPL calculation factors via runtime XOR bitwise transformation in `FormulaCalculationEngine`.
- Enforce R8 full minification, internal package repackaging (`com.parfumecreator.internal`), resource shrinking, and production log stripping in `proguard-rules.pro`.
- Restrict backup and device data extraction via `allowBackup="false"`, `usesCleartextTraffic="false"`, `backup_rules.xml`, and `data_extraction_rules.xml`.
- Provide automated 3-layer lightweight headless Android emulator CLI testing loop and scripts (`start-emulator.ps1`, `stop-emulator.ps1`, `status-emulator.ps1`, `capture-ui-tree.ps1`, `get-error-logs.ps1`, `run-fast-loop.ps1`).

## 1.2.0 — 2026-10-26

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
