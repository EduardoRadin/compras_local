package br.edu.unoesc.compraslocal.sync

import br.edu.unoesc.compraslocal.api.ApiClient
import br.edu.unoesc.compraslocal.api.AddCartItemRequest
import br.edu.unoesc.compraslocal.api.ApiException
import br.edu.unoesc.compraslocal.api.CreateMarketRequest
import br.edu.unoesc.compraslocal.api.CreateProductRequest
import br.edu.unoesc.compraslocal.api.IdResponse
import br.edu.unoesc.compraslocal.api.MarketDto
import br.edu.unoesc.compraslocal.api.PatchCartItemRequest
import br.edu.unoesc.compraslocal.api.ProductDto
import br.edu.unoesc.compraslocal.data.AppDatabase
import br.edu.unoesc.compraslocal.data.entity.CategoryEntity
import br.edu.unoesc.compraslocal.data.entity.ProductEntity
import br.edu.unoesc.compraslocal.data.entity.PurchaseEntity
import br.edu.unoesc.compraslocal.data.entity.PurchaseItemEntity
import br.edu.unoesc.compraslocal.data.entity.StoreEntity
import br.edu.unoesc.compraslocal.data.entity.SyncLogEntity
import br.edu.unoesc.compraslocal.domain.CategoryClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Response

data class SyncProgress(
    val running: Boolean = false,
    val phase: String? = null,
    val current: Int = 0,
    val total: Int = 0,
    val lastError: String? = null,
    val lastRunAt: Long? = null,
    val lastSuccess: Boolean = false,
)

data class SyncResult(
    val pushedStores: Int = 0,
    val pushedProducts: Int = 0,
    val pushedPurchases: Int = 0,
    val pulledMarkets: Int = 0,
    val pulledProducts: Int = 0,
    val pulledHistory: Int = 0,
    val error: String? = null,
) {
    val isSuccess get() = error == null
}

class SyncManager(private val db: AppDatabase) {

    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: SyncManager? = null
        fun get(db: AppDatabase): SyncManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncManager(db).also { INSTANCE = it }
            }
    }

    private fun setProgress(
        running: Boolean? = null,
        phase: String? = null,
        current: Int? = null,
        total: Int? = null,
        lastError: String? = null,
    ) {
        val cur = _progress.value
        _progress.value = cur.copy(
            running = running ?: cur.running,
            phase = phase ?: cur.phase,
            current = current ?: cur.current,
            total = total ?: cur.total,
            lastError = lastError ?: cur.lastError,
        )
    }

    private suspend fun <T> call(block: suspend () -> Response<T>): T {
        val res = block()
        return ApiClient.parseResponse(res)
    }

    suspend fun healthCheck(): Boolean {
        return try {
            call { ApiClient.getService().health() }.status == "ok"
        } catch (_: Exception) {
            false
        }
    }

    suspend fun syncAll(): SyncResult {
        val start = System.currentTimeMillis()
        val logId = db.syncDao().insertLog(
            SyncLogEntity(
                startedAt = start,
                direction = "BIDIRECTIONAL",
                status = "RUNNING",
            ),
        )
        setProgress(running = true, phase = "Preparando", current = 0, total = 6, lastError = null)
        val result = runCatching {
            val push = pushInternal()
            val pull = pullInternal()
            SyncResult(
                pushedStores = push.pushedStores,
                pushedProducts = push.pushedProducts,
                pushedPurchases = push.pushedPurchases,
                pulledMarkets = pull.pulledMarkets,
                pulledProducts = pull.pulledProducts,
                pulledHistory = pull.pulledHistory,
                error = push.error ?: pull.error,
            )
        }.getOrElse { SyncResult(error = it.message ?: "Falha na sincronização") }

        runCatching {
            val end = System.currentTimeMillis()
            val base = SyncLogEntity(startedAt = start, direction = "BIDIRECTIONAL", status = "RUNNING")
            val updated = base.copy(
                id = logId,
                finishedAt = end,
                status = if (result.isSuccess) "SUCCESS" else "FAILED",
                pushedStores = result.pushedStores,
                pushedProducts = result.pushedProducts,
                pushedPurchases = result.pushedPurchases,
                pulledMarkets = result.pulledMarkets,
                pulledProducts = result.pulledProducts,
                pulledHistory = result.pulledHistory,
                errorMessage = result.error,
            )
            db.syncDao().updateLog(updated)
        }

        runCatching {
            val auth = db.authDao().get()
            if (auth != null && result.isSuccess) {
                db.authDao().upsert(auth.copy(lastSyncAt = System.currentTimeMillis()))
            }
        }

        setProgress(
            running = false,
            phase = null,
            current = 6,
            total = 6,
            lastError = result.error,
        )
        _progress.value = _progress.value.copy(
            lastRunAt = System.currentTimeMillis(),
            lastSuccess = result.isSuccess,
        )
        return result
    }

    suspend fun push(): SyncResult {
        val start = System.currentTimeMillis()
        val logId = db.syncDao().insertLog(
            SyncLogEntity(startedAt = start, direction = "PUSH", status = "RUNNING"),
        )
        setProgress(running = true, phase = "Preparando envio", current = 0, total = 3, lastError = null)
        val result = runCatching { pushInternal() }
            .getOrElse { SyncResult(error = it.message ?: "Falha no envio") }
        runCatching {
            db.syncDao().updateLog(
                SyncLogEntity(
                    id = logId,
                    startedAt = start,
                    direction = "PUSH",
                    finishedAt = System.currentTimeMillis(),
                    status = if (result.isSuccess) "SUCCESS" else "FAILED",
                    pushedStores = result.pushedStores,
                    pushedProducts = result.pushedProducts,
                    pushedPurchases = result.pushedPurchases,
                    errorMessage = result.error,
                ),
            )
        }
        setProgress(
            running = false,
            phase = null,
            current = 3,
            total = 3,
            lastError = result.error,
        )
        _progress.value = _progress.value.copy(
            lastRunAt = System.currentTimeMillis(),
            lastSuccess = result.isSuccess,
        )
        return result
    }

    suspend fun pull(): SyncResult {
        val start = System.currentTimeMillis()
        val logId = db.syncDao().insertLog(
            SyncLogEntity(startedAt = start, direction = "PULL", status = "RUNNING"),
        )
        setProgress(running = true, phase = "Preparando recebimento", current = 0, total = 3, lastError = null)
        val result = runCatching { pullInternal() }
            .getOrElse { SyncResult(error = it.message ?: "Falha no recebimento") }
        runCatching {
            db.syncDao().updateLog(
                SyncLogEntity(
                    id = logId,
                    startedAt = start,
                    direction = "PULL",
                    finishedAt = System.currentTimeMillis(),
                    status = if (result.isSuccess) "SUCCESS" else "FAILED",
                    pulledMarkets = result.pulledMarkets,
                    pulledProducts = result.pulledProducts,
                    pulledHistory = result.pulledHistory,
                    errorMessage = result.error,
                ),
            )
        }
        setProgress(
            running = false,
            phase = null,
            current = 3,
            total = 3,
            lastError = result.error,
        )
        _progress.value = _progress.value.copy(
            lastRunAt = System.currentTimeMillis(),
            lastSuccess = result.isSuccess,
        )
        return result
    }

    private suspend fun pushInternal(): SyncResult {
        var stores = 0
        var products = 0
        var purchases = 0

        setProgress(phase = "Enviando mercados", current = 1, total = 3)
        stores += pushStores()

        setProgress(phase = "Enviando produtos", current = 2, total = 3)
        products += pushProducts()

        setProgress(phase = "Enviando compras", current = 3, total = 3)
        purchases += pushPurchases()

        return SyncResult(
            pushedStores = stores,
            pushedProducts = products,
            pushedPurchases = purchases,
        )
    }

    private suspend fun pushStores(): Int {
        val dirtyStores = db.storeDao().findDirty()
        if (dirtyStores.isEmpty()) return 0
        var count = 0
        for (store in dirtyStores) {
            val existingRemote = findRemoteMarketByName(store.name)
            if (existingRemote != null) {
                db.storeDao().update(
                    store.copy(
                        remoteId = existingRemote,
                        dirty = false,
                        lastSyncAt = System.currentTimeMillis(),
                    ),
                )
                count++
                continue
            }
            runCatching {
                val market: MarketDto = call {
                    ApiClient.getService().createMarket(CreateMarketRequest(name = store.name))
                }
                db.storeDao().update(
                    store.copy(
                        remoteId = market.id,
                        dirty = false,
                        lastSyncAt = System.currentTimeMillis(),
                    ),
                )
                count++
            }
        }
        return count
    }

    private suspend fun findRemoteMarketByName(name: String): String? {
        return runCatching {
            val all: List<MarketDto> = call { ApiClient.getService().listMarkets() }
            all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
        }.getOrNull()
    }

    private val dummyMarketId: String = "00000000-0000-0000-0000-000000000000"

    private suspend fun pushProducts(): Int {
        val allCategories = db.categoryDao().getAll()
        val categoriesById = allCategories.associateBy { it.id }
        val dirty = db.productDao().findDirty()
        if (dirty.isEmpty()) return 0
        var count = 0
        for (product in dirty) {
            val unit = product.unit?.ifBlank { null } ?: inferUnit(product.name)
            val category = (product.categoryId?.let { categoriesById[it]?.name } ?: "Outros").take(80)
            val res: IdResponse = call {
                ApiClient.getService().createProduct(
                    CreateProductRequest(
                        name = product.name.take(200),
                        brand = product.brand?.take(100),
                        category = category,
                        unit = unit,
                        imageUrl = product.imageUrl,
                    ),
                )
            }
            db.productDao().update(
                product.copy(
                    remoteId = res.id,
                    dirty = false,
                    unit = unit,
                    lastSyncAt = System.currentTimeMillis(),
                ),
            )
            count++
        }
        return count
    }

    private suspend fun pushPurchases(): Int {
        val pending = db.purchaseDao().findNotSynced()
        if (pending.isEmpty()) return 0
        var count = 0
        val allCategories = db.categoryDao().getAll()
        for (purchase in pending) {
            runCatching {
                db.purchaseDao().update(
                    purchase.copy(
                        lastSyncAttemptAt = System.currentTimeMillis(),
                        syncError = null,
                    ),
                )
            }
            try {
                val store = db.storeDao().findById(purchase.storeId)
                    ?: throw ApiException("Mercado da compra ${purchase.id} não encontrado")
                val storeRemote = ensureStoreRemote(store)
                val items = db.purchaseItemDao().findByPurchaseId(purchase.id)
                for (item in items) {
                    if (item.synced && item.remoteCartItemId != null) continue
                    val productId = item.productId
                    val productRemoteId: String = if (productId != null) {
                        val product = db.productDao().findById(productId)
                            ?: throw ApiException("Produto da item ${item.id} não encontrado")
                        ensureProductRemote(product, allCategories)
                    } else {
                        createOrGetProductForItem(item.description, allCategories)
                    }
                    val cartRes: IdResponse = call {
                        ApiClient.getService().addCartItem(
                            AddCartItemRequest(productId = productRemoteId),
                        )
                    }
                    val qty = item.quantity.coerceAtLeast(0.001)
                    val price = item.unitPrice.coerceAtLeast(0.01)
                    call {
                        ApiClient.getService().patchCartItem(
                            id = cartRes.id,
                            req = PatchCartItemRequest(
                                purchased = true,
                                marketId = storeRemote,
                                quantity = qty,
                                unitPrice = price,
                            ),
                        )
                    }
                    db.purchaseItemDao().update(
                        item.copy(remoteCartItemId = cartRes.id, synced = true),
                    )
                }
                db.purchaseDao().update(
                    purchase.copy(
                        synced = true,
                        lastSyncAttemptAt = System.currentTimeMillis(),
                        syncError = null,
                    ),
                )
                count++
            } catch (e: Exception) {
                val msg = e.message ?: "Erro desconhecido"
                runCatching {
                    db.purchaseDao().update(
                        purchase.copy(
                            lastSyncAttemptAt = System.currentTimeMillis(),
                            syncError = msg,
                        ),
                    )
                }
                // Continua com as próximas compras
            }
        }
        return count
    }

    private suspend fun ensureStoreRemote(store: StoreEntity): String {
        if (store.remoteId != null) return store.remoteId
        val byName = findRemoteMarketByName(store.name)
        if (byName != null) {
            db.storeDao().update(
                store.copy(
                    remoteId = byName,
                    dirty = false,
                    lastSyncAt = System.currentTimeMillis(),
                ),
            )
            return byName
        }
        val created = runCatching {
            val market: MarketDto = call {
                ApiClient.getService().createMarket(CreateMarketRequest(name = store.name))
            }
            db.storeDao().update(
                store.copy(
                    remoteId = market.id,
                    dirty = false,
                    lastSyncAt = System.currentTimeMillis(),
                ),
            )
            market.id
        }
        return created.getOrElse { dummyMarketId }
    }

    private suspend fun ensureProductRemote(
        product: ProductEntity,
        allCategories: List<CategoryEntity>,
    ): String {
        if (product.remoteId != null) return product.remoteId
        val categoriesById = allCategories.associateBy { it.id }
        val unit = product.unit?.ifBlank { null } ?: inferUnit(product.name)
        val category = (product.categoryId?.let { categoriesById[it]?.name } ?: "Outros").take(80)
        val res: IdResponse = call {
            ApiClient.getService().createProduct(
                CreateProductRequest(
                    name = product.name.take(200),
                    brand = product.brand?.take(100),
                    category = category,
                    unit = unit,
                    imageUrl = product.imageUrl,
                ),
            )
        }
        db.productDao().update(
            product.copy(
                remoteId = res.id,
                dirty = false,
                unit = unit,
                lastSyncAt = System.currentTimeMillis(),
            ),
        )
        return res.id
    }

    private suspend fun createOrGetProductForItem(
        description: String,
        allCategories: List<CategoryEntity>,
    ): String {
        val normalized = CategoryClassifier.normalize(description)
        val existing = db.productDao().findByNormalizedName(normalized)
        if (existing != null) {
            existing.remoteId?.let { return it }
            return ensureProductRemote(existing, allCategories)
        }
        val catId = CategoryClassifier.classify(description, allCategories)
        val unit = inferUnit(description)
        val id = db.productDao().insert(
            ProductEntity(
                name = description,
                normalizedName = normalized,
                categoryId = catId,
                unit = unit,
                dirty = true,
            ),
        )
        val product = db.productDao().findById(id)
            ?: error("Produto não encontrado após inserção")
        return ensureProductRemote(product, allCategories)
    }

    private fun inferUnit(name: String): String {
        val n = name.lowercase()
        return when {
            " kg" in n || "kg " in n || "/kg" in n || " quilo" in n || "kilo" in n -> "kg"
            " g " in n || " grama" in n || Regex("""\dg\b""")
                .containsMatchIn(n) || n.endsWith(" g") -> "g"
            " l " in n || " litro" in n || "litra" in n || "/l" in n -> "l"
            " ml" in n || "mililitro" in n -> "ml"
            " unid" in n || "unidade" in n || " cada" in n -> "un"
            else -> "un"
        }
    }

    private suspend fun pullInternal(): SyncResult {
        var markets = 0
        var products = 0
        var history = 0

        setProgress(phase = "Recebendo mercados", current = 1, total = 3)
        markets += pullMarkets()

        setProgress(phase = "Recebendo produtos", current = 2, total = 3)
        products += pullProducts()

        setProgress(phase = "Recebendo histórico", current = 3, total = 3)
        history += pullHistory()

        return SyncResult(
            pulledMarkets = markets,
            pulledProducts = products,
            pulledHistory = history,
        )
    }

    private suspend fun pullMarkets(): Int {
        val remote: List<MarketDto> = call { ApiClient.getService().listMarkets() }
        var count = 0
        for (market in remote) {
            val existingByRemote = db.storeDao().findByRemoteId(market.id)
            if (existingByRemote != null) {
                val updated = existingByRemote.copy(
                    name = market.name,
                    dirty = false,
                    lastSyncAt = System.currentTimeMillis(),
                )
                if (updated != existingByRemote) {
                    db.storeDao().update(updated)
                    count++
                }
                continue
            }
            val byName = db.storeDao().findByName(market.name)
            if (byName != null) {
                db.storeDao().update(
                    byName.copy(
                        remoteId = market.id,
                        dirty = false,
                        lastSyncAt = System.currentTimeMillis(),
                    ),
                )
                count++
                continue
            }
            db.storeDao().insert(
                StoreEntity(
                    name = market.name,
                    remoteId = market.id,
                    dirty = false,
                    lastSyncAt = System.currentTimeMillis(),
                ),
            )
            count++
        }
        return count
    }

    private suspend fun pullProducts(): Int {
        val remote: List<ProductDto> = call { ApiClient.getService().listProducts() }
        var count = 0
        for (p in remote) {
            val existingByRemote = db.productDao().findByRemoteId(p.id)
            val catId = p.category?.let { catName ->
                val allCats = db.categoryDao().getAll()
                val exists = allCats.firstOrNull { it.name.equals(catName, ignoreCase = true) }
                if (exists != null) {
                    exists.id
                } else {
                    val newCat = CategoryEntity(name = catName.take(80), keywords = catName)
                    db.categoryDao().insertAll(listOf(newCat))
                    db.categoryDao().findByName(catName)?.id
                }
            }
            if (existingByRemote != null) {
                val updated = existingByRemote.copy(
                    name = p.name,
                    brand = p.brand,
                    unit = p.unit,
                    imageUrl = p.imageUrl,
                    categoryId = catId ?: existingByRemote.categoryId,
                    dirty = false,
                    lastSyncAt = System.currentTimeMillis(),
                )
                if (updated != existingByRemote) {
                    db.productDao().update(updated)
                    count++
                }
                continue
            }
            val normalized = CategoryClassifier.normalize(p.name)
            val byNormalized = db.productDao().findByNormalizedName(normalized)
            if (byNormalized != null) {
                db.productDao().update(
                    byNormalized.copy(
                        remoteId = p.id,
                        brand = p.brand ?: byNormalized.brand,
                        unit = p.unit ?: byNormalized.unit,
                        imageUrl = p.imageUrl ?: byNormalized.imageUrl,
                        categoryId = catId ?: byNormalized.categoryId,
                        dirty = false,
                        lastSyncAt = System.currentTimeMillis(),
                    ),
                )
                count++
                continue
            }
            db.productDao().insert(
                ProductEntity(
                    name = p.name,
                    normalizedName = normalized,
                    categoryId = catId,
                    brand = p.brand,
                    unit = p.unit ?: "un",
                    imageUrl = p.imageUrl,
                    remoteId = p.id,
                    dirty = false,
                    lastSyncAt = System.currentTimeMillis(),
                ),
            )
            count++
        }
        return count
    }

    private suspend fun pullHistory(): Int {
        val remote = call { ApiClient.getService().getHistory() }
        if (remote.isEmpty()) return 0
        val allCategories = db.categoryDao().getAll()
        var count = 0
        val byObservedAt = remote.groupBy { it.price.observedAt }
        for ((observedAt, entries) in byObservedAt) {
            val millis = runCatching {
                java.time.Instant.parse(observedAt).toEpochMilli()
            }.getOrNull() ?: System.currentTimeMillis()
            val entriesByMarket = entries.groupBy { it.price.market }
            for ((marketName, group) in entriesByMarket) {
                val storeId = findOrInsertStore(marketName)
                val totalAmount = group.sumOf { it.price.amount }
                val purchaseId = db.purchaseDao().insert(
                    PurchaseEntity(
                        storeId = storeId,
                        purchasedAt = millis,
                        totalAmount = totalAmount,
                        synced = true,
                        nfeKey = null,
                    ),
                )
                val items = group.mapNotNull { entry ->
                    val normalized = CategoryClassifier.normalize(entry.name)
                    val productId = findOrInsertProduct(entry, normalized, allCategories)
                    PurchaseItemEntity(
                        purchaseId = purchaseId,
                        productId = productId,
                        description = entry.name,
                        quantity = 1.0,
                        unitPrice = entry.price.amount,
                        totalPrice = entry.price.amount,
                        synced = true,
                    )
                }
                if (items.isNotEmpty()) {
                    db.purchaseItemDao().insertAll(items)
                    count += items.size
                }
            }
        }
        return count
    }

    private suspend fun findOrInsertStore(name: String): Long {
        db.storeDao().findByName(name)?.let { return it.id }
        return db.storeDao().insert(
            StoreEntity(
                name = name,
                dirty = false,
                lastSyncAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun findOrInsertProduct(
        entry: br.edu.unoesc.compraslocal.api.HistoryEntryDto,
        normalized: String,
        allCategories: List<CategoryEntity>,
    ): Long? {
        db.productDao().findByNormalizedName(normalized)?.let { return it.id }
        val catId = CategoryClassifier.classify(entry.name, allCategories)
        val id = db.productDao().insert(
            ProductEntity(
                name = entry.name,
                normalizedName = normalized,
                categoryId = catId,
                brand = entry.brand,
                unit = entry.unit ?: "un",
                imageUrl = entry.imageUrl,
                dirty = false,
                lastSyncAt = System.currentTimeMillis(),
            ),
        )
        return id.takeIf { it > 0 }
    }
}
