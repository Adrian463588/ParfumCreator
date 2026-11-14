package com.parfumecreator.core.security

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.AEADBadTagException

class SecurityHardeningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testVerifyPackageName_valid() {
        assertTrue(SecurityHardeningManager.verifyPackageName("com.parfumecreator"))
    }

    @Test
    fun testVerifyPackageName_invalid() {
        assertFalse(SecurityHardeningManager.verifyPackageName("com.tampered.app"))
        assertFalse(SecurityHardeningManager.verifyPackageName("com.parfumecreator.clone"))
    }

    @Test
    fun testVerifyCertificate_emptyTrustedAllowsDiagnosticMode() {
        assertTrue(SecurityHardeningManager.verifyCertificate(listOf("ABCDEF1234"), emptySet()))
    }

    @Test
    fun testVerifyCertificate_matchingFingerprintWithColonsAndCase() {
        val cert = "12:34:56:78:90:AB:CD:EF"
        val trusted = setOf("12:34:56:78:90:ab:cd:ef")
        val currentFingerprints = listOf("1234567890ABCDEF")

        assertTrue(SecurityHardeningManager.verifyCertificate(currentFingerprints, trusted))
    }

    @Test
    fun testVerifyCertificate_mismatchFails() {
        val currentFingerprints = listOf("AAAABBBBCCCCDDDD")
        val trusted = setOf("1111222233334444")
        assertFalse(SecurityHardeningManager.verifyCertificate(currentFingerprints, trusted))
    }

    @Test
    fun testCheckDebuggerAttached_tracerPidZero_notAttached() {
        val statusFile = tempFolder.newFile("status_zero")
        statusFile.writeText("Name:\tcom.parfumecreator\nState:\tS (sleeping)\nTracerPid:\t0\nUid:\t1000\n")

        assertFalse(SecurityHardeningManager.checkDebuggerAttached(statusFile))
    }

    @Test
    fun testCheckDebuggerAttached_tracerPidNonZero_detected() {
        val statusFile = tempFolder.newFile("status_traced")
        statusFile.writeText("Name:\tcom.parfumecreator\nState:\tt (tracing stop)\nTracerPid:\t29412\nUid:\t1000\n")

        assertTrue(SecurityHardeningManager.checkDebuggerAttached(statusFile))
    }

    @Test
    fun testCheckDebuggerAttached_missingFile_returnsFalse() {
        val nonExistent = File(tempFolder.root, "does_not_exist")
        assertFalse(SecurityHardeningManager.checkDebuggerAttached(nonExistent))
    }

    @Test
    fun testCheckRoot_testKeysBuildTag_detected() {
        assertTrue(SecurityHardeningManager.checkRoot(knownPaths = emptyList(), buildTags = "test-keys"))
        assertTrue(SecurityHardeningManager.checkRoot(knownPaths = emptyList(), buildTags = "userdebug,test-keys"))
    }

    @Test
    fun testCheckRoot_suBinaryExists_detected() {
        val fakeSu = tempFolder.newFile("fake_su")
        assertTrue(SecurityHardeningManager.checkRoot(knownPaths = listOf(fakeSu.absolutePath), buildTags = "release-keys"))
    }

    @Test
    fun testCheckRoot_cleanEnvironment_notDetected() {
        val nonExistentSu = File(tempFolder.root, "no_su").absolutePath
        assertFalse(SecurityHardeningManager.checkRoot(knownPaths = listOf(nonExistentSu), buildTags = "release-keys"))
    }

    @Test
    fun testCheckHookingFrameworks_cleanMaps_notDetected() {
        val mapsFile = tempFolder.newFile("maps_clean")
        mapsFile.writeText(
            """
            7f901000-7f902000 r--p 00000000 fd:00 12345 /apex/com.android.runtime/lib64/bionic/libc.so
            7f902000-7f903000 r-xp 00001000 fd:00 12345 /apex/com.android.art/lib64/libart.so
            """.trimIndent()
        )

        assertFalse(SecurityHardeningManager.checkHookingFrameworks(mapsFile, checkPort = false))
    }

    @Test
    fun testCheckHookingFrameworks_fridaDetected() {
        val mapsFile = tempFolder.newFile("maps_frida")
        mapsFile.writeText(
            """
            7f901000-7f902000 r--p 00000000 fd:00 12345 /data/local/tmp/frida-agent-64.so
            """.trimIndent()
        )

        assertTrue(SecurityHardeningManager.checkHookingFrameworks(mapsFile, checkPort = false))
    }

    @Test
    fun testCheckHookingFrameworks_xposedDetected() {
        val mapsFile = tempFolder.newFile("maps_xposed")
        mapsFile.writeText(
            """
            7f901000-7f902000 r--p 00000000 fd:00 12345 /system/framework/libxposed_art.so
            """.trimIndent()
        )

        assertTrue(SecurityHardeningManager.checkHookingFrameworks(mapsFile, checkPort = false))
    }

    @Test
    fun testComputeSha256() {
        val input = "ParfumeCreator".toByteArray(Charsets.UTF_8)
        val hash = SecurityHardeningManager.computeSha256(input)
        assertEquals(64, hash.length)
        assertTrue(hash.all { it.isDigit() || it in 'A'..'F' })
    }

    @Test
    fun testEncryptedPayload_toAndFromBase64String() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val ciphertext = byteArrayOf(50, 60, 70, 80)
        val payload = EncryptedPayload(iv, ciphertext)

        val encoded = payload.toBase64String()
        val parsed = EncryptedPayload.fromBase64String(encoded)

        assertNotNull(parsed)
        assertArrayEquals(iv, parsed!!.iv)
        assertArrayEquals(ciphertext, parsed.ciphertext)
        assertEquals(payload, parsed)
    }

    @Test
    fun testEncryptedPayload_invalidStringReturnsNull() {
        assertNull(EncryptedPayload.fromBase64String("invalid-no-colon"))
        assertNull(EncryptedPayload.fromBase64String("part1:part2:part3"))
        assertNull(EncryptedPayload.fromBase64String("%%%not-base64%%%:%%%not-base64%%%"))
    }

    @Test
    fun testAesGcm_encryptAndDecryptRoundtrip() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()

        val crypto = AndroidKeyStoreCrypto()
        val originalText = "Top-Secret Perfume Formula 2026: Bergamot 12%, Ambroxan 5%"

        val encryptedBase64 = crypto.encryptString(originalText, secretKey)
        assertNotEquals(originalText, encryptedBase64)
        assertTrue(encryptedBase64.contains(":"))

        val decryptedText = crypto.decryptString(encryptedBase64, secretKey)
        assertEquals(originalText, decryptedText)
    }

    @Test
    fun testAesGcm_tamperingThrowsOrFails() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()

        val crypto = AndroidKeyStoreCrypto()
        val originalText = "Original Untampered Text"

        val payload = crypto.encrypt(originalText.toByteArray(Charsets.UTF_8), secretKey)

        // Corrupt one byte of ciphertext
        val tamperedCiphertext = payload.ciphertext.clone()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()
        val tamperedPayload = EncryptedPayload(payload.iv, tamperedCiphertext)

        try {
            crypto.decrypt(tamperedPayload, secretKey)
            fail("Expected decryption to fail with AEADBadTagException or GeneralSecurityException due to tampering")
        } catch (_: Exception) {
            // Expected: AES-GCM MAC verification failure
        }

        // Also test decryptString returns null when tampered
        val tamperedEncoded = tamperedPayload.toBase64String()
        val result = crypto.decryptString(tamperedEncoded, secretKey)
        assertNull(result)
    }
}
