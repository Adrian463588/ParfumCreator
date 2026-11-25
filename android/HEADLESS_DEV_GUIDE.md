# Headless Lightweight Android Development & Autonomous 24/7 Loop Guide

This guide describes how to operate the 3-layer autonomous testing and development harness for `ParfumCreator` without physical devices or Android Studio GUI.

---

## 1. Architecture Overview

```
+-------------------------------------------------------------------------+
|                  LAYER 3: AUTONOMOUS 24/7 LOOP HARNESS                  |
|  - android/BACKLOG.md (Task Queue) & android/PROGRESS.md (Audit Log)     |
|  - run-fast-loop.ps1: Compile -> Unit Test -> Install -> Verify State   |
+------------------------------------+------------------------------------+
                                     |
+------------------------------------+------------------------------------+
|               LAYER 2: AGENT CONTROL & OBSERVABILITY                    |
|  - Mobile MCP (@mobilenext/mobile-mcp): Accessibility Tree & UI Control |
|  - capture-ui-tree.ps1: Headless XML hierarchy inspection               |
|  - get-error-logs.ps1: Filtered logcat error & crash diagnosis          |
+------------------------------------+------------------------------------+
                                     |
+------------------------------------+------------------------------------+
|               LAYER 1: LIGHTWEIGHT RUNTIME (HEADLESS CLI)               |
|  - Android Emulator CLI (AOSP API 33): -no-window -memory 1536          |
|  - Robolectric / JVM Fast-Fail Unit Testing                             |
+-------------------------------------------------------------------------+
```

---

## 2. Headless Emulator CLI Optimization

The headless AOSP AVD runs with resource-optimized parameters:
```powershell
emulator -avd parfume_creator_api33 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 1536
```
- **RAM footprint**: ~1.5 GB RAM (down from 7 GB in GUI mode).
- **CPU idle**: < 1%.
- **Zero background Play Services bloat**: pure AOSP x86_64 image.

### Management Scripts:
- **Start Emulator**:
  ```powershell
  .\android\scripts\headless\start-emulator.ps1
  ```
- **Check Status & Resources**:
  ```powershell
  .\android\scripts\headless\status-emulator.ps1
  ```
- **Stop Emulator**:
  ```powershell
  .\android\scripts\headless\stop-emulator.ps1
  ```

---

## 3. Fast-Fail Autonomous Loop

The continuous fast-fail loop enforces 4 strict gates:
1. **Gate 1**: JVM Unit Tests (`.\gradlew :app:testDebugUnitTest`)
2. **Gate 2**: APK Compilation (`.\gradlew :app:assembleDebug`)
3. **Gate 3**: Headless APK Installation via ADB (`adb install -r ...`)
4. **Gate 4**: App Launch & Error Verification (`adb shell am start ...` + logcat check)

Run the entire cycle with:
```powershell
.\android\scripts\headless\run-fast-loop.ps1
```

---

## 4. UI Observability & Mobile MCP

To observe and inspect UI state without a window:
- Run `capture-ui-tree.ps1` to dump and parse the Android accessibility tree:
  ```powershell
  .\android\scripts\headless\capture-ui-tree.ps1
  ```
- Retrieve runtime crashes:
  ```powershell
  .\android\scripts\headless\get-error-logs.ps1
  ```
- Or call `mobile_list_elements_on_screen` directly through Mobile MCP.
