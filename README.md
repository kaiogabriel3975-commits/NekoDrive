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

## Etapa 5: player de vídeo

- Tocar num vídeo abre o player em tela cheia (mesma animação de expansão das fotos)
- Streaming direto do Drive com o token da conta (renovado durante a reprodução)
- Controles: play/pause, ±10 s, barra de posição, velocidade (0,5x a 2x), legendas, Picture-in-Picture e girar a tela
- Gestos: toque mostra/esconde controles, toque duplo avança/volta 10 s, segurar acelera 2x, pinça dá zoom, arrastar para baixo fecha
- Legendas: faixas embutidas e arquivos .srt/.vtt com o mesmo nome na mesma pasta
- Retoma de onde parou; passou de 90% vira "assistido" (usado na etapa 6, cache e limpeza)
- Formatos que o ExoPlayer não toca (ex.: AVI) mostram erro; a versão convertida do Drive entra na etapa 8
- Chromecast: etapa 8

## Etapa 6: cache, downloads e configurações

- Um cache único (pasta de cache do app) guarda tudo que é assistido e tudo que é baixado, para vídeos e fotos
- Baixar: toque longo para selecionar e use o botão de baixar (só arquivos, não pastas); no visualizador de fotos há um botão de baixar
- Itens salvos mostram um ícone de download concluído; vídeos assistidos (90% ou mais) mostram um ícone de visto; o download mostra o progresso
- Limite de GB ajustável em Configurações (padrão 5 GB). Ao passar do limite, o que foi usado há mais tempo é apagado primeiro
- Vídeos e fotos já salvos abrem sem internet
- Configurações: tela inicial (grade ou lista), contas, limite e limpar cache
- O Android pode limpar o cache do app quando falta espaço
- Downloads continuam com o app em segundo plano enquanto o Android mantiver o processo; baixar de novo retoma de onde parou

## Etapa 7: favoritos e lixeira

- Favoritar: botão de estrela na seleção em lote, no visualizador de fotos e no player. Só arquivos (não pastas)
- Favoritos ficam só no app (não mexem na estrela do Drive), separados por conta; itens favoritos mostram uma estrela na miniatura
- Aba Favoritos: grade ou lista; abre fotos e vídeos; avisa quando um favorito não existe mais no Drive e permite limpar a lista
- Lixeira: lista os itens da lixeira do Drive; toque longo seleciona; restaurar, excluir definitivamente e esvaziar (com duas confirmações)
- Mover para a lixeira (aba Drive ou Favoritos) tira o item dos favoritos
- Restaurar atualiza a aba Drive

## Etapa 8: Chromecast e versão convertida

- Ícone de Cast na barra do player: escolhe o dispositivo da rede; o vídeo passa a tocar na TV e o celular vira controle (pausar, ±10 s, barra, parar). Parar continua o vídeo no celular de onde estava na TV
- A TV busca o vídeo em um servidor local no celular, que repassa o conteúdo do Drive com o token da conta. Celular e TV precisam estar na mesma rede Wi-Fi
- Formatos que o receptor padrão do Chromecast não toca (por exemplo MKV) tentam a versão convertida do Drive
- Versão convertida: se o player local não tocar o arquivo (AVI, codecs sem suporte), o app tenta sozinho a versão mp4 que o Drive gera. Usa um endereço NÃO oficial do Drive (get_video_info), que pode mudar ou parar de funcionar; legendas laterais não valem nesse modo
- Android 17: o app mira o SDK 35, então a permissão de rede local (ACCESS_LOCAL_NETWORK) é concedida implicitamente. Se um dia o targetSdk subir para 37 ou mais, será preciso pedi-la

## Etapa 9: polimento

- Vidro com desfoque de verdade (biblioteca Haze 1.5.4) na barra superior, na faixa de ferramentas de cada aba e na barra inferior: as listas rolam por baixo delas
- O desfoque usa a cor do tema (Material You) com transparência
- Vibração leve ao selecionar com toque longo
- No Android 12 exato (não 12L), o Haze 1.5 deixa o desfoque desligado por padrão e mostra só a cor translúcida; do Android 12L em diante (inclusive o 17) o desfoque funciona
- Haze 2.x e 1.7 exigem Kotlin e Compose mais novos que os do projeto; atualizar o conjunto todo fica como tarefa futura

## Roteiro de teste final (Android 12 e Android 17)

1. Entrar com duas contas e trocar entre elas
2. Abrir pastas (animação de expansão), voltar, buscar, ordenar, alternar grade e lista
3. Foto: abrir, deslizar entre fotos, zoom com pinça, girar, copiar (colar em outro app), detalhes, fechar puxando para baixo
4. Vídeo: tocar, ±10 s, segurar para acelerar, legendas laterais, Picture-in-Picture, retomar de onde parou
5. Baixar um vídeo, desligar a internet e abrir de novo; mudar o limite de GB e limpar o cache
6. Favoritar, ver na aba Favoritos; mover para a lixeira, restaurar, excluir e esvaziar
7. Chromecast na mesma rede Wi-Fi; formato que o player não toca (versão convertida)

## Atualização de UI (documento de especificação)

- ActionBar nativa removida (tema) e edge-to-edge sem a faixa escura nas barras do sistema
- Cabeçalho com gradiente de blur (Haze, desfoque progressivo); no paisagem, cartão flutuante ao lado da NavigationRail
- Toolbar flutuante (pílula com vidro, some ao rolar para baixo) no retrato; NavigationRail no paisagem
- Botão Drive, quando já está na aba, volta ao início (raiz e topo da lista); breadcrumb removido
- Animação de pasta parte do card cinza da miniatura, com a mesma duração (500 ms) e curva suave estilo iOS
- Galeria única: fotos e vídeos da pasta na mesma sequência (deslize horizontal), com dissolução com blur entre tipos
- Fotos: nome no topo (uma linha, marquee a 24 FPS), pílula inferior com girar, copiar, baixar, favoritar, informações e excluir; sem botão de copiar em GIF
- GIFs e imagens animadas (Coil com decodificador de imagens animadas)
- Fechar puxando para baixo: a imagem encolhe em direção ao centro, sem acompanhar o dedo por inteiro, com perspectiva 3D (topo para trás, base como referência)
- Botões somem com fade + blur (390 ms: 90 ms originais + 300 ms)
- Vídeo: sem botões de ±10 s; toque duplo ±10 s, segurar 2x, mensagens de 500 ms no topo; pausa à esquerda e giro à direita da barra de progresso em círculos de vidro; menu de três pontos (velocidade, transmitir, legendas, informações); pílula com favoritar, baixar, PiP e excluir; barras do sistema só se escondem depois de carregar e acompanham os controles
- Menus de informações só mostram o que o Drive confirma (data de criação, envio, nome, local, tamanho, resolução, formato, dispositivo/câmera quando houver EXIF, conta proprietária, link); FPS vem do player
- Arquivos que o app não abre oferecem abrir com outro app (também no erro do player)
- Ícones arredondados (Material Icons Rounded)

## Ajustes de comportamento (segunda rodada de UI)

- Botão Drive: volta à raiz pasta por pasta, com a animação normal de fechar a cada passo
- Só a página ativa da galeria é elemento compartilhado (sem miniaturas "duplas" ao abrir); a mídia expande opaca, sem fade
- Fechamento integrado (gesto, botão de voltar ou círculo): encolher suave + perspectiva sutil (aceleração) + voo até a miniatura ao mesmo tempo
- Cantos da miniatura mudam aos poucos para cantos retos (galeria e tela da pasta)
- Pasta: o card (miniatura + legenda, mesma cor) vira a tela da pasta, com o mesmo formato e a mesma cor durante a animação
- Botões de busca, ordem e grade/lista, voltar e seleção em círculos/pílulas de vidro (sem barra de fundo)
- Todos os blurs com +35% de saturação; menus do player com blur (feitos na própria composição)
- Cor mais frequente da mídia tinge o card e o texto da grade, aos poucos, quando a miniatura carrega
- GIFs e imagens animadas em loop (miniaturas em baixa resolução, sem reduzir os quadros)
- Toque duplo: zoom nas fotos; no vídeo, laterais ±10 s e centro aplica/reduz o zoom
- Giro de foto com zoom out suave e ajuste exato à proporção da imagem
- Bounce e atualizar puxando a lista (quadrado de vidro com mola); grade e lista trocam com deslize suave
- Favoritar pastas (abrem no Drive pelo Favoritos); compartilhar; abrir em outro player (streaming local) ou outro app
- Arquivos que não são mídia baixam para a pasta Downloads do Android
- Player: resolução original e versões convertidas disponíveis; padrão 1080p no Wi-Fi e 540p (ou a mais próxima abaixo) em dados, com ajuste automático pela rede enquanto você não escolher
