package com.nekodrive.app.ui

import android.accounts.AccountManager
import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nekodrive.app.NekoDriveApplication
import com.nekodrive.app.ui.drive.ContasSheet
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NekoDriveApp() {
    val context = LocalContext.current
    val container = (context.applicationContext as NekoDriveApplication).container
    val vm: DriveViewModel = viewModel(factory = viewModelFactory { initializer { DriveViewModel(container) } })
    val scope = rememberCoroutineScope()

    var atual by rememberSaveable { mutableStateOf(Destino.Drive) }
    var mostrarContas by rememberSaveable { mutableStateOf(false) }
    var mostrarConfig by rememberSaveable { mutableStateOf(false) }
    val paisagem = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Vidro com blur: estilo geral e estilo do cabeçalho (gradiente de blur)
    val corVidro = MaterialTheme.colorScheme.surface
    val vidroEstado = remember { HazeState() }
    val vidroEstilo = HazeStyle(
        backgroundColor = corVidro,
        tint = HazeTint(corVidro.copy(alpha = 0.55f)),
        blurRadius = 24.dp,
        noiseFactor = 0f,
    )
    val vidroCabecalho = HazeStyle(
        backgroundColor = corVidro,
        tint = HazeTint(corVidro.copy(alpha = 0f)),
        blurRadius = 28.dp,
        noiseFactor = 0f,
    )
    val vidroCtx = remember(vidroEstado, vidroEstilo, vidroCabecalho) { Vidro(vidroEstado, vidroEstilo, vidroCabecalho) }

    // 1) tela de consentimento do Google (quando necessária)
    val consentimento = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        try {
            vm.aoAutorizar(container.auth.fromIntent(res.data).accessToken)
        } catch (e: Exception) {
            vm.mostrarErro("Login cancelado ou não concluído.")
        }
    }
    // 2) autoriza o Drive para a conta escolhida
    val autorizar: (String) -> Unit = { email ->
        scope.launch {
            try {
                val r = container.auth.authorize(email)
                val pendente = r.pendingIntent
                if (r.hasResolution() && pendente != null) {
                    consentimento.launch(IntentSenderRequest.Builder(pendente.intentSender).build())
                } else {
                    vm.aoAutorizar(r.accessToken)
                }
            } catch (e: Exception) {
                vm.mostrarErro("Falha ao entrar: ${e.message}")
            }
        }
    }
    // 3) seletor de contas do Android (permite escolher outra conta ou adicionar uma nova)
    val escolherConta = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val email = res.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (res.resultCode == Activity.RESULT_OK && email != null) autorizar(email)
    }
    val entrar: () -> Unit = {
        val intent = AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)
        escolherConta.launch(intent)
    }

    // Toque em "Drive": se já estiver na aba, volta para o início
    val aoSelecionar: (Destino) -> Unit = { d ->
        if (d == Destino.Drive && atual == Destino.Drive) vm.irParaInicio()
        atual = d
        vm.limparSelecao()
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
        LocalMarqueePausado provides (vm.visualizador != null || congelarNomes),
    ) {
        SharedTransitionLayout {
            val st = this
            AnimatedContent(
                targetState = vm.visualizador != null,
                // A galeria só se dissolve por cima: a tela de trás não se move nem esmaece
                transitionSpec = {
                    if (targetState) {
                        (fadeIn(tween(300)) togetherWith ExitTransition.None).apply { targetContentZIndex = 1f }
                    } else {
                        (EnterTransition.None togetherWith fadeOut(tween(300))).apply { targetContentZIndex = -1f }
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
                        abrirConfig = { mostrarConfig = true },
                    )
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
    if (mostrarConfig) {
        ConfiguracoesSheet(vm, onContas = { mostrarContas = true }, onFechar = { mostrarConfig = false })
    }
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
    abrirConfig: () -> Unit,
) {
    val tela: @Composable (PaddingValues) -> Unit = { padding ->
        when (atual) {
            Destino.Drive -> DriveScreen(vm, onEntrar = entrar, st = st, avExterno = av, contentPadding = padding)
            Destino.Favoritos -> FavoritosScreen(vm, st, av, padding)
            Destino.Lixeira -> LixeiraScreen(vm, st, av, padding)
        }
    }
    if (paisagem) {
        PrincipalPaisagem(atual, aoSelecionar, abrirContas, abrirConfig, tela)
    } else {
        PrincipalRetrato(atual, aoSelecionar, abrirContas, abrirConfig, tela)
    }
}

/** Retrato: cabeçalho com gradiente de blur no topo e toolbar flutuante embaixo (some ao rolar para baixo). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrincipalRetrato(
    atual: Destino,
    aoSelecionar: (Destino) -> Unit,
    abrirContas: () -> Unit,
    abrirConfig: () -> Unit,
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
    Scaffold(topBar = { Cabecalho(abrirContas, abrirConfig) }) { padding ->
        Box(Modifier.fillMaxSize().nestedScroll(conexao)) {
            // O conteúdo ocupa a tela toda: as listas rolam por baixo do cabeçalho e da toolbar
            tela(PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp))
            BarraFlutuante(
                atual = atual,
                aoSelecionar = aoSelecionar,
                visivel = barraVisivel,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding() + 16.dp),
            )
        }
    }
}

/** Paisagem: NavigationRail e cartão flutuante com as informações do cabeçalho à esquerda; o resto é conteúdo. */
@Composable
private fun PrincipalPaisagem(
    atual: Destino,
    aoSelecionar: (Destino) -> Unit,
    abrirContas: () -> Unit,
    abrirConfig: () -> Unit,
    tela: @Composable (PaddingValues) -> Unit,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CartaoCabecalho(abrirContas, abrirConfig)
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 4.dp,
            ) {
                NavigationRail(containerColor = Color.Transparent, windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) {
                    Spacer(Modifier.weight(1f))
                    Destino.entries.forEach { d ->
                        NavigationRailItem(
                            selected = atual == d,
                            onClick = { aoSelecionar(d) },
                            icon = { Icon(if (atual == d) d.iconeSelecionado else d.icone, contentDescription = d.rotulo) },
                            label = { Text(d.rotulo) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Box(
            Modifier.weight(1f).fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)),
        ) {
            tela(PaddingValues(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()))
        }
    }
}

/** Cabeçalho personalizado (patinha, nome, perfil e configurações) com gradiente de blur. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Cabecalho(abrirContas: () -> Unit, abrirConfig: () -> Unit) {
    val cor = MaterialTheme.colorScheme.surface
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("nekodrive", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                PawIcon()
            }
        },
        actions = {
            IconButton(onClick = abrirContas) { Icon(Icons.Rounded.AccountCircle, contentDescription = "Contas") }
            IconButton(onClick = abrirConfig) { Icon(Icons.Rounded.Settings, contentDescription = "Configurações") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        modifier = Modifier.vidroGradiente()
            .background(Brush.verticalGradient(listOf(cor.copy(alpha = 0.7f), Color.Transparent))),
    )
}

/** Cabeçalho da paisagem: cartão flutuante ligado visualmente à NavigationRail, com separação entre os dois. */
@Composable
private fun CartaoCabecalho(abrirContas: () -> Unit, abrirConfig: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier.width(104.dp).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PawIcon()
            Text("nekodrive", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row {
                IconButton(onClick = abrirContas, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = "Contas")
                }
                IconButton(onClick = abrirConfig, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Configurações")
                }
            }
        }
    }
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
    visivel: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visivel,
        modifier = modifier,
        enter = slideInVertically(tween(300)) { it * 2 } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(300)) { it * 2 } + fadeOut(tween(200)),
    ) {
        val forma = RoundedCornerShape(50)
        Row(
            Modifier.shadow(10.dp, forma).clip(forma).vidro().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destino.entries.forEach { d -> ItemBarra(d, d == atual) { aoSelecionar(d) } }
        }
    }
}

@Composable
private fun ItemBarra(d: Destino, selecionado: Boolean, aoClicar: () -> Unit) {
    val fundo by animateColorAsState(
        if (selecionado) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "itemBarra",
    )
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(fundo).clickable(onClick = aoClicar)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (selecionado) d.iconeSelecionado else d.icone, contentDescription = d.rotulo)
        AnimatedVisibility(visible = selecionado) {
            Text(d.rotulo, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}
