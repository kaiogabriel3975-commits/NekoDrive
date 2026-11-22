[Uploading README.md…]()
# NekoDrive

App Android (Kotlin + Jetpack Compose) para ver vídeos e fotos do seu Google Drive, organizado por pastas.
Roda só no seu aparelho: não existe servidor do app, e o acesso ao Drive usa a sua própria conta e o seu próprio
projeto no Google Cloud.

## O que o app faz

- **Navegação:** pastas do Meu Drive em grade ou lista, ordenação por nome, data ou tamanho, busca por nome em todo o Drive e várias contas Google.
- **Fotos:** visualizador em tela cheia com zoom por pinça e toque duplo, giro, informações do arquivo, GIFs e imagens animadas. A resolução da foto se ajusta ao nível do aparelho (entrada, intermediário ou topo).
- **Vídeos:** player em tela cheia com legendas (embutidas e arquivos `.srt` ou `.vtt` ao lado do vídeo), velocidade, escolha de resolução, Picture-in-Picture, retomar de onde parou e Chromecast.
- **Cache e offline:** o que você assiste e baixa fica em um cache único, com limite ajustável (padrão 5 GB). Itens salvos abrem sem internet. Se o arquivo for editado no Drive, a cópia antiga do cache é descartada (o app compara o md5 do conteúdo).
- **Organização:** favoritos (só no app, separados por conta), lixeira do Drive com restaurar e excluir definitivamente, mover, copiar, renomear, criar pasta, enviar arquivos, compartilhar e abrir em outro app ou player.
- **Idiomas:** português e inglês. Em Configurações > Idioma / Language escolha Automático (segue o idioma do aparelho), Português ou English. O texto que ainda não tiver tradução aparece em português.
- **Sincronização:** favoritos, capas de pasta e ajustes do app ficam guardados no seu próprio Drive e voltam sozinhos ao reinstalar o app ou entrar em outro aparelho (veja abaixo).
- **Miniaturas:** GIFs grandes ganham uma versão leve em cache depois da primeira vez, para gastar menos dados e memória.

Requer **Android 12 ou mais novo** (minSdk 31).

## Como instalar

1. **Baixar o APK** das *Releases* deste repositório, quando houver um publicado. Ele é assinado pelo autor, então o SHA-1 que o app mostra na tela de configuração é sempre o mesmo.
2. **Ou compilar o seu**, pelo GitHub Actions (seção mais abaixo). Nesse caso o SHA-1 é o da sua própria chave.

Em qualquer caso, antes de entrar no Drive você precisa do passo seguinte.

## Configurar o login (uma vez)

O Google exige que todo app que acessa o Drive use um **cliente OAuth** criado dentro de um projeto do Google Cloud.
Aqui cada pessoa cria o seu (é grátis), cola o ID no app e pronto. Assim não há limite de usuários do autor e ninguém
além de você tem acesso ao seu projeto. Os nomes dos menus do Google mudam de vez em quando.

1. Em [console.cloud.google.com](https://console.cloud.google.com), crie um projeto.
2. Em **APIs e serviços > Biblioteca**, ative a **Google Drive API**.
3. Em **Google Auth Platform** (tela de consentimento OAuth), configure o app:
   - Público: **Externo**.
   - Escopo: `https://www.googleapis.com/auth/drive`.
   - Status: **publique como "Em produção"**. Em "Em teste", só entram as contas listadas como usuários de teste e a autorização expira em 7 dias.
4. Em **Clientes**, crie um cliente do tipo **Android**:
   - Nome do pacote: `com.nekodrive.app`
   - SHA-1: o que o app mostra (veja o passo 5) ou o da sua keystore.
5. Abra o cliente criado, vá em **Configurações avançadas** e ligue **Ativar esquema de URI personalizado**. Sem isso o Google responde "Erro 400: invalid_request". A mudança pode levar alguns minutos para valer.
6. Copie o **ID do cliente** (termina em `.apps.googleusercontent.com`).
7. No app, toque em entrar (ou vá em **Configurações > Conta > ID do cliente do Google**). A tela mostra o pacote e o SHA-1 do app, com botão de copiar, e tem o campo para colar o ID.

Na primeira vez o Google mostra "o app não foi verificado". É o seu próprio projeto e só você o usa:
toque em **Avançado** e continue. O limite de 100 usuários do modo sem verificação não atrapalha, porque são só as suas contas.

### Como o login funciona

- O login abre uma aba do navegador (AppAuth, código de autorização com PKCE). Qualquer conta Google serve, mesmo uma que não esteja no aparelho.
- O app guarda um **token de renovação** criptografado (AES-GCM, chave no Android Keystore, fora do backup). Com ele o acesso se renova sozinho, sem abrir o navegador. Se o Google invalidar o acesso, o app reabre o login na mesma conta.
- Remover uma conta no app apaga os dados dela do aparelho. Para revogar o acesso também no Google, use [myaccount.google.com/permissions](https://myaccount.google.com/permissions).
- Para um build seu que já nasça configurado, ponha o ID em `CLIENT_ID_PADRAO` em
  `app/src/main/java/com/nekodrive/app/data/auth/OAuthConfig.kt`. Em repositório público, deixe vazio.

### Se der erro no login

| Mensagem | O que fazer |
|---|---|
| Erro 400: invalid_request, "Custom URI scheme is not enabled" | Passo 5: ligar o esquema de URI personalizado no cliente Android e esperar alguns minutos |
| Acesso bloqueado, app em teste | Publicar como "Em produção" ou adicionar a conta como usuário de teste |
| Volta a pedir login a cada 7 dias | O projeto ainda está em "Em teste" |
| Erro de desenvolvedor ou cliente inválido | Conferir o pacote, o SHA-1 e o ID colado; o SHA-1 muda se o APK for assinado por outra chave |
| Mudou de ID do cliente | As contas pedem login de novo, é esperado |

## Compilar pelo GitHub Actions

1. Crie um repositório **privado** seu com o conteúdo deste projeto (branch `main`). Ele precisa ser privado porque o
   workflow que gera a chave entrega um arquivo com a senha.
2. O workflow **Build APK** roda a cada push e também manualmente (aba Actions). O APK fica em **Artifacts > nekodrive-apk**.
3. Sem os secrets abaixo, o APK sai assinado com a chave de debug, que **muda a cada build**. Serve para ver o visual, mas
   o login do Google não funciona direito porque o SHA-1 muda. Para usar de verdade, crie uma chave fixa.

### Chave de assinatura (fazer uma vez, guardar bem)

**Pelo GitHub, sem PC:**

1. Em **Actions > Gerar keystore > Run workflow** (o arquivo precisa estar na branch `main`).
2. Ao terminar, a execução mostra o **SHA-1**. É ele que vai no cliente Android do Google Cloud.
3. Baixe o artefato **keystore-nekodrive** (fica 3 dias) e **guarde uma cópia**.
4. Crie os 4 secrets em Settings > Secrets and variables > Actions, com os valores de `credenciais.txt`:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Atalho opcional: um token fino do GitHub, só para este repositório, com permissão **Secrets: Read and write**, salvo como
secret `GH_PAT`. Assim o workflow grava os 4 secrets sozinho. O workflow se recusa a rodar se `KEYSTORE_BASE64` já existir,
para você não trocar a chave por acidente (trocar obriga a desinstalar o app e perde favoritos e cache locais).

**No computador:**

    keytool -genkeypair -v -keystore nekodrive.jks -alias nekodrive \
      -keyalg RSA -keysize 2048 -validity 10000
    base64 -w0 nekodrive.jks        # copie a saída para o secret KEYSTORE_BASE64
    keytool -list -v -keystore nekodrive.jks -alias nekodrive    # mostra o SHA-1

Nunca envie o `.jks` ao repositório (já está no `.gitignore`).

## Sincronização de ajustes, favoritos e capas

O que só o app enxerga (favoritos, capas de pasta e ajustes) é guardado no Drive de cada conta, na pasta **etc** da
raiz do Meu Drive, em um único arquivo: **configNkapp.txt**. Ele é compactado (gzip) para ocupar o mínimo e não foi feito
para ser aberto ou editado à mão. Não apague a pasta se quiser manter esses dados.

- **Quando carrega:** ao abrir o app (alguns segundos depois, em segundo plano) e ao adicionar uma conta.
- **Quando salva:** cerca de 30 segundos depois da última mudança e quando o app vai para segundo plano. Só sobe se algo
  mudou de verdade.
- **O que não viaja:** tokens de acesso, o ID do cliente do Google, o limite do cache (depende do espaço de cada aparelho),
  o aviso de notificação, e posição e "assistido" dos vídeos.
- **Conflitos:** os ajustes valem para o app todo e vence o mais recente (usa o relógio dos aparelhos). Favoritos e capas
  são por conta: se só o Drive mudou, ele vence; se os dois lados mudaram, os dois são juntados.
- **Privacidade:** o arquivo fica na sua conta, no seu Drive, e nenhum servidor do app o vê. Remover uma conta do app não
  apaga o arquivo; para apagar de vez, exclua a pasta **etc** no Drive.

## Uso

- **Pastas:** toque abre; segurar seleciona vários itens (baixar, favoritar, mover para a lixeira). Puxar a lista para baixo atualiza.
- **Foto:** deslizar troca de foto, pinça ou toque duplo dá zoom, arrastar para baixo fecha.
- **Vídeo:** toque mostra ou esconde os controles, toque duplo nas laterais pula 5 a 30 s (ajustável), segurar acelera, arrastar para baixo fecha.
- **Download:** selecione e use o botão de baixar. Arquivos que não são mídia vão para a pasta Downloads do Android. Baixar de novo retoma de onde parou.
- **Chromecast:** celular e TV na mesma rede Wi-Fi. A TV busca o vídeo em um servidor local no celular, que repassa o conteúdo do Drive com o seu token. O servidor fecha sozinho quando não é mais usado.
- **Configurações:** tema, grade ou lista, blur, animações, resolução do vídeo no Wi-Fi e nos dados, limite do cache, exportar logs de diagnóstico.

## Limitações conhecidas

- Formatos que o ExoPlayer não toca (como AVI) tentam a **versão convertida** do Drive. Ela usa um endereço **não oficial** (`get_video_info`) que pode mudar ou parar de funcionar, e não vale para legendas laterais.
- O Android pode limpar o cache do app quando falta espaço.
- O Google pode mudar as regras do OAuth e do consentimento. Confira o estado atual no console.

## Para quem desenvolve

- Kotlin + Jetpack Compose, injeção manual simples (`AppContainer`), minSdk 31, targetSdk 35 (a permissão de rede local do Chromecast no Android 17 vem implícita; se o targetSdk subir para 37 ou mais, será preciso pedi-la).
- Principais pastas em `app/src/main/java/com/nekodrive/app`: `ui/drive` (tela principal e `DriveViewModel`), `ui/galeria` (fotos e vídeos), `ui/player` (Chromecast), `data/drive` (API do Drive via OkHttp), `data/cache` (cache de mídia, servidor local e GIF leve), `data/auth` (login e tokens).
- Blur com a biblioteca Haze 1.5.4 (ligado também no Android 12). Haze 2.x e 1.7 exigem Kotlin e Compose mais novos que os do projeto.
- O ambiente de desenvolvimento do autor não compila localmente: quem compila é o GitHub Actions (passo "Compilar").

## Roteiro de teste

1. Entrar com duas contas (uma delas que não esteja no sistema Android) e trocar entre elas; fechar e abrir o app mais de 1 hora depois e conferir que segue logado.
2. Abrir pastas, voltar, buscar, ordenar, alternar grade e lista.
3. Foto: deslizar, zoom, girar, copiar, detalhes, fechar puxando para baixo.
4. Vídeo: tocar, pular, segurar para acelerar, legendas, Picture-in-Picture, retomar de onde parou.
5. Baixar um vídeo, desligar a internet e abrir de novo; mudar o limite de GB e limpar o cache.
6. Favoritar, ver na aba Favoritos; mover para a lixeira, restaurar, excluir e esvaziar.
7. Pasta com GIFs grandes: rolar, fechar e abrir de novo (a segunda vez deve carregar mais leve).
8. Chromecast na mesma rede Wi-Fi e um formato que o player não toca (versão convertida).

## Licença

Apache License 2.0, com os créditos a **Kyle_Neeko**. Você pode usar, alterar e redistribuir o código, inclusive em
projetos comerciais, desde que mantenha o arquivo `LICENSE`, o arquivo `NOTICE` (com o crédito) e avise nos arquivos
que modificar. O código é fornecido como está, sem garantia: use por sua conta e risco, principalmente em operações
que alteram o seu Drive (mover, apagar, enviar).

