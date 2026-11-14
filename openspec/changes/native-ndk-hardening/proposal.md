# Android NDK Native Hardening & C++ Algorithm Migration

Date: 2026-11-14
Status: accepted

## Why

Following the initial Mobile App Hardening baseline, reverse engineers using decompiler tools (such as JADX or CFR) can still inspect decompiled bytecode if sensitive algorithms reside purely in Kotlin. To elevate protection to OWASP MASVS-RESILIENCE L2:
1. High-value proprietary algorithms (e.g., Scent Profile Level calculation, evaporation velocity heuristic, note weight normalization) must be moved from JVM bytecode into native C++ machine code (`.so` library).
2. Native binaries are compiled to ELF machine code (ARM64, x86_64), making static disassembly significantly more difficult and time-consuming than inspecting decompiled Java/Kotlin classes.
3. Native-level anti-debugging (`ptrace(PTRACE_TRACEME, ...)` and direct POSIX `/proc/self/status` syscall inspection) provides defense-in-depth beneath the Java runtime layer, resisting standard Java method hooking.
4. Native symbol stripping (`-fvisibility=hidden`, `-Wl,--strip-all`) eliminates internal function names from the dynamic symbol table in release builds.

## What Changes

1. **Native C++ Library (`libparfume_core.so`)**:
   - Create `app/src/main/cpp/parfume_core.cpp` and `CMakeLists.txt` using CMake 3.22+ and NDK r27/r28.
   - Implement native perfumery mathematics:
     - `calculateSplNative`: Normalized note ratios, weighted top/mid/base factors, heuristic SPL score.
     - `calculateConcentrationNative`: Mass percentage calculation and safety bounds.
   - Implement native anti-debugging & tampering detection:
     - `checkNativeSecurity`: Invokes `ptrace(PTRACE_TRACEME, ...)` to detect active tracers/debuggers.
     - Direct POSIX file descriptor reads of `/proc/self/status` for `TracerPid`.
     - Direct POSIX file descriptor reads of `/proc/self/maps` for `frida-agent`, `libxposed`, etc.
2. **Native-to-Kotlin Bridge (`NativeSecurityBridge`)**:
   - Provide `NativeSecurityBridge` object with `System.loadLibrary("parfume_core")`.
   - Implement graceful fallback to Kotlin engine if native library is unavailable (e.g., during pure host JVM unit testing suites like `testDebugUnitTest`).
3. **Build & Compiler Hardening**:
   - Configure CMake compiler flags: `-std=c++17`, `-fvisibility=hidden`, `-fvisibility-inlines-hidden`, `-O3`.
   - Configure NDK ABI filters: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
   - Configure ProGuard keep rules for JNI bridge methods.
4. **Verification & Observability**:
   - Unit tests validating native calculation parity with Kotlin formulas.
   - Headless Android emulator verification (`run-fast-loop.ps1`) executing native `.so` on `emulator-5562`.

## Scope & Non-Goals

- No unnecessary external third-party C++ dependencies (pure standard C++17 library).
- Retain existing Room/SQLite and LiteRT-LM structures without breaking database schema or LLM pipeline.
- Maintain 100% unit test pass rate in pure JVM host environments via graceful dual-mode bridge.
