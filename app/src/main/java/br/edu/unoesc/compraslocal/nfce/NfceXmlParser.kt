package br.edu.unoesc.compraslocal.nfce

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

object NfceXmlParser {
    fun parse(xml: String): NfceReceipt {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var event = parser.eventType
        var accessKey = ""
        var storeName = "Estabelecimento"
        var storeCnpj: String? = null
        var issuedAt = System.currentTimeMillis()
        var total = 0.0
        val items = mutableListOf<NfceItem>()

        var inDet = false
        var currentDesc = ""
        var currentQty = 1.0
        var currentUnit = 0.0
        var currentTotal = 0.0
        var textTag = ""

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    textTag = parser.name
                    when (textTag) {
                        "det" -> {
                            inDet = true
                            currentDesc = ""
                            currentQty = 1.0
                            currentUnit = 0.0
                            currentTotal = 0.0
                        }
                        "chNFe" -> accessKey = parser.nextText().trim()
                        "xNome" -> if (!inDet) storeName = parser.nextText().trim()
                        "CNPJ" -> if (!inDet) storeCnpj = parser.nextText().trim()
                        "dhEmi" -> issuedAt = parseDate(parser.nextText())
                        "vNF" -> total = parser.nextText().replace(",", ".").toDoubleOrNull() ?: total
                        "xProd" -> if (inDet) currentDesc = parser.nextText().trim()
                        "qCom" -> if (inDet) currentQty = parser.nextText().replace(",", ".").toDoubleOrNull() ?: 1.0
                        "vUnCom" -> if (inDet) currentUnit = parser.nextText().replace(",", ".").toDoubleOrNull() ?: 0.0
                        "vProd" -> if (inDet) currentTotal = parser.nextText().replace(",", ".").toDoubleOrNull() ?: 0.0
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "det" && inDet && currentDesc.isNotBlank()) {
                        items += NfceItem(
                            description = currentDesc,
                            quantity = currentQty,
                            unitPrice = currentUnit,
                            totalPrice = if (currentTotal > 0) currentTotal else currentUnit * currentQty,
                        )
                        inDet = false
                    }
                }
            }
            event = parser.next()
        }

        if (accessKey.length != 44) {
            val match = Regex("(\\d{44})").find(xml)
            if (match != null) accessKey = match.value
        }
        if (items.isEmpty()) throw IllegalArgumentException("Nenhum item encontrado no XML da NFC-e")

        return NfceReceipt(
            accessKey = accessKey,
            storeName = storeName,
            storeCnpj = storeCnpj,
            issuedAt = issuedAt,
            totalAmount = if (total > 0) total else items.sumOf { it.totalPrice },
            items = items,
        )
    }

    private fun parseDate(raw: String): Long {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
        )
        for (pattern in patterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(raw.substring(0, minOf(raw.length, 25)))?.time
                    ?: continue
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }
}
