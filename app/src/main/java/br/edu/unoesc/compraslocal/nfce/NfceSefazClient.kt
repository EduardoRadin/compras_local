package br.edu.unoesc.compraslocal.nfce

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class NfceSefazClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
) {
    suspend fun fetchReceipt(accessKey: String, consultUrl: String?): NfceReceipt = withContext(Dispatchers.IO) {
        val urls = buildConsultUrls(accessKey, consultUrl)
        var lastError: Exception? = null

        for (url in urls) {
            try {
                val body = download(url)
                return@withContext parseResponse(body, accessKey)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Não foi possível consultar a NFC-e na SEFAZ/SC.")
    }

    fun parseResponse(body: String, accessKey: String): NfceReceipt {
        if (NfceHtmlParser.isCaptchaPage(body)) {
            throw NfceCaptchaException()
        }
        if (body.contains("<nfeProc", ignoreCase = true) || body.contains("<NFe", ignoreCase = true)) {
            return NfceXmlParser.parse(body)
        }
        extractXmlLink(body)?.let { link ->
            val xml = download(link)
            if (!NfceHtmlParser.isCaptchaPage(xml) && xml.contains("<NFe", ignoreCase = true)) {
                return NfceXmlParser.parse(xml)
            }
        }
        if (NfceHtmlParser.canParse(body)) {
            return NfceHtmlParser.parse(body, accessKey)
        }
        throw IllegalStateException(
            "Resposta da SEFAZ não contém dados da nota (${body.length} bytes). " +
                "Use a consulta no site dentro do app.",
        )
    }

    private fun buildConsultUrls(accessKey: String, consultUrl: String?): List<String> {
        val list = mutableListOf<String>()
        consultUrl?.let { list += normalizeUrl(it) }
        list += fallbackUrls(accessKey)
        return list.distinct()
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        trimmed.toHttpUrlOrNull()?.let { return it.toString() }
        return trimmed.replace(" ", "")
    }

    private fun download(url: String): String {
        val host = url.toHttpUrlOrNull()?.host ?: ""
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "pt-BR,pt;q=0.9")
            .apply {
                if (host.contains("sef.sc.gov.br")) {
                    header("Referer", "https://sat.sef.sc.gov.br/nfce/consulta")
                }
            }
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} em $host")
            return response.body?.string() ?: error("Resposta vazia")
        }
    }

    private fun extractXmlLink(html: String): String? {
        val patterns = listOf(
            Regex("""href="([^"]+\.xml[^"]*)""", RegexOption.IGNORE_CASE),
            Regex("""(https?://[^"'\s>]+\.xml[^"'\s>]*)""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(html)?.groupValues?.get(1)?.let { link ->
                if (link.startsWith("http")) link else "https://sat.sef.sc.gov.br$link"
            }
        }
    }

    private fun fallbackUrls(key: String): List<String> {
        return when (key.take(2)) {
            "42" -> listOf(
                "https://sat.sef.sc.gov.br/nfce/consulta?p=$key|2|1|1|",
            )
            "35" -> listOf(
                "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=$key|2|1|1|",
            )
            "41" -> listOf(
                "https://www.fazenda.pr.gov.br/nfce/qrcode?p=$key|2|1|1|",
            )
            else -> listOf(
                "https://sat.sef.sc.gov.br/nfce/consulta?p=$key|2|1|1|",
            )
        }
    }

}
