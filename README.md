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

## Resistência a ser encerrado pelo sistema (importante em Samsung/Xiaomi/etc)

O serviço agora roda como **foreground service**, com uma notificação
persistente e silenciosa ("FinApp Listener ativo"). Isso aumenta bastante a
prioridade do processo perante o gerenciador de memória do Android, mas
**não elimina totalmente** o risco de o sistema encerrá-lo — fabricantes como
Samsung têm camadas próprias de gerenciamento de bateria bem agressivas.

Para reduzir ainda mais o risco de o serviço parar silenciosamente:

1. **Configurações → Apps → FinApp Listener → Bateria** → mude para
   **"Sem restrições"**
2. **Configurações → Cuidados com o dispositivo → Bateria** → desative
   *"Colocar apps não usados em repouso"* para o FinApp Listener (ou
   adicione-o à lista de apps que nunca hibernam)
3. Na lista de **apps recentes**, toque no ícone de cadeado no card do
   FinApp Listener para impedir que ele seja descartado da memória

Se a notificação "FinApp Listener ativo" nunca aparecer, confira se a
permissão de notificações foi concedida (o app pede isso na primeira
abertura, a partir do Android 13) — sem ela a notificação simplesmente não
é exibida, embora o serviço ainda tente rodar.

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
