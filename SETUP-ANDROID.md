# Compras Local — app Android (Local-First)

Gestão de listas de compras, leitura de QR Code da **NFC-e** (consulta SEFAZ só na leitura), comparativo de preços, histórico, categorias automáticas, sugestão de reposição e exportação CSV.

## Abrir no Android Studio

1. **File → Open** → pasta `compras_local`
2. Aguarde o **Gradle Sync**
3. **Run** no emulador ou celular (mesmas regras de depuração USB do projeto anterior)

## Uso rápido

| Aba | Função |
|-----|--------|
| Início | Últimas compras e sugestões de reposição |
| Listas | Criar lista e marcar itens (cruzamento após importar nota) |
| NFC-e | Escanear QR da nota (internet só aqui) |
| Preços | Comparativo por loja (verde/vermelho) e gráfico |
| Backup | Exportar CSV e compartilhar (Drive, e-mail, etc.) |

**Botão + na aba NFC-e:** importa uma compra **demo offline** (sem internet), útil para testar.

## Stack

- Kotlin + Jetpack Compose + Material 3
- **Room** (SQLite) — 100% offline após importação
- CameraX + ML Kit (QR)
- OkHttp — consulta portal SEFAZ na leitura da nota
- Vico — gráfico de histórico

## NFC-e Santa Catarina (importante)

A SEFAZ/SC (`sat.sef.sc.gov.br`) costuma exibir **captcha** para acessos automáticos. Por isso:

1. Escaneie o QR ou cole a **URL completa** do cupom
2. Se aparecer erro de captcha, toque em **Consultar no site**
3. Resolva o captcha na WebView e depois **Importar nota desta página**

O backup CSV só terá notas reais depois de importar cupons verdadeiros (não só a demo).

## Pacote

`br.edu.unoesc.compraslocal`
