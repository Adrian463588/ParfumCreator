# Mobile App Hardening & Anti-Tampering / Anti-Reverse Engineering

Date: 2026-11-14
Status: accepted

## Why

ParfumCreator is a 100% offline-first Android application containing proprietary perfumery calculation algorithms (`FormulaCalculationEngine`), local Room datasets, and user formulation drafts. Because offline mobile applications run client-side on user-controlled hardware, they are susceptible to:
1. Decompilation and reverse engineering using tools such as JADX, APKTool, and CFR.
2. Repackaging and tampering (modding, disabling constraints, cracking, resigning with rogue certificates).
3. Runtime inspection and manipulation using dynamic instrumentation (Frida, Xposed) and debuggers.
4. Data extraction from local app storage via `adb backup` or rooted device inspection.

As established by OWASP MASVS-RESILIENCE, while client-side code execution cannot be made 100% unbreakable, layered defensive controls make reverse engineering, tampering, and algorithm theft significantly more complex and costly.

## What Changes

1. **R8 & ProGuard Optimization & Obfuscation**:
   - Enable `isMinifyEnabled = true` and `isShrinkResources = true` in release builds.
   - Configure aggressive R8 optimization, class repackaging, member name obfuscation, line attribute stripping, and automated stripping of debug `android.util.Log` calls.
2. **Runtime Anti-Tampering & Integrity Verification**:
   - Package name verification against authorized package ID (`com.parfumecreator`).
   - Signing certificate SHA-256 fingerprint verification to detect APK repackaging and resigning.
3. **Anti-Debugging & Dynamic Instrumentation Defense**:
   - Detect active Java debuggers (`Debug.isDebuggerConnected()`, `Debug.waitingForDebugger()`).
   - Detect native debuggers and tracer attachment via `/proc/self/status` `TracerPid` analysis.
   - Detect hooking frameworks and dynamic binary instrumentation (Frida gadget/agent in `/proc/self/maps`, standard Frida port 27042, Xposed classes).
4. **Root & Environment Integrity Detection**:
   - Scan for su binaries in standard Android system paths.
   - Check build tags for unofficial `test-keys`.
5. **Secure Local Storage & Cryptography**:
   - Provide `AndroidKeyStoreCrypto` utilizing the hardware-backed Android Keystore provider for 256-bit AES-GCM encryption/decryption without hardcoding static keys.
6. **Manifest & Storage Hardening**:
   - Set `android:allowBackup="false"` and `android:usesCleartextTraffic="false"`.
   - Disallow cloud/device-transfer data extraction of private app databases.

## Scope & Non-Goals

- No third-party commercial RASP or paid SDKs (follows KISS, YAGNI, Ponytail discipline).
- No cloud-based attestation / Google Play Integrity requirement (the app operates 100% offline).
- Does not block developer debugging or automated CI tests (diagnostic mode in debug, strict enforcement in release).
