# Design: Android NDK Native Hardening & C++ Algorithm Migration

## Architecture Overview

```
+--------------------------------------------------------------------------+
|                     Android Application Layer                            |
|  - FormulaCalculationEngine -> delegates to NativeSecurityBridge        |
|  - SecurityHardeningManager -> delegates to NativeSecurityBridge        |
+------------------------------------+-------------------------------------+
                                     | JNI Calls
+------------------------------------+-------------------------------------+
|                  Native C++ Subsystem (libparfume_core.so)               |
|  - parfume_core.cpp:                                                     |
|      * Native calculation: calculateSplNative(pct, top, mid, base)       |
|      * Native calculation: calculateConcentrationNative(fragG, totalG)   |
|      * Native anti-debug: ptrace(PTRACE_TRACEME, ...)                    |
|      * Direct POSIX /proc/self/status TracerPid parser                   |
|      * Direct POSIX /proc/self/maps hook scanner                         |
+------------------------------------+-------------------------------------+
                                     | Compiled with NDK clang / CMake
+------------------------------------+-------------------------------------+
|                     Build & Binary Hardening                             |
|  - Flags: -std=c++17 -fvisibility=hidden -fvisibility-inlines-hidden -O3 |
|  - Symbol stripping: strip --strip-all on release .so binaries           |
|  - Multi-ABI support: arm64-v8a, armeabi-v7a, x86_64, x86                |
|  - Dual-mode fallback: Graceful Kotlin fallback in pure JVM host tests    |
+--------------------------------------------------------------------------+
```

## Detailed Component Design

### 1. `NativeSecurityBridge` (`com.parfumecreator.core.nativebridge`)
- Manages dynamic library loading (`System.loadLibrary("parfume_core")`).
- Tracks `isNativeLoaded: Boolean`.
- Exposes JNI native functions:
  ```kotlin
  external fun calculateSplNative(
      concentrationPct: Double,
      topRatio: Double,
      middleRatio: Double,
      baseRatio: Double
  ): Double

  external fun calculateConcentrationNative(
      fragranceWeightG: Double,
      totalWeightG: Double
  ): Double

  external fun checkNativeAntiDebug(): Boolean
  external fun checkNativeHooking(): Boolean
  ```
- If `isNativeLoaded` is false (e.g. standard JVM host unit test run without native runtime), falls back to pure Kotlin calculation without throwing `UnsatisfiedLinkError`.

### 2. Native C++ Source (`app/src/main/cpp/parfume_core.cpp`)
- Built as shared library `libparfume_core.so`.
- Calculations:
  - Strict input sanitization (NaN / Infinite bounds check).
  - Exact formula equivalence:
    `normTop = (top / max) / sum; normMid = (mid / max) / sum; normBase = (base / max) / sum;`
    `noteFactor = (normBase * 1.5 + normMid * 1.0 + normTop * 0.5) / 3.0;`
    `spl = concentrationPct * noteFactor;` clamped to `[0.0, 100.0]`.
- Native Anti-Debugging:
  - `ptrace(PTRACE_TRACEME, 0, 1, 0)`: returns -1 if process is already traced/attached by ptrace/gdb/frida.
  - Low-level POSIX file I/O: opens `/proc/self/status` using `open()`, reads lines using `read()`, scans for `TracerPid:\t[1-9]`.
  - Low-level POSIX file I/O: opens `/proc/self/maps` using `open()`, reads lines looking for substrings: `frida`, `gadget`, `xposed`, `substrate`, `cydia`.

### 3. CMake Build Configuration (`app/src/main/cpp/CMakeLists.txt`)
- Minimum CMake version 3.22.1.
- Builds `parfume_core` as `SHARED`.
- Links `log` library for conditional debug logging.
- Applies `-fvisibility=hidden -fvisibility-inlines-hidden -O3`.
- NDK ABI packaging for `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.

### 4. ProGuard & R8 Rules
- Ensure JNI entrypoints in `com.parfumecreator.core.nativebridge.NativeSecurityBridge` are kept so R8 does not obfuscate native method names.
- Keep `-keepclasseswithmembernames class * { native <methods>; }`.
