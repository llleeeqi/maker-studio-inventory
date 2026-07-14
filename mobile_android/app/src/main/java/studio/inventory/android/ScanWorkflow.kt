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

enum class SortingDisposition {
    Missing,
    NotInStock,
    AlreadyThere,
    Move,
}

object ScanWorkflowRules {
    fun allowsItem(mode: ScanMode, localStatus: StockStatus?): Boolean = when (mode) {
        ScanMode.StockIn -> localStatus != StockStatus.InStock && localStatus != StockStatus.Archived
        ScanMode.Stocktake, ScanMode.BindLocation -> localStatus == StockStatus.InStock
    }

    fun confirmItem(state: ScanWorkflowState, selected: FixedData): ScanWorkflowState {
        val replacing = state.item != null && !state.item.id.equals(selected.id, ignoreCase = true)
        val retainedWeight = state.weightG.takeUnless { replacing || selected.type == ItemType.Other }
        val quantity = if (selected.type == ItemType.Part) {
            quantityFromWeight(retainedWeight, selected.unitWeightG)
        } else {
            null
        }
        return state.copy(
            item = selected,
            weightG = retainedWeight,
            quantity = quantity,
            location = state.location.takeUnless { replacing },
            review = null,
        )
    }

    fun canStockIn(state: ScanWorkflowState, existingStatus: StockStatus?): Boolean {
        val fixed = state.item ?: return false
        if (state.location?.id.isNullOrBlank()) return false
        if (!allowsItem(ScanMode.StockIn, existingStatus)) return false
        return when (fixed.type) {
            ItemType.Spool -> {
                val current = state.weightG
                val tare = fixed.tareG
                current != null && tare != null && current > tare
            }
            ItemType.Part -> resolvedPartQuantity(state, fixed) != null
            ItemType.Other -> true
            ItemType.Location, ItemType.Weight -> false
        }
    }

    fun canStocktake(state: ScanWorkflowState, existing: InventoryItem?): Boolean {
        if (existing?.state?.status != StockStatus.InStock) return false
        return when (existing.type) {
            ItemType.Spool -> {
                val current = state.weightG
                val tare = existing.fixed.tareG
                current != null && tare != null && current > tare
            }
            ItemType.Part -> resolvedPartQuantity(state, existing.fixed) != null
            ItemType.Other, ItemType.Location, ItemType.Weight -> false
        }
    }

    fun canMove(state: ScanWorkflowState, existing: InventoryItem?): Boolean =
        existing?.state?.status == StockStatus.InStock && state.location != null

    fun sortingDisposition(existing: InventoryItem?, location: LocationValue): SortingDisposition = when {
        existing == null -> SortingDisposition.Missing
        existing.state.status != StockStatus.InStock -> SortingDisposition.NotInStock
        existing.state.locationId == location.id -> SortingDisposition.AlreadyThere
        else -> SortingDisposition.Move
    }

    fun resolvedPartQuantity(state: ScanWorkflowState, fixed: FixedData): Int? =
        state.quantity ?: quantityFromWeight(state.weightG, fixed.unitWeightG)
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
