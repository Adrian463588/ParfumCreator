# Tasks: Mobile App Hardening & Anti-Tampering / Anti-Reverse Engineering

## Analysis & Planning
- [x] Analyze offline Android app threats under OWASP MASVS-RESILIENCE guidelines.
- [x] Author proposal, design, spec, and tasks for OpenSpec change `mobile-app-hardening`.
- [x] Validate OpenSpec change strictly.

## Implementation
- [x] Implement `SecurityHardeningManager` with anti-debugging, signature verification, root detection, and Frida/hooking scanning.
- [x] Implement `AndroidKeyStoreCrypto` for hardware-backed AES-GCM data encryption without static keys.
- [x] Mask proprietary engine heuristic constants in `FormulaCalculationEngine`.
- [x] Harden `AndroidManifest.xml` (`allowBackup="false"`, `usesCleartextTraffic="false"`) and `data_extraction_rules.xml`.
- [x] Update `app/build.gradle.kts` with `isMinifyEnabled = true` and `isShrinkResources = true` in release build type.
- [x] Update `app/proguard-rules.pro` with aggressive R8 optimization, log stripping, class repackaging, and reflection keeps.

## Verification & QA
- [x] Add unit tests for `SecurityHardeningManager` and `AndroidKeyStoreCrypto` in `SecurityHardeningTest.kt`.
- [x] Run `:app:testDebugUnitTest` to verify all tests pass (181/181 passed).
- [x] Run `:app:lintDebug` to verify zero lint errors.
- [x] Run `:app:assembleDebug` and `:app:assembleRelease` to verify R8 bytecode minification and resource shrinking.
- [x] Run connected tests on headless emulator `emulator-5562` (AVD `codex_psikotes_api33`, API 33).
- [x] Update audit documentation, PRD, and changelog.
