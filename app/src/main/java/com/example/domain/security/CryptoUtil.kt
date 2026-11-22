package com.example.domain.security

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtil {
    private val secureRandom = SecureRandom()
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    fun newUuid(): String = UUID.randomUUID().toString()

    fun generateSaltBase64(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyPassword(password: String, saltBase64: String, expectedHashBase64: String): Boolean {
        if (password.isEmpty() || saltBase64.isEmpty() || expectedHashBase64.isEmpty()) return false
        val actual = Base64.decode(hashPassword(password, saltBase64), Base64.NO_WRAP)
        val expected = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
        return MessageDigest.isEqual(actual, expected)
    }

    fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    fun sha256Hex(input: String): String = sha256Hex(input.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun generateSixDigitCode(): String {
        val num = secureRandom.nextInt(1_000_000)
        return "%06d".format(num)
    }

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encodeBase32(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                out.append(BASE32_ALPHABET[(buffer ushr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return out.toString()
    }

    fun decodeBase32(encoded: String): ByteArray? {
        val cleaned = encoded.trim()
            .replace(" ", "")
            .replace("-", "")
            .trimEnd('=')
            .uppercase()
        if (cleaned.isEmpty()) return null
        val result = ArrayList<Byte>((cleaned.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        for (ch in cleaned) {
            val idx = BASE32_ALPHABET.indexOf(ch)
            if (idx < 0) return null
            buffer = (buffer shl 5) or idx
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                result.add(((buffer ushr bitsLeft) and 0xFF).toByte())
            }
        }
        return result.toByteArray()
    }

    fun isStandardBase32Secret(secret: String): Boolean {
        val stripped = secret.trim().replace(" ", "").replace("-", "").trimEnd('=')
        return stripped.length >= 16 && stripped.all { it in BASE32_ALPHABET }
    }

    private fun decodeTotpKeyBytes(secret: String): ByteArray {
        val trimmed = secret.trim()
        if (isStandardBase32Secret(trimmed)) {
            decodeBase32(trimmed)?.let { return it }
        }
        return runCatching { Base64.decode(trimmed, Base64.NO_WRAP) }
            .getOrElse { decodeBase32(trimmed) ?: ByteArray(0) }
    }

    fun toBase32TotpSecret(secret: String): String {
        val trimmed = secret.trim()
        return if (isStandardBase32Secret(trimmed)) {
            trimmed.replace(" ", "").replace("-", "").trimEnd('=')
        } else {
            val bytes = decodeTotpKeyBytes(trimmed)
            if (bytes.isNotEmpty()) encodeBase32(bytes) else trimmed.uppercase()
        }
    }

    fun formatTotpSecretGrouped(secret: String): String =
        toBase32TotpSecret(secret).chunked(4).joinToString(" ")

    fun buildTotpOtpAuthUri(
        accountEmail: String,
        secret: String,
        issuer: String = "ONE HOME PRO"
    ): String {
        val cleanSecret = toBase32TotpSecret(secret)
        val cleanIssuer = issuer.trim().ifBlank { "ONE HOME PRO" }
        val cleanAccount = accountEmail.trim().ifBlank { "admin" }
        val encIssuer = java.net.URLEncoder.encode(cleanIssuer, "UTF-8").replace("+", "%20")
        val encAccount = java.net.URLEncoder.encode(cleanAccount, "UTF-8").replace("+", "%20")
        return "otpauth://totp/$encIssuer:$encAccount?secret=$cleanSecret&issuer=$encIssuer&algorithm=SHA1&digits=6&period=30"
    }

    fun generateTotpSecret(): String {
        val bytes = ByteArray(20)
        secureRandom.nextBytes(bytes)
        return encodeBase32(bytes)
    }

    fun currentTotpCode(secret: String, timeMillis: Long = System.currentTimeMillis()): String {
        val key = decodeTotpKeyBytes(secret)
        if (key.isEmpty()) return "000000"
        val counter = timeMillis / 30_000L
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hmac = mac.doFinal(data)
        val offset = hmac.last().toInt() and 0x0F
        val binary =
            ((hmac[offset].toInt() and 0x7F) shl 24) or
                ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                (hmac[offset + 3].toInt() and 0xFF)
        return "%06d".format(binary % 1_000_000)
    }

    fun verifyTotpCode(secret: String, code: String, timeMillis: Long = System.currentTimeMillis()): Boolean {
        val clean = code.trim().replace(" ", "")
        if (clean.length != 6 || !clean.all { it.isDigit() }) return false
        for (window in -1..1) {
            val expected = currentTotpCode(secret, timeMillis + window * 30_000L)
            if (MessageDigest.isEqual(expected.toByteArray(), clean.toByteArray())) return true
        }
        return false
    }

    fun generateRecoveryCodes(count: Int = 6): List<String> =
        (1..count).map {
            val part1 = "%04X".format(secureRandom.nextInt(65536))
            val part2 = "%04X".format(secureRandom.nextInt(65536))
            "OHP-$part1-$part2"
        }

    fun generateMasterRecoveryKey(): String {
        val parts = (1..4).map { "%04X".format(secureRandom.nextInt(65536)) }
        return "OHP-KEY-${parts.joinToString("-")}"
    }

    fun gzipCompress(plainBytes: ByteArray): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(baos).use { it.write(plainBytes) }
        return baos.toByteArray()
    }

    fun gzipDecompress(compressedBytes: ByteArray): ByteArray {
        return java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(compressedBytes)).use {
            it.readBytes()
        }
    }

    fun encryptAesGcm(plainBytes: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val keySpec = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plainBytes)
        return ByteBuffer.allocate(salt.size + iv.size + cipherText.size)
            .put(salt)
            .put(iv)
            .put(cipherText)
            .array()
    }

    fun decryptAesGcm(encryptedBytes: ByteArray, passphrase: String): ByteArray {
        require(encryptedBytes.size > 28) { "Corrupted encrypted payload" }
        val buf = ByteBuffer.wrap(encryptedBytes)
        val salt = ByteArray(16).also { buf.get(it) }
        val iv = ByteArray(12).also { buf.get(it) }
        val cipherText = ByteArray(buf.remaining()).also { buf.get(it) }
        val keySpec = deriveAesKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 65_536, 256)
        val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(secret, "AES")
    }
}
