package com.nekodrive.app.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nekodrive.app.data.drive.DriveFile
import com.nekodrive.app.ui.drive.DriveViewModel
import com.nekodrive.app.ui.drive.FotosAbertas
import com.nekodrive.app.ui.drive.pastaBounds
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun Visualizador(vm: DriveViewModel, dados: FotosAbertas, st: SharedTransitionScope, av: AnimatedVisibilityScope) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = dados.indice.coerceIn(0, dados.fotos.lastIndex)) { dados.fotos.size }
    var barras by remember { mutableStateOf(true) }
    var zoomado by remember { mutableStateOf(false) }
    val giros = remember { mutableStateMapOf<String, Int>() }
    val arrasto = remember { Animatable(0f) }

    BackHandler { vm.fecharFoto() }
    LaunchedEffect(Unit) { vm.renovarToken() }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { vm.indiceFoto(it) } }

    val atual = dados.fotos[pager.currentPage.coerceIn(0, dados.fotos.lastIndex)]

    val copiar: () -> Unit = {
        scope.launch {
            try {
                Toast.makeText(ctx, "Copiando…", Toast.LENGTH_SHORT).show()
                val arquivo = vm.copiarParaCache(atual)
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", arquivo)
                val area = ctx.getSystemService(ClipboardManager::class.java)
                area.setPrimaryClip(ClipData.newUri(ctx.contentResolver, atual.name, uri))
                Toast.makeText(ctx, "Imagem copiada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Não foi possível copiar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        val limite = h * 0.18f
        val fundo = Color.Black.copy(alpha = (1f - arrasto.value / h * 2f).coerceIn(0.35f, 1f))
        val drag = rememberDraggableState { d -> scope.launch { arrasto.snapTo((arrasto.value + d).coerceAtLeast(0f)) } }

        Box(
            Modifier.fillMaxSize().background(fundo).draggable(
                state = drag,
                orientation = Orientation.Vertical,
                enabled = !zoomado,
                onDragStopped = { velocidade ->
                    if (arrasto.value > limite || velocidade > 2500f) {
                        vm.fecharFoto()
                    } else {
                        arrasto.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                    }
                },
            ),
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationY = arrasto.value },
                beyondViewportPageCount = 1,
                pageSpacing = 16.dp,
                // Mola ao soltar: o álbum termina o movimento com inércia e leve vai-e-vem.
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pager,
                    snapAnimationSpec = spring(dampingRatio = 0.75f, stiffness = 180f),
                ),
                userScrollEnabled = !zoomado,
                key = { dados.fotos[it].id },
            ) { i ->
                PaginaFoto(
                    f = dados.fotos[i],
                    token = vm.token,
                    giro = giros[dados.fotos[i].id] ?: 0,
                    ehAtual = i == pager.currentPage,
                    st = st,
                    av = av,
                    aoZoom = { zoomado = it },
                    aoTocar = { barras = !barras },
                )
            }

            AnimatedVisibility(
                visible = barras && arrasto.value < 1f,
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.fecharFoto() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(atual.name, color = Color.White, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${pager.currentPage + 1} de ${dados.fotos.size}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { giros[atual.id] = ((giros[atual.id] ?: 0) + 1) % 4 }) {
                        Icon(Icons.Outlined.RotateRight, "Girar", tint = Color.White)
                    }
                    IconButton(onClick = copiar) {
                        Icon(Icons.Outlined.ContentCopy, "Copiar imagem", tint = Color.White)
                    }
                    IconButton(onClick = { vm.abrirDetalhes(atual) }) {
                        Icon(Icons.Outlined.Info, "Detalhes", tint = Color.White)
                    }
                }
            }
        }
    }
}

/** Uma foto: zoom com pinça, arrastar quando ampliada, giro de 90° e toque para mostrar/esconder as barras. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PaginaFoto(
    f: DriveFile,
    token: String?,
    giro: Int,
    ehAtual: Boolean,
    st: SharedTransitionScope,
    av: AnimatedVisibilityScope,
    aoZoom: (Boolean) -> Unit,
    aoTocar: () -> Unit,
) {
    val ctx = LocalContext.current
    var escala by remember { mutableFloatStateOf(1f) }
    var deslocamento by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize().pastaBounds("foto-${f.id}", st, av)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // Com giro de 90°, encolhe um pouco para a foto continuar inteira na tela.
        val ajusteGiro = if (giro % 2 == 1) minOf(w / h, h / w) else 1f

        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val evento = awaitPointerEvent()
                            val toques = evento.changes.count { it.pressed }
                            if (toques >= 2 || escala > 1f) {
                                val zoom = evento.calculateZoom()
                                val pan = evento.calculatePan()
                                val nova = (escala * zoom).coerceIn(1f, 6f)
                                val lx = w * (nova - 1f) / 2f
                                val ly = h * (nova - 1f) / 2f
                                deslocamento = if (nova <= 1f) {
                                    Offset.Zero
                                } else {
                                    Offset(
                                        (deslocamento.x + pan.x).coerceIn(-lx, lx),
                                        (deslocamento.y + pan.y).coerceIn(-ly, ly),
                                    )
                                }
                                escala = nova
                                if (ehAtual) aoZoom(escala > 1f)
                                evento.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (evento.changes.any { it.pressed })
                        if (escala <= 1.02f) {
                            escala = 1f
                            deslocamento = Offset.Zero
                            if (ehAtual) aoZoom(false)
                        }
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { aoTocar() }) }
                .graphicsLayer {
                    scaleX = escala * ajusteGiro
                    scaleY = escala * ajusteGiro
                    translationX = deslocamento.x
                    translationY = deslocamento.y
                    rotationZ = giro * 90f
                },
        ) {
            val original = ImageRequest.Builder(ctx)
                .data("https://www.googleapis.com/drive/v3/files/${f.id}?alt=media")
                .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                .size(2400, 2400)
                .crossfade(true)
                .build()
            SubcomposeAsyncImage(
                model = original,
                contentDescription = f.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (f.thumbnailLink != null) {
                            val miniatura = ImageRequest.Builder(ctx)
                                .data(f.thumbnailLink)
                                .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                                .build()
                            AsyncImage(miniatura, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
                    }
                },
            )
        }
    }
}

/** Folha com todos os campos que a API do Drive devolve para o arquivo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalhesSheet(vm: DriveViewModel) {
    ModalBottomSheet(onDismissRequest = { vm.fecharDetalhes() }) {
        if (vm.carregandoDetalhes) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item {
                    Text(vm.tituloDetalhes, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(vm.detalhes.orEmpty()) { (chave, valor) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(chave, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer { Text(valor, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}
