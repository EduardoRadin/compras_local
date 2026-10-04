package br.edu.unoesc.compraslocal.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import br.edu.unoesc.compraslocal.data.dao.ExportRow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    fun export(context: Context, rows: List<ExportRow>): Intent {
        val file = File(context.getExternalFilesDir(null), "compras_backup_${System.currentTimeMillis()}.csv")
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("pt", "BR"))
        file.bufferedWriter().use { out ->
            out.appendLine("data;loja;produto;categoria;quantidade;preco_unitario;total;chave_nfe")
            rows.forEach { row ->
                out.appendLine(
                    listOf(
                        dateFmt.format(Date(row.purchasedAt)),
                        row.storeName.escape(),
                        (row.productName ?: "").escape(),
                        (row.categoryName ?: "").escape(),
                        row.quantity.toString(),
                        row.unitPrice.toString(),
                        row.totalPrice.toString(),
                        (row.nfeKey ?: "").escape(),
                    ).joinToString(";"),
                )
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "Backup Compras Local")
        }
    }

    private fun String.escape() = replace(";", ",")
}
