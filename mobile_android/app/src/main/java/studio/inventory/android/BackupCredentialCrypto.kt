package studio.inventory.android

import com.google.gson.Gson
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupCredentialCrypto(private val gson: Gson) {
    fun encrypt(payload: BackupCredentialPayload, password: CharArray): EncryptedCredentialBlock {
        require(password.size >= 8) { "备份密码至少 8 位。" }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(password, salt, Iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(AssociatedData)
        val encrypted = cipher.doFinal(gson.toJson(payload).toByteArray(Charsets.UTF_8))
        return EncryptedCredentialBlock(
            iterations = Iterations,
            salt = Base64.getEncoder().encodeToString(salt),
            nonce = Base64.getEncoder().encodeToString(nonce),
            ciphertext = Base64.getEncoder().encodeToString(encrypted),
        )
    }

    fun decrypt(block: EncryptedCredentialBlock, password: CharArray): BackupCredentialPayload {
        require(block.schema == 1 && block.algorithm == "AES-256-GCM") { "不支持的凭据备份格式。" }
        val salt = Base64.getDecoder().decode(block.salt)
        val nonce = Base64.getDecoder().decode(block.nonce)
        val encrypted = Base64.getDecoder().decode(block.ciphertext)
        val key = deriveKey(password, salt, block.iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(AssociatedData)
        val plain = cipher.doFinal(encrypted)
        return gson.fromJson(String(plain, Charsets.UTF_8), BackupCredentialPayload::class.java)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    companion object {
        private const val Iterations = 210_000
        private val AssociatedData = "studio-inventory-full-backup-v1".toByteArray()
    }
}
