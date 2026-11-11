package com.homeside.apkupdater

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * La lógica que puede fallar en silencio: porcentajes, ids que llegan como
 * string, hashes con mayúsculas y nombres de archivo hostiles.
 */
class ApkUpdaterLogicTest {

    @Test
    @DisplayName("progress nunca divide por cero ni pasa de 100")
    fun progressIsClampedAndSafe() {
        assertEquals(0, ApkUpdaterLogic.progress(0, 0))
        assertEquals(0, ApkUpdaterLogic.progress(100, 0))
        assertEquals(0, ApkUpdaterLogic.progress(100, -5))
        assertEquals(0, ApkUpdaterLogic.progress(-10, 100))
        assertEquals(25, ApkUpdaterLogic.progress(50, 200))
        assertEquals(100, ApkUpdaterLogic.progress(200, 200))
        assertEquals(100, ApkUpdaterLogic.progress(300, 200))
        assertEquals(33, ApkUpdaterLogic.progress(1, 3))
    }

    @Test
    @DisplayName("parseId acepta número o string y rechaza el resto")
    fun parseIdAcceptsNumbersAndNumericStrings() {
        assertEquals(42L, ApkUpdaterLogic.parseId(42))
        assertEquals(42L, ApkUpdaterLogic.parseId(42L))
        assertEquals(7L, ApkUpdaterLogic.parseId("7"))
        assertEquals(7L, ApkUpdaterLogic.parseId("  7 "))
        assertEquals(9007199254740993L, ApkUpdaterLogic.parseId("9007199254740993"))
    }

    @Test
    @DisplayName("parseId rechaza ids inválidos o no positivos")
    fun parseIdRejectsInvalidIds() {
        assertNull(ApkUpdaterLogic.parseId(null))
        assertNull(ApkUpdaterLogic.parseId(""))
        assertNull(ApkUpdaterLogic.parseId("abc"))
        assertNull(ApkUpdaterLogic.parseId(0))
        assertNull(ApkUpdaterLogic.parseId(-5))
        assertNull(ApkUpdaterLogic.parseId(3.5))
    }

    @Test
    @DisplayName("sólo un SHA-256 de 64 caracteres hexadecimales es válido")
    fun sha256Validation() {
        assertTrue(ApkUpdaterLogic.isValidSha256("a".repeat(64)))
        assertTrue(ApkUpdaterLogic.isValidSha256("A1B2C3D4E5F6".repeat(5) + "A1B2"))
        assertTrue(ApkUpdaterLogic.isValidSha256("  " + "f".repeat(64) + "  "))

        assertFalse(ApkUpdaterLogic.isValidSha256(null))
        assertFalse(ApkUpdaterLogic.isValidSha256("a".repeat(63)))
        assertFalse(ApkUpdaterLogic.isValidSha256("a".repeat(65)))
        assertFalse(ApkUpdaterLogic.isValidSha256("g".repeat(64)))
        assertFalse(ApkUpdaterLogic.isValidSha256(""))
    }

    @Test
    @DisplayName("el checksum se compara sin importar mayúsculas ni espacios")
    fun checksumComparisonIsForgiving() {
        val expected = "905615432b8d565a004a1e4f1e6b39723cab9c1f2cb95e22553f91a61bb95851"

        assertTrue(ApkUpdaterLogic.checksumMatches(expected, expected))
        assertTrue(ApkUpdaterLogic.checksumMatches(expected.uppercase(), expected))
        assertTrue(ApkUpdaterLogic.checksumMatches("  $expected ", expected))

        assertFalse(ApkUpdaterLogic.checksumMatches(expected, "0".repeat(64)))
        assertFalse(ApkUpdaterLogic.checksumMatches(null, expected))
        assertFalse(ApkUpdaterLogic.checksumMatches(expected, null))
    }

    @Test
    @DisplayName("toHex produce el formato que espera el manifiesto")
    fun toHexProducesLowercasePairs() {
        assertEquals("00ff10ab", ApkUpdaterLogic.toHex(byteArrayOf(0x00, 0xFF.toByte(), 0x10, 0xAB.toByte())))
        assertEquals("", ApkUpdaterLogic.toHex(ByteArray(0)))
    }

    @Test
    @DisplayName("safeFileName neutraliza rutas y extensiones raras")
    fun safeFileNameStripsPaths() {
        assertEquals("update.apk", ApkUpdaterLogic.safeFileName(null))
        assertEquals("update.apk", ApkUpdaterLogic.safeFileName(""))
        assertEquals("update.apk", ApkUpdaterLogic.safeFileName("   "))

        assertEquals("homeside-0.2.0.apk", ApkUpdaterLogic.safeFileName("homeside-0.2.0.apk"))
        assertEquals("homeside.apk", ApkUpdaterLogic.safeFileName("../../etc/homeside.apk"))
        assertEquals("passwd.apk", ApkUpdaterLogic.safeFileName("/etc/passwd"))
        assertEquals("evil.apk", ApkUpdaterLogic.safeFileName("C:\\temp\\evil.apk"))

        assertEquals("update.apk", ApkUpdaterLogic.safeFileName(".."))
        assertEquals("update.apk", ApkUpdaterLogic.safeFileName("."))

        assertEquals("sin_extension.apk", ApkUpdaterLogic.safeFileName("sin extension"))
        assertEquals("con_espacios.apk", ApkUpdaterLogic.safeFileName("con espacios"))
        assertEquals("env.apk", ApkUpdaterLogic.safeFileName(".env"))
        assertEquals("APK.apk", ApkUpdaterLogic.safeFileName("APK"))
    }

    @Test
    @DisplayName("el nombre se recorta para no romper el sistema de archivos")
    fun safeFileNameIsBounded() {
        val long = "a".repeat(300) + ".apk"

        val result = ApkUpdaterLogic.safeFileName(long)

        assertTrue(result.length <= 120)
        assertTrue(result.endsWith(".apk"))
    }

    @Test
    @DisplayName("el enum expone los nombres que consume PHP")
    fun stateNamesMatchThePhpContract() {
        assertEquals("pending", ApkUpdaterLogic.stateName(DownloadState.Pending))
        assertEquals("running", ApkUpdaterLogic.stateName(DownloadState.Running))
        assertEquals("paused", ApkUpdaterLogic.stateName(DownloadState.Paused))
        assertEquals("successful", ApkUpdaterLogic.stateName(DownloadState.Successful))
        assertEquals("failed", ApkUpdaterLogic.stateName(DownloadState.Failed))
        assertEquals("unknown", ApkUpdaterLogic.stateName(DownloadState.Unknown))
    }
}
