package br.edu.unoesc.compraslocal.nfce

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parser da página HTML da NFC-e (layout SEFAZ nacional / SC: txtTit2, Rqtd, RvlUnit, valor).
 * Referência: estrutura usada por consultas públicas estaduais.
 */
object NfceHtmlParser {

    fun canParse(html: String): Boolean =
        !isCaptchaPage(html) && (
            html.contains("txtTit2", ignoreCase = true) ||
                html.contains("txtProd", ignoreCase = true) ||
                html.contains("txtTopo", ignoreCase = true)
            )

    fun looksLikeReceipt(html: String): Boolean = canParse(html)

    fun isCaptchaPage(html: String): Boolean =
        (html.contains("Verifica", ignoreCase = true) && html.contains("prosseguimento", ignoreCase = true)) ||
            html.contains("cssCaptchaField", ignoreCase = true) ||
            html.contains("CaptchaField", ignoreCase = true)

    fun parse(html: String, accessKey: String): NfceReceipt {
        if (isCaptchaPage(html)) throw NfceCaptchaException()

        val embeddedXml = extractEmbeddedXml(html)
        if (embeddedXml != null) return NfceXmlParser.parse(embeddedXml)

        val storeName = extractStoreName(html) ?: "Estabelecimento"
        val cnpj = extractCnpj(html)
        val items = extractItems(html)
        if (items.isEmpty()) {
            throw IllegalStateException("Itens da nota não encontrados no HTML da SEFAZ.")
        }
        val total = extractTotal(html) ?: items.sumOf { it.totalPrice }
        val issuedAt = extractDate(html)
        val key = extractKey(html) ?: accessKey

        return NfceReceipt(
            accessKey = key,
            storeName = storeName,
            storeCnpj = cnpj,
            issuedAt = issuedAt,
            totalAmount = total,
            items = items,
        )
    }

    private fun extractEmbeddedXml(html: String): String? {
        val patterns = listOf(
            Regex("""<nfeProc[\s\S]*?</nfeProc>""", RegexOption.IGNORE_CASE),
            Regex("""<NFe[\s\S]*?</NFe>""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.value }
    }

    private fun extractStoreName(html: String): String? {
        val patterns = listOf(
            Regex("""class="txtTopo"[^>]*>([^<]+)""", RegexOption.IGNORE_CASE),
            Regex("""class='txtTopo'[^>]*>([^<]+)""", RegexOption.IGNORE_CASE),
            Regex("""xNome[^>]*>([^<]+)""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1)?.trim() }
            ?.takeIf { it.length > 2 }
    }

    private fun extractCnpj(html: String): String? {
        val formatted = Regex("""(\d{2}\.\d{3}\.\d{3}/\d{4}-\d{2})""").find(html)?.value
        if (formatted != null) return formatted.replace(Regex("[^0-9]"), "")
        return Regex("""CNPJ[:\s]*(\d{14})""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
    }

    private fun extractKey(html: String): String? {
        val fromSpan = Regex("""class="chave"[^>]*>([^<]+)""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.replace(Regex("[^0-9]"), "")
        if (fromSpan?.length == 44) return fromSpan
        return Regex("""\b(\d{44})\b""").find(html)?.value
    }

    private fun extractDate(html: String): Long {
        val patterns = listOf(
            Regex("""(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2})"""),
            Regex("""(\d{2}/\d{2}/\d{4})"""),
        )
        for (pattern in patterns) {
            val raw = pattern.find(html)?.groupValues?.get(1) ?: continue
            for (fmt in listOf("dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy")) {
                try {
                    return SimpleDateFormat(fmt, Locale("pt", "BR")).parse(raw)?.time ?: continue
                } catch (_: Exception) {
                }
            }
        }
        return System.currentTimeMillis()
    }

    private fun extractTotal(html: String): Double? {
        val patterns = listOf(
            Regex("""class="totalNumb txtMax"[^>]*>([^<]+)""", RegexOption.IGNORE_CASE),
            Regex("""Valor\s*a\s*Pagar[^0-9]*([\d.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""vNF[^>]*>([\d.,]+)""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { parseMoney(it.find(html)?.groupValues?.get(1)) }
    }

    private fun extractItems(html: String): List<NfceItem> {
        val tit2 = extractByClassPattern(html, "txtTit2")
        if (tit2.isNotEmpty()) return tit2

        val tit = extractByClassPattern(html, "txtTit")
        if (tit.isNotEmpty()) return tit

        val prod = extractSpanLayoutItems(html, "txtProd")
        if (prod.isNotEmpty()) return prod

        return extractTableItems(html)
    }

    /** Alinha spans por índice: txtTit2[i], Rqtd[i], RvlUnit[i], valor[i] */
    private fun extractByClassPattern(html: String, titleClass: String): List<NfceItem> {
        val titles = extractAllClassTexts(html, titleClass)
        val qtys = extractAllClassTexts(html, "Rqtd")
        val units = extractAllClassTexts(html, "RvlUnit")
        val totals = extractAllClassTexts(html, "valor")

        if (titles.isEmpty()) return emptyList()

        return titles.mapIndexed { index, title ->
            val qty = parseMoney(qtys.getOrNull(index)) ?: 1.0
            val unit = parseMoney(units.getOrNull(index)) ?: totals.getOrNull(index)?.let { parseMoney(it) } ?: 0.0
            val total = parseMoney(totals.getOrNull(index)) ?: (unit * qty)
            NfceItem(
                description = title,
                quantity = qty,
                unitPrice = if (unit > 0) unit else total / qty.coerceAtLeast(1.0),
                totalPrice = total,
            )
        }.filter { it.description.isNotBlank() && (it.unitPrice > 0 || it.totalPrice > 0) }
    }

    private fun extractAllClassTexts(html: String, className: String): List<String> {
        val regex = Regex("""class="$className"[^>]*>([^<]*)<""", RegexOption.IGNORE_CASE)
        return regex.findAll(html).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
    }

    private fun extractSpanLayoutItems(html: String, prodClass: String): List<NfceItem> {
        val blocks = Regex(
            """$prodClass[^>]*>([^<]+)<[\s\S]{0,500}?Rqtd[^>]*>([^<]+)<[\s\S]{0,300}?RvlUnit[^>]*>([^<]+)<""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)

        return blocks.mapNotNull { match ->
            val desc = match.groupValues[1].trim()
            val qty = parseMoney(match.groupValues[2]) ?: 1.0
            val unit = parseMoney(match.groupValues[3]) ?: return@mapNotNull null
            if (desc.isBlank()) return@mapNotNull null
            NfceItem(desc, qty, unit, unit * qty)
        }.toList()
    }

    private fun extractTableItems(html: String): List<NfceItem> {
        val rows = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE).findAll(html)
        val items = mutableListOf<NfceItem>()
        for (row in rows) {
            val cells = Regex("""<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
                .findAll(row.groupValues[1])
                .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotBlank() }
                .toList()
            if (cells.size < 2) continue
            val desc = cells.firstOrNull { it.length > 3 && !it.matches(Regex("""[\d.,]+""")) } ?: continue
            val numbers = cells.mapNotNull { parseMoney(it) }
            if (numbers.isEmpty()) continue
            val unit = numbers.last()
            val qty = if (numbers.size >= 2) numbers[numbers.size - 2] else 1.0
            items += NfceItem(desc, qty, unit, unit * qty)
        }
        return items.distinctBy { it.description.lowercase() }
    }

    private fun parseMoney(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace("\u00a0", "")
            .replace("Qtde.:", "")
            .replace("Vl. Unit.:", "")
            .replace(Regex("[^0-9,.]"), "")
        if (cleaned.isBlank()) return null
        return when {
            cleaned.contains(',') -> cleaned.replace(".", "").replace(',', '.').toDoubleOrNull()
            else -> cleaned.toDoubleOrNull()
        }
    }
}
