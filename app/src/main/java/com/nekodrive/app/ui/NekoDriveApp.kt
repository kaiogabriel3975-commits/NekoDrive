package com.nekodrive.app.ui

import android.app.Activity
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import com.nekodrive.app.data.auth.OAuthConfig
import com.nekodrive.app.ui.galeria.AmbienteLuz
import com.nekodrive.app.ui.galeria.CirculoVidro
import com.nekodrive.app.ui.galeria.LocalLuz
import com.nekodrive.app.ui.galeria.LocalLuzPrincipal
import com.nekodrive.app.ui.galeria.LocalLuzSoToolbars
import com.nekodrive.app.ui.galeria.reflexoLuz
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nekodrive.app.NekoDriveApplication
import com.nekodrive.app.ui.drive.ContasSheet
import com.nekodrive.app.ui.drive.DialogDownloadPasta
import com.nekodrive.app.ui.drive.SeletorCapa
import com.nekodrive.app.ui.drive.DriveScreen
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.drive.FotosAbertas
import com.nekodrive.app.ui.favoritos.FavoritosScreen
import com.nekodrive.app.ui.galeria.Galeria
import com.nekodrive.app.ui.galeria.InfoSheet
import com.nekodrive.app.ui.lixeira.LixeiraScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Destino(val rotulo: String, val icone: ImageVector, val iconeSelecionado: ImageVector) {
    Drive("Drive", Icons.Rounded.Folder, Icons.Rounded.Folder),
    Favoritos("Favoritos", Icons.Rounded.FavoriteBorder, Icons.Rounded.Favorite),
    Lixeira("Lixeira", Icons.Rounded.Delete, Icons.Rounded.Delete),
    Config("Config", Icons.Rounded.Settings, Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NekoDriveApp() {
    val context = LocalContext.current
    val container = (context.applicationContext as NekoDriveApplication).container
    val vm: DriveViewModel = viewModel(factory = viewModelFactory { initializer { DriveViewModel(container, createSavedStateHandle()) } })
    val scope = rememberCoroutineScope()

    var atual by rememberSaveable { mutableStateOf(Destino.Drive) }
    var mostrarContas by rememberSaveable { mutableStateOf(false) }
    val paisagem = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Vidro com blur: estilo geral e estilo do cabeçalho (gradiente de blur)
    val corVidro = MaterialTheme.colorScheme.surface
    val vidroEstado = remember { HazeState() }
    val fatorBlur = when (vm.blurIntensidade) { 0 -> 0.5f; 2 -> 1.5f; else -> 1f }
    val vidroEstilo = HazeStyle(
        backgroundColor = corVidro,
        tint = HazeTint(corVidro.copy(alpha = 0.55f)),
        blurRadius = 24.dp * fatorBlur,
        noiseFactor = 0f,
    )
    val vidroCabecalho = HazeStyle(
        backgroundColor = corVidro,
        tint = HazeTint(corVidro.copy(alpha = 0f)),
        blurRadius = 28.dp * fatorBlur,
        noiseFactor = 0f,
    )
    val redeWifi = rememberRedeWifi()
    val vidroCtx = remember(vidroEstado, vidroEstilo, vidroCabecalho, corVidro) { Vidro(vidroEstado, vidroEstilo, vidroCabecalho, corVidro) }

    // 1) login do Google no navegador (Custom Tab, código + PKCE); o resultado volta aqui
    val loginNavegador = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        // fechar a aba sem terminar não é erro: nada acontece
        if (res.resultCode == Activity.RESULT_OK) {
            scope.launch {
                try {
                    val login = container.auth.concluirLogin(res.data)
                    vm.aoAutorizar(login.accessToken, login.renovacao, login.validoMs)
                } catch (e: Exception) {
                    vm.mostrarErro("Falha ao entrar: ${e.message}")
                }
            }
        }
    }
    // 2) abre o login: com [email] já vem na conta certa (autorização expirada); sem ele, escolhe a conta
    // sem o ID do cliente do Google Cloud não há login: abre a tela de configuração e continua depois de salvar
    var configGoogle by remember { mutableStateOf(false) }
    var emailPendente by remember { mutableStateOf<String?>(null) }
    val autorizar: (String?) -> Unit = { email ->
        if (!OAuthConfig.configurado(context)) {
            emailPendente = email
            configGoogle = true
        } else {
            try {
                loginNavegador.launch(container.auth.intentDeLogin(email))
            } catch (e: Exception) {
                vm.mostrarErro("Não foi possível abrir o navegador para entrar: ${e.message}")
            }
        }
    }
    if (configGoogle) {
        ConfigurarGoogleDialog(
            onFechar = { configGoogle = false },
            onSalvo = {
                configGoogle = false
                // quem queria entrar continua direto no login
                autorizar(emailPendente)
            },
        )
    }
    // Autorização expirada/revogada: reabre o login para a mesma conta (uma vez por falha)
    LaunchedEffect(vm.reautorizar) {
        vm.reautorizar?.let { email ->
            vm.consumirReautorizacao()
            autorizar(email)
        }
    }
    // 3) entrar com outra conta (ou a primeira): o Google mostra a escolha, qualquer conta serve
    val entrar: () -> Unit = { autorizar(null) }

    // Toque em "Drive": se já estiver na aba, volta para o início
    val aoSelecionar: (Destino) -> Unit = { d ->
        if (d == Destino.Drive && atual == Destino.Drive) vm.irParaInicio()
        atual = d
        vm.limparSelecao()
    }

    // Busca: botão ao lado da toolbar; vai para o Drive e abre o campo de busca
    val aoBuscar: () -> Unit = {
        atual = Destino.Drive
        vm.limparSelecao()
        vm.iniciarBusca()
    }

    LaunchedEffect(vm.aviso) {
        vm.aviso?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.limparAviso()
        }
    }

    val ultimo = remember { mutableStateOf<FotosAbertas?>(null) }
    if (vm.visualizador != null) SideEffect { ultimo.value = vm.visualizador }

    // Os nomes ficam parados enquanto a galeria abre ou fecha
    var congelarNomes by remember { mutableStateOf(false) }
    LaunchedEffect(vm.visualizador != null) {
        congelarNomes = true
        delay(450)
        congelarNomes = false
    }

    CompositionLocalProvider(
        LocalVidro provides vidroCtx,
        LocalVidroAtivo provides vm.vidroAtivo,
        LocalVibrar provides vm.vibrar,
        LocalMostrarNomes provides vm.mostrarNomes,
        LocalAnimarGifs provides vm.animarGifs,
        LocalMiniaturasSoCache provides (vm.miniaturasSoWifi && !redeWifi),
        LocalReflexoPilula provides vm.reflexoPilula,
        LocalFundoCard provides vm.fundoCard,
        LocalRenovarCapa provides vm::renovarCapaVideo,
        LocalMarqueePausado provides (vm.visualizador != null || congelarNomes),
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
        SharedTransitionLayout {
            val st = this
            AnimatedContent(
                targetState = vm.visualizador != null,
                // A galeria só se dissolve por cima: a tela de trás não se move nem esmaece
                transitionSpec = {
                    // Sem fade: a miniatura expande opaca e a galeria fecha sobre a tela de trás, que não se move
                    if (targetState) {
                        (EnterTransition.None togetherWith ExitTransition.None).apply { targetContentZIndex = 1f }
                    } else {
                        (EnterTransition.None togetherWith ExitTransition.None).apply { targetContentZIndex = -1f }
                    }
                },
                label = "galeria",
            ) { aberta ->
                val av = this
                val dados = vm.visualizador ?: ultimo.value
                if (aberta && dados != null) {
                    Galeria(vm, dados, st, av)
                } else {
                    Principal(
                        vm = vm,
                        atual = atual,
                        aoSelecionar = aoSelecionar,
                        entrar = entrar,
                        st = st,
                        av = av,
                        paisagem = paisagem,
                        abrirContas = { mostrarContas = true },
                        aoBuscar = aoBuscar,
                    )
                }
            }
        }
        }
    }

    if (mostrarContas) {
        ContasSheet(
            contas = vm.contas,
            atual = vm.contaAtual,
            onSelecionar = vm::selecionar,
            onRemover = vm::remover,
            onAdicionar = entrar,
            onFechar = { mostrarContas = false },
        )
    }
    if (vm.detalhes != null) InfoSheet(vm)
    vm.planoDownload?.let { DialogDownloadPasta(vm, it) }
    if (vm.pastaDaCapa != null) SeletorCapa(vm)
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Principal(
    vm: DriveViewModel,
    atual: Destino,
    aoSelecionar: (Destino) -> Unit,
    entrar: () -> Unit,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    paisagem: Boolean,
    abrirContas: () -> Unit,
    aoBuscar: () -> Unit,
) {
    // luz da tela principal: as miniaturas visíveis da lista iluminam as peças de vidro (a lista informa as fontes)
    val luz = remember { AmbienteLuz().also { it.raioDp = 260f } }
    val tela: @Composable (PaddingValues) -> Unit = { padding ->
        when (atual) {
            Destino.Drive -> DriveScreen(vm, onEntrar = entrar, st = st, avExterno = av, contentPadding = padding)
            Destino.Favoritos -> FavoritosScreen(vm, st, av, padding) { pasta ->
                aoSelecionar(Destino.Drive)
                vm.abrirPastaDireto(pasta)
            }
            Destino.Lixeira -> LixeiraScreen(vm, st, av, padding)
            Destino.Config -> ConfiguracoesTela(vm, padding, abrirContas)
        }
    }
    CompositionLocalProvider(
        LocalLuz provides (if (vm.reflexoPilula) luz else null),
        LocalLuzSoToolbars provides vm.reflexoSoToolbars,
        LocalLuzPrincipal provides luz,
    ) {
        if (paisagem) {
            PrincipalPaisagem(atual, aoSelecionar, aoBuscar, tela)
        } else {
            PrincipalRetrato(luz, atual, aoSelecionar, aoBuscar, tela)
        }
    }
}

/** Retrato: cabeçalho com gradiente de blur no topo e toolbar flutuante embaixo (some ao rolar para baixo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrincipalRetrato(
    luz: AmbienteLuz,
    atual: Destino,
    aoSelecionar: (Destino) -> Unit,
    aoBuscar: () -> Unit,
    tela: @Composable (PaddingValues) -> Unit,
) {
    var barraVisivel by remember { mutableStateOf(true) }
    LaunchedEffect(atual) { barraVisivel = true }
    val conexao = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) barraVisivel = false else if (available.y > 6f) barraVisivel = true
                return Offset.Zero
            }
        }
    }
    Scaffold(topBar = { Cabecalho() }) { padding ->
        Box(
            Modifier.fillMaxSize().nestedScroll(conexao)
                .onPlaced { c ->
                    val p = c.positionInRoot()
                    luz.origemX = p.x; luz.origemY = p.y
                    luz.largura = c.size.width.toFloat(); luz.altura = c.size.height.toFloat()
                },
        ) {
            // O conteúdo ocupa a tela toda: as listas rolam por baixo do cabeçalho e da toolbar
            tela(PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp))
            BarraFlutuante(
                atual = atual,
                aoSelecionar = aoSelecionar,
                aoBuscar = aoBuscar,
                visivel = barraVisivel,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding() + 16.dp),
            )
        }
    }
}

/** Paisagem: cabeçalho e NavigationRail formam uma só peça flutuante à esquerda; o resto da tela é conteúdo. */
@Composable
private fun PrincipalPaisagem(
    atual: Destino,
    aoSelecionar: (Destino) -> Unit,
    aoBuscar: () -> Unit,
    tela: @Composable (PaddingValues) -> Unit,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            LateralPaisagem(atual, aoSelecionar, aoBuscar)
        }
        Box(
            Modifier.weight(1f).fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)),
        ) {
            tela(PaddingValues(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()))
        }
    }
}

@Composable
private fun LateralPaisagem(
    atual: Destino,
    aoSelecionar: (Destino) -> Unit,
    aoBuscar: () -> Unit,
) {
    Surface(
        Modifier.fillMaxHeight(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier.width(92.dp).fillMaxHeight().padding(top = 14.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PawIcon()
            Spacer(Modifier.height(4.dp))
            Text("nekodrive", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = aoBuscar, modifier = Modifier.padding(top = 4.dp).size(40.dp)) {
                Icon(Icons.Rounded.Search, contentDescription = "Buscar")
            }
            Spacer(Modifier.height(8.dp))
            NavigationRail(
                containerColor = Color.Transparent,
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Em paisagem a altura é pouca: os botões têm tamanho próprio (não encolhem) e a lista rola.
                // Quando cabem, continuam centralizados como antes (a coluna ocupa no mínimo a altura visível).
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val alturaVisivel = maxHeight
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = alturaVisivel),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        Destino.entries.forEach { d ->
                            NavigationRailItem(
                                selected = atual == d,
                                onClick = { aoSelecionar(d) },
                                icon = { Icon(if (atual == d) d.iconeSelecionado else d.icone, contentDescription = d.rotulo) },
                                label = { Text(d.rotulo) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Cabeçalho personalizado (nome e patinha, centralizados na tela) com gradiente de blur. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Cabecalho() {
    val cor = MaterialTheme.colorScheme.surface
    val vidroAtivo = LocalVidroAtivo.current
    // sem blur o gradiente é branco no tema claro (texto escuro) e preto no escuro (texto claro)
    val textoClaro = !vidroAtivo && MaterialTheme.colorScheme.surface.luminance() <= 0.5f
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("nekodrive", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                PawIcon()
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            // sobre o gradiente preto (tema escuro, blur desligado) o texto e os ícones precisam ser claros
            titleContentColor = if (textoClaro) Color.White else MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = if (textoClaro) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.vidroGradiente()
            .then(
                if (vidroAtivo) Modifier.background(Brush.verticalGradient(listOf(cor.copy(alpha = 0.7f), Color.Transparent)))
                else Modifier,
            ),
    )
}

/**
 * Toolbar flutuante horizontal (no estilo da HorizontalFloatingToolbar do Material 3 Expressive):
 * pílula flutuante com vidro, destino selecionado expandido e recolhimento ao rolar a lista para baixo.
 * A versão oficial exige Material 3 1.4+ (Compose 1.9+), mais novo que as dependências do projeto.
 */
@Composable
private fun BarraFlutuante(
    atual: Destino,
    aoSelecionar: (Destino) -> Unit,
    aoBuscar: () -> Unit,
    visivel: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visivel,
        modifier = modifier,
        enter = Animacoes.entrada(slideInVertically(tween(300)) { it * 2 } + fadeIn(tween(300))),
        exit = Animacoes.saida(slideOutVertically(tween(300)) { it * 2 } + fadeOut(tween(200))),
    ) {
        val forma = RoundedCornerShape(50)
        val luz = LocalLuz.current
        // a toolbar e o botão de busca (separado, ao lado) são peças independentes de vidro
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(forma).vidro()
                    .then(if (luz != null) Modifier.reflexoLuz(luz) else Modifier.border(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), forma))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Destino.entries.forEach { d -> ItemBarra(d, d == atual) { aoSelecionar(d) } }
            }
            CirculoVidro(aoClicar = aoBuscar, modifier = Modifier.size(52.dp), toolbar = true) {
                Icon(Icons.Rounded.Search, "Buscar", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ItemBarra(d: Destino, selecionado: Boolean, aoClicar: () -> Unit) {
    val fundo by animateColorAsState(
        if (selecionado) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = Animacoes.espec(spring(), tween(120)),
        label = "itemBarra",
    )
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(fundo).clickable(onClick = aoClicar)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (selecionado) d.iconeSelecionado else d.icone, contentDescription = d.rotulo)
        AnimatedVisibility(
            visible = selecionado,
            enter = Animacoes.entrada(fadeIn() + expandIn(), fadeIn(tween(100))),
            exit = Animacoes.saida(fadeOut() + shrinkOut(), fadeOut(tween(100))),
        ) {
            Text(d.rotulo, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}
