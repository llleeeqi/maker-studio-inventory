package studio.inventory.android

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.floor

enum class ItemType(val payload: String, val label: String) {
    Spool("spool", "耗材"),
    Part("part", "零件"),
    Other("other", "其他"),
    Location("location", "库位"),
    Weight("weight", "重量");

    companion object {
        fun fromPayload(value: String): ItemType? =
            entries.firstOrNull { it.payload == value.lowercase() }
    }
}

private const val PayloadPrefix = "v1"

enum class StockStatus(val value: String, val label: String) {
    InStock("in_stock", "在库"),
    CheckedOut("checked_out", "已出库"),
    Archived("archived", "已归档");

    companion object {
        fun fromValue(value: String): StockStatus =
            entries.firstOrNull { it.value == value } ?: InStock
    }
}

data class FixedData(
    val type: ItemType = ItemType.Other,
    val id: String = "",
    val name: String = "",
    val brand: String = "",
    val material: String = "",
    val color: String = "",
    val tareG: Double? = null,
    val fullG: Double? = null,
    val netG: Double? = null,
    val category: String = "",
    val spec: String = "",
    val unitWeightG: Double? = null,
    val note: String = "",
) {
    val displayName: String
        get() = when {
            name.isNotBlank() -> name
            type == ItemType.Spool -> listOf(brand, material, color)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { id }
            else -> id
        }

    val searchText: String
        get() = listOf(
            id,
            displayName,
            brand,
            material,
            color,
            category,
            spec,
            note,
        ).joinToString(" ").lowercase()

    fun missingRequiredFields(): List<String> {
        val missing = mutableListOf<String>()
        if (id.isBlank()) missing += "id"
        when (type) {
            ItemType.Spool -> {
                if (brand.isBlank()) missing += "brand"
                if (material.isBlank()) missing += "material"
                if (color.isBlank()) missing += "color"
                if (tareG == null || tareG <= 0.0) missing += "tare_g"
            }
            ItemType.Part -> {
                if (displayName.isBlank()) missing += "name"
                if (unitWeightG == null || unitWeightG <= 0.0) missing += "unit_weight_g"
            }
            ItemType.Other -> {
                if (displayName.isBlank()) missing += "name"
            }
            ItemType.Location -> {
                if (displayName.isBlank()) missing += "name"
            }
            ItemType.Weight -> Unit
        }
        return missing
    }

    fun equivalentTo(other: FixedData): Boolean {
        return type == other.type &&
            id.equals(other.id, ignoreCase = true) &&
            displayName == other.displayName &&
            brand == other.brand &&
            material == other.material &&
            color == other.color &&
            tareG == other.tareG &&
            fullG == other.fullG &&
            netG == other.netG &&
            category == other.category &&
            spec == other.spec &&
            unitWeightG == other.unitWeightG &&
            note == other.note
    }
}

data class ItemState(
    val status: StockStatus = StockStatus.InStock,
    val currentG: Double? = null,
    val currentQty: Int? = null,
    val locationId: String = "",
    val locationName: String = "",
    val stockedOn: String = todayCode(),
    val checkedOutOn: String? = null,
    val updatedAt: String = nowIso(),
)

data class InventoryItem(
    val id: String = "",
    val type: ItemType = ItemType.Other,
    val fixed: FixedData = FixedData(),
    val state: ItemState = ItemState(),
) {
    val usableG: Double?
        get() {
            val current = state.currentG ?: return null
            val tare = fixed.tareG ?: return null
            return round1(current - tare)
        }

    val locationText: String
        get() = state.locationName.ifBlank { state.locationId.ifBlank { "未绑定" } }

    val stockText: String
        get() = when (state.status) {
            StockStatus.CheckedOut -> "已出库"
            StockStatus.Archived -> "已归档"
            StockStatus.InStock -> when (type) {
                ItemType.Spool -> usableG?.let { "可用 ${it.gText()}g" } ?: "未称重"
                ItemType.Part -> state.currentQty?.let { "$it 件" } ?: "未记录数量"
                ItemType.Other -> "在库"
                ItemType.Location, ItemType.Weight -> state.status.label
            }
        }

    val searchText: String
        get() = "${fixed.searchText} ${state.status.label} ${state.locationId} ${state.locationName}".lowercase()
}

data class InventoryTransaction(
    val txId: String = "",
    val action: String = "",
    val itemId: String = "",
    val itemType: ItemType = ItemType.Other,
    val createdAt: String = nowIso(),
    val before: InventoryItem? = null,
    val after: InventoryItem? = null,
)

data class ScanLogEntry(
    val payload: String = "",
    val createdAt: String = nowIso(),
)

data class InventorySnapshot(
    val schema: Int = 1,
    val deviceId: String = "android-phone",
    val items: Map<String, InventoryItem> = emptyMap(),
    val transactions: List<InventoryTransaction> = emptyList(),
    val scanLog: List<ScanLogEntry> = emptyList(),
)

data class ParsedPayload(
    val type: ItemType?,
    val fields: Map<String, String>,
    val fixed: FixedData? = null,
    val weightG: Double? = null,
    val raw: String = "",
) {
    val id: String
        get() = fields["id"].orEmpty().trim().uppercase()
}

data class LocationValue(
    val id: String,
    val name: String,
)

fun parseV1Payload(raw: String): ParsedPayload {
    val text = raw.trim()
    if (!text.lowercase().startsWith("$PayloadPrefix;")) {
        return ParsedPayload(type = null, fields = emptyMap(), raw = raw)
    }

    val fields = text.split(";")
        .drop(1)
        .mapNotNull { token ->
            val index = token.indexOf("=")
            if (index <= 0) return@mapNotNull null
            token.substring(0, index).trim().lowercase() to token.substring(index + 1).trim()
        }
        .toMap()

    val type = ItemType.fromPayload(fields["type"].orEmpty())
    val id = fields["id"].orEmpty().trim().uppercase()
    return when (type) {
        ItemType.Spool -> ParsedPayload(
            type = type,
            fields = fields,
            fixed = FixedData(
                type = type,
                id = id,
                name = fields["name"].orEmpty(),
                brand = fields["brand"].orEmpty(),
                material = fields["material"].orEmpty(),
                color = fields["color"].orEmpty(),
                tareG = fields["tare_g"]?.toDoubleOrNull(),
                fullG = fields["full_g"]?.toDoubleOrNull(),
                netG = fields["net_g"]?.toDoubleOrNull(),
                note = fields["note"].orEmpty(),
            ),
            raw = raw,
        )
        ItemType.Part -> ParsedPayload(
            type = type,
            fields = fields,
            fixed = FixedData(
                type = type,
                id = id,
                name = fields["name"].orEmpty(),
                category = fields["category"].orEmpty(),
                spec = fields["spec"].orEmpty(),
                color = fields["color"].orEmpty(),
                unitWeightG = fields["unit_weight_g"]?.toDoubleOrNull(),
                note = fields["note"].orEmpty(),
            ),
            raw = raw,
        )
        ItemType.Other -> ParsedPayload(
            type = type,
            fields = fields,
            fixed = FixedData(
                type = type,
                id = id,
                name = fields["name"].orEmpty(),
                note = fields["note"].orEmpty(),
            ),
            raw = raw,
        )
        ItemType.Location -> ParsedPayload(
            type = type,
            fields = fields,
            fixed = FixedData(
                type = type,
                id = id,
                name = fields["name"].orEmpty(),
                note = fields["note"].orEmpty(),
            ),
            raw = raw,
        )
        ItemType.Weight -> ParsedPayload(
            type = type,
            fields = fields,
            weightG = fields["value_g"]?.toDoubleOrNull(),
            raw = raw,
        )
        null -> ParsedPayload(type = null, fields = fields, raw = raw)
    }
}

fun buildV1Payload(fields: Map<String, String>): String {
    val buffer = StringBuilder(PayloadPrefix)
    fields.forEach { (key, value) ->
        val clean = value.trim()
        if (clean.isNotEmpty()) {
            buffer.append(";").append(key).append("=").append(clean)
        }
    }
    return buffer.toString()
}

fun todayCode(): String {
    val now = OffsetDateTime.now()
    return "%02d%02d%02d".format(now.year % 100, now.monthValue, now.dayOfMonth)
}

fun nowIso(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

fun Double.gText(): String {
    val rounded = round1(this)
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

fun nextAutoId(type: ItemType, existingIds: Set<String>): String {
    val prefix = when (type) {
        ItemType.Spool -> "FIL"
        ItemType.Part -> "PART"
        ItemType.Other -> "ITEM"
        ItemType.Location -> "LOC"
        ItemType.Weight -> "WEIGHT"
    }
    val date = todayCode()
    for (index in 1..999) {
        val id = "$prefix-$date-${index.toString().padStart(3, '0')}"
        if (!existingIds.contains(id)) return id
    }
    return "$prefix-$date-${System.currentTimeMillis().toString().takeLast(5)}"
}

fun quantityFromWeight(weightG: Double?, unitWeightG: Double?): Int? {
    if (weightG == null || unitWeightG == null || unitWeightG <= 0.0) return null
    return floor(weightG / unitWeightG).toInt().takeIf { it > 0 }
}
