package studio.inventory.android

import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryLocationGroupingTest {
    private val searchedSpool = item(
        id = "FIL-001",
        type = ItemType.Spool,
        name = "测试耗材",
        locationId = "LOC-002",
        locationName = "测试库位2",
    )
    private val sameLocationPart = item(
        id = "PART-001",
        type = ItemType.Part,
        name = "同库位零件",
        locationId = "LOC-002",
        locationName = "测试库位2",
    )
    private val otherLocationItem = item(
        id = "ITEM-001",
        type = ItemType.Other,
        name = "其他库位物品",
        locationId = "LOC-001",
        locationName = "测试库位1",
    )

    @Test
    fun searchLimitsVisibleLocationsButKeepsAllItemsInsideMatchedLocation() {
        val groups = groupInventoryByLocation(
            items = listOf(searchedSpool, sameLocationPart, otherLocationItem),
            visibleLocationIds = setOf(searchedSpool.state.locationId),
        )

        assertEquals(1, groups.size)
        assertEquals("LOC-002", groups.single().id)
        assertEquals(listOf("FIL-001", "PART-001"), groups.single().items.map { it.id })
    }

    private fun item(
        id: String,
        type: ItemType,
        name: String,
        locationId: String,
        locationName: String,
    ) = InventoryItem(
        id = id,
        type = type,
        fixed = FixedData(type = type, id = id, name = name),
        state = ItemState(
            status = StockStatus.InStock,
            locationId = locationId,
            locationName = locationName,
        ),
    )
}
