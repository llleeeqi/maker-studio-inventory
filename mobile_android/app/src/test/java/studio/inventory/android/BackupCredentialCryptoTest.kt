package studio.inventory.android

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCredentialCryptoTest {
    private val crypto = BackupCredentialCrypto(GsonBuilder().create())

    @Test
    fun encryptsOnlyCredentialPayloadWithBackupPassword() {
        val payload = BackupCredentialPayload(
            webDavPassword = "webdav-secret",
            repositoryKey = SyncCrypto.encodeKey(ByteArray(32) { 7 }),
        )
        val encrypted = crypto.encrypt(payload, "backup-password".toCharArray())

        assertFalse(encrypted.ciphertext.contains("webdav-secret"))
        assertEquals(payload, crypto.decrypt(encrypted, "backup-password".toCharArray()))
        assertThrows(Exception::class.java) {
            crypto.decrypt(encrypted, "wrong-password".toCharArray())
        }
    }
}
