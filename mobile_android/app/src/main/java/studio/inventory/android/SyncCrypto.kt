package studio.inventory.android

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SyncCrypto(keyBytes: ByteArray) {
    private val key: SecretKey
    private val random = SecureRandom()

    init {
        require(keyBytes.size == KeySizeBytes) { "同步仓库密钥必须是 32 字节。" }
        key = SecretKeySpec(keyBytes.copyOf(), "AES")
    }

    fun encrypt(plain: ByteArray, associatedData: ByteArray = byteArrayOf()): EncryptedSyncBlob {
        val nonce = ByteArray(NonceSizeBytes).also(random::nextBytes)
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TagSizeBits, nonce))
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        val encrypted = cipher.doFinal(plain)
        return EncryptedSyncBlob(
            nonce = Base64.getEncoder().encodeToString(nonce),
            ciphertext = Base64.getEncoder().encodeToString(encrypted),
        )
    }

    fun decrypt(blob: EncryptedSyncBlob, associatedData: ByteArray = byteArrayOf()): ByteArray {
        require(blob.schema == 1 && blob.algorithm == "AES-256-GCM") { "不支持的同步加密格式。" }
        val nonce = Base64.getDecoder().decode(blob.nonce)
        val encrypted = Base64.getDecoder().decode(blob.ciphertext)
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TagSizeBits, nonce))
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        return cipher.doFinal(encrypted)
    }

    companion object {
        const val KeySizeBytes = 32
        private const val NonceSizeBytes = 12
        private const val TagSizeBits = 128
        private const val CipherTransformation = "AES/GCM/NoPadding"

        fun generateKey(): ByteArray = ByteArray(KeySizeBytes).also(SecureRandom()::nextBytes)

        fun encodeKey(key: ByteArray): String {
            require(key.size == KeySizeBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(key)
        }

        fun decodeKey(encoded: String): ByteArray {
            val decoded = Base64.getUrlDecoder().decode(encoded.trim())
            require(decoded.size == KeySizeBytes) { "同步仓库密钥格式错误。" }
            return decoded
        }
    }
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }
