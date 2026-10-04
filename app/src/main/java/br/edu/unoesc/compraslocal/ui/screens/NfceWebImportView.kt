package br.edu.unoesc.compraslocal.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import br.edu.unoesc.compraslocal.nfce.NfceHtmlParser

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NfceWebImportView(
    accessKey: String,
    consultUrl: String?,
    onImportHtml: (html: String, accessKey: String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Resolva o captcha e toque em Importar nota.") }
    var importing by remember { mutableStateOf(false) }
    val startUrl = remember(accessKey, consultUrl) {
        consultUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?: "https://sat.sef.sc.gov.br/tax.NET/Sat.DFe.NFCe.Web/Consultas/ConsultaPublicaNFe.aspx?chNFe=$accessKey"
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        }
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                status = when {
                    url == null -> "Carregando..."
                    url.contains("captcha", ignoreCase = true) -> "Captcha: preencha e confirme. Depois toque em Importar."
                    else -> "Página carregada. Se a nota aparecer, toque em Importar nota."
                }
            }
        }
        webView.loadUrl(startUrl)
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    fun extractAndImport() {
        if (importing) return
        importing = true
        status = "Lendo página..."
        webView.evaluateJavascript(
            "(function(){ return document.documentElement.outerHTML; })();",
        ) { raw ->
            importing = false
            val html = raw
                ?.trim()
                ?.removeSurrounding("\"")
                ?.replace("\\u003C", "<")
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?: ""
            if (html.length < 500) {
                status = "HTML vazio. Resolva o captcha e tente de novo."
                return@evaluateJavascript
            }
            if (!NfceHtmlParser.looksLikeReceipt(html)) {
                status = "Ainda não é a nota (captcha ou página inicial). Resolva e tente de novo."
                return@evaluateJavascript
            }
            status = "Importando..."
            onImportHtml(html, accessKey)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Fechar")
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
            IconButton(
                onClick = {
                    status = "Recarregando..."
                    webView.reload()
                },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Recarregar")
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { extractAndImport() },
                enabled = !importing,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (importing) "Aguarde..." else "Importar nota")
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(startUrl)))
                },
            ) {
                Text("Navegador")
            }
        }
        Text(
            "O app não lê a página automaticamente (evita travar no captcha).",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        )
    }
}
