## ADDED Requirements

### Requirement: Native Fragrance Physics and SPL Engine
The application SHALL execute proprietary fragrance calculations (SPL score and mass concentration) within a compiled C++17 shared library (`libparfume_core.so`).

#### Scenario: Proprietary SPL Calculation via Native Engine
- **WHEN** valid inputs for concentration (20.0%), top note ratio (2.0), middle note ratio (3.0), and base note ratio (5.0) are calculated
- **THEN** the returned SPL score matches the normalized weighted note formula clamped to 0.0..100.0

#### Scenario: Zero or Invalid Inputs to Native Calculation
- **WHEN** non-finite, negative, or zero inputs are supplied
- **THEN** the native engine returns 0.0 safely without memory faults

### Requirement: Native Anti-Debugging and Tracer Detection
The native C++ library SHALL directly query kernel and process tracing status via low-level POSIX calls and ptrace.

#### Scenario: Active debugger or ptrace tracer attached
- **WHEN** a debugger attaches or ptrace tracing is active
- **THEN** `NativeSecurityBridge.checkNativeAntiDebug()` evaluates as true

#### Scenario: Clean native execution
- **WHEN** no tracer or debugger is attached
- **THEN** `NativeSecurityBridge.checkNativeAntiDebug()` evaluates as false

### Requirement: Low-Level Hooking Inspection via Native POSIX Calls
The native C++ library SHALL inspect `/proc/self/maps` using low-level POSIX file descriptors to detect injected dynamic instrumentation libraries.

#### Scenario: Injected Frida or Xposed agent in process memory
- **WHEN** `/proc/self/maps` contains signatures of dynamic instrumentation libraries
- **THEN** `NativeSecurityBridge.checkNativeHooking()` evaluates as true

### Requirement: Dual-Mode Fallback Architecture
The application SHALL gracefully fall back to pure Kotlin mathematics when executing in pure JVM host environments where the native `.so` library is not loaded.

#### Scenario: Execution in host JVM test runner
- **WHEN** unit tests run on host JVM without native binary loaded
- **THEN** calculation methods seamlessly invoke Kotlin reference implementations without throwing UnsatisfiedLinkError
