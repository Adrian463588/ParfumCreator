package com.parfumecreator.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.parfumecreator.BuildConfig
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Locale

/**
 * Result data model of security posture evaluation.
 */
data class SecurityReport(
    val isPackageValid: Boolean,
    val isSignatureValid: Boolean,
    val isDebuggerAttached: Boolean,
    val isRootDetected: Boolean,
    val isHookingDetected: Boolean,
    val detectedAnomalies: List<String> = emptyList()
) {
    /**
     * Determines whether the app is running in an untampered, non-instrumented environment.
     */
    val isSecure: Boolean
        get() = isPackageValid && isSignatureValid && !isDebuggerAttached && !isRootDetected && !isHookingDetected
}

/**
 * Mobile App Hardening manager providing offline anti-tampering, anti-debugging,
 * root detection, and dynamic instrumentation defense following OWASP MASVS-RESILIENCE.
 */
object SecurityHardeningManager {

    private const val EXPECTED_PACKAGE_NAME = "com.parfumecreator"

    private val DEFAULT_SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su"
    )

    private val SUSPICIOUS_HOOK_LIBS = listOf(
        "frida-agent",
        "frida-gadget",
        "libfrida",
        "libxposed",
        "libsubstrate",
        "cydiasubstrate",
        "edxposed",
        "sandhook",
        "lsposed"
    )

    private const val FRIDA_DEFAULT_PORT = 27042

    /**
     * Detects Java debuggers and native tracer/ptrace attachments via /proc/self/status.
     */
    fun checkDebuggerAttached(statusFile: File = File("/proc/self/status")): Boolean {
        // 1. Java runtime debugger check
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                return true
            }
        } catch (_: Exception) {
            // Unmocked Android framework runtime call in JVM tests, fall through to native TracerPid
        }

        // 2. Native ptrace tracer detection
        if (statusFile.exists() && statusFile.canRead()) {
            try {
                statusFile.useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith("TracerPid:", ignoreCase = true)) {
                            val pidStr = line.substringAfter(":").trim()
                            val pid = pidStr.toIntOrNull() ?: 0
                            if (pid > 0) return true
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore I/O read failures, fallback to Java debugger check
            }
        }
        return false
    }

    /**
     * Verifies that the package name has not been renamed or cloned.
     */
    fun verifyPackageName(packageName: String, expectedName: String = EXPECTED_PACKAGE_NAME): Boolean {
        return packageName == expectedName
    }

    /**
     * Verifies that the package name has not been renamed or cloned using Context.
     */
    fun verifyPackageName(context: Context, expectedName: String = EXPECTED_PACKAGE_NAME): Boolean {
        return verifyPackageName(context.packageName, expectedName)
    }

    /**
     * Computes SHA-256 certificate fingerprints of current APK signer.
     */
    fun getSigningCertificateFingerprints(context: Context): List<String> {
        val fingerprints = mutableListOf<String>()
        try {
            val pm = context.packageManager
            val packageName = context.packageName

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo
                val signatures = if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else null

                signatures?.forEach { sig ->
                    fingerprints.add(computeSha256(sig.toByteArray()))
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures?.forEach { sig ->
                    fingerprints.add(computeSha256(sig.toByteArray()))
                }
            }
        } catch (_: Exception) {
            // Error querying package manager
        }
        return fingerprints
    }

    /**
     * Verifies fingerprints against authorized list.
     */
    fun verifyCertificate(currentFingerprints: List<String>, trustedFingerprints: Set<String>): Boolean {
        if (trustedFingerprints.isEmpty()) return true
        if (currentFingerprints.isEmpty()) return false
        return currentFingerprints.any { cert ->
            trustedFingerprints.any { trusted ->
                cert.equals(trusted.replace(":", "").trim(), ignoreCase = true)
            }
        }
    }

    /**
     * Verifies APK certificate SHA-256 against authorized fingerprints.
     * Returns true if trustedFingerprints is empty (diagnostic mode) or matches at least one.
     */
    fun verifyCertificate(context: Context, trustedFingerprints: Set<String>): Boolean {
        if (trustedFingerprints.isEmpty()) return true
        val currentFingerprints = getSigningCertificateFingerprints(context)
        return verifyCertificate(currentFingerprints, trustedFingerprints)
    }

    /**
     * Detects su binaries or test-keys build tags indicating a rooted environment.
     */
    fun checkRoot(
        knownPaths: List<String> = DEFAULT_SU_PATHS,
        buildTags: String? = Build.TAGS
    ): Boolean {
        if (buildTags != null && buildTags.contains("test-keys", ignoreCase = true)) {
            return true
        }
        for (path in knownPaths) {
            if (File(path).exists()) {
                return true
            }
        }
        return false
    }

    /**
     * Detects dynamic hooking frameworks (Frida, Xposed, Substrate) by inspecting
     * process memory maps and local socket ports.
     */
    fun checkHookingFrameworks(
        mapsFile: File = File("/proc/self/maps"),
        checkPort: Boolean = false,
        port: Int = FRIDA_DEFAULT_PORT
    ): Boolean {
        // 1. Inspect loaded libraries in memory maps
        if (mapsFile.exists() && mapsFile.canRead()) {
            try {
                mapsFile.useLines { lines ->
                    for (line in lines) {
                        val lower = line.lowercase(Locale.ROOT)
                        if (SUSPICIOUS_HOOK_LIBS.any { lower.contains(it) }) {
                            return true
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore read failures
            }
        }

        // 2. Check for standard Frida server TCP port if requested
        if (checkPort) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 80)
                }
                return true // Port is open, Frida server likely running
            } catch (_: IOException) {
                // Port closed or unreachable, normal behavior
            }
        }

        return false
    }

    /**
     * Evaluates comprehensive security status of the running app.
     */
    fun evaluateSecurity(
        context: Context,
        trustedFingerprints: Set<String> = emptySet(),
        statusFile: File = File("/proc/self/status"),
        mapsFile: File = File("/proc/self/maps"),
        checkFridaPort: Boolean = false
    ): SecurityReport {
        val anomalies = mutableListOf<String>()

        val isPkgValid = verifyPackageName(context)
        if (!isPkgValid) anomalies.add("Package name mismatch: ${context.packageName}")

        val isSigValid = verifyCertificate(context, trustedFingerprints)
        if (!isSigValid) anomalies.add("APK signature certificate verification failed")

        val isDebugger = checkDebuggerAttached(statusFile)
        if (isDebugger) anomalies.add("Debugger or tracer detected")

        val isRoot = checkRoot()
        if (isRoot) anomalies.add("Root privilege markers or test-keys detected")

        val isHooking = checkHookingFrameworks(mapsFile, checkPort = checkFridaPort)
        if (isHooking) anomalies.add("Hooking or dynamic instrumentation framework detected")

        return SecurityReport(
            isPackageValid = isPkgValid,
            isSignatureValid = isSigValid,
            isDebuggerAttached = isDebugger,
            isRootDetected = isRoot,
            isHookingDetected = isHooking,
            detectedAnomalies = anomalies
        )
    }

    internal fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02X".format(it) }
    }
}
