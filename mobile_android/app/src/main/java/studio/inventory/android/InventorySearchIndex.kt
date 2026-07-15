package studio.inventory.android

import com.github.promeg.pinyinhelper.Pinyin
import kotlin.math.min

data class InventorySearchMatch(
    val item: InventoryItem,
    val score: Int,
)

class InventorySearchIndex private constructor(
    private val documents: List<SearchDocument>,
) {
    fun search(
        query: String,
        typeFilter: ItemType?,
        statusFilter: StockStatus,
    ): List<InventorySearchMatch> {
        val normalizedQuery = normalizeSearchText(query)
        val compactQuery = compactSearchText(normalizedQuery)
        if (compactQuery.isBlank()) return emptyList()

        return documents.asSequence()
            .filter { document ->
                (typeFilter == null || document.item.type == typeFilter) &&
                    document.item.state.status == statusFilter
            }
            .mapNotNull { document ->
                document.score(normalizedQuery, compactQuery)?.let { score ->
                    InventorySearchMatch(document.item, score)
                }
            }
            .sortedWith(
                compareByDescending<InventorySearchMatch> { it.score }
                    .thenBy { it.item.state.locationId }
                    .thenBy { it.item.id },
            )
            .toList()
    }

    companion object {
        fun build(items: Collection<InventoryItem>): InventorySearchIndex {
            return InventorySearchIndex(items.map(::SearchDocument))
        }
    }
}

private class SearchDocument(val item: InventoryItem) {
    private val rawFields = listOf(
        item.id,
        item.fixed.displayName,
        item.fixed.brand,
        item.fixed.material,
        item.fixed.color,
        item.fixed.category,
        item.fixed.spec,
        item.fixed.note,
        item.state.status.label,
        item.state.locationId,
        item.state.locationName,
    ).filter(String::isNotBlank)

    private val normalizedFields = rawFields.map(::normalizeSearchText)
    private val compactFields = normalizedFields.map(::compactSearchText)
    private val pinyinFields = rawFields.map(::toCompactPinyin)
    private val initialFields = rawFields.map(::toPinyinInitials)

    fun score(normalizedQuery: String, compactQuery: String): Int? {
        val id = normalizeSearchText(item.id)
        val name = normalizeSearchText(item.fixed.displayName)
        if (normalizedQuery == id || normalizedQuery == name) return 1_000

        bestFieldScore(normalizedFields, normalizedQuery, 930, 880, 800)?.let { return it }
        bestFieldScore(compactFields, compactQuery, 920, 860, 790)?.let { return it }
        bestFieldScore(pinyinFields, compactQuery, 850, 780, 700)?.let { return it }
        bestFieldScore(initialFields, compactQuery, 820, 750, 670)?.let { return it }

        if (compactQuery.length >= 4) {
            val maximumDistance = if (compactQuery.length >= 5) 2 else 1
            val candidates = compactFields + pinyinFields
            val distance = candidates.minOfOrNull {
                approximateSubstringDistance(it, compactQuery, maximumDistance)
            } ?: Int.MAX_VALUE
            if (distance <= maximumDistance) return 560 - distance * 40
        }
        return null
    }
}

private fun bestFieldScore(
    fields: List<String>,
    query: String,
    exactScore: Int,
    prefixScore: Int,
    containsScore: Int,
): Int? {
    if (query.isBlank()) return null
    if (fields.any { it == query }) return exactScore
    if (fields.any { it.startsWith(query) }) return prefixScore
    if (fields.any { it.contains(query) }) return containsScore
    return null
}

private fun normalizeSearchText(value: String): String {
    return value.trim().lowercase().replace(WhitespaceRegex, " ")
}

private fun compactSearchText(value: String): String {
    return value.filter(Char::isLetterOrDigit).lowercase()
}

private fun toCompactPinyin(value: String): String {
    return Pinyin.toPinyin(value, "").lowercase().filter(Char::isLetterOrDigit)
}

private fun toPinyinInitials(value: String): String {
    val result = StringBuilder()
    var latinWord = false
    value.forEach { character ->
        when {
            Pinyin.isChinese(character) -> {
                result.append(Pinyin.toPinyin(character).first().lowercaseChar())
                latinWord = false
            }
            character.isLetterOrDigit() -> {
                if (!latinWord) result.append(character.lowercaseChar())
                latinWord = true
            }
            else -> latinWord = false
        }
    }
    return result.toString()
}

private fun approximateSubstringDistance(candidate: String, query: String, maximumDistance: Int): Int {
    if (candidate.isBlank()) return Int.MAX_VALUE
    if (candidate.length <= query.length + maximumDistance) {
        return boundedLevenshtein(candidate, query, maximumDistance)
    }
    var best = Int.MAX_VALUE
    val minimumLength = (query.length - maximumDistance).coerceAtLeast(1)
    val maximumLength = query.length + maximumDistance
    for (length in minimumLength..maximumLength) {
        if (length > candidate.length) continue
        for (start in 0..candidate.length - length) {
            val distance = boundedLevenshtein(
                candidate.substring(start, start + length),
                query,
                maximumDistance,
            )
            best = min(best, distance)
            if (best == 0) return 0
        }
    }
    return best
}

private fun boundedLevenshtein(left: String, right: String, maximumDistance: Int): Int {
    if (kotlin.math.abs(left.length - right.length) > maximumDistance) return maximumDistance + 1
    var previous = IntArray(right.length + 1) { it }
    for (leftIndex in left.indices) {
        val current = IntArray(right.length + 1)
        current[0] = leftIndex + 1
        var rowMinimum = current[0]
        for (rightIndex in right.indices) {
            val substitutionCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
            current[rightIndex + 1] = minOf(
                current[rightIndex] + 1,
                previous[rightIndex + 1] + 1,
                previous[rightIndex] + substitutionCost,
            )
            rowMinimum = min(rowMinimum, current[rightIndex + 1])
        }
        if (rowMinimum > maximumDistance) return maximumDistance + 1
        previous = current
    }
    return previous[right.length]
}

private val WhitespaceRegex = Regex("\\s+")
