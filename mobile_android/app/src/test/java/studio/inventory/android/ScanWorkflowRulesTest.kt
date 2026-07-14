package studio.inventory.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanWorkflowRulesTest {
    private val location = LocationValue("LOC-001", "测试库位")

    @Test
    fun itemAvailabilityMatchesModeAndStatus() {
        assertTrue(ScanWorkflowRules.allowsItem(ScanMode.StockIn, null))
        assertTrue(ScanWorkflowRules.allowsItem(ScanMode.StockIn, StockStatus.CheckedOut))
        assertFalse(ScanWorkflowRules.allowsItem(ScanMode.StockIn, StockStatus.InStock))
        assertFalse(ScanWorkflowRules.allowsItem(ScanMode.StockIn, StockStatus.Archived))
        assertTrue(ScanWorkflowRules.allowsItem(ScanMode.Stocktake, StockStatus.InStock))
        assertTrue(ScanWorkflowRules.allowsItem(ScanMode.BindLocation, StockStatus.InStock))
        assertFalse(ScanWorkflowRules.allowsItem(ScanMode.Stocktake, StockStatus.CheckedOut))
        assertFalse(ScanWorkflowRules.allowsItem(ScanMode.BindLocation, null))
    }

    @Test
    fun replacingItemClearsDependentInputs() {
        val state = ScanWorkflowState(
            item = part("PART-001"),
            weightG = 42.0,
            quantity = 100,
            location = location,
            review = ScanReview.Weight(42.0, false),
        )

        val result = ScanWorkflowRules.confirmItem(state, spool("FIL-001"))

        assertEquals("FIL-001", result.item?.id)
        assertNull(result.weightG)
        assertNull(result.quantity)
        assertNull(result.location)
        assertNull(result.review)
    }

    @Test
    fun sameIdTypeChangeDropsPartQuantityWithoutDroppingWeightOrLocation() {
        val state = ScanWorkflowState(
            item = part("ITEM-001"),
            weightG = 500.0,
            quantity = 1000,
            location = location,
        )

        val result = ScanWorkflowRules.confirmItem(state, spool("ITEM-001"))

        assertEquals(500.0, result.weightG)
        assertNull(result.quantity)
        assertEquals(location, result.location)
    }

    @Test
    fun otherItemAlwaysDropsPreScannedWeight() {
        val state = ScanWorkflowState(weightG = 200.0, location = location)

        val result = ScanWorkflowRules.confirmItem(
            state,
            FixedData(type = ItemType.Other, id = "ITEM-001", name = "热风枪"),
        )

        assertNull(result.weightG)
        assertNull(result.quantity)
        assertEquals(location, result.location)
    }

    @Test
    fun stockInRequiresValidVariablesForEachItemType() {
        val spoolState = ScanWorkflowState(item = spool("FIL-001"), location = location, weightG = 200.0)
        assertFalse(ScanWorkflowRules.canStockIn(spoolState, null))
        assertTrue(ScanWorkflowRules.canStockIn(spoolState.copy(weightG = 200.1), null))
        assertFalse(ScanWorkflowRules.canStockIn(spoolState.copy(weightG = 500.0), StockStatus.Archived))

        val partState = ScanWorkflowState(item = part("PART-001"), location = location, weightG = 42.0)
        assertTrue(ScanWorkflowRules.canStockIn(partState, null))
        assertFalse(ScanWorkflowRules.canStockIn(partState.copy(location = null), null))

        val otherState = ScanWorkflowState(
            item = FixedData(type = ItemType.Other, id = "ITEM-001", name = "热风枪"),
            location = location,
        )
        assertTrue(ScanWorkflowRules.canStockIn(otherState, null))
    }

    @Test
    fun stocktakeAndMoveRequireInStockItem() {
        val fixed = spool("FIL-001")
        val inStock = inventoryItem(fixed, StockStatus.InStock)
        val checkedOut = inventoryItem(fixed, StockStatus.CheckedOut)
        val state = ScanWorkflowState(item = fixed, weightG = 450.0, location = location)

        assertTrue(ScanWorkflowRules.canStocktake(state, inStock))
        assertFalse(ScanWorkflowRules.canStocktake(state, checkedOut))
        assertTrue(ScanWorkflowRules.canMove(state, inStock))
        assertFalse(ScanWorkflowRules.canMove(state, checkedOut))
        assertFalse(ScanWorkflowRules.canMove(state.copy(location = null), inStock))
    }

    @Test
    fun sortingDispositionPreventsDuplicateMoves() {
        assertEquals(SortingDisposition.Missing, ScanWorkflowRules.sortingDisposition(null, location))
        assertEquals(
            SortingDisposition.NotInStock,
            ScanWorkflowRules.sortingDisposition(
                inventoryItem(spool("FIL-001"), StockStatus.CheckedOut),
                location,
            ),
        )
        assertEquals(
            SortingDisposition.AlreadyThere,
            ScanWorkflowRules.sortingDisposition(
                inventoryItem(spool("FIL-001"), StockStatus.InStock, location.id),
                location,
            ),
        )
        assertEquals(
            SortingDisposition.Move,
            ScanWorkflowRules.sortingDisposition(
                inventoryItem(spool("FIL-001"), StockStatus.InStock, "LOC-OLD"),
                location,
            ),
        )
    }

    @Test
    fun v1PayloadDecodesChineseAndWeight() {
        val item = parseV1Payload(
            "v1;type=part;id=part-001;name=M3x8%E9%BB%91%E8%89%B2%E8%9E%BA%E4%B8%9D;unit_weight_g=0.42",
        )
        val weight = parseV1Payload("v1;type=weight;value_g=712.4")

        assertEquals(ItemType.Part, item.type)
        assertEquals("PART-001", item.id)
        assertEquals("M3x8黑色螺丝", item.fixed?.name)
        assertEquals(0.42, item.fixed?.unitWeightG)
        assertEquals(712.4, weight.weightG)
    }

    private fun spool(id: String) = FixedData(
        type = ItemType.Spool,
        id = id,
        brand = "Bambu",
        material = "PLA",
        color = "white",
        tareG = 200.0,
    )

    private fun part(id: String) = FixedData(
        type = ItemType.Part,
        id = id,
        name = "M3 螺丝",
        unitWeightG = 0.42,
    )

    private fun inventoryItem(fixed: FixedData, status: StockStatus, locationId: String = "") = InventoryItem(
        id = fixed.id,
        type = fixed.type,
        fixed = fixed,
        state = ItemState(status = status, locationId = locationId),
    )
}
