# API do MeuMercado

Este documento descreve as rotas implementadas no backend NestJS. A URL local padrão é `http://localhost:3000` (configurável por `PORT`). As requisições com corpo usam JSON e as datas retornadas são strings ISO 8601 em UTC.

## Começando

1. Inicie o backend e confirme que `GET /health` retorna `status: "ok"`.
2. Crie uma conta com `POST /register` ou entre com `POST /login`.
3. Guarde `token` e `refreshToken` da resposta. Nas rotas marcadas como **autenticadas**, envie `Authorization: Bearer <token>`.
4. Quando o token de acesso expirar, use `POST /token/refresh` e substitua **ambos** os tokens armazenados. O refresh token só pode ser usado uma vez e expira após 30 dias.

Exemplo de cabeçalhos para uma rota autenticada:

```http
GET http://localhost:3000/me
Authorization: Bearer <token>
```

Os exemplos abaixo usam UUIDs ilustrativos. Substitua-os por IDs recebidos da sua API. Valores monetários são números em reais, como `7.50`, com ponto decimal no JSON. Unidades de produto aceitas: `un`, `kg`, `g`, `l` e `ml`.

## Índice de rotas

| Método | Rota                          | Autenticação | Uso                                               |
| ------ | ----------------------------- | ------------ | ------------------------------------------------- |
| GET    | `/health`                     | Não          | Verificar disponibilidade da API                  |
| POST   | `/produtos`                   | Não          | Validar um produto de exemplo; não salva no banco |
| POST   | `/register`                   | Não          | Criar conta                                       |
| POST   | `/login`                      | Não          | Entrar na conta                                   |
| GET    | `/me`                         | Sim          | Consultar usuário autenticado                     |
| POST   | `/token/refresh`              | Não          | Renovar os tokens                                 |
| GET    | `/token`                      | Sim          | Validar sessão e consultar expiração              |
| POST   | `/user/recovery`              | Não          | Enviar código de recuperação                      |
| POST   | `/user/recovery/verify`       | Não          | Validar código de recuperação                     |
| POST   | `/user/recovery/new-password` | Não          | Redefinir senha                                   |
| GET    | `/products`                   | Sim          | Listar catálogo de produtos                       |
| POST   | `/products`                   | Sim          | Cadastrar produto no catálogo                     |
| GET    | `/markets`                    | Sim          | Listar mercados                                   |
| POST   | `/markets`                    | Sim          | Cadastrar ou reutilizar mercado pelo nome         |
| GET    | `/cart/items`                 | Sim          | Listar itens do carrinho                          |
| POST   | `/cart/items`                 | Sim          | Adicionar produto ao carrinho                     |
| PATCH  | `/cart/items/:id`             | Sim          | Marcar ou desmarcar compra                        |
| DELETE | `/cart/items/:id`             | Sim          | Remover item do carrinho                          |
| GET    | `/history`                    | Sim          | Consultar preços registrados pelo usuário         |
| GET    | `/dashboard/stats`            | Sim          | Indicadores do dashboard                          |
| GET    | `/dashboard/summary`          | Sim          | Produtos mais comprados no mês                    |
| GET    | `/graphics/products`          | Sim          | Produtos com preços disponíveis para gráficos     |
| GET    | `/graphics/price-history`     | Sim          | Histórico de preços de um produto                 |
| GET    | `/graphics/compare-markets`   | Sim          | Último preço por mercado de um produto            |
| GET    | `/graphics/price-evolution`   | Sim          | Evolução mensal do preço de um produto            |

### Fluxo comum de compra

1. Faça login e use o `token` recebido nas próximas requisições.
2. Escolha um produto em `GET /products` ou cadastre um em `POST /products`.
3. Escolha um mercado em `GET /markets` ou cadastre um em `POST /markets`.
4. Adicione o produto com `POST /cart/items` e guarde o `id` **do item do carrinho** retornado.
5. Envie `PATCH /cart/items/:id` com `purchased: true`, `marketId`, `quantity` e `unitPrice` para registrar a compra.
6. Consulte `GET /dashboard/summary` e `GET /dashboard/stats`. O produto aparecerá no resumo do mês e o preço registrado passará a integrar os indicadores.

## Saúde e rota de exemplo

### `GET /health`

Retorna **200**:

```json
{ "status": "ok", "timestamp": "2026-10-03T20:00:00.000Z" }
```

### `POST /produtos`

Rota de exemplo para validação; **não cadastra um produto**. Para cadastrar no banco, use `POST /products`.

```http
POST http://localhost:3000/produtos
Content-Type: application/json

{ "nome": "Arroz", "preco": 24.90, "quantidade": 2 }
```

`nome` precisa ser uma string não vazia; `preco`, um número positivo; `quantidade`, um inteiro maior ou igual a zero. Retorna **201**:

```json
{
  "message": "Produto validado com sucesso",
  "produto": { "nome": "Arroz", "preco": 24.9, "quantidade": 2 }
}
```

## Conta e sessão

### `POST /register`

Cria uma conta e retorna **201** com os tokens e o usuário. Campos obrigatórios: `username` (mínimo 3 caracteres), `password` (mínimo 6), `name` (não vazio) e `email` válido. `avatarUrl` é opcional e precisa ser uma URL válida.

```http
POST http://localhost:3000/register
Content-Type: application/json

{
  "username": "ana123",
  "password": "senha-segura-123",
  "name": "Ana Silva",
  "email": "ana@example.com"
}
```

Resposta (mesma estrutura de `POST /login`):

```json
{
  "token": "<jwt-de-acesso>",
  "refreshToken": "<64-caracteres-hexadecimais>",
  "user": {
    "id": "123e4567-e89b-42d3-a456-426614174000",
    "username": "ana123",
    "name": "Ana Silva",
    "email": "ana@example.com",
    "timezone": "America/Sao_Paulo",
    "createdAt": "2026-10-03T20:00:00.000Z",
    "updatedAt": "2026-10-03T20:00:00.000Z"
  }
}
```

`avatarUrl` só aparece no objeto `user` quando existir. Usuário ou e-mail já cadastrado retorna **409**.

### `POST /login`

Autentica pelo **nome de usuário**, não pelo e-mail. Retorna **200** no mesmo formato de `/register`. Credenciais inválidas retornam **401**.

```http
POST http://localhost:3000/login
Content-Type: application/json

{ "username": "ana123", "password": "senha-segura-123" }
```

### `GET /me`

Retorna **200** com `{ "user": { ... } }`, usando o mesmo formato de `user` mostrado em `/register`.

### `GET /token`

Valida o token de acesso e a sessão. Retorna **200**:

```json
{
  "user": {
    "id": "123e4567-e89b-42d3-a456-426614174000",
    "username": "ana123",
    "name": "Ana Silva",
    "email": "ana@example.com",
    "timezone": "America/Sao_Paulo",
    "createdAt": "2026-10-03T20:00:00.000Z",
    "updatedAt": "2026-10-03T20:00:00.000Z"
  },
  "expiresAt": "2026-10-04T20:00:00.000Z"
}
```

O formato de `user` é o mesmo de `/me`. Um token expirado, revogado ou inválido retorna **401**.

### `POST /token/refresh`

Envia o refresh token recebido no login ou cadastro. Retorna **200** com um novo par `{ "token": "...", "refreshToken": "..." }`. O refresh token anterior deixa de funcionar.

```http
POST http://localhost:3000/token/refresh
Content-Type: application/json

{ "refreshToken": "<64-caracteres-hexadecimais>" }
```

## Recuperação de senha

Estas rotas não precisam de token de acesso. O código enviado por e-mail tem **seis dígitos**, validade de **cinco minutos** e até **cinco tentativas inválidas**. Não é devolvido no corpo da API.

### `POST /user/recovery`

Gera um código e envia ao e-mail cadastrado. Retorna **201 sem corpo**. Um código ainda válido impede novo envio e retorna **409**.

```http
POST http://localhost:3000/user/recovery
Content-Type: application/json

{ "email": "ana@example.com" }
```

### `POST /user/recovery/verify`

Confere o código sem alterar a senha. Retorna **204 sem corpo** quando válido.

```http
POST http://localhost:3000/user/recovery/verify
Content-Type: application/json

{ "email": "ana@example.com", "code": "012345" }
```

### `POST /user/recovery/new-password`

Troca a senha e consome o código. `newPassword` precisa ter pelo menos seis caracteres. Retorna **204 sem corpo**; as sessões anteriores são revogadas.

```http
POST http://localhost:3000/user/recovery/new-password
Content-Type: application/json

{
  "email": "ana@example.com",
  "code": "012345",
  "newPassword": "nova-senha-123"
}
```

Código inválido ou expirado retorna **400**. O envio do e-mail depende da configuração MailerSend do backend.

## Produtos e mercados

### `GET /products`

Lista o catálogo, ordenado por nome. A lista pode conter produtos criados por outros usuários. `latestPrice` traz o registro mais recente que seja público ou do usuário autenticado; fica `null` quando não há preço visível.

```json
[
  {
    "id": "123e4567-e89b-42d3-a456-426614174010",
    "name": "Arroz 1 kg",
    "brand": "Marca Exemplo",
    "category": "Mercearia",
    "unit": "kg",
    "imageUrl": null,
    "latestPrice": {
      "id": "123e4567-e89b-42d3-a456-426614174020",
      "amount": 7.5,
      "marketId": "123e4567-e89b-42d3-a456-426614174030",
      "market": "Mercado Central",
      "observedAt": "2026-10-03T20:00:00.000Z"
    }
  }
]
```

### `POST /products`

Cria um produto no catálogo. `name` tem de 1 a 200 caracteres; `category`, de 1 a 80; `unit` aceita `un`, `kg`, `g`, `l` ou `ml`. `brand` (até 100 caracteres) e `imageUrl` (URL HTTP/HTTPS) são opcionais e podem ser `null`. **Criar produto não registra preço.** Retorna **201** com `{ "id": "<uuid>" }`.

```http
POST http://localhost:3000/products
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Arroz 1 kg",
  "brand": "Marca Exemplo",
  "category": "Mercearia",
  "unit": "kg",
  "imageUrl": null
}
```

### `GET /markets`

Lista os mercados cadastrados, ordenados por nome. Retorna **200** com objetos neste formato:

```json
[
  {
    "id": "123e4567-e89b-42d3-a456-426614174030",
    "name": "Mercado Central",
    "addressLine": null,
    "neighborhood": null,
    "city": null,
    "state": null,
    "postalCode": null
  }
]
```

### `POST /markets`

Aceita `name` com 1 a 150 caracteres. Se já existir um mercado com o mesmo nome, ignorando maiúsculas/minúsculas, retorna o existente; caso contrário, cria um. Retorna **201** com o mesmo formato de item de `GET /markets`.

```http
POST http://localhost:3000/markets
Authorization: Bearer <token>
Content-Type: application/json

{ "name": "Mercado Central" }
```

## Carrinho e compras

### `GET /cart/items`

Lista os itens do usuário, mais recentes primeiro. `status` é `pending` ou `purchased`; `purchasedAt` só é preenchido quando comprado. `unitPrice`, `totalAmount` e `marketName` vêm da compra vinculada e podem ser `null` em itens antigos. `suggestedMarketId` e `suggestedUnitPrice` vêm do preço escolhido ao adicionar o produto ou, na ausência dele, do último preço visível; são apenas sugestões.

```json
[
  {
    "id": "123e4567-e89b-42d3-a456-426614174040",
    "productId": "123e4567-e89b-42d3-a456-426614174010",
    "name": "Arroz 1 kg",
    "imageUrl": null,
    "unit": "kg",
    "quantity": 2,
    "status": "purchased",
    "purchasedAt": "2026-10-03T20:00:00.000Z",
    "unitPrice": 7.5,
    "totalAmount": 15,
    "marketName": "Mercado Central",
    "suggestedMarketId": "123e4567-e89b-42d3-a456-426614174030",
    "suggestedUnitPrice": 7.5
  }
]
```

### `POST /cart/items`

Adiciona um produto do catálogo à lista. `priceRecordId` é opcional e indica qual preço visível usar como sugestão. Se esse ID não existir, não pertencer ao produto ou não for visível para o usuário, o item ainda é adicionado sem essa sugestão específica. Se já existir um item **pendente** desse produto, devolve o ID dele. Retorna **201** com `{ "id": "<uuid-do-item>" }`.

```http
POST http://localhost:3000/cart/items
Authorization: Bearer <token>
Content-Type: application/json

{
  "productId": "123e4567-e89b-42d3-a456-426614174010",
  "priceRecordId": "123e4567-e89b-42d3-a456-426614174020"
}
```

### `PATCH /cart/items/:id`

Para **marcar como comprado**, informe mercado, quantidade e preço **por unidade**. `marketId` deve existir; `quantity` deve ser positiva, com até três casas decimais; `unitPrice` deve ser positivo, com até duas. O backend salva a compra e um registro privado de preço, que alimentam o dashboard. Retorna **200** com `id`, `status` e `purchasedAt`.

```http
PATCH http://localhost:3000/cart/items/123e4567-e89b-42d3-a456-426614174040
Authorization: Bearer <token>
Content-Type: application/json

{
  "purchased": true,
  "marketId": "123e4567-e89b-42d3-a456-426614174030",
  "quantity": 2,
  "unitPrice": 7.50
}
```

Resposta:

```json
{
  "id": "123e4567-e89b-42d3-a456-426614174040",
  "status": "purchased",
  "purchasedAt": "2026-10-03T20:00:00.000Z"
}
```

Para **desmarcar**, envie apenas `{ "purchased": false }`. A compra ligada ao item é desfeita, inclusive o registro de preço gerado com ela. Retorna **200** com `status: "pending"` e `purchasedAt: null`. Um item inexistente retorna **404**. Se já houver outro item pendente do mesmo produto, desmarcar retorna **409**.

### `DELETE /cart/items/:id`

Remove o item da lista e retorna **200** com `{ "id": "<uuid-do-item>" }`. Se o item já estava comprado, a compra histórica permanece; para desfazer a compra, use primeiro `PATCH` com `purchased: false`. Item inexistente retorna **404**.

## Histórico e dashboard

### `GET /history`

Lista **todos os registros de preço criados pelo usuário**, do mais recente para o mais antigo. Retorna **200**:

```json
[
  {
    "id": "123e4567-e89b-42d3-a456-426614174020",
    "name": "Arroz 1 kg",
    "brand": "Marca Exemplo",
    "category": "Mercearia",
    "unit": "kg",
    "imageUrl": null,
    "price": {
      "amount": 7.5,
      "market": "Mercado Central",
      "observedAt": "2026-10-03T20:00:00.000Z"
    }
  }
]
```

### `GET /dashboard/stats`

Retorna **200** com os indicadores do usuário:

```json
{
  "marketsCompared": 3,
  "pricesRegistered": 18,
  "savings": 12.4,
  "trackedProducts": 7
}
```

`marketsCompared` conta mercados distintos nos registros de preço do usuário; `pricesRegistered`, todos os registros de preço dele; `trackedProducts`, produtos distintos nesses registros. Essas três contagens abrangem todo o histórico. `savings` considera somente **compras do mês atual** e compara o preço pago por item com a média dos preços observados para o mesmo produto no mês (preços públicos ou privados do usuário); só soma diferenças positivas. É uma estimativa, não um desconto registrado no caixa. Os limites do mês são calculados no fuso do servidor.

### `GET /dashboard/summary`

Retorna **200** com até cinco produtos mais comprados **no mês atual**, ordenados pela quantidade comprada:

```json
[
  {
    "productId": "123e4567-e89b-42d3-a456-426614174010",
    "name": "Arroz 1 kg",
    "imageUrl": null,
    "totalQuantity": 4,
    "totalAmount": 30
  }
]
```

`totalAmount` é a soma dos valores pagos pelos itens desse produto. Sem compras no mês, retorna `[]`. Apenas colocar um produto no carrinho não cria uma compra; é preciso confirmá-la com `PATCH /cart/items/:id`.

## Gráficos de preços

### `GET /graphics/products`

Lista produtos que têm pelo menos um preço público ou registrado pelo usuário. Retorna **200** com itens `{ "id", "name", "brand", "unit" }`. `brand` pode ser `null`.

### `GET /graphics/price-history`

Parâmetros de query obrigatórios: `productId` (UUID) e `period` (`30d`, `3m`, `6m` ou `1y`). O histórico desta rota usa **registros do próprio usuário** no período, em ordem cronológica. Retorna **200**:

```http
GET http://localhost:3000/graphics/price-history?productId=123e4567-e89b-42d3-a456-426614174010&period=30d
Authorization: Bearer <token>
```

```json
{
  "product": {
    "id": "123e4567-e89b-42d3-a456-426614174010",
    "name": "Arroz 1 kg",
    "unit": "kg"
  },
  "summary": {
    "minPrice": 7.5,
    "maxPrice": 8.5,
    "currentPrice": 8.5,
    "variation": 13.33
  },
  "history": [
    {
      "id": "123e4567-e89b-42d3-a456-426614174020",
      "price": 7.5,
      "observedAt": "2026-10-01T20:00:00.000Z",
      "market": {
        "id": "123e4567-e89b-42d3-a456-426614174030",
        "name": "Mercado Central"
      }
    }
  ]
}
```

`variation` é a variação percentual entre o primeiro e o último preço do período. Sem registros, os quatro valores de `summary` são `0` e `history` é `[]`.

### `GET /graphics/compare-markets`

`productId` (UUID) é obrigatório. `sortBy` é opcional: `price_asc` (padrão), `price_desc` ou `name_asc`. `marketId` (UUID) filtra um mercado específico. A comparação usa o **registro mais recente do próprio usuário por mercado**, sem limite de período. Retorna **200**:

```http
GET http://localhost:3000/graphics/compare-markets?productId=123e4567-e89b-42d3-a456-426614174010&sortBy=price_asc
Authorization: Bearer <token>
```

```json
{
  "product": {
    "id": "123e4567-e89b-42d3-a456-426614174010",
    "name": "Arroz 1 kg",
    "unit": "kg"
  },
  "cheapestMarket": {
    "marketId": "123e4567-e89b-42d3-a456-426614174030",
    "marketName": "Mercado Central",
    "price": 7.5
  },
  "markets": [
    {
      "marketId": "123e4567-e89b-42d3-a456-426614174030",
      "marketName": "Mercado Central",
      "price": 7.5,
      "lastUpdated": "2026-10-03T20:00:00.000Z",
      "diffFromCheapestPercentage": 0,
      "isCheapest": true
    }
  ]
}
```

Sem preços, `cheapestMarket` é `null` e `markets` é `[]`.

### `GET /graphics/price-evolution`

`productId` (UUID) é obrigatório; `months` aceita de 1 a 12 e assume `7` quando omitido. Retorna **200** com um ponto para cada mês, no formato `YYYY-MM`. Esta rota usa preços públicos e os privados do usuário.

```http
GET http://localhost:3000/graphics/price-evolution?productId=123e4567-e89b-42d3-a456-426614174010&months=3
Authorization: Bearer <token>
```

```json
{
  "product": {
    "id": "123e4567-e89b-42d3-a456-426614174010",
    "name": "Arroz 1 kg",
    "unit": "kg"
  },
  "months": [
    { "month": "2026-08", "averagePrice": 8.2, "lowestPrice": 7.9 },
    { "month": "2026-09", "averagePrice": null, "lowestPrice": null },
    { "month": "2026-10", "averagePrice": 7.8, "lowestPrice": 7.5 }
  ]
}
```

## Erros

Erros da aplicação seguem este formato:

```json
{
  "statusCode": 400,
  "code": "VALIDATION_ERROR",
  "message": "Dados inválidos."
}
```

Os códigos HTTP mais comuns são **400** (dados inválidos, código de recuperação inválido ou expirado), **401** (credenciais ou token inválido), **404** (produto ou item de carrinho inexistente), **409** (conflito, como conta já cadastrada) e **500** (falha interna, inclusive falha inesperada no serviço de e-mail). O campo `code` permite tratar o erro sem depender do texto de `message`.
