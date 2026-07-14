package studio.inventory.android

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncCryptoTest {
    @Test
    fun encryptsAndDecryptsWithAssociatedData() {
        val key = SyncCrypto.generateKey()
        val crypto = SyncCrypto(key)
        val plain = "库存快照 0.5.0".toByteArray()
        val aad = "item:FIL-001".toByteArray()

        val encrypted = crypto.encrypt(plain, aad)

        assertFalse(encrypted.ciphertext.contains("库存"))
        assertArrayEquals(plain, crypto.decrypt(encrypted, aad))
        assertThrows(Exception::class.java) {
            crypto.decrypt(encrypted, "item:FIL-002".toByteArray())
        }
    }

    @Test
    fun repositoryKeyRoundTripsAsPortableString() {
        val key = SyncCrypto.generateKey()
        assertArrayEquals(key, SyncCrypto.decodeKey(SyncCrypto.encodeKey(key)))
    }
}
