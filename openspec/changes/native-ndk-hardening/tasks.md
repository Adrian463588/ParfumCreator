# Tasks: Android NDK Native Hardening & C++ Algorithm Migration

- [x] **TASK-001**: Configure CMake build configuration in `app/src/main/cpp/CMakeLists.txt` and integrate `externalNativeBuild` into `app/build.gradle.kts`.
- [x] **TASK-002**: Implement C++ native library `app/src/main/cpp/parfume_core.cpp` with proprietary SPL calculations, concentration logic, and native anti-debug/hooking scanners.
- [x] **TASK-003**: Create `com.parfumecreator.core.nativebridge.NativeSecurityBridge` with dual-mode fallback (native JNI when loaded, Kotlin fallback on host JVM).
- [x] **TASK-004**: Wire `FormulaCalculationEngine` and `SecurityHardeningManager` to delegate to `NativeSecurityBridge`.
- [x] **TASK-005**: Add JNI keep rules in `app/proguard-rules.pro` to prevent R8 obfuscation of native interface method names.
- [x] **TASK-006**: Add comprehensive unit tests in `app/src/test/kotlin/com/parfumecreator/core/nativebridge/NativeSecurityBridgeTest.kt`.
- [x] **TASK-007**: Validate OpenSpec change with `npx @fission-ai/openspec validate native-ndk-hardening --strict`.
- [x] **TASK-008**: Run verification suite: `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleRelease`.
- [x] **TASK-009**: Run headless smoke test via `run-fast-loop.ps1` on `emulator-5562`.
- [x] **TASK-010**: Commit with conventional commit, push to GitHub, and verify remote SHA.
