# FinApp Listener

App Android companion minimalista para o [FinApp](https://github.com/nicolasdtb/finapp).
Escuta notificações do Nubank, Banco Inter e Bradesco e envia para o webhook
do FinApp — substitui a macro do Automate.

## O que ele faz

- Escuta notificações via `NotificationListenerService` (só dos 3 apps monitorados,
  e só dos que você marcar como ativos na tela)
- Monta o mesmo JSON usado hoje no fluxo do Automate:
  ```json
  {"app_name": "Nubank", "title": "...", "text": "..."}
  ```
- Envia via `POST` para `https://172.23.17.157/api/v1/webhooks/bank-notification`

## Como compilar (sem Android Studio, via GitHub Actions)

1. Crie um repositório novo no GitHub (ou uma pasta dentro de um existente) e suba este projeto:
   ```bash
   git init
   git add .
   git commit -m "FinApp Listener v1"
   git remote add origin <url-do-seu-repo>
   git push -u origin main
   ```
2. O workflow em `.github/workflows/build.yml` roda automaticamente a cada push
   na branch `main` (ou pode ser disparado manualmente pela aba **Actions** do
   GitHub, botão **Run workflow**).
3. Quando o build terminar, abra a execução em **Actions**, role até
   **Artifacts** e baixe `finapp-listener-debug` — dentro tem o `app-debug.apk`.
4. Transfira o APK pro celular (Google Drive, Telegram, e-mail — o que for mais
   fácil) e instale (pode ser necessário habilitar "instalar de fontes
   desconhecidas" nas configurações do Android).

## Primeira configuração no celular

1. Abra o app **FinApp Listener**
2. Marque quais bancos você quer monitorar (Nubank / Inter / Bradesco)
3. Toque em **"Ativar acesso a notificações"** — isso abre a tela do sistema
   Android; procure **"FinApp Listener"** na lista e habilite o acesso
4. Volte pro app — o texto de status deve indicar que o acesso foi concedido

A partir daí o serviço roda sozinho em segundo plano, mesmo com o app fechado.

## Ajustando a URL do webhook

Por padrão está fixo em `AppConfig.kt` (`WEBHOOK_URL_DEFAULT`). Se precisar
trocar sem recompilar, dá pra evoluir isso depois para uma tela de configurações
que grava um valor customizado via `AppConfig.setAppEnabled`-style em
SharedPreferences (o método `AppConfig.webhookUrl()` já está preparado para ler
um valor salvo, se algum dia for adicionado um campo de edição na UI).

## Limitações da v1

- Sem fila local de retry: se o POST falhar (ex: fora da VPN), a notificação
  é perdida — não há fallback como o `nubank_queue.jsonl` do fluxo do Automate.
  Pode ser adicionado depois (gravar em arquivo local + um `WorkManager`
  periódico para tentar reenviar, similar à lógica já usada no Automate).
- Apps monitorados são fixos no código (3 bancos). Para adicionar um app novo,
  edite `AppConfig.MONITORED_APPS` e o layout/MainActivity, e recompile.
