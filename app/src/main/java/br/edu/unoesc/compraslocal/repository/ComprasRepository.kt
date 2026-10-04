package br.edu.unoesc.compraslocal.repository

import br.edu.unoesc.compraslocal.data.AppDatabase
import br.edu.unoesc.compraslocal.data.entity.ProductEntity
import br.edu.unoesc.compraslocal.data.entity.PurchaseEntity
import br.edu.unoesc.compraslocal.data.entity.PurchaseItemEntity
import br.edu.unoesc.compraslocal.data.entity.ShoppingListEntity
import br.edu.unoesc.compraslocal.data.entity.ShoppingListItemEntity
import br.edu.unoesc.compraslocal.data.entity.StoreEntity
import br.edu.unoesc.compraslocal.domain.CategoryClassifier
import br.edu.unoesc.compraslocal.nfce.NfceReceipt
import br.edu.unoesc.compraslocal.nfce.NfceSefazClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ComprasRepository(private val db: AppDatabase) {
    private val sefaz = NfceSefazClient()

    val activeLists: Flow<List<ShoppingListEntity>> = db.shoppingListDao().observeActiveLists()
    val purchases = db.purchaseDao().observePurchases()

    suspend fun createList(name: String): Long {
        return db.shoppingListDao().insertList(
            ShoppingListEntity(name = name, createdAt = System.currentTimeMillis()),
        )
    }

    fun listItems(listId: Long) = db.shoppingListDao().observeItems(listId)

    suspend fun addListItem(listId: Long, description: String, quantity: Double = 1.0) {
        db.shoppingListDao().insertItem(
            ShoppingListItemEntity(listId = listId, description = description, quantity = quantity),
        )
    }

    suspend fun toggleItem(item: ShoppingListItemEntity) {
        db.shoppingListDao().updateItem(item.copy(isChecked = !item.isChecked))
    }

    suspend fun updateListItem(item: ShoppingListItemEntity, description: String, quantity: Double) {
        db.shoppingListDao().updateItem(
            item.copy(description = description.trim(), quantity = quantity.coerceAtLeast(0.01)),
        )
    }

    suspend fun deleteItem(id: Long) = db.shoppingListDao().deleteItem(id)

    suspend fun deleteCheckedItems(listId: Long) = db.shoppingListDao().deleteCheckedItems(listId)

    suspend fun lastPricesByNormalizedName(): Map<String, Double> {
        val map = linkedMapOf<String, Double>()
        for (row in db.purchaseItemDao().recentItemPrices()) {
            val key = CategoryClassifier.normalize(row.description)
            if (key.isNotBlank() && key !in map) map[key] = row.unitPrice
        }
        return map
    }

    suspend fun deletePurchase(purchaseId: Long) {
        db.purchaseDao().deleteById(purchaseId)
    }

    suspend fun deleteDemoPurchases(): Int = db.purchaseDao().deleteDemoPurchases()

    suspend fun importNfce(accessKey: String, consultUrl: String?): Long {
        val key = accessKey.filter { it.isDigit() }
        if (key.length != 44) error("Chave NFC-e inválida")
        if (db.purchaseDao().findByNfeKey(key) != null) {
            error("Esta nota já foi importada")
        }
        val receipt = sefaz.fetchReceipt(key, consultUrl)
        return persistReceipt(receipt)
    }

    suspend fun importNfceFromHtml(html: String, accessKey: String): Long {
        val key = accessKey.filter { it.isDigit() }
        val receipt = sefaz.parseResponse(html, if (key.length == 44) key else "0".repeat(44))
        if (db.purchaseDao().findByNfeKey(receipt.accessKey) != null) {
            error("Esta nota já foi importada")
        }
        return persistReceipt(receipt)
    }

    suspend fun importNfceOfflineDemo() = persistReceipt(demoReceipt())

    private suspend fun persistReceipt(receipt: NfceReceipt): Long {
        val categoryList = db.categoryDao().getAll()

        val storeId = receipt.storeCnpj?.let { db.storeDao().findByCnpj(it)?.id }
            ?: db.storeDao().insert(StoreEntity(name = receipt.storeName, cnpj = receipt.storeCnpj))

        val purchaseId = db.purchaseDao().insert(
            PurchaseEntity(
                storeId = storeId,
                purchasedAt = receipt.issuedAt,
                nfeKey = receipt.accessKey,
                totalAmount = receipt.totalAmount,
            ),
        )

        val purchaseLines = mutableListOf<PurchaseItemEntity>()
        for (item in receipt.items) {
            val normalized = CategoryClassifier.normalize(item.description)
            var product = db.productDao().findByNormalizedName(normalized)
            if (product == null) {
                val catId = CategoryClassifier.classify(item.description, categoryList)
                val id = db.productDao().insert(
                    ProductEntity(
                        name = item.description,
                        normalizedName = normalized,
                        categoryId = catId,
                    ),
                )
                product = ProductEntity(id = id, name = item.description, normalizedName = normalized, categoryId = catId)
            } else if (product.categoryId == null) {
                val catId = CategoryClassifier.classify(item.description, categoryList)
                if (catId != null) {
                    product = product.copy(categoryId = catId, dirty = true)
                    db.productDao().update(product)
                }
            }

            purchaseLines += PurchaseItemEntity(
                purchaseId = purchaseId,
                productId = product.id,
                description = item.description,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                totalPrice = item.totalPrice,
            )
        }
        db.purchaseItemDao().insertAll(purchaseLines)
        matchOpenListItems(purchaseLines)
        return purchaseId
    }

    private suspend fun matchOpenListItems(purchased: List<PurchaseItemEntity>) {
        val active = db.shoppingListDao().observeActiveLists().first()
        if (active.isEmpty()) return
        val listId = active.first().id
        val openItems = db.shoppingListDao().observeItems(listId).first().filter { !it.isChecked }

        for (open in openItems) {
            val match = purchased.firstOrNull {
                CategoryClassifier.normalize(it.description).contains(
                    CategoryClassifier.normalize(open.description),
                ) || CategoryClassifier.normalize(open.description).contains(
                    CategoryClassifier.normalize(it.description),
                )
            } ?: continue
            db.shoppingListDao().updateItem(
                open.copy(isChecked = true, matchedPurchaseItemId = match.id, productId = match.productId),
            )
        }
    }

    suspend fun frequentProducts() = db.purchaseItemDao().frequentProducts()

    suspend fun productsForAnalytics() = db.purchaseItemDao().productsForAnalytics()

    suspend fun purchaseItems(purchaseId: Long) = db.purchaseItemDao().itemsForPurchase(purchaseId)

    suspend fun compareProduct(productId: Long) = db.purchaseItemDao().compareStores(productId)

    suspend fun priceHistory(productId: Long) = db.purchaseItemDao().priceHistory(productId)

    suspend fun exportRows() = db.exportDao().exportRows()

    private fun demoReceipt() = NfceReceipt(
        accessKey = System.currentTimeMillis().toString().padStart(44, '9'),
        storeName = "Mercado Demo",
        storeCnpj = "12345678000199",
        issuedAt = System.currentTimeMillis(),
        totalAmount = 42.90,
        items = listOf(
            br.edu.unoesc.compraslocal.nfce.NfceItem("Leite integral 1L", 2.0, 4.99, 9.98),
            br.edu.unoesc.compraslocal.nfce.NfceItem("Detergente líquido", 1.0, 2.49, 2.49),
            br.edu.unoesc.compraslocal.nfce.NfceItem("Peito de frango kg", 1.2, 18.90, 22.68),
        ),
    )
}
