package studio.inventory.android

import org.junit.Assert.assertEquals
import org.junit.Test

class InventorySearchTest {
    private val spool = InventoryItem(
        id = "FIL-001",
        type = ItemType.Spool,
        fixed = FixedData(
            type = ItemType.Spool,
            id = "FIL-001",
            brand = "Bambu",
            material = "PLA",
            color = "White",
            tareG = 200.0,
            note = "常用白色",
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
    private val checkedOut = InventoryItem(
        id = "ITEM-001",
        type = ItemType.Other,
        fixed = FixedData(type = ItemType.Other, id = "ITEM-001", name = "热风枪"),
        state = ItemState(status = StockStatus.CheckedOut),
    )
    private val items = listOf(spool, part, checkedOut)

    @Test
    fun searchesFixedFieldsAndLocationNamesCaseInsensitively() {
        assertIds("bambu", "FIL-001")
        assertIds("pla", "FIL-001")
        assertIds("white", "FIL-001")
        assertIds("常用白色", "FIL-001")
        assertIds("五金架", "PART-001")
        assertIds("m3x8", "PART-001")
    }

    @Test
    fun combinesTypeAndStatusFilters() {
        assertEquals(
            listOf("FIL-001"),
            filterInventoryItems(items, "", ItemType.Spool, StockStatus.InStock).map { it.id },
        )
        assertEquals(
            listOf("ITEM-001"),
            filterInventoryItems(items, "热风枪", null, StockStatus.CheckedOut).map { it.id },
        )
    }

    @Test
    fun sortsByLocationThenId() {
        assertEquals(
            listOf("PART-001", "FIL-001"),
            filterInventoryItems(items, "", null, StockStatus.InStock).map { it.id },
        )
    }

    private fun assertIds(query: String, vararg expected: String) {
        assertEquals(
            expected.toList(),
            filterInventoryItems(items, query, null, StockStatus.InStock).map { it.id },
        )
    }
}
