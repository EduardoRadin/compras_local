# Compras Local

App Android para controle de compras de supermercado, com leitura de NFC-e via QR Code ou Código de Barras, listas de compras inteligentes, histórico de preços, comparação de mercados, análise de gastos e sincronização opcional com um backend NestJS ([MeuMercado](https://github.com/LuizChristani/MeuMercado)).

Trabalho desenvolvido para a disciplina de Desenvolvimento Mobile da UNOESC.

---

## Funcionalidades Principais

### 🛒 Listas de compras
- Criar listas ativas (ex.: "Compras da semana")
- Adicionar itens com descrição e quantidade
- Marcar/desmarcar como comprados (check)
- Excluir itens ou limpar os já marcados
- **Match automático**: ao importar uma NFC-e, itens da sua lista ativa já são marcados como comprados se der match por nome normalizado

### 🧾 Importação de NFC-e (Nota Fiscal do Consumidor Eletrônica)
- **Leitura por câmera** (QR Code) usando CameraX + ML Kit Barcode Scanning
- **Cola da chave de acesso** (44 dígitos) ou da URL inteira da consulta SEFAZ
- **Consulta SEFAZ automática** (busca os dados da nota direto da receita, parseando HTML ou XML)
- **Offline demo**: modo offline para testar com dados de exemplo sem consultar a SEFAZ
- Parser dos itens: descrição, quantidade, unidade, preço unitário e preço total

### 💰 Histórico e Comparação de Preços
- Histórico de preços por produto (gráfico de evolução)
- Comparação do **mesmo produto em mercados diferentes** (qual mercado é mais barato)
- Último preço pago por produto na tela de criação de lista (sugestão de valor)
- Produtos frequentes para repor (sugestão do que comprar baseado em quantas vezes você comprou)

### 📊 Análises e Gráficos
- Gráficos de preços (evolução mensal, comparação por mercado)
- Estimativa de economia (média de preços vs preço pago)
- Produtos mais comprados no mês
- Exportação de **todas as compras para CSV** (para usar em planilhas)

### ☁️ Sincronização com Backend (Opcional)
- Tudo funciona **100% offline first** — o servidor é opcional
- **Push**: envia mercados, produtos e compras pendentes para o MeuMercado
- **Pull**: baixa catálogo de produtos, mercados e registros de preços do backend
- Tratamento robusto de erros de rede, tokens expirados (refresh automático), rotas faltantes no servidor
- Campos `remoteId` em todas as tabelas para rastrear o que já foi sincronizado

### 🔐 Autenticação
- Registro e login com conta no backend MeuMercado (JWT com Access Token + Refresh Token)
- Interceptor Retrofit que injeta `Authorization: Bearer <token>` automaticamente
- Refresh automático do token se receber 401

---

## Stack Técnica

| Camada | Tecnologias |
|---|---|
| **Linguagem** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material 3 (toda a UI é Compose) |
| **Navegação** | Navigation Compose |
| **Injeção de Dependência** | Padrão Service Locator (Objetos Singletons: `ApiClient`, `SyncManager`, `AppDatabase`) |
| **ViewModel** | Lifecycle ViewModel Compose + Flows (StateFlow) |
| **Banco Local** | Room (KSP) com SQLite — Stores, Products, Purchases, PurchaseItems, ShoppingLists, Auth, SyncLogs |
| **Classificação/Categorização** | `CategoryClassifier` (regras heurísticas + keywords) para categorizar produtos automaticamente |
| **Motores de domínio** | `PriceAnalytics` (comparação de mercados), `ReplenishmentEngine` (sugestão de reposição) |
| **Rede** | Retrofit 2 + OkHttp (com HttpLoggingInterceptor e interceptors de auth/refresh) |
| **JSON** | Moshi + Codegen (`@JsonClass(generateAdapter = true)`) |
| **Câmera e QR** | CameraX Lifecycle + ML Kit Barcode Scanning |
| **Gráficos** | Vico Compose (biblioteca de gráficos para Jetpack Compose) |
| **Build** | Gradle Kotlin DSL com Version Catalog (`gradle/libs.versions.toml`) |
| **SDK mínimo/alvo** | `minSdk 24` (Android 7.0+) / `targetSdk 35` (Android 15) |

---

## Como rodar o projeto

### Pré-requisitos
- Android Studio Iguana ou mais novo (suporte a Kotlin 2.0 e KSP)
- Android SDK 35 instalado
- JDK 11+ (recomendado 17+)
- (Opcional) Backend MeuMercado rodando em Docker ou máquina local

### Passo a passo

1. Clone o repositório:
   ```bash
   git clone https://github.com/EduardoRadin/compras_local.git
   cd compras_local
   ```
2. Abra a pasta no **Android Studio** — ele vai detectar o Gradle automaticamente e baixar as dependências.
3. Sincronize o Gradle (botão "Sync Project with Gradle Files").
4. Conecte um celular via USB (ou crie um AVD Emulador) e rode o app (Shift+F10).

### (Opcional) Configurar a URL do backend
No arquivo [`ApiClient.kt`](app/src/main/java/br/edu/unoesc/compraslocal/api/ApiClient.kt) ajuste `DEFAULT_BASE_URL` e `PHYSICAL_DEVICE_URL`:
```kotlin
object ApiClient {
    // Emulador: 10.0.2.2 aponta para o localhost do PC hospedeiro
    const val DEFAULT_BASE_URL = "http://10.0.2.2:3000/"
    // Celular físico na mesma rede Wi-Fi do PC: IP da sua placa de rede
    const val PHYSICAL_DEVICE_URL = "http://192.168.1.100:3000/"
}
```
Também pode trocar em tempo real pela tela de Configuração/Sync do app.

### (Opcional) Gerar APK Release / Debug
Pelo Android Studio: `Build → Build Bundle(s)/APK(s) → Build APK(s)`  
Pelo terminal:
```bash
# Linux / macOS
./gradlew assembleDebug   # APK de debug em  app/build/outputs/apk/debug/
./gradlew assembleRelease # APK de release (precisa de keystore configurado)
```

---

## Estrutura de Pastas

```
app/src/main/java/br/edu/unoesc/compraslocal/
├── api/                      # Camada de rede (Retrofit + Moshi)
│   ├── ApiClient.kt          # Retrofit singleton, interceptors, auth, parseResponse
│   ├── ApiModels.kt          # DTOs de requisição/resposta (CreateMarketRequest, ProductDto, etc.)
│   └── MeuMercadoApi.kt      # Interface Retrofit com as rotas
├── data/                     # Camada de dados local (Room)
│   ├── AppDatabase.kt        # @Database Room + instância singleton
│   ├── DatabaseSeeder.kt     # Dados iniciais / demo
│   ├── dao/Daos.kt           # Todos os DAOs (Store, Product, Purchase, etc.)
│   └── entity/Entities.kt    # Todas as @Entity Room
├── domain/                   # Lógica de negócio PURA (sem Android)
│   ├── CategoryClassifier.kt # Normalização + categorização automática
│   ├── PriceAnalytics.kt     # Comparação de preços entre mercados
│   └── ReplenishmentEngine.kt# Sugestão de produtos para repor
├── export/                   # Exportação CSV
│   └── CsvExporter.kt        # Converte as compras em arquivo CSV
├── nfce/                     # Integração NFC-e / SEFAZ
│   ├── NfceQrParser.kt       # Extrai chave + URL do QR
│   ├── NfceSefazClient.kt    # Fetch + parse da nota no site da SEFAZ
│   ├── NfceHtmlParser.kt     # Parse de HTML
│   ├── NfceXmlParser.kt      # Parse de XML
│   └── NfceModels.kt         # Modelos internos da NFC-e
├── repository/               # Repositório de domínio (abstrai Room + lógica comum)
│   └── ComprasRepository.kt  # Listas, compras, persistReceipt, NFCe demo, analytics
├── sync/                     # Sincronização bidirecional MeuMercado ↔ Mobile
│   └── SyncManager.kt        # push, pull, healthCheck, progress, fallback 404 de rotas
├── ui/theme/                 # Tema Compose (Material 3)
├── ui/screens/               # Telas Compose + ComprasAppShell com BottomNav
├── viewmodel/                # ViewModel compartilhada
│   └── MainViewModel.kt      # State holders, todos os eventos de UI
├── ComprasApp.kt             # Application class
└── MainActivity.kt           # Activity root, setContent do Compose
```

---

## Modelagem do Banco Local (Room)

```
stores (mercados)               products (catálogo)
 ├─ id (Long, PK)                 ├─ id (Long, PK)
 ├─ name                           ├─ name
 ├─ cnpj (opcional)                ├─ normalizedName (match/desduplicação)
 ├─ remoteId (UUID MeuMercado)     ├─ brand (opcional)
 └─ dirty / lastSyncAt             ├─ unit (un/kg/g/l/ml)
                                  ├─ categoryId (FK -> categories)
categories                         ├─ remoteId
 ├─ id                              └─ dirty / lastSyncAt
 ├─ name
 └─ keywords                    shopping_lists (listas ativas)
                                      ├─ id
purchases (notas)                    ├─ name
 ├─ id                               ├─ createdAt
 ├─ storeId (FK -> stores)           └─ isActive
 ├─ purchasedAt (epoch ms)
 ├─ totalAmount                   shopping_list_items
 ├─ nfeKey (chave 44 digitos)       ├─ id
 ├─ synced / syncError              ├─ listId
 └─ lastSyncAttemptAt               ├─ productId (FK -> products)
                                      ├─ description
purchase_items (itens da nota)      ├─ quantity
 ├─ id                               └─ isChecked
 ├─ purchaseId
 ├─ productId (opcional -> products)
 ├─ description, quantity          auth_state (1 linha só)
 ├─ unitPrice, totalPrice            ├─ accessToken, refreshToken
 ├─ remoteCartItemId                 ├─ user info
 └─ synced                           └─ expiresAt, lastSyncAt
```

---

## Sincronização com MeuMercado (Backend)

### Fluxo de sincronização (BIDIRECIONAL)
1. **Push (App → Backend)**
   - **Mercados**: Tenta `POST /markets`; se a rota não existir (404) em versões antigas do MeuMercado, busca por nome no `GET /markets` e continua sem quebrar o sync.
   - **Produtos**: `POST /products` sempre que tiver `dirty=true`.
   - **Compras**: Para cada item não sincronizado → `POST /cart/items` (add no carrinho do backend) → `PATCH /cart/items/:id` (marca como `purchased=true` com `marketId`, `quantity`, `unitPrice`). Campos extras no body são **strippados silenciosamente** pelo Zod do MeuMercado, então o app envia tudo sem erro mesmo se o backend ainda não ligar esses campos ao `purchases` / `price_records`.
2. **Pull (Backend → App)**
   - Mercados, produtos, histórico de preços via `GET /markets`, `GET /products`, `GET /history`.
   - Dados mesclados por `remoteId` ou por `nome normalizado` para evitar duplicatas.

### Rotas usadas no MeuMercado
| Método | Rota | Uso |
|---|---|---|
| `GET` | `/health` | Health check antes do sync |
| `POST` | `/register`, `/login` | Autenticação |
| `GET` | `/me`, `/token`, `/token/refresh` | Sessão |
| `GET/POST` | `/products`, `/markets` | Catálogo |
| `GET/POST/PATCH/DELETE` | `/cart/items` | Lista/carrinho no backend |
| `GET` | `/history`, `/dashboard/stats`, `/dashboard/summary` | Indicadores |

---

## FAQ / Troubleshooting

**O app funciona sem backend?**  
Sim, 100% offline first. Tudo é salvo em Room primeiro. A sincronização é só conveniência, não requisito.

**A tela de sincronização deu erro "Cannot POST /markets" e continuou?**  
Normal — o app foi adaptado para continuar o sync mesmo que o MeuMercado não tenha a rota. Se sua versão do MeuMercado antiga não tem `POST /markets`, os mercados novos ficam só no celular (não tem `remoteId`), mas o resto sincroniza normalmente.

**O token expira e tenho que ficar logando de novo?**  
Não — o interceptor de refresh em `ApiClient.kt` detecta 401 e chama `POST /token/refresh` sozinho, transparente.

**Não consigo conectar o celular físico no backend local?**  
1. Confirme que celular e PC estão na mesma Wi-Fi  
2. Descubra o IP do PC (`ip addr` / `ifconfig` / `ipconfig`)  
3. Ajuste `PHYSICAL_DEVICE_URL` em ApiClient.kt para `http://SEU_IP:3000/`  
4. Firewall do PC: libere a porta 3000 de entrada

**Como apagar todos os dados locais do app?**  
Configurações do Android → Apps → Compras Local → Armazenamento → "Limpar dados". Ou desinstale/reinstale.

---



## Licença

Projeto de trabalho acadêmico — uso educacional livre.
