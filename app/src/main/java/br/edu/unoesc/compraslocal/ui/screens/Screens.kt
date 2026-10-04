package br.edu.unoesc.compraslocal.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import br.edu.unoesc.compraslocal.data.dao.ProductPickerRow
import br.edu.unoesc.compraslocal.data.dao.PurchaseItemRow
import br.edu.unoesc.compraslocal.data.dao.PurchaseWithStore
import br.edu.unoesc.compraslocal.data.entity.ShoppingListItemEntity
import br.edu.unoesc.compraslocal.domain.CategoryClassifier
import br.edu.unoesc.compraslocal.domain.ComparisonInsight
import br.edu.unoesc.compraslocal.domain.PriceSignal
import br.edu.unoesc.compraslocal.domain.ReplenishmentSuggestion
import br.edu.unoesc.compraslocal.ui.theme.AmberAverage
import br.edu.unoesc.compraslocal.ui.theme.BrandGreen
import br.edu.unoesc.compraslocal.ui.theme.BrandMint
import br.edu.unoesc.compraslocal.ui.theme.GreenCheap
import br.edu.unoesc.compraslocal.ui.theme.PageBackground
import br.edu.unoesc.compraslocal.ui.theme.RedExpensive
import br.edu.unoesc.compraslocal.ui.theme.SoftBorder
import br.edu.unoesc.compraslocal.viewmodel.MainViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class AppTab { Home, Prices, Lists, Profile }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComprasAppShell(vm: MainViewModel) {
    var tab by remember { mutableStateOf(AppTab.Lists) }
    var showScan by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val loading by vm.loading.collectAsState()
    val useWeb by vm.useWebImport.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    if (showScan || useWeb) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = PageBackground,
            topBar = {
                if (!useWeb) {
                    TopAppBar(
                        title = { Text("Ler NFC-e", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { showScan = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                    )
                }
            },
            floatingActionButton = {
                if (!useWeb) {
                    FloatingActionButton(
                        onClick = { vm.importDemo() },
                        containerColor = BrandGreen,
                        contentColor = Color.White,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Demo offline")
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                ScanScreen(vm)
                if (loading && !useWeb) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandGreen)
                    }
                }
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = PageBackground,
        topBar = {
            BrandTopBar(
                onScan = { showScan = true },
                onProfile = { tab = AppTab.Profile },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandGreen,
                    selectedTextColor = BrandGreen,
                    indicatorColor = BrandMint,
                    unselectedIconColor = Color(0xFF98A2B3),
                    unselectedTextColor = Color(0xFF98A2B3),
                )
                NavigationBarItem(
                    selected = tab == AppTab.Home,
                    onClick = { tab = AppTab.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Início") },
                    label = { Text("Início") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == AppTab.Prices,
                    onClick = { tab = AppTab.Prices },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Preços") },
                    label = { Text("Preços") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == AppTab.Lists,
                    onClick = { tab = AppTab.Lists },
                    icon = { Icon(Icons.Default.List, contentDescription = "Minhas listas") },
                    label = { Text("Minhas listas") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = tab == AppTab.Profile,
                    onClick = { tab = AppTab.Profile },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Perfil") },
                    label = { Text("Perfil") },
                    colors = colors,
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                AppTab.Home -> HomeScreen(
                    vm = vm,
                    onOpenList = { tab = AppTab.Lists },
                    onOpenPrices = { tab = AppTab.Prices },
                    onScan = { showScan = true },
                )
                AppTab.Prices -> AnalyticsScreen(vm)
                AppTab.Lists -> ListsScreen(vm)
                AppTab.Profile -> BackupScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrandTopBar(onScan: () -> Unit, onProfile: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "EconomizAe",
                        fontWeight = FontWeight.Bold,
                        color = BrandGreen,
                        fontSize = 20.sp,
                        lineHeight = 22.sp,
                    )
                    Text(
                        "Consulte. Compare. Economize.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF667085),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onScan) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Ler NFC-e", tint = BrandGreen)
            }
            IconButton(onClick = onProfile) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.dp, SoftBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Perfil", tint = BrandGreen)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
    )
}

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenList: () -> Unit,
    onOpenPrices: () -> Unit,
    onScan: () -> Unit,
) {
    val purchases by vm.purchases.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val lists by vm.lists.collectAsState()
    val currentId by vm.currentListId.collectAsState()
    var purchaseToDelete by remember { mutableStateOf<PurchaseWithStore?>(null) }
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    val listId = currentId ?: lists.firstOrNull()?.id ?: 0L
    val listItems by vm.listItemsFlow(listId).collectAsState(initial = emptyList())
    val checked = listItems.count { it.isChecked }

    purchaseToDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { purchaseToDelete = null },
            title = { Text("Excluir compra?") },
            text = { Text("${p.storeName}\n${dateFmt.format(Date(p.purchasedAt))} • ${currency.format(p.totalAmount)}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePurchase(p.id)
                    purchaseToDelete = null
                }) { Text("Excluir", color = RedExpensive) }
            },
            dismissButton = {
                TextButton(onClick = { purchaseToDelete = null }) { Text("Cancelar") }
            },
        )
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            PromoBanner(
                title = "Preços do dia a dia",
                subtitle = "Compare o histórico das suas notas e ache o menor preço.",
                action = "Ver preços",
                onClick = onOpenPrices,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard("Ler NFC-e", "Importar nota", Icons.Default.QrCodeScanner, Modifier.weight(1f), onScan)
                QuickActionCard("Minha lista", "$checked/${listItems.size} itens", Icons.Default.List, Modifier.weight(1f), onOpenList)
            }
        }
        item { SectionTitle("Reposição sugerida") }
        if (suggestions.isEmpty()) {
            item { EmptyHint("Importe notas NFC-e para receber sugestões de reposição.") }
        } else {
            items(suggestions, key = { it.productId }) { s ->
                SuggestionCard(s) { vm.addItem(s.productName, 1.0) }
            }
        }
        item { SectionTitle("Últimas compras") }
        if (purchases.isEmpty()) {
            item { EmptyHint("Nenhuma compra registrada ainda.") }
        } else {
            items(purchases) { p ->
                PurchaseCard(
                    p = p,
                    currency = currency,
                    dateFmt = dateFmt,
                    onDelete = { purchaseToDelete = p },
                    fetchItems = { vm.loadPurchaseItems(it) },
                )
            }
        }
    }
}

@Composable
fun ListsScreen(vm: MainViewModel) {
    val lists by vm.lists.collectAsState()
    val currentId by vm.currentListId.collectAsState()
    val lastPrices by vm.lastPrices.collectAsState()
    var newItem by remember { mutableStateOf("") }
    var newQty by remember { mutableStateOf("1") }
    var adding by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf("") }
    var creatingList by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingListItemEntity?>(null) }
    val listId = currentId ?: lists.firstOrNull()?.id ?: 0L
    val items by vm.listItemsFlow(listId).collectAsState(initial = emptyList())
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    LaunchedEffect(lists, currentId) {
        if (currentId == null && lists.isNotEmpty()) vm.selectList(lists.first().id)
    }

    editingItem?.let { item ->
        EditListItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { desc, qty ->
                vm.updateListItem(item, desc, qty)
                editingItem = null
            },
            onDelete = {
                vm.deleteListItem(item.id)
                editingItem = null
            },
        )
    }

    val remaining = items.filter { !it.isChecked }
    val done = items.filter { it.isChecked }
    val progress = if (items.isEmpty()) 0f else done.size / items.size.toFloat()
    val estimate = remaining.sumOf { item ->
        (lookupPrice(item.description, lastPrices) ?: 0.0) * item.quantity
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Minha lista", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (items.isEmpty()) "Comece adicionando o que falta em casa"
                    else "${done.size} de ${items.size} conferidos",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF667085),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Nova lista") },
                        onClick = {
                            menuOpen = false
                            creatingList = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Limpar itens comprados") },
                        onClick = {
                            menuOpen = false
                            vm.clearCheckedItems()
                        },
                    )
                }
            }
        }

        if (lists.size > 1) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lists, key = { it.id }) { list ->
                    FilterChip(
                        selected = list.id == listId,
                        onClick = { vm.selectList(list.id) },
                        label = { Text(list.name) },
                    )
                }
            }
        } else if (lists.isNotEmpty()) {
            Text(lists.first().name, style = MaterialTheme.typography.labelMedium, color = BrandGreen)
        }

        if (creatingList) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newList,
                onValueChange = { newList = it },
                label = { Text("Nome da lista") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        if (newList.isNotBlank()) {
                            vm.createList(newList)
                            newList = ""
                            creatingList = false
                        }
                    }) { Icon(Icons.Default.Check, contentDescription = "Criar") }
                },
            )
        }

        if (items.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                color = BrandGreen,
                trackColor = BrandMint,
            )
            if (estimate > 0) {
                Text(
                    "Estimativa do que falta: ${currency.format(estimate)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandGreen,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        AddItemRow(
            adding = adding,
            newItem = newItem,
            newQty = newQty,
            onToggle = { adding = !adding },
            onItemChange = { newItem = it },
            onQtyChange = { newQty = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
            onConfirm = {
                val qty = newQty.replace(',', '.').toDoubleOrNull() ?: 1.0
                if (newItem.isNotBlank()) {
                    vm.addItem(newItem, qty)
                    newItem = ""
                    newQty = "1"
                    adding = false
                }
            },
            onCancel = {
                adding = false
                newItem = ""
                newQty = "1"
            },
        )

        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(remaining + done, key = { it.id }) { item ->
                ListItemRow(
                    item = item,
                    lastPrice = lookupPrice(item.description, lastPrices),
                    currency = currency,
                    onToggle = { vm.toggleItem(item) },
                    onEdit = { editingItem = item },
                )
            }
            if (items.isEmpty()) {
                item { EmptyHint("Sua lista está vazia. Toque em Adicionar item.") }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AddItemRow(
    adding: Boolean,
    newItem: String,
    newQty: String,
    onToggle: () -> Unit,
    onItemChange: (String) -> Unit,
    onQtyChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!adding) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(BrandMint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Adicionar item", color = BrandGreen, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = onItemChange,
                    label = { Text("Produto") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newQty,
                        onValueChange = onQtyChange,
                        label = { Text("Qtd") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar")
                    }
                    IconButton(onClick = onConfirm) {
                        Icon(Icons.Default.Check, contentDescription = "Incluir", tint = BrandGreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditListItemDialog(
    item: ShoppingListItemEntity,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit,
    onDelete: () -> Unit,
) {
    var desc by remember(item.id) { mutableStateOf(item.description) }
    var qty by remember(item.id) { mutableStateOf(item.quantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Produto") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                    label = { Text("Quantidade") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.replace(',', '.').toDoubleOrNull() ?: 1.0
                onSave(desc, q)
            }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Excluir", color = RedExpensive) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
fun ScanScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val loading by vm.loading.collectAsState()
    val useWeb by vm.useWebImport.collectAsState()
    val pendingUrl by vm.pendingConsultUrl.collectAsState()
    val pendingKey by vm.pendingAccessKey.collectAsState()
    var scanToken by remember { mutableStateOf(0) }
    var manualInput by remember { mutableStateOf("") }
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCamera) launcher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(loading) {
        if (!loading) scanToken++
    }

    if (useWeb) {
        val key = pendingKey.orEmpty()
        if (key.length != 44) {
            Column(Modifier.padding(16.dp)) {
                Text("Chave NFC-e inválida. Volte e informe os 44 dígitos.")
                Button(onClick = { vm.closeWebImport() }) { Text("Voltar") }
            }
            return
        }
        NfceWebImportView(
            consultUrl = pendingUrl,
            accessKey = key,
            onImportHtml = { html, k -> vm.importFromWebHtml(html, k) },
            onClose = { vm.closeWebImport() },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Aponte para o QR da NFC-e. Se a SEFAZ pedir captcha, use Consultar no site.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = manualInput,
            onValueChange = { manualInput = it },
            label = { Text("Chave (44 dígitos) ou URL do QR") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            minLines = 2,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { vm.importByAccessKey(manualInput) },
                modifier = Modifier.weight(1f),
                enabled = !loading && manualInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            ) { Text("Importar direto") }
            Button(
                onClick = {
                    val digits = manualInput.filter { it.isDigit() }
                    if (digits.length == 44) vm.importByAccessKey(manualInput)
                    vm.openWebImport()
                },
                modifier = Modifier.weight(1f),
                enabled = manualInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            ) { Text("Consultar no site") }
        }

        if (hasCamera) {
            QrScannerView(scanToken = scanToken, onQr = vm::scanNfce)
        } else {
            Text("Permissão da câmera necessária.", modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun AnalyticsScreen(vm: MainViewModel) {
    val comparisons by vm.comparisons.collectAsState()
    val history by vm.historyPoints.collectAsState()
    val historyStores by vm.historyStores.collectAsState()
    val products by vm.analyticsProducts.collectAsState()
    val selectedId by vm.historyProductId.collectAsState()
    val productName by vm.selectedProductName.collectAsState()
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            PromoBanner(
                title = "Preços do dia a dia",
                subtitle = "Toque em um produto para ver o menor preço entre os mercados das suas notas.",
                action = null,
                onClick = {},
            )
        }
        item { SectionTitle("Produtos recentes") }
        if (products.isEmpty()) {
            item { EmptyHint("Importe notas NFC-e para registrar produtos e acompanhar preços.") }
        } else {
            items(products, key = { it.productId }) { p ->
                PriceProductCard(
                    p = p,
                    selected = selectedId == p.productId,
                    currency = currency,
                    onClick = { vm.loadHistory(p.productId) },
                )
            }
            item {
                Text(
                    productName ?: "Produto",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                when {
                    history.isEmpty() -> Text("Nenhum registro de preço para este produto.")
                    history.size == 1 -> {
                        val (at, price) = history.first()
                        Text(
                            "Último preço: ${currency.format(price)} • ${historyStores.firstOrNull().orEmpty()} • ${dateFmt.format(Date(at))}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    else -> PriceChart(history.map { it.second })
                }
            }
            if (history.isNotEmpty()) {
                item { SectionTitle("Registros") }
                items(history.size) { i ->
                    val (at, price) = history[i]
                    val store = historyStores.getOrNull(i).orEmpty()
                    Text("${dateFmt.format(Date(at))} • $store • ${currency.format(price)}")
                }
            }
        }
        item { SectionTitle("Comparativo por loja") }
        if (comparisons.isEmpty()) {
            item { EmptyHint("Importe o mesmo produto em lojas diferentes para comparar.") }
        } else {
            items(comparisons) { row -> ComparisonCard(row, currency) }
        }
    }
}

@Composable
private fun PriceProductCard(
    p: ProductPickerRow,
    selected: Boolean,
    currency: NumberFormat,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) BrandMint else Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    p.productName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${p.purchaseCount} registro(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF667085),
                )
                Text(
                    "Menor preço",
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandGreen,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                currency.format(p.lastUnitPrice),
                fontWeight = FontWeight.Bold,
                color = BrandGreen,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BackupScreen(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isLoggedIn by vm.isLoggedIn.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    val syncLoading by vm.syncLoading.collectAsState()
    val syncProgress by vm.syncProgress.collectAsState()
    val lastSyncResult by vm.lastSyncResult.collectAsState()
    val message by vm.message.collectAsState()

    var showLoginDialog by remember { mutableStateOf(false) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var registerUsername by remember { mutableStateOf("") }
    var registerPassword by remember { mutableStateOf("") }
    var registerName by remember { mutableStateOf("") }
    var registerEmail by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf(vm.getApiBaseUrl()) }

    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            vm.clearMessage()
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = BrandGreen)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        if (isLoggedIn) {
                            Text(
                                "Logado como",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF667085),
                            )
                            Text(
                                currentUser?.name ?: currentUser?.username ?: "Usuário",
                                fontWeight = FontWeight.Bold,
                                color = BrandGreen,
                            )
                            currentUser?.email?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text("Não conectado ao MeuMercado", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Faça login para sincronizar seus dados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF667085),
                            )
                        }
                    }
                }

                if (!isLoggedIn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showLoginDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                        ) { Text("Entrar") }
                        OutlinedButton(
                            onClick = { showRegisterDialog = true },
                            modifier = Modifier.weight(1f),
                        ) { Text("Criar conta") }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            urlText = vm.getApiBaseUrl()
                            showUrlDialog = true
                        },
                    ) { Text("URL do servidor") }
                    TextButton(
                        onClick = {
                            scope.launch { vm.checkApiHealth() }
                        },
                        enabled = !syncLoading,
                    ) { Text("Testar conexão") }
                    if (isLoggedIn) {
                        TextButton(
                            onClick = { scope.launch { vm.logout() } },
                        ) { Text("Sair", color = RedExpensive) }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sincronização MeuMercado", fontWeight = FontWeight.Bold, color = BrandGreen)
                Text(
                    "Envie seus mercados, produtos e compras do NFC-e para o web e receba o histórico de preços.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                val p = syncProgress
                if (p != null && (p.running || p.phase != null || p.lastError != null)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (syncLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandGreen,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                p.phase ?: (if (p.running) "Sincronizando" else "Sincronização"),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            if (p.total > 0) {
                                Text(
                                    "${p.current}/${p.total}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF667085),
                                )
                            }
                        }
                        if (p.total > 0) {
                            LinearProgressIndicator(
                                progress = { (p.current.toFloat() / p.total.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = BrandGreen,
                                trackColor = BrandMint,
                            )
                        }
                        p.lastError?.let { err ->
                            Text(err, color = RedExpensive, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                lastSyncResult?.let { r ->
                    val color = if (r.isSuccess) BrandGreen else RedExpensive
                    val pushedTotal = r.pushedStores + r.pushedProducts + r.pushedPurchases
                    val pulledTotal = r.pulledMarkets + r.pulledProducts + r.pulledHistory
                    val total = pushedTotal + pulledTotal
                    val label = buildString {
                        if (pushedTotal > 0) append("Enviados: $pushedTotal · ")
                        if (pulledTotal > 0) append("Recebidos: $pulledTotal · ")
                        if (total == 0) append("Nenhum dado novo")
                        else deleteRange(length - 3, length)
                        if (!r.isSuccess) append(" · falhou")
                    }
                    Text(label, color = color, style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scope.launch { vm.syncAll() } },
                        enabled = isLoggedIn && !syncLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                    ) { Text("🔄 Sincronizar tudo") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { vm.syncPush() } },
                        enabled = isLoggedIn && !syncLoading,
                        modifier = Modifier.weight(1f),
                    ) { Text("⬆ Enviar") }
                    OutlinedButton(
                        onClick = { scope.launch { vm.syncPull() } },
                        enabled = isLoggedIn && !syncLoading,
                        modifier = Modifier.weight(1f),
                    ) { Text("⬇ Baixar") }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup local", fontWeight = FontWeight.Bold, color = BrandGreen)
                Text(
                    "Seus dados ficam no aparelho. Exporte um CSV para Drive, e-mail ou WhatsApp.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        scope.launch {
                            val intent = vm.buildExportIntent()
                            context.startActivity(android.content.Intent.createChooser(intent, "Salvar backup"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                ) { Text("Exportar backup CSV") }
                TextButton(
                    onClick = { vm.deleteAllDemoPurchases() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remover compras demo (Mercado Demo)", color = RedExpensive)
                }
            }
        }
    }

    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("Entrar no MeuMercado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = loginUsername,
                        onValueChange = { loginUsername = it },
                        label = { Text("Usuário") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text("Senha") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLoginDialog = false
                        scope.launch { vm.login(loginUsername, loginPassword) }
                    },
                    enabled = loginUsername.isNotBlank() && loginPassword.isNotBlank() && !syncLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                ) { Text("Entrar") }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) { Text("Cancelar") }
            },
        )
    }

    if (showRegisterDialog) {
        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text("Criar conta no MeuMercado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = registerUsername,
                        onValueChange = { registerUsername = it },
                        label = { Text("Usuário (min 3)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = registerPassword,
                        onValueChange = { registerPassword = it },
                        label = { Text("Senha (min 6)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    OutlinedTextField(
                        value = registerName,
                        onValueChange = { registerName = it },
                        label = { Text("Nome completo") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = registerEmail,
                        onValueChange = { registerEmail = it },
                        label = { Text("E-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegisterDialog = false
                        scope.launch { vm.register(registerUsername, registerPassword, registerName, registerEmail) }
                    },
                    enabled = registerUsername.length >= 3 &&
                        registerPassword.length >= 6 &&
                        registerName.isNotBlank() &&
                        registerEmail.isNotBlank() &&
                        !syncLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) { Text("Cancelar") }
            },
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("URL do servidor MeuMercado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("Endereço") },
                        singleLine = true,
                    )
                    Text(
                        "Emulador: http://10.0.2.2:3000/\nCelular: http://IP-DO-SEU-PC:3000/",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF667085),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.setApiBaseUrl(urlText)
                        showUrlDialog = false
                        scope.launch { vm.checkApiHealth() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                ) { Text("Salvar e testar") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun QrScannerView(scanToken: Int, onQr: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var handled by remember(scanToken) { mutableStateOf(false) }
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analyzer = ImageAnalysis.Builder().build().also { analysis ->
                    val scanner = BarcodeScanning.getClient()
                    val executor = Executors.newSingleThreadExecutor()
                    analysis.setAnalyzer(executor) { imageProxy ->
                        val media = imageProxy.image ?: return@setAnalyzer
                        val image = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val raw = barcodes.firstOrNull()?.rawValue
                                if (!raw.isNullOrBlank() && !handled) {
                                    handled = true
                                    onQr(raw)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer,
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun PriceChart(values: List<Double>) {
    val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val max = values.maxOrNull() ?: 1.0
    val min = values.minOrNull() ?: 0.0
    Column(Modifier.fillMaxWidth().height(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEachIndexed { index, price ->
            val avg = values.average()
            val color = when {
                price <= avg * 0.95 -> GreenCheap
                price >= avg * 1.08 -> RedExpensive
                else -> BrandGreen
            }
            val widthFraction = if (max > min) ((price - min) / (max - min)).toFloat().coerceIn(0.15f, 1f) else 1f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${index + 1}", modifier = Modifier.size(28.dp, 20.dp), style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier
                        .weight(1f)
                        .height(18.dp)
                        .background(color.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(widthFraction)
                            .height(18.dp)
                            .background(color, RoundedCornerShape(4.dp)),
                    )
                }
                Text(currency.format(price), color = color, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyHint(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Text(text, modifier = Modifier.padding(16.dp), color = Color(0xFF667085))
    }
}

@Composable
private fun PromoBanner(title: String, subtitle: String, action: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1F9D55), Color(0xFF3CBF73))))
            .clickable(enabled = action != null, onClick = onClick)
            .padding(18.dp),
    ) {
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
            if (action != null) {
                Spacer(Modifier.height(10.dp))
                Text(action, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = BrandGreen)
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085))
        }
    }
}

@Composable
private fun PurchaseCard(
    p: PurchaseWithStore,
    currency: NumberFormat,
    dateFmt: SimpleDateFormat,
    onDelete: () -> Unit,
    fetchItems: suspend (Long) -> List<PurchaseItemRow>,
) {
    var expanded by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<PurchaseItemRow>?>(null) }
    var loadingItems by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = !expanded
                        if (expanded && items == null && !loadingItems) {
                            loadingItems = true
                            scope.launch {
                                items = fetchItems(p.id)
                                loadingItems = false
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.storeName, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${dateFmt.format(Date(p.purchasedAt))} • ${p.itemCount} itens • ${currency.format(p.totalAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Recolher" else "Expandir",
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir compra", tint = RedExpensive)
                }
            }
            if (expanded) {
                when {
                    loadingItems -> {
                        CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), color = BrandGreen)
                    }
                    items.isNullOrEmpty() -> {
                        Text("Nenhum item nesta compra.", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        items!!.forEach { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.description, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${formatQty(item.quantity)} × ${currency.format(item.unitPrice)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(currency.format(item.totalPrice), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(s: ReplenishmentSuggestion, onAdd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.productName, fontWeight = FontWeight.SemiBold)
                Text(s.reason, style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085))
            }
            TextButton(onClick = onAdd) { Text("Adicionar") }
        }
    }
}

@Composable
private fun ListItemRow(
    item: ShoppingListItemEntity,
    lastPrice: Double?,
    currency: NumberFormat,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundCheck(checked = item.isChecked, onClick = onToggle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable { onEdit() }) {
                Text(
                    item.description,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                    fontWeight = FontWeight.Medium,
                    color = if (item.isChecked) Color(0xFF98A2B3) else Color(0xFF1D2939),
                )
                val details = buildString {
                    append("Qtd ${formatQty(item.quantity)}")
                    if (lastPrice != null) append(" • último ${currency.format(lastPrice)}")
                }
                Text(details, style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085))
            }
            Icon(Icons.Default.MoreVert, contentDescription = "Editar", tint = Color(0xFFD0D5DD), modifier = Modifier.clickable { onEdit() })
        }
    }
}

@Composable
private fun RoundCheck(checked: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (checked) BrandGreen else Color.White)
            .border(2.dp, if (checked) BrandGreen else Color(0xFFD0D5DD), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Default.Check, contentDescription = "Marcar comprado", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

private fun formatQty(qty: Double): String =
    if (qty % 1.0 == 0.0) qty.toInt().toString() else "%.2f".format(qty)

private fun lookupPrice(description: String, prices: Map<String, Double>): Double? {
    val normalized = CategoryClassifier.normalize(description)
    if (normalized.isBlank()) return null
    prices[normalized]?.let { return it }
    return prices.entries.firstOrNull { (key, _) ->
        key.contains(normalized) || normalized.contains(key)
    }?.value
}

@Composable
private fun ComparisonCard(row: ComparisonInsight, currency: NumberFormat) {
    val color = when (row.signal) {
        PriceSignal.CHEAPER -> GreenCheap
        PriceSignal.EXPENSIVE -> RedExpensive
        PriceSignal.AVERAGE -> AmberAverage
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(row.storeName, fontWeight = FontWeight.Bold)
                Text(currency.format(row.avgUnitPrice), color = color, fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (row.percentVsBest <= 0.1) "Menor preço" else "+${"%.0f".format(row.percentVsBest)}%",
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
