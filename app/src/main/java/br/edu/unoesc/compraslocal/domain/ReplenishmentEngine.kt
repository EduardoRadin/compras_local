package br.edu.unoesc.compraslocal.domain

import br.edu.unoesc.compraslocal.data.dao.FrequentProductRow

data class ReplenishmentSuggestion(
    val productId: Long,
    val productName: String,
    val daysSinceLastPurchase: Int,
    val reason: String,
)

object ReplenishmentEngine {
  private const val DEFAULT_CYCLE_DAYS = 21

    fun suggest(rows: List<FrequentProductRow>, nowMs: Long = System.currentTimeMillis()): List<ReplenishmentSuggestion> {
        return rows.mapNotNull { row ->
            val daysSince = ((nowMs - row.lastPurchasedAt) / 86_400_000L).toInt()
            val cycle = if (row.avgDaysBetween > 0) row.avgDaysBetween.toInt() else DEFAULT_CYCLE_DAYS
            val threshold = (cycle * 0.85).toInt().coerceAtLeast(7)
            if (daysSince >= threshold) {
                ReplenishmentSuggestion(
                    productId = row.productId,
                    productName = row.productName,
                    daysSinceLastPurchase = daysSince,
                    reason = "Comprado ${row.purchaseCount}x; última há $daysSince dias",
                )
            } else null
        }.take(8)
    }
}
