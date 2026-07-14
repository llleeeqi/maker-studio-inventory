package studio.inventory.android

enum class ScanMode(val label: String) {
    StockIn("入库"),
    Stocktake("更新库存"),
    BindLocation("绑定库位"),
}

data class ScanWorkflowState(
    val mode: ScanMode = ScanMode.StockIn,
    val item: FixedData? = null,
    val weightG: Double? = null,
    val quantity: Int? = null,
    val location: LocationValue? = null,
    val sortingLocation: LocationValue? = null,
    val review: ScanReview? = null,
) {
    val hasSession: Boolean
        get() = item != null || weightG != null || quantity != null || location != null || sortingLocation != null

    fun clearedForMode(nextMode: ScanMode = mode): ScanWorkflowState = ScanWorkflowState(mode = nextMode)
}

sealed interface ScanReview {
    data class Item(
        val scanned: FixedData,
        val local: InventoryItem?,
        val replacesCurrentItem: Boolean,
    ) : ScanReview {
        val hasFixedConflict: Boolean
            get() = local != null && !local.fixed.equivalentTo(scanned)
    }

    data class Weight(
        val valueG: Double,
        val replacesCurrentWeight: Boolean,
    ) : ScanReview

    data class Location(
        val id: String,
        val suggestedName: String,
        val replacesCurrentLocation: Boolean,
    ) : ScanReview

    data class ModeSwitch(val target: ScanMode) : ScanReview
}
