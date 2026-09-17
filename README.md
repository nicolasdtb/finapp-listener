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

## Fallback de conectividade (fila local)

Se o POST falhar (ex: celular fora da VPN no momento da notificação), a
notificação é gravada em um arquivo local (`pending_notifications.jsonl`,
dentro da pasta privada do app). O serviço fica de olho em mudanças de rede
(equivalente ao trigger "Network state changed" do Automate) e, assim que uma
rede com internet fica disponível, tenta reenviar tudo que estiver pendente —
com uma pequena espera de 2s para dar tempo da rota da VPN se estabilizar.

Itens que falharem de novo no reenvio voltam para a fila, sem serem perdidos.
A tela principal mostra se há algo pendente no momento.

## Limitações da v1

- Apps monitorados são fixos no código (3 bancos). Para adicionar um app novo,
  edite `AppConfig.MONITORED_APPS` e o layout/MainActivity, e recompile.
- A fila local não tem limite de tamanho nem expiração — em uso normal isso
  não deve ser um problema (poucas notificações por dia), mas vale saber que
  ela cresce indefinidamente se o app ficar muito tempo sem conseguir enviar.
