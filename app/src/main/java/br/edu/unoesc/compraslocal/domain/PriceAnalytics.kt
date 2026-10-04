package br.edu.unoesc.compraslocal.domain

import br.edu.unoesc.compraslocal.data.dao.StoreComparisonRow

enum class PriceSignal { CHEAPER, AVERAGE, EXPENSIVE }

data class ComparisonInsight(
    val storeName: String,
    val avgUnitPrice: Double,
    val percentVsBest: Double,
    val signal: PriceSignal,
)

object PriceAnalytics {
    fun compare(rows: List<StoreComparisonRow>): List<ComparisonInsight> {
        if (rows.isEmpty()) return emptyList()
        val best = rows.minOf { it.avgUnitPrice }
        return rows.map { row ->
            val pct = if (best > 0) ((row.avgUnitPrice - best) / best) * 100.0 else 0.0
            val signal = when {
                row.avgUnitPrice <= best * 1.02 -> PriceSignal.CHEAPER
                row.avgUnitPrice >= best * 1.12 -> PriceSignal.EXPENSIVE
                else -> PriceSignal.AVERAGE
            }
            ComparisonInsight(row.storeName, row.avgUnitPrice, pct, signal)
        }
    }

    fun signalVsHistorical(current: Double, history: List<Double>): PriceSignal {
        if (history.isEmpty()) return PriceSignal.AVERAGE
        val avg = history.average()
        return when {
            current <= avg * 0.95 -> PriceSignal.CHEAPER
            current >= avg * 1.08 -> PriceSignal.EXPENSIVE
            else -> PriceSignal.AVERAGE
        }
    }
}
