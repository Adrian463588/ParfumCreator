# Design: Mobile App Hardening & Anti-Tampering / Anti-Reverse Engineering

## Architecture Overview

```
+--------------------------------------------------------------------------+
|                     Android Application Layer                            |
|  - AndroidManifest.xml: allowBackup="false", usesCleartextTraffic="false"|
|  - ParfumeApp / MainActivity: Security status evaluation on launch      |
+------------------------------------+-------------------------------------+
                                     |
+------------------------------------+-------------------------------------+
|                  Security & Cryptography Subsystem                       |
|  - SecurityHardeningManager:                                             |
|      * Anti-debugging: Debug.isDebuggerConnected(), TracerPid check     |
|      * Anti-tampering: Package name & certificate SHA-256 fingerprint     |
|      * Anti-hooking: /proc/self/maps scan (Frida, Xposed), Frida port    |
|      * Root detection: su binary paths, test-keys build tags             |
|  - AndroidKeyStoreCrypto:                                                |
|      * AndroidKeyStore provider with AES-256 GCM                        |
|      * Zero hardcoded secret keys in APK                                 |
+------------------------------------+-------------------------------------+
                                     |
+------------------------------------+-------------------------------------+
|                     Build & Bytecode Hardening                           |
|  - R8 Compiler & Optimizer: isMinifyEnabled = true, isShrinkResources    |
|  - proguard-rules.pro: -assumenosideeffects (strip Log.*), -repackage    |
|  - Stripped debug symbols, line tables, and masked engine constants      |
+--------------------------------------------------------------------------+
```

## Security Hardening Details

### 1. `SecurityHardeningManager`
- Pure, testable security evaluator in `com.parfumecreator.core.security`.
- Methods:
  - `checkDebuggerAttached()`: checks `Debug.isDebuggerConnected()`, `Debug.waitingForDebugger()`, and parses `/proc/self/status` for `TracerPid: [1-9][0-9]*`.
  - `verifyPackageIntegrity(context, expectedPackageName, expectedCertFingerprints)`: retrieves signing certificate fingerprints via `PackageManager.GET_SIGNING_CERTIFICATES` (API 28+) or legacy signatures, computing SHA-256 hashes.
  - `checkRootPresence()`: inspects known binary locations (`/system/bin/su`, `/system/xbin/su`, `/sbin/su`, `/data/local/su`, `/data/local/bin/su`, `/system/sd/xbin/su`) and evaluates `Build.TAGS?.contains("test-keys")`.
  - `checkHookingFrameworks()`: reads `/proc/self/maps` looking for library signatures (`frida-agent.so`, `frida-gadget.so`, `libxposed`, `substrate`, etc.) and tests connection to standard Frida TCP port (27042).
  - `evaluateSecurityStatus(context)`: aggregates all checks into a strongly-typed `SecurityReport`.
  - Behavior: If `BuildConfig.DEBUG` is true, returns security status without crashing (permitting unit tests, automated CI, and local development); in release mode, informs UI/caller to halt or restrict sensitive actions.

### 2. `AndroidKeyStoreCrypto`
- Initializes `KeyStore.getInstance("AndroidKeyStore")`.
- Generates 256-bit AES key with `KeyGenParameterSpec`:
  - Purpose: `PURPOSE_ENCRYPT or PURPOSE_DECRYPT`
  - Block mode: `BLOCK_MODE_GCM`
  - Encryption padding: `ENCRYPTION_PADDING_NONE`
- Key is stored inside the secure Android Keystore hardware enclave (TEE/StrongBox when available on device).
- Provides `encrypt(plaintext: ByteArray): EncryptedData` and `decrypt(encryptedData: EncryptedData): ByteArray`.
- Nonce/IV is dynamically generated per encryption call and stored alongside ciphertext.

### 3. R8 / ProGuard Directives
- Optimization levels via `proguard-android-optimize.txt`.
- Rules to remove logging:
  ```proguard
  -assumenosideeffects class android.util.Log {
      public static boolean isLoggable(java.lang.String, int);
      public static int v(...);
      public static int d(...);
      public static int i(...);
  }
  ```
- Package and class obfuscation:
  ```proguard
  -repackageclasses 'com.parfumecreator.internal'
  -allowaccessmodification
  -renamesourcefileattribute SourceFile
  -keepattributes Exceptions,InnerClasses,Signature
  ```
- Preservation of runtime-reflective libraries (Room, Hilt, LiteRT, PDFBox, Gson).
