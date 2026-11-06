# nekodrive

App Android (Kotlin + Jetpack Compose) para ver vídeos e fotos do Google Drive.
Etapa 1: projeto base (tema Material You, cabeçalho, barra inferior, ícones e build no GitHub).

## Compilar pelo GitHub Actions

1. Crie um repositório **privado** e envie o conteúdo desta pasta (branch `main`).
2. O workflow `Build APK` roda a cada push e também manualmente (aba Actions).
3. O APK fica em **Artifacts > nekodrive-apk** ao fim do build.

Sem os secrets abaixo, o APK sai assinado com a chave de debug. Funciona para testar o visual,
mas o login do Google (etapa 2) exige a chave fixa.

## Chave de assinatura (fazer uma vez, guardar bem)

    keytool -genkeypair -v -keystore nekodrive.jks -alias nekodrive \
      -keyalg RSA -keysize 2048 -validity 10000
    base64 -w0 nekodrive.jks        # copie a saída para o secret KEYSTORE_BASE64

Em Settings > Secrets and variables > Actions, crie:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (nekodrive) e `KEY_PASSWORD`.

SHA-1 para o cliente OAuth do Google Cloud (etapa 2):

    keytool -list -v -keystore nekodrive.jks -alias nekodrive

Nunca envie o `.jks` ao repositório (já está no `.gitignore`).

## Estado atual

- minSdk 31 (Android 12), pacote `com.nekodrive.app`
- Barras translúcidas; o blur real (Haze) entra no polimento
- Botões de conta e configurações ainda sem ação

## Etapa 2: login e listagem do Drive

Para o login funcionar, configure o Google Cloud (uma vez):

1. Crie um projeto em console.cloud.google.com e ative a **Google Drive API**.
2. Em **Google Auth Platform** (tela de consentimento): tipo Externo, adicione o escopo
   `https://www.googleapis.com/auth/drive`.
3. Em **Clientes**, crie um cliente **Android** com o pacote `com.nekodrive.app` e o **SHA-1 da sua keystore fixa**
   (veja o comando acima). Com a chave de debug o login falha (erro de desenvolvedor).
4. Status do app:
   - **Em teste:** só contas listadas como usuários de teste entram (adicione cada conta) e a autorização expira em 7 dias.
   - **Em produção (sem verificação):** qualquer conta entra, com aviso de app não verificado e limite de 100 usuários.
     É a opção indicada para uso pessoal. Confirme as regras atuais no console.

Estado atual da etapa 2:
- Várias contas: botão de contas no cabeçalho (trocar, adicionar, remover)
- Pastas e arquivos do Meu Drive em grade com miniaturas, navegação por pastas e botão voltar
- Tocar em foto ou vídeo ainda não faz nada (etapas 4 e 5)
- Sem Hilt: usei injeção manual simples (AppContainer)

## Gerar a keystore pelo GitHub (sem PC)

1. Em **Actions > Gerar keystore > Run workflow** (o arquivo precisa estar na branch `main`).
2. Ao terminar, abra a execução: o resumo mostra o **SHA-1** (cole no cliente OAuth do Google Cloud).
3. Baixe o artefato **keystore-nekodrive** (fica 3 dias) e **guarde uma cópia**. Ele tem a keystore e as senhas.
4. Crie os 4 secrets do build com os valores de `credenciais.txt`:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Atalho opcional: crie um token fino em GitHub > Settings > Developer settings, só para este repositório, com permissão
**Secrets: Read and write**, e salve como secret `GH_PAT`. Assim o workflow grava os 4 secrets sozinho.

O workflow se recusa a rodar se `KEYSTORE_BASE64` já existir, para você não trocar a chave por acidente.

## Etapa 3: navegação

- Alternar grade/lista, ordenar por nome, data ou tamanho (toque de novo para inverter) e busca por nome em todo o Drive
- A pasta se expande ao tocar (transição de elemento compartilhado); voltar restaura a lista na hora
- Toque longo seleciona vários itens; por enquanto a ação em lote é mover para a lixeira (com confirmação)
- Favoritar e baixar entram na barra de seleção nas etapas 6 e 7

## Etapa 4: visualizador de fotos

- Tocar numa foto abre o visualizador em tela cheia, com a foto expandindo a partir da miniatura
- Deslizar para os lados troca de foto (mola ao soltar); pinça dá zoom; arrastar para baixo fecha
- Toque na foto mostra/esconde as barras. Botões: girar (só visual), copiar a imagem e detalhes
- Detalhes mostram todos os campos que a API do Drive devolve
- A rolagem de cada pasta agora é guardada (ao voltar de uma pasta ou fechar uma foto)
