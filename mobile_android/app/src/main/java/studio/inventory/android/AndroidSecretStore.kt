package studio.inventory.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidSecretStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = listOf(
            Base64.getEncoder().encodeToString(cipher.iv),
            Base64.getEncoder().encodeToString(encrypted),
        ).joinToString(":")
        preferences.edit().putString(name, encoded).apply()
    }

    fun get(name: String): String? {
        val encoded = preferences.getString(name, null) ?: return null
        return runCatching {
            val parts = encoded.split(":", limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])),
            )
            String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), Charsets.UTF_8)
        }.getOrNull()
    }

    fun remove(name: String) {
        preferences.edit().remove(name).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
        val keyStore = KeyStore.getInstance(KeyStoreName).apply { load(null) }
        if (keyStore.containsAlias(KeyAlias)) keyStore.deleteEntry(KeyAlias)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KeyStoreName).apply { load(null) }
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KeyStoreName)
        generator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PreferencesName = "sync_secrets"
        private const val KeyStoreName = "AndroidKeyStore"
        private const val KeyAlias = "studio_inventory_sync_local_key"
        private const val Transformation = "AES/GCM/NoPadding"
    }
}
