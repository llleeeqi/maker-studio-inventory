package studio.inventory.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun comparesSemanticVersionParts() {
        assertTrue(isVersionNewer("0.5.3", "0.5.2"))
        assertTrue(isVersionNewer("v1.0.0", "0.9.9"))
        assertFalse(isVersionNewer("0.5.2", "0.5.2"))
        assertFalse(isVersionNewer("0.5.1", "0.5.2"))
        assertFalse(isVersionNewer("0.5.2", "0.5.2-beta"))
    }
}
