package br.edu.unoesc.compraslocal.nfce

object NfceQrParser {
    private val keyRegex = Regex("\\b(\\d{44})\\b")

    fun parse(raw: String): Pair<String, String?> {
        val trimmed = raw.trim()
        val key = extractAccessKey(trimmed)
            ?: throw IllegalArgumentException("Chave NFC-e (44 dígitos) não encontrada no QR Code")
        val consultUrl = extractConsultUrl(trimmed)
        return key to consultUrl
    }

    private fun extractConsultUrl(text: String): String? {
        if (text.startsWith("http", ignoreCase = true)) return text
        val urlInText = Regex("""(https?://[^\s]+)""", RegexOption.IGNORE_CASE).find(text)?.value
        return urlInText
    }

    private fun extractAccessKey(text: String): String? {
        if (text.contains("p=")) {
            val afterP = text.substringAfter("p=").substringBefore("&")
            val candidate = afterP.substringBefore("|").filter { it.isDigit() }
            if (candidate.length == 44) return candidate
        }
        if (text.contains("|")) {
            val parts = text.split("|")
            parts.forEach { part ->
                val digits = part.filter { it.isDigit() }
                if (digits.length == 44) return digits
            }
        }
        return keyRegex.find(text)?.value
    }
}
