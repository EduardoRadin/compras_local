package br.edu.unoesc.compraslocal.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.edu.unoesc.compraslocal.api.ApiClient
import br.edu.unoesc.compraslocal.api.ApiException
import br.edu.unoesc.compraslocal.api.AuthResponse
import br.edu.unoesc.compraslocal.data.AppDatabase
import br.edu.unoesc.compraslocal.data.DatabaseSeeder
import br.edu.unoesc.compraslocal.data.dao.ProductPickerRow
import br.edu.unoesc.compraslocal.data.dao.PurchaseItemRow
import br.edu.unoesc.compraslocal.data.entity.AuthStateEntity
import br.edu.unoesc.compraslocal.data.entity.ShoppingListItemEntity
import br.edu.unoesc.compraslocal.domain.ComparisonInsight
import br.edu.unoesc.compraslocal.domain.PriceAnalytics
import br.edu.unoesc.compraslocal.domain.ReplenishmentEngine
import br.edu.unoesc.compraslocal.domain.ReplenishmentSuggestion
import br.edu.unoesc.compraslocal.export.CsvExporter
import br.edu.unoesc.compraslocal.nfce.NfceCaptchaException
import br.edu.unoesc.compraslocal.nfce.NfceQrParser
import br.edu.unoesc.compraslocal.repository.ComprasRepository
import br.edu.unoesc.compraslocal.sync.SyncManager
import br.edu.unoesc.compraslocal.sync.SyncProgress
import br.edu.unoesc.compraslocal.sync.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = ComprasRepository(db)
    private val syncManager = SyncManager.get(db)

    val purchases = repo.purchases.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lists = repo.activeLists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isLoggedIn: StateFlow<Boolean> = ApiClient.isLoggedIn
    val syncProgress: StateFlow<SyncProgress> = syncManager.progress

    private val _currentListId = MutableStateFlow<Long?>(null)
    val currentListId = _currentListId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _syncLoading = MutableStateFlow(false)
    val syncLoading = _syncLoading.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult = _lastSyncResult.asStateFlow()

    private val _comparisons = MutableStateFlow<List<ComparisonInsight>>(emptyList())
    val comparisons = _comparisons.asStateFlow()

    private val _suggestions = MutableStateFlow<List<ReplenishmentSuggestion>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _historyProductId = MutableStateFlow<Long?>(null)
    val historyProductId = _historyProductId.asStateFlow()

    private val _historyPoints = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val historyPoints = _historyPoints.asStateFlow()

    private val _historyStores = MutableStateFlow<List<String>>(emptyList())
    val historyStores = _historyStores.asStateFlow()

    private val _analyticsProducts = MutableStateFlow<List<ProductPickerRow>>(emptyList())
    val analyticsProducts = _analyticsProducts.asStateFlow()

    private val _selectedProductName = MutableStateFlow<String?>(null)
    val selectedProductName = _selectedProductName.asStateFlow()

    private val _pendingConsultUrl = MutableStateFlow<String?>(null)
    val pendingConsultUrl = _pendingConsultUrl.asStateFlow()

    private val _pendingAccessKey = MutableStateFlow<String?>(null)
    val pendingAccessKey = _pendingAccessKey.asStateFlow()

    private val _useWebImport = MutableStateFlow(false)
    val useWebImport = _useWebImport.asStateFlow()

    private val _lastPrices = MutableStateFlow<Map<String, Double>>(emptyMap())
    val lastPrices = _lastPrices.asStateFlow()

    private val _currentUser = MutableStateFlow<AuthStateEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    init {
        viewModelScope.launch {
            DatabaseSeeder.seedIfNeeded(db)
            val auth = ApiClient.loadAuth()
            _currentUser.value = auth
            refreshInsights()
        }
    }

    fun listItemsFlow(listId: Long) = repo.listItems(listId)

    fun selectList(id: Long) {
        _currentListId.value = id
    }

    fun createList(name: String) = viewModelScope.launch {
        val id = repo.createList(name)
        _currentListId.value = id
        _message.value = "Lista criada"
    }

    fun addItem(description: String, quantity: Double) = viewModelScope.launch {
        if (description.isBlank()) return@launch
        val listId = _currentListId.value ?: lists.value.firstOrNull()?.id
        if (listId == null) {
            val id = repo.createList("Compras da semana")
            _currentListId.value = id
            repo.addListItem(id, description, quantity)
        } else {
            repo.addListItem(listId, description, quantity)
        }
        _message.value = "Item adicionado"
    }

    fun updateListItem(item: ShoppingListItemEntity, description: String, quantity: Double) = viewModelScope.launch {
        if (description.isBlank()) return@launch
        repo.updateListItem(item, description, quantity)
        _message.value = "Item atualizado"
    }

    fun deleteListItem(id: Long) = viewModelScope.launch {
        repo.deleteItem(id)
        _message.value = "Item removido"
    }

    fun clearCheckedItems() = viewModelScope.launch {
        val listId = _currentListId.value ?: return@launch
        repo.deleteCheckedItems(listId)
        _message.value = "Itens comprados removidos"
    }

    fun toggleItem(item: ShoppingListItemEntity) = viewModelScope.launch {
        repo.toggleItem(item)
    }

    fun scanNfce(rawQr: String) = viewModelScope.launch {
        _loading.value = true
        try {
            val (key, url) = NfceQrParser.parse(rawQr)
            _pendingAccessKey.value = key
            _pendingConsultUrl.value = url
            repo.importNfce(key, url)
            _message.value = "Nota importada: ${key.take(8)}..."
            refreshInsights()
        } catch (e: NfceCaptchaException) {
            _useWebImport.value = true
            _message.value = e.message
        } catch (e: Exception) {
            _message.value = e.message ?: "Falha ao importar NFC-e"
        } finally {
            _loading.value = false
        }
    }

    fun importByAccessKey(keyInput: String) = viewModelScope.launch {
        _loading.value = true
        try {
            val trimmed = keyInput.trim()
            val digits = trimmed.filter { it.isDigit() }
            val url = if (trimmed.startsWith("http", ignoreCase = true)) trimmed else null
            val key = when {
                digits.length == 44 -> digits
                url != null -> NfceQrParser.parse(url).first
                else -> error("Informe a chave com 44 dígitos ou cole a URL completa do QR")
            }
            _pendingAccessKey.value = key
            _pendingConsultUrl.value = url
            repo.importNfce(key, url)
            _message.value = "Nota importada com sucesso"
            refreshInsights()
        } catch (e: NfceCaptchaException) {
            _useWebImport.value = true
            _message.value = e.message
        } catch (e: Exception) {
            _message.value = e.message ?: "Falha ao importar"
        } finally {
            _loading.value = false
        }
    }

    fun importFromWebHtml(html: String, accessKey: String) = viewModelScope.launch {
        try {
            repo.importNfceFromHtml(html, accessKey)
            _message.value = "Nota importada da consulta SEFAZ"
            _useWebImport.value = false
            refreshInsights()
        } catch (e: Exception) {
            _message.value = e.message ?: "Falha ao importar HTML"
        }
    }

    fun deletePurchase(purchaseId: Long) = viewModelScope.launch {
        try {
            repo.deletePurchase(purchaseId)
            _message.value = "Compra removida"
            refreshInsights()
        } catch (e: Exception) {
            _message.value = e.message ?: "Não foi possível excluir"
        }
    }

    fun deleteAllDemoPurchases() = viewModelScope.launch {
        try {
            val n = repo.deleteDemoPurchases()
            _message.value = if (n > 0) "$n compra(s) demo removida(s)" else "Nenhuma compra demo encontrada"
            refreshInsights()
        } catch (e: Exception) {
            _message.value = e.message ?: "Falha ao limpar demos"
        }
    }

    fun openWebImport() {
        _useWebImport.value = true
    }

    fun closeWebImport() {
        _useWebImport.value = false
    }

    fun importDemo() = viewModelScope.launch {
        _loading.value = true
        try {
            repo.importNfceOfflineDemo()
            _message.value = "Compra demo salva (offline)"
            refreshInsights()
        } catch (e: Exception) {
            _message.value = e.message
        } finally {
            _loading.value = false
        }
    }

    fun refreshInsights() = viewModelScope.launch {
        _lastPrices.value = repo.lastPricesByNormalizedName()
        _suggestions.value = ReplenishmentEngine.suggest(repo.frequentProducts())
        val products = repo.productsForAnalytics()
        _analyticsProducts.value = products
        val currentId = _historyProductId.value
        val pick = products.firstOrNull { it.productId == currentId }
            ?: products.firstOrNull()
        if (pick != null) {
            loadHistory(pick.productId)
        } else {
            _historyProductId.value = null
            _selectedProductName.value = null
            _historyPoints.value = emptyList()
            _historyStores.value = emptyList()
            _comparisons.value = emptyList()
        }
    }

    fun loadHistory(productId: Long) = viewModelScope.launch {
        val products = _analyticsProducts.value.ifEmpty { repo.productsForAnalytics() }
        _analyticsProducts.value = products
        val meta = products.firstOrNull { it.productId == productId }
        _historyProductId.value = productId
        _selectedProductName.value = meta?.productName
            ?: products.firstOrNull { it.productId == productId }?.productName
        val points = repo.priceHistory(productId)
        _historyPoints.value = points.map { it.purchasedAt to it.unitPrice }
        _historyStores.value = points.map { it.storeName }
        _comparisons.value = PriceAnalytics.compare(repo.compareProduct(productId))
    }

    suspend fun loadPurchaseItems(purchaseId: Long): List<PurchaseItemRow> =
        repo.purchaseItems(purchaseId)

    suspend fun buildExportIntent(): android.content.Intent {
        return CsvExporter.export(getApplication(), repo.exportRows())
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---- Autenticação e sincronização ----

    fun setApiBaseUrl(url: String) {
        ApiClient.setBaseUrl(url)
    }

    fun getApiBaseUrl(): String = ApiClient.getBaseUrl()

    fun login(username: String, password: String) = viewModelScope.launch {
        _syncLoading.value = true
        try {
            val res: AuthResponse = ApiClient.login(username, password)
            val expiresAt = res.token.takeIf { it.isNotBlank() }?.let {
                runCatching {
                    val split = it.split(".")
                    if (split.size == 3) {
                        val payload = String(android.util.Base64.decode(split[1], android.util.Base64.URL_SAFE))
                        val re = """"exp":\s*(\d+)""".toRegex()
                        re.find(payload)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
                    } else null
                }.getOrNull()
            }
            val auth = AuthStateEntity(
                accessToken = res.token,
                refreshToken = res.refreshToken,
                userId = res.user.id,
                username = res.user.username,
                name = res.user.name,
                email = res.user.email,
                expiresAt = expiresAt,
                lastSyncAt = null,
            )
            ApiClient.saveAuth(auth)
            _currentUser.value = auth
            _message.value = "Olá, ${res.user.name}! Login realizado"
        } catch (e: ApiException) {
            _message.value = e.message ?: "Credenciais inválidas"
        } catch (e: Exception) {
            _message.value = e.message ?: "Falha ao conectar"
        } finally {
            _syncLoading.value = false
        }
    }

    fun register(username: String, password: String, name: String, email: String) = viewModelScope.launch {
        _syncLoading.value = true
        try {
            val res = ApiClient.register(username, password, name, email)
            val auth = AuthStateEntity(
                accessToken = res.token,
                refreshToken = res.refreshToken,
                userId = res.user.id,
                username = res.user.username,
                name = res.user.name,
                email = res.user.email,
                expiresAt = null,
                lastSyncAt = null,
            )
            ApiClient.saveAuth(auth)
            _currentUser.value = auth
            _message.value = "Conta criada: ${res.user.username}"
        } catch (e: ApiException) {
            _message.value = e.message ?: "Dados inválidos"
        } catch (e: Exception) {
            _message.value = e.message ?: "Falha ao conectar"
        } finally {
            _syncLoading.value = false
        }
    }

    fun logout() = viewModelScope.launch {
        ApiClient.logout()
        _currentUser.value = null
        _message.value = "Sessão encerrada"
    }

    fun refreshAuthState() = viewModelScope.launch {
        _currentUser.value = ApiClient.loadAuth()
    }

    fun syncAll() = viewModelScope.launch {
        performSync { syncManager.syncAll() }
    }

    fun syncPush() = viewModelScope.launch {
        performSync { syncManager.push() }
    }

    fun syncPull() = viewModelScope.launch {
        performSync { syncManager.pull() }
    }

    private suspend fun performSync(action: suspend () -> SyncResult) {
        if (!ApiClient.isLoggedIn.value) {
            _message.value = "Faça login antes de sincronizar"
            return
        }
        _syncLoading.value = true
        try {
            val result = action()
            _lastSyncResult.value = result
            if (result.isSuccess) {
                val parts = mutableListOf<String>()
                if (result.pushedStores > 0) parts += "${result.pushedStores} mercado(s)"
                if (result.pushedProducts > 0) parts += "${result.pushedProducts} produto(s)"
                if (result.pushedPurchases > 0) parts += "${result.pushedPurchases} compra(s)"
                if (result.pulledMarkets > 0) parts += "${result.pulledMarkets} mercado(s) baixados"
                if (result.pulledProducts > 0) parts += "${result.pulledProducts} produto(s) baixados"
                if (result.pulledHistory > 0) parts += "${result.pulledHistory} registros de preço baixados"
                val msg = if (parts.isEmpty()) "Tudo já sincronizado" else "Sincronizado: ${parts.joinToString(", ")}"
                _message.value = msg
                refreshInsights()
            } else {
                _message.value = "Erro: ${result.error}"
            }
        } catch (e: Exception) {
            _message.value = e.message ?: "Falha na sincronização"
        } finally {
            _syncLoading.value = false
        }
    }

    fun checkApiHealth() = viewModelScope.launch {
        _syncLoading.value = true
        try {
            val ok = syncManager.healthCheck()
            _message.value = if (ok) "API online" else "API indisponível"
        } catch (e: Exception) {
            _message.value = "Falha de conexão"
        } finally {
            _syncLoading.value = false
        }
    }
}

