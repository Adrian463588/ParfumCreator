## ADDED Requirements

### Requirement: Anti-Debugging Detection
The application SHALL detect Java and native debugger attachment, including ptrace tracers.

#### Scenario: Active debugger detected
- **WHEN** a debugger is attached via JDWP or ptrace sets TracerPid > 0
- **THEN** `SecurityHardeningManager` flags `isDebuggerAttached` as true

#### Scenario: No debugger attached in normal execution
- **WHEN** the application executes normally without debugger or ptrace
- **THEN** `SecurityHardeningManager` reports `isDebuggerAttached` as false

### Requirement: APK Integrity and Signature Verification
The application SHALL verify that its runtime package name and certificate SHA-256 fingerprint match authorized signatures.

#### Scenario: Repackaged or resigned APK
- **WHEN** an APK is resigned with an unauthorized certificate or running under an altered package name
- **THEN** `verifyPackageIntegrity` returns false and flags a tampering event

#### Scenario: Genuine authorized APK
- **WHEN** the APK runs with the authentic package name and expected certificate fingerprint
- **THEN** `verifyPackageIntegrity` returns true

### Requirement: Root and Hooking Framework Detection
The application SHALL check for common root binaries, test-keys build tags, and dynamic instrumentation agents (Frida / Xposed).

#### Scenario: Injected instrumentation library detected
- **WHEN** `/proc/self/maps` contains entries matching Frida or Xposed shared objects
- **THEN** `checkHookingFrameworks` reports true

#### Scenario: Clean execution environment
- **WHEN** no su binaries or hooking libraries are present
- **THEN** root and hooking checks evaluate as false

### Requirement: Hardware-Backed Key Storage & Encryption
The application SHALL generate and retrieve encryption keys using Android Keystore without embedding static master passwords in bytecode.

#### Scenario: Encryption and decryption roundtrip
- **WHEN** data is encrypted using `AndroidKeyStoreCrypto`
- **THEN** it produces distinct ciphertext with unique IVs, and decrypts back to the original plaintext

### Requirement: R8 Code Obfuscation and Log Stripping
Release builds of the application SHALL have R8 bytecode minification and resource shrinking enabled, stripping debugging log statements.

#### Scenario: Release build compilation
- **WHEN** `:app:assembleRelease` is executed
- **THEN** R8 minifies bytecode, strips `android.util.Log` calls, and packages the optimized APK without errors
