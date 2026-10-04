package br.edu.unoesc.compraslocal.nfce

data class NfceItem(
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
)

data class NfceReceipt(
    val accessKey: String,
    val storeName: String,
    val storeCnpj: String?,
    val issuedAt: Long,
    val totalAmount: Double,
    val items: List<NfceItem>,
)
