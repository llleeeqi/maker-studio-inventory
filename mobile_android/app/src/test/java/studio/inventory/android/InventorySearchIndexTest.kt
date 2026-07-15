package studio.inventory.android

import org.junit.Assert.assertEquals
import org.junit.Test

class InventorySearchIndexTest {
    private val spool = InventoryItem(
        id = "FIL-001",
        type = ItemType.Spool,
        fixed = FixedData(
            type = ItemType.Spool,
            id = "FIL-001",
            brand = "拓竹",
            material = "PLA",
            color = "白色",
            tareG = 200.0,
        ),
        state = ItemState(locationId = "LOC-B", locationName = "打印区第二层"),
    )
    private val part = InventoryItem(
        id = "PART-001",
        type = ItemType.Part,
        fixed = FixedData(
            type = ItemType.Part,
            id = "PART-001",
            name = "M3x8黑色圆头螺丝",
            unitWeightG = 0.42,
        ),
        state = ItemState(locationId = "LOC-A", locationName = "五金架第一层"),
    )
    private val index = InventorySearchIndex.build(listOf(spool, part))

    @Test
    fun searchesFullPinyinAndInitials() {
        assertIds("luosi", "PART-001")
        assertIds("ls", "PART-001")
        assertIds("dayinqu", "FIL-001")
        assertIds("dyqdec", "FIL-001")
    }

    @Test
    fun toleratesOnePinyinTypingError() {
        assertIds("lousi", "PART-001")
    }

    @Test
    fun exactNameRanksBeforeNoteOrLocationMatches() {
        val another = spool.copy(
            id = "FIL-002",
            fixed = spool.fixed.copy(
                id = "FIL-002",
                brand = "其他",
                color = "灰色",
                note = "拓竹 PLA 白色",
            ),
        )
        val ranked = InventorySearchIndex.build(listOf(spool, another)).search(
            query = "拓竹 PLA 白色",
            typeFilter = null,
            statusFilter = StockStatus.InStock,
        )
        assertEquals("FIL-001", ranked.first().item.id)
    }

    private fun assertIds(query: String, vararg expected: String) {
        assertEquals(
            expected.toList(),
            index.search(query, null, StockStatus.InStock).map { it.item.id },
        )
    }
}
