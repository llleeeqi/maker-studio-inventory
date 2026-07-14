package studio.inventory.android

import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryDisplayTest {
    @Test
    fun formatsStoredDateCode() {
        assertEquals("2026-07-14", displayDate("260714"))
        assertEquals("无", displayDate(null))
    }

    @Test
    fun formatsStoredTimestampWithoutSeconds() {
        assertEquals(
            "2026-07-14 15:29",
            displayTimestamp("2026-07-14T15:29:44.824+08:00"),
        )
        assertEquals("invalid", displayTimestamp("invalid"))
    }

    @Test
    fun translatesMainTransactionActions() {
        assertEquals("入库", transactionActionLabel("stock_in"))
        assertEquals("出库", transactionActionLabel("checkout"))
        assertEquals("更新库存", transactionActionLabel("stocktake"))
        assertEquals("撤销", transactionActionLabel("undo"))
        assertEquals("custom", transactionActionLabel("custom"))
    }

    @Test
    fun keepsFixedWeightParameterPrecision() {
        assertEquals("0.42", 0.42.parameterText())
        assertEquals("200", 200.0.parameterText())
        assertEquals("198.35", 198.35.parameterText())
    }
}
