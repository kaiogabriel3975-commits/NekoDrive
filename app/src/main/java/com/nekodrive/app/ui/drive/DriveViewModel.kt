package com.nekodrive.app.ui.drive

import android.content.ContentValues
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nekodrive.app.ui.Animacoes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nekodrive.app.data.AppContainer
import com.nekodrive.app.data.Progresso
import com.nekodrive.app.data.auth.GoogleAccount
import com.nekodrive.app.data.auth.NeedsConsentException
import com.nekodrive.app.data.drive.ArquivoDePasta
import com.nekodrive.app.data.drive.DriveFile
import com.nekodrive.app.data.drive.DrivePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.UnknownHostException

data class Pasta(val id: String, val nome: String)

/** Situação de um arquivo na lista: salvo no cache, assistido, ou baixando (progresso de 0 a 1). */
data class EstadoArquivo(
    val noCache: Boolean,
    val assistido: Boolean,
    val progresso: Float?,
    val favorito: Boolean = false,
    /** Id da foto ou do vídeo escolhido como capa (só para pastas). */
    val capa: String? = null,
    /** Miniatura do vídeo usada como capa; null quando a capa é uma foto. */
    val capaLink: String? = null,
)

/** Pastas escolhidas para baixar: o que foi encontrado dentro delas e o quanto isso pesa. */
class PlanoDownload(
    val nomes: List<String>,
    val arquivos: List<ArquivoDePasta>,
    val ignorados: Int,
    val calculando: Boolean,
    val contados: Int = 0,
) {
    val bytes: Long get() = arquivos.sumOf { it.arquivo.size ?: 0L }
    val bytesMidia: Long get() = arquivos.filter { it.arquivo.isImage || it.arquivo.isVideo }.sumOf { it.arquivo.size ?: 0L }
}

/** Galeria aberta: fotos e vídeos da lista atual, na ordem da pasta, e o item em exibição. */
data class FotosAbertas(val fotos: List<DriveFile>, val indice: Int)

enum class CampoOrdem(val rotulo: String, val chaveApi: String) {
    Nome("Nome", "name_natural"),
    Data("Data", "modifiedTime"),
    Tamanho("Tamanho", "quotaBytesUsed"),
}

class DriveViewModel(private val c: AppContainer) : ViewModel() {
    private val raiz = Pasta("root", "Meu Drive")

    var contas by mutableStateOf(c.accounts.load()); private set
    var contaAtual by mutableStateOf<GoogleAccount?>(null); private set
    var pilha by mutableStateOf(listOf(raiz)); private set
    var itens by mutableStateOf<List<DriveFile>>(emptyList()); private set
    var carregando by mutableStateOf(false); private set
    var temMais by mutableStateOf(false); private set
    var erro by mutableStateOf<String?>(null); private set
    var token by mutableStateOf<String?>(null); private set

    var grade by mutableStateOf(c.prefs.grade); private set
    var campo by mutableStateOf(runCatching { CampoOrdem.valueOf(c.prefs.campo) }.getOrDefault(CampoOrdem.Nome)); private set
    var crescente by mutableStateOf(c.prefs.crescente); private set

    var buscando by mutableStateOf(false); private set
    var termo by mutableStateOf(""); private set
    var selecionados by mutableStateOf(setOf<String>()); private set

    var visualizador by mutableStateOf<FotosAbertas?>(null); private set

    // ---------- ajustes ----------
    var vidroAtivo by mutableStateOf(c.prefs.vidro); private set
    var tamanhoMiniatura by mutableIntStateOf(c.prefs.miniatura); private set
    var repetirVideo by mutableStateOf(c.prefs.repetirVideo); private set
    var retomarVideo by mutableStateOf(c.prefs.retomarVideo); private set
    var saltoSeg by mutableIntStateOf(c.prefs.saltoSeg); private set
    var velocidadeTurbo by mutableStateOf(c.prefs.turbo); private set
    var resWifi by mutableIntStateOf(c.prefs.resWifi); private set
    var resMovel by mutableIntStateOf(c.prefs.resMovel); private set
    var controlesMs by mutableIntStateOf(c.prefs.controlesMs); private set
    var esperaFecharMs by mutableIntStateOf(c.prefs.esperaFecharMs); private set
    var animarGifs by mutableStateOf(c.prefs.animarGifs); private set
    var colunas by mutableIntStateOf(c.prefs.colunas); private set
    var blurIntensidade by mutableIntStateOf(c.prefs.blurIntensidade); private set
    var reflexoPilula by mutableStateOf(c.prefs.reflexoPilula); private set
    var reflexoSoToolbars by mutableStateOf(c.prefs.reflexoSoToolbars); private set
    var fundoCard by mutableIntStateOf(c.prefs.fundoCard); private set
    var pastasPrimeiro by mutableStateOf(c.prefs.pastasPrimeiro); private set
    var mostrarNomes by mutableStateOf(c.prefs.mostrarNomes); private set
    var miniaturasSoWifi by mutableStateOf(c.prefs.miniaturasSoWifi); private set
    var vibrar by mutableStateOf(c.prefs.vibrar); private set
    var animacoes by mutableIntStateOf(c.prefs.animacoes); private set
    val tema: Int get() = c.prefs.tema

    fun definirTema(v: Int) { c.prefs.tema = v }
    fun definirVidro(v: Boolean) { vidroAtivo = v; c.prefs.vidro = v }
    fun definirTamanhoMiniatura(v: Int) { tamanhoMiniatura = v; c.prefs.miniatura = v }
    fun definirRepetirVideo(v: Boolean) { repetirVideo = v; c.prefs.repetirVideo = v }
    fun definirRetomarVideo(v: Boolean) { retomarVideo = v; c.prefs.retomarVideo = v }
    fun definirSaltoSeg(v: Int) { saltoSeg = v; c.prefs.saltoSeg = v }
    fun definirTurbo(v: Float) { velocidadeTurbo = v; c.prefs.turbo = v }
    fun definirResWifi(v: Int) { resWifi = v; c.prefs.resWifi = v }
    fun definirResMovel(v: Int) { resMovel = v; c.prefs.resMovel = v }
    fun definirControlesMs(v: Int) { controlesMs = v; c.prefs.controlesMs = v }
    fun definirEsperaFechar(v: Int) { esperaFecharMs = v; c.prefs.esperaFecharMs = v; TempoGaleria.esperaMs = v }
    fun definirAnimarGifs(v: Boolean) { animarGifs = v; c.prefs.animarGifs = v }
    fun definirColunas(v: Int) { colunas = v; c.prefs.colunas = v }
    fun definirBlurIntensidade(v: Int) { blurIntensidade = v; c.prefs.blurIntensidade = v }
    fun definirReflexoPilula(v: Boolean) { reflexoPilula = v; c.prefs.reflexoPilula = v }
    fun definirReflexoSoToolbars(v: Boolean) { reflexoSoToolbars = v; c.prefs.reflexoSoToolbars = v }
    fun definirFundoCard(v: Int) { fundoCard = v; c.prefs.fundoCard = v }
    fun definirMostrarNomes(v: Boolean) { mostrarNomes = v; c.prefs.mostrarNomes = v }
    fun definirMiniaturasSoWifi(v: Boolean) { miniaturasSoWifi = v; c.prefs.miniaturasSoWifi = v }
    fun definirVibrar(v: Boolean) { vibrar = v; c.prefs.vibrar = v }
    fun definirAnimacoes(v: Int) { animacoes = v; c.prefs.animacoes = v; Animacoes.modo = v }
    fun definirPastasPrimeiro(v: Boolean) {
        pastasPrimeiro = v
        c.prefs.pastasPrimeiro = v
        cache.clear()
        if (buscando) definirTermo(termo) else recarregar()
    }

    val pediuNotificacao: Boolean get() = c.prefs.pediuNotificacao
    fun marcarPediuNotificacao() { c.prefs.pediuNotificacao = true }

    /** Verdadeiro só logo depois de tocar na lupa: o campo de busca pega o foco uma única vez. */
    var pedirFocoBusca = false; private set
    fun consumirFocoBusca() { pedirFocoBusca = false }

    // capas de pasta (guardadas só no aparelho)
    private val capas = mutableStateMapOf<String, String>()
    var pastaDaCapa by mutableStateOf<DriveFile?>(null); private set
    var imagensCapa by mutableStateOf<List<DriveFile>>(emptyList()); private set
    var carregandoCapa by mutableStateOf(false); private set
    var temMaisCapa by mutableStateOf(false); private set
    private var proximaCapa: String? = null
    private var jobCapa: Job? = null

    // download de pastas inteiras
    var planoDownload by mutableStateOf<PlanoDownload?>(null); private set
    private var jobPlano: Job? = null

    /** Arquivo cujas informações estão abertas (para o botão de renomear). */
    var arquivoDetalhes by mutableStateOf<DriveFile?>(null); private set

    /** Muda quando o cache ou o progresso mudam, para as listas se atualizarem. */
    var versaoCache by mutableIntStateOf(0); private set
    var limiteGb by mutableIntStateOf(c.prefs.limiteGb); private set
    val progressos = mutableStateMapOf<String, Float>()
    private val tarefas = HashMap<String, Job>()
    val midia get() = c.midia

    /** Mensagem curta para o usuário (por exemplo, arquivo salvo em Downloads). */
    var aviso by mutableStateOf<String?>(null); private set
    fun limparAviso() { aviso = null }

    // favoritos (locais) e lixeira
    var favoritosItens by mutableStateOf<List<DriveFile>>(emptyList()); private set
    var favoritosIds by mutableStateOf(setOf<String>()); private set
    var indisponiveis by mutableStateOf(setOf<String>()); private set
    var carregandoFavoritos by mutableStateOf(false); private set

    var lixeira by mutableStateOf<List<DriveFile>>(emptyList()); private set
    var carregandoLixeira by mutableStateOf(false); private set
    var temMaisLixeira by mutableStateOf(false); private set
    var erroLixeira by mutableStateOf<String?>(null); private set
    private var proximaLixeira: String? = null
    private var jobLixeira: Job? = null

    /** Lista e posição de rolagem de onde a foto/vídeo foi aberto (Drive, Favoritos...). */
    private var rolagem: Pair<String, List<DriveFile>>? = null

    /** null = folha de detalhes fechada. */
    var detalhes by mutableStateOf<List<Pair<String, String>>?>(null); private set
    var tituloDetalhes by mutableStateOf(""); private set
    var carregandoDetalhes by mutableStateOf(false); private set

    private val estadosGrade = HashMap<String, LazyGridState>()
    private val estadosLista = HashMap<String, LazyListState>()

    private var proxima: String? = null
    private var job: Job? = null

    /** Conta quantas vezes a carga foi cancelada/refeita: um job antigo não mexe mais em [carregando]. */
    private var geracaoCarga = 0

    private fun cancelarCarga() {
        job?.cancel()
        geracaoCarga++
    }
    /** Listagens já carregadas por pasta, para voltar instantaneamente. */
    private val cache = HashMap<String, Pair<List<DriveFile>, String?>>()

    init {
        TempoGaleria.esperaMs = c.prefs.esperaFecharMs
        Animacoes.modo = c.prefs.animacoes
        contaAtual = contas.firstOrNull { it.email == c.accounts.currentEmail } ?: contas.firstOrNull()
        c.midia.definirConta(contaAtual?.email)
        recarregarFavoritosLocais()
        recarregar()
    }

    fun mostrarErro(msg: String) { erro = msg }

    /**
     * Conta cuja autorização do Google expirou ou foi revogada. A tela observa isto e abre a confirmação de
     * acesso do Google (a mesma do login), em vez de pedir para remover e adicionar a conta de novo.
     */
    var reautorizar by mutableStateOf<String?>(null); private set

    fun consumirReautorizacao() { reautorizar = null }

    private fun pedirReautorizacao(email: String) {
        erro = "A autorização do Google expirou. Confirme o acesso na tela que vai abrir."
        reautorizar = email
    }

    /** Posição de rolagem por pasta: sobrevive à abertura do visualizador e ao voltar de uma pasta. */
    fun estadoGrade(chave: String): LazyGridState = estadosGrade.getOrPut(chave) { LazyGridState() }
    fun estadoLista(chave: String): LazyListState = estadosLista.getOrPut(chave) { LazyListState() }
    private fun reiniciarRolagem(chave: String) { estadosGrade.remove(chave); estadosLista.remove(chave) }

    // ---------- consulta ----------

    private fun consulta(): String =
        if (buscando) {
            val t = termo.replace("\\", "\\\\").replace("'", "\\'")
            "name contains '$t' and trashed = false"
        } else {
            "'${pilha.last().id}' in parents and trashed = false"
        }

    private fun ordenacao(): String = (if (pastasPrimeiro) "folder," else "") + campo.chaveApi + if (crescente) "" else " desc"

    private fun chave(id: String) = "${contaAtual?.email}|$id"

    private fun salvarCache() {
        if (!buscando) cache[chave(pilha.last().id)] = itens to proxima
    }

    fun recarregar() = carregarPastaAtual(usarCache = false)

    /** Atualiza a pasta atual mantendo a lista na tela até a nova chegar (puxar para atualizar). */
    fun atualizar() {
        val conta = contaAtual ?: return
        cancelarCarga()
        selecionados = emptySet()
        erro = null
        val minha = geracaoCarga
        job = viewModelScope.launch {
            carregando = true
            try {
                val p = c.drive.list(conta.email, consulta(), ordenacao(), null)
                itens = p.files
                proxima = p.nextPageToken
                temMais = proxima != null
                token = c.auth.token(conta.email)
                if (!buscando) cache.remove(chave(pilha.last().id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnknownHostException) {
                erro = "Sem conexão com a internet."
            } catch (e: NeedsConsentException) {
                pedirReautorizacao(conta.email)
            } catch (e: Exception) {
                erro = e.message ?: "Falha ao atualizar a pasta."
            } finally {
                // só o job mais recente desliga o indicador (um job cancelado não apaga o do novo)
                if (minha == geracaoCarga) carregando = false
            }
        }
    }

    private fun carregarPastaAtual(usarCache: Boolean) {
        cancelarCarga()
        selecionados = emptySet()
        erro = null
        val conta = contaAtual
        if (conta == null) {
            itens = emptyList(); proxima = null; temMais = false; carregando = false
            return
        }
        val salvo = if (usarCache) cache[chave(pilha.last().id)] else null
        if (salvo != null) {
            itens = salvo.first; proxima = salvo.second; temMais = proxima != null; carregando = false
            return
        }
        itens = emptyList(); proxima = null; temMais = false
        job = viewModelScope.launch { carregarPagina(conta, null) }
    }

    fun carregarMais() {
        val conta = contaAtual ?: return
        val pagina = proxima ?: return
        if (carregando) return
        job = viewModelScope.launch { carregarPagina(conta, pagina) }
    }

    private suspend fun carregarPagina(conta: GoogleAccount, pagina: String?) {
        val minha = geracaoCarga
        carregando = true
        try {
            val p = c.drive.list(conta.email, consulta(), ordenacao(), pagina)
            itens = itens + p.files
            proxima = p.nextPageToken
            temMais = proxima != null
            token = c.auth.token(conta.email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            erro = "Sem conexão com a internet."
        } catch (e: NeedsConsentException) {
            pedirReautorizacao(conta.email)
        } catch (e: Exception) {
            erro = e.message ?: "Falha ao carregar a pasta."
        } finally {
            if (minha == geracaoCarga) carregando = false
        }
    }

    // ---------- navegação ----------

    fun abrirPasta(f: DriveFile) {
        if (buscando) {
            buscando = false; termo = ""
            pilha = listOf(raiz, Pasta(f.id, f.name))
        } else {
            salvarCache()
            pilha = pilha + Pasta(f.id, f.name)
        }
        reiniciarRolagem(f.id)
        carregarPastaAtual(usarCache = false)
    }

    /** Toque no botão Drive: volta à raiz, pasta por pasta, com a animação normal de fechar a cada passo. */
    fun irParaInicio() {
        selecionados = emptySet()
        if (buscando) sairDaBusca()
        viewModelScope.launch {
            while (pilha.size > 1) {
                voltar()
                delay(520)
            }
            try {
                estadoGrade(chaveRolagemDrive()).animateScrollToItem(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sem layout ainda: não há nada para rolar
            }
        }
    }

    /** Abre uma pasta direto do Favoritos: o caminho passa a ser Meu Drive › pasta. */
    fun abrirPastaDireto(f: DriveFile) {
        if (buscando) { cancelarCarga(); buscando = false; termo = "" }
        pilha = listOf(raiz, Pasta(f.id, f.name))
        reiniciarRolagem(f.id)
        carregarPastaAtual(usarCache = false)
    }

    fun voltar(): Boolean {
        if (buscando) { sairDaBusca(); return true }
        if (pilha.size <= 1) return false
        pilha = pilha.dropLast(1)
        carregarPastaAtual(usarCache = true)
        return true
    }

    // ---------- visualização ----------

    fun alternarModo() {
        grade = !grade
        c.prefs.grade = grade
    }

    fun definirOrdem(novo: CampoOrdem) {
        if (novo == campo) {
            crescente = !crescente
        } else {
            campo = novo
            crescente = novo == CampoOrdem.Nome
        }
        c.prefs.campo = campo.name
        c.prefs.crescente = crescente
        cache.clear()
        if (buscando) definirTermo(termo) else recarregar()
    }

    // ---------- busca ----------

    fun iniciarBusca() {
        if (buscando) return
        salvarCache()
        cancelarCarga()
        reiniciarRolagem("busca")
        buscando = true; termo = ""
        pedirFocoBusca = true
        itens = emptyList(); proxima = null; temMais = false; carregando = false
        erro = null; selecionados = emptySet()
    }

    fun definirTermo(t: String) {
        termo = t
        cancelarCarga()
        selecionados = emptySet()
        val conta = contaAtual ?: return
        if (t.isBlank()) {
            itens = emptyList(); proxima = null; temMais = false; carregando = false; erro = null
            return
        }
        job = viewModelScope.launch {
            delay(400)
            erro = null; itens = emptyList(); proxima = null; temMais = false
            carregarPagina(conta, null)
        }
    }

    fun sairDaBusca() {
        if (!buscando) return
        cancelarCarga()
        buscando = false; termo = ""
        carregarPastaAtual(usarCache = true)
    }

    // ---------- seleção e lixeira ----------

    fun alternarSelecao(f: DriveFile) {
        selecionados = if (f.id in selecionados) selecionados - f.id else selecionados + f.id
    }

    fun limparSelecao() { selecionados = emptySet() }

    fun moverParaLixeira() {
        val conta = contaAtual ?: return
        val ids = selecionados.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val falhas = mutableSetOf<String>()
            for (id in ids) {
                try {
                    c.drive.trash(conta.email, id)
                    tirarDasListas(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    falhas += id
                }
            }
            selecionados = falhas
            if (falhas.isNotEmpty()) erro = "Não foi possível mover ${falhas.size} item(ns) para a lixeira."
        }
    }

    private fun tirarDasListas(id: String) {
        itens = itens.filterNot { it.id == id }
        favoritosItens = favoritosItens.filterNot { it.id == id }
        favoritosIds = favoritosIds - id
        contaAtual?.let { c.favoritos.salvar(it.email, favoritosItens) }
    }

    /** Move para a lixeira o arquivo aberto na galeria e segue para o próximo (fecha se era o último). */
    fun excluirDaGaleria(f: DriveFile) {
        val conta = contaAtual ?: return
        viewModelScope.launch {
            try {
                c.drive.trash(conta.email, f.id)
                tirarDasListas(f.id)
                val v = visualizador
                if (v != null) {
                    val restantes = v.fotos.filterNot { it.id == f.id }
                    visualizador = if (restantes.isEmpty()) null
                    else v.copy(fotos = restantes, indice = v.indice.coerceAtMost(restantes.lastIndex))
                }
                versaoCache++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erro = "Não foi possível mover para a lixeira: ${e.message}"
            }
        }
    }

    // ---------- galeria (fotos e vídeos na mesma sequência) ----------

    private fun chaveRolagemDrive() = if (buscando) "busca" else pilha.last().id

    fun abrirFoto(f: DriveFile, lista: List<DriveFile> = itens, chave: String = chaveRolagemDrive()) =
        abrirGaleria(f, lista, chave)

    fun abrirVideo(f: DriveFile, lista: List<DriveFile> = itens, chave: String = chaveRolagemDrive()) =
        abrirGaleria(f, lista, chave)

    /** Fotos e vídeos da lista entram na mesma sequência, na ordem em que aparecem na pasta. */
    private fun abrirGaleria(f: DriveFile, lista: List<DriveFile>, chave: String) {
        val midias = lista.filter { it.isImage || it.isVideo }
        val i = midias.indexOfFirst { it.id == f.id }
        if (i >= 0) {
            rolagem = chave to lista
            visualizador = FotosAbertas(midias, i)
        }
    }

    fun indiceFoto(i: Int) {
        visualizador = visualizador?.takeIf { i in it.fotos.indices }?.copy(indice = i) ?: visualizador
    }

    /** Fecha a galeria depois de rolar a lista até o item atual, para a animação voltar ao lugar certo. */
    fun fecharFoto() {
        val v = visualizador ?: return
        val atual = v.fotos.getOrNull(v.indice)
        viewModelScope.launch {
            if (atual != null) garantirVisivel(atual.id)
            visualizador = null
            versaoCache++
        }
    }

    private suspend fun garantirVisivel(id: String) {
        val (chave, lista) = rolagem ?: (chaveRolagemDrive() to itens)
        val idx = lista.indexOfFirst { it.id == id }
        if (idx < 0) return
        if (grade || chave != "favoritos") {
            val e = estadoGrade(chave)
            if (e.layoutInfo.visibleItemsInfo.none { it.index == idx }) e.scrollToItem(idx)
        } else {
            val e = estadoLista(chave)
            if (e.layoutInfo.visibleItemsInfo.none { it.index == idx }) e.scrollToItem(idx)
        }
    }

    /** Legendas laterais (.srt/.vtt com o mesmo nome) de um vídeo, procuradas na lista de onde ele foi aberto. */
    fun legendasDe(f: DriveFile): List<DriveFile> {
        val lista = rolagem?.second ?: itens
        val base = f.name.substringBeforeLast('.', f.name)
        return lista.filter { l ->
            !l.isFolder && l.id != f.id &&
                (l.name.endsWith(".srt", true) || l.name.endsWith(".vtt", true)) &&
                l.name.startsWith(base, ignoreCase = true)
        }
    }

    fun progresso(f: DriveFile): Progresso? = contaAtual?.let { c.playback.obter(it.email, f.id) }

    fun salvarProgresso(f: DriveFile, posicaoMs: Long, duracaoMs: Long) {
        val conta = contaAtual ?: return
        c.playback.salvar(conta.email, f.id, posicaoMs, duracaoMs)
    }

    /** Garante um token válido para carregar as imagens em tamanho original. */
    fun renovarToken() {
        val conta = contaAtual ?: return
        viewModelScope.launch {
            try { token = c.auth.token(conta.email) } catch (e: CancellationException) { throw e } catch (e: Exception) { }
        }
    }

    fun abrirDetalhes(f: DriveFile, extras: List<Pair<String, String>> = emptyList()) {
        val conta = contaAtual ?: return
        tituloDetalhes = f.name
        arquivoDetalhes = f
        detalhes = emptyList()
        carregandoDetalhes = true
        viewModelScope.launch {
            try {
                val meta = c.drive.metadados(conta.email, f.id)
                val pai = meta.optJSONArray("parents")?.optString(0)?.takeIf { it.isNotEmpty() }
                val caminho = try {
                    c.drive.caminho(conta.email, pai)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ""
                }
                detalhes = montarInformacoes(meta, caminho) + extras
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detalhes = listOf("Erro" to (e.message ?: "Falha ao carregar as informações"))
            } finally {
                carregandoDetalhes = false
            }
        }
    }

    fun fecharDetalhes() { detalhes = null; arquivoDetalhes = null }

    /** Baixa o arquivo para a pasta de cópias do cache (usada pelo botão copiar) e devolve o arquivo. */
    suspend fun copiarParaCache(f: DriveFile, pasta: String = "copiar"): File {
        val conta = contaAtual ?: error("Nenhuma conta selecionada")
        val dir = File(c.cacheDir, pasta)
        // Só limpa o que já é antigo: apagar tudo quebrava um anexo ou compartilhamento ainda em uso
        // por outro app (ex.: rascunho de e-mail) quando se compartilhava outro arquivo em seguida.
        withContext(Dispatchers.IO) {
            val limite = System.currentTimeMillis() - IDADE_LIMPEZA_COPIAS_MS
            dir.listFiles()?.forEach { if (it.lastModified() < limite) it.delete() }
        }
        val nome = f.id + "_" + f.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val arquivo = File(dir, nome)
        c.drive.baixar(conta.email, f.id, arquivo)
        return arquivo
    }

    // ---------- cache e downloads ----------

    fun chaveCache(f: DriveFile): String = c.midia.chave(contaAtual?.email ?: "", f.id)

    fun arquivoLocal(f: DriveFile): java.io.File? = contaAtual?.let { c.midia.arquivoLocal(it.email, f) }

    fun estadoDe(f: DriveFile): EstadoArquivo {
        val versao = versaoCache // lê o contador para a lista se atualizar quando ele mudar
        val conta = contaAtual
        if (conta == null || versao < 0) return EstadoArquivo(false, false, null)
        return EstadoArquivo(
            noCache = !f.isFolder && c.midia.completo(conta.email, f),
            assistido = f.isVideo && c.playback.obter(conta.email, f.id)?.assistido == true,
            progresso = progressos[f.id],
            favorito = f.id in favoritosIds,
            capa = if (f.isFolder) capas[f.id]?.let { idDaCapa(it) } else null,
            capaLink = if (f.isFolder) capas[f.id]?.let { linkDaCapa(it) } else null,
        )
    }

    /**
     * Os downloads avisam o progresso a cada bloco lido (dezenas de vezes por segundo), e cada gravação
     * em [progressos] reavalia todos os itens da tela. Aqui só passa o que mudou pelo menos 1% e com
     * 250 ms de intervalo; o primeiro valor e o 100% sempre passam, então a barra não fica "em degraus".
     */
    private fun progressoLimitado(id: String): (Float) -> Unit {
        var ultimo = -1f
        var instante = 0L
        return { p ->
            val agora = SystemClock.elapsedRealtime()
            if (ultimo < 0f || (p >= 1f && ultimo < 1f) || (p - ultimo >= 0.01f && agora - instante >= 250L)) {
                ultimo = p
                instante = agora
                progressos[id] = p
            }
        }
    }

    fun baixar(f: DriveFile, subpasta: String? = null) {
        val conta = contaAtual ?: return
        if (f.isFolder || tarefas[f.id]?.isActive == true) return
        val midia = f.isImage || f.isVideo
        if (midia && c.midia.completo(conta.email, f)) return
        progressos[f.id] = 0f
        tarefas[f.id] = c.escopo.launch {
            try {
                c.semaforo.withPermit {
                    if (midia) {
                        c.midia.baixar(conta.email, f, progressoLimitado(f.id))
                    } else {
                        salvarEmDownloads(conta.email, f, subpasta)
                    }
                }
                if (!midia && subpasta == null) aviso = "“${f.name}” salvo em Downloads"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erro = "Falha ao baixar ${f.name}: ${e.message}"
            } finally {
                progressos.remove(f.id)
                versaoCache++
            }
        }
    }

    /** Arquivos que não são fotos nem vídeos são salvos na pasta Downloads do Android (não no cache do app). */
    private suspend fun salvarEmDownloads(email: String, f: DriveFile, subpasta: String? = null) = withContext(Dispatchers.IO) {
        val resolver = c.resolver
        val valores = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, f.mimeType.ifEmpty { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + (subpasta?.let { "/" + it } ?: ""))
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
            ?: throw IOException("Não foi possível criar o arquivo em Downloads")
        try {
            val saida = resolver.openOutputStream(uri) ?: throw IOException("Não foi possível gravar em Downloads")
            val aoProgresso = progressoLimitado(f.id)
            saida.use { c.drive.baixarParaStream(email, f.id, it, aoProgresso) }
            val concluido = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, concluido, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** Arquivos são baixados na hora; pastas pedem confirmação antes, mostrando quantos arquivos têm dentro. */
    fun baixarSelecionados() {
        val alvo = (itens + favoritosItens).distinctBy { it.id }.filter { it.id in selecionados }
        alvo.filter { !it.isFolder }.forEach { baixar(it) }
        val pastas = alvo.filter { it.isFolder }
        selecionados = emptySet()
        if (pastas.isNotEmpty()) prepararDownloadDePastas(pastas)
    }

    private fun prepararDownloadDePastas(pastas: List<DriveFile>) {
        val conta = contaAtual ?: return
        jobPlano?.cancel()
        val nomes = pastas.map { it.name }
        planoDownload = PlanoDownload(nomes, emptyList(), 0, calculando = true)
        jobPlano = viewModelScope.launch {
            try {
                val todos = mutableListOf<ArquivoDePasta>()
                for (p in pastas) {
                    val base = todos.size
                    todos += c.drive.arquivosDaPasta(conta.email, p.id, p.name) { n ->
                        planoDownload = PlanoDownload(nomes, emptyList(), 0, calculando = true, contados = base + n)
                    }
                }
                val baixaveis = todos.filter { !it.arquivo.mimeType.startsWith("application/vnd.google-apps") }
                planoDownload = PlanoDownload(nomes, baixaveis, todos.size - baixaveis.size, calculando = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                planoDownload = null
                aviso = "Não foi possível ler a pasta: ${e.message}"
            }
        }
    }

    fun confirmarDownloadDePastas() {
        val plano = planoDownload ?: return
        planoDownload = null
        plano.arquivos.forEach { baixar(it.arquivo, it.caminho) }
        aviso = "Baixando ${plano.arquivos.size} arquivo(s)…"
    }

    fun cancelarDownloadDePastas() {
        jobPlano?.cancel()
        planoDownload = null
    }

    // ---------- renomear ----------

    /** Renomeia no Drive e atualiza o nome em todas as listas abertas. */
    fun renomear(f: DriveFile, novoNome: String, aoConcluir: () -> Unit = {}) {
        val conta = contaAtual ?: return
        val nome = novoNome.trim()
        if (nome.isEmpty() || nome == f.name) { aoConcluir(); return }
        viewModelScope.launch {
            try {
                c.drive.renomear(conta.email, f.id, nome)
                aplicarNome(f.id, nome)
                aoConcluir()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aviso = "Não foi possível renomear: ${e.message}"
            }
        }
    }

    private fun aplicarNome(id: String, nome: String) {
        fun List<DriveFile>.ren() = map { if (it.id == id) it.copy(name = nome) else it }
        itens = itens.ren()
        favoritosItens = favoritosItens.ren()
        contaAtual?.let { c.favoritos.salvar(it.email, favoritosItens) }
        visualizador = visualizador?.let { it.copy(fotos = it.fotos.ren()) }
        rolagem = rolagem?.let { it.first to it.second.ren() }
        cache.entries.toList().forEach { (k, v) -> cache[k] = v.first.ren() to v.second }
        pilha = pilha.map { if (it.id == id) it.copy(nome = nome) else it }
        if (arquivoDetalhes?.id == id) {
            arquivoDetalhes = arquivoDetalhes?.copy(name = nome)
            tituloDetalhes = nome
            detalhes = detalhes?.map { if (it.first == "Nome no Drive") it.first to nome else it }
        }
        versaoCache++
    }

    // ---------- mover, copiar, criar pasta e enviar ----------

    /** Escolha de destino aberta: false = mover, true = copiar, null = fechada. */
    var seletorDestino by mutableStateOf<Boolean?>(null); private set

    /** Itens que serão movidos/copiados (o seletor não deixa entrar numa pasta que está entre eles). */
    var alvosDestino by mutableStateOf<List<DriveFile>>(emptyList()); private set

    /** Texto do que está acontecendo em segundo plano (envio, cópia ou movimentação); null se nada. */
    var operacao by mutableStateOf<String?>(null); private set

    fun abrirSeletorDestino(copiar: Boolean) {
        val alvos = itens.filter { it.id in selecionados }
        if (alvos.isEmpty()) return
        alvosDestino = alvos
        seletorDestino = copiar
    }

    fun fecharSeletorDestino() { seletorDestino = null }

    /** Pastas dentro de [idPai], para o seletor de destino. */
    suspend fun pastasDe(idPai: String, pagina: String?): DrivePage =
        contaAtual?.let { c.drive.pastasDe(it.email, idPai, pagina) } ?: DrivePage(emptyList(), null)

    fun confirmarDestino(idDestino: String, nomeDestino: String) {
        val copiar = seletorDestino ?: return
        val conta = contaAtual ?: return
        val alvos = alvosDestino
        seletorDestino = null
        viewModelScope.launch {
            var feitos = 0
            var falhas = 0
            alvos.forEachIndexed { i, f ->
                operacao = (if (copiar) "Copiando" else "Movendo") + " ${i + 1}/${alvos.size}: ${f.name}"
                try {
                    if (copiar) c.drive.copiar(conta.email, f, idDestino) else c.drive.mover(conta.email, f.id, idDestino)
                    feitos++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    falhas++
                    erro = "Não foi possível ${if (copiar) "copiar" else "mover"} ${f.name}: ${e.message}"
                }
            }
            operacao = null
            if (feitos > 0) {
                aviso = "$feitos item(ns) ${if (copiar) "copiado(s)" else "movido(s)"} para $nomeDestino" +
                    if (falhas > 0) " ($falhas com erro)" else ""
                limparSelecao()
                versaoCache++
                // A lista em cache da pasta de destino ficou velha (ex.: mover para a pasta de cima e voltar).
                cache.remove(chave(idDestino))
                recarregar()
            }
        }
    }

    fun criarPasta(nome: String) {
        val conta = contaAtual ?: return
        val limpo = nome.trim()
        if (limpo.isEmpty()) return
        val pai = pilha.last().id
        viewModelScope.launch {
            try {
                c.drive.criarPasta(conta.email, limpo, pai)
                aviso = "Pasta \"$limpo\" criada"
                recarregar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erro = "Não foi possível criar a pasta: ${e.message}"
            }
        }
    }

    /** Envia os arquivos escolhidos para a pasta aberta. */
    fun enviarArquivos(uris: List<android.net.Uri>) {
        val conta = contaAtual ?: return
        if (uris.isEmpty()) return
        val pai = pilha.last().id
        viewModelScope.launch {
            var ok = 0
            uris.forEachIndexed { i, uri ->
                var nome = "arquivo"
                var tamanho = -1L
                runCatching {
                    c.resolver.query(uri, null, null, null, null)?.use { cur ->
                        if (cur.moveToFirst()) {
                            val iNome = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val iTam = cur.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (iNome >= 0) nome = cur.getString(iNome) ?: nome
                            if (iTam >= 0 && !cur.isNull(iTam)) tamanho = cur.getLong(iTam)
                        }
                    }
                }
                val mime = c.resolver.getType(uri) ?: "application/octet-stream"
                val prefixo = "Enviando ${i + 1}/${uris.size}: $nome"
                operacao = prefixo
                var ultimo = -1
                try {
                    c.drive.enviar(
                        conta.email, nome, mime, tamanho, pai,
                        abrir = { c.resolver.openInputStream(uri) ?: throw java.io.IOException("Não foi possível ler $nome") },
                        aoProgresso = { p ->
                            val pct = (p * 100).toInt()
                            if (pct != ultimo) { ultimo = pct; operacao = "$prefixo ($pct%)" }
                        },
                    )
                    ok++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    erro = "Falha ao enviar $nome: ${e.message}"
                }
            }
            operacao = null
            if (ok > 0) {
                aviso = "$ok arquivo(s) enviado(s)"
                recarregar()
            }
        }
    }

    // ---------- capa de pasta ----------

    fun capaDe(idPasta: String): String? = capas[idPasta]

    // capa guardada como "ID" (foto) ou "v:ID|LINK" (vídeo, com a miniatura do Drive)
    private fun idDaCapa(valor: String) = if (valor.startsWith("v:")) valor.substring(2).substringBefore('|') else valor
    private fun linkDaCapa(valor: String) = if (valor.startsWith("v:")) valor.substringAfter('|', "").ifEmpty { null } else null

    private val capasRenovadas = mutableSetOf<String>()

    /** O link da miniatura de um vídeo expira: se a capa não carregar, busca um novo (uma vez por pasta e sessão). */
    fun renovarCapaVideo(idPasta: String) {
        val conta = contaAtual ?: return
        val valor = capas[idPasta] ?: return
        if (!valor.startsWith("v:") || !capasRenovadas.add(idPasta)) return
        val idVideo = idDaCapa(valor)
        viewModelScope.launch {
            try {
                val novo = c.drive.arquivo(conta.email, idVideo)?.thumbnailLink ?: return@launch
                capas[idPasta] = "v:$idVideo|$novo"
                c.capas.salvar(conta.email, capas.toMap())
                versaoCache++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sem rede ou vídeo removido: a pasta fica com o card normal
            }
        }
    }

    fun abrirSeletorCapa(pasta: DriveFile) {
        val conta = contaAtual ?: return
        jobCapa?.cancel()
        pastaDaCapa = pasta
        imagensCapa = emptyList(); proximaCapa = null; temMaisCapa = false
        jobCapa = viewModelScope.launch { paginaCapa(conta, pasta, null) }
        selecionados = emptySet()
    }

    fun carregarMaisCapa() {
        val conta = contaAtual ?: return
        val pasta = pastaDaCapa ?: return
        val pagina = proximaCapa ?: return
        if (carregandoCapa) return
        jobCapa = viewModelScope.launch { paginaCapa(conta, pasta, pagina) }
    }

    private suspend fun paginaCapa(conta: GoogleAccount, pasta: DriveFile, pagina: String?) {
        carregandoCapa = true
        try {
            val p = c.drive.imagensDaPasta(conta.email, pasta.id, pagina)
            imagensCapa = imagensCapa + p.files
            proximaCapa = p.nextPageToken
            temMaisCapa = proximaCapa != null
            token = c.auth.token(conta.email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aviso = "Não foi possível listar as fotos: ${e.message}"
        } finally {
            carregandoCapa = false
        }
    }

    fun fecharSeletorCapa() {
        jobCapa?.cancel()
        pastaDaCapa = null
        imagensCapa = emptyList()
    }

    /** [foto] null remove a capa. */
    fun escolherCapa(foto: DriveFile?) {
        val conta = contaAtual ?: return
        val pasta = pastaDaCapa ?: return
        if (foto == null) {
            capas.remove(pasta.id)
        } else if (foto.isVideo) {
            val link = foto.thumbnailLink
            if (link == null) { aviso = "Este vídeo ainda não tem miniatura no Drive."; return }
            capas[pasta.id] = "v:${foto.id}|$link"
        } else {
            capas[pasta.id] = foto.id
        }
        c.capas.salvar(conta.email, capas.toMap())
        versaoCache++
        fecharSeletorCapa()
    }

    fun tamanhoCache(): Long = c.midia.tamanhoTotal()

    /** Verdadeiro enquanto o cache está sendo apagado ou ajustado em segundo plano. */
    var limpandoCache by mutableStateOf(false); private set
    private var operacoesCache = 0

    fun definirLimiteGb(gb: Int) {
        limiteGb = gb
        c.prefs.limiteGb = gb
        trabalharNoCache { c.midia.definirLimite(gb) }
    }

    fun limparCache() {
        trabalharNoCache { c.midia.limpar() }
    }

    /**
     * Apagar o cache (milhares de arquivos) ou baixar o limite remove arquivos um a um: fora da thread
     * principal, senão a tela trava. O contador evita que uma operação desligue o aviso da outra.
     */
    private fun trabalharNoCache(bloco: () -> Unit) {
        viewModelScope.launch {
            operacoesCache++
            limpandoCache = true
            try {
                withContext(Dispatchers.IO) { bloco() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erro = "Não foi possível ajustar o cache: ${e.message}"
            } finally {
                operacoesCache--
                limpandoCache = operacoesCache > 0
                versaoCache++
            }
        }
    }

    // ---------- favoritos ----------

    private fun recarregarFavoritosLocais() {
        val lista = contaAtual?.let { c.favoritos.ler(it.email) } ?: emptyList()
        favoritosItens = lista
        favoritosIds = lista.map { it.id }.toSet()
        indisponiveis = emptySet()
        capas.clear()
        contaAtual?.let { capas.putAll(c.capas.todas(it.email)) }
    }

    private fun gravarFavoritos(email: String, lista: List<DriveFile>) {
        c.favoritos.salvar(email, lista)
        favoritosItens = lista
        favoritosIds = lista.map { it.id }.toSet()
    }

    fun alternarFavorito(f: DriveFile) {
        val conta = contaAtual ?: return
        val novos = if (f.id in favoritosIds) favoritosItens.filterNot { it.id == f.id } else listOf(f) + favoritosItens
        gravarFavoritos(conta.email, novos)
    }

    /** Se todos os selecionados já são favoritos, remove; senão, adiciona os que faltam. */
    fun favoritarSelecao(origem: List<DriveFile>) {
        val conta = contaAtual ?: return
        val alvo = origem.filter { it.id in selecionados }
        if (alvo.isEmpty()) { selecionados = emptySet(); return }
        val ids = alvo.map { it.id }.toSet()
        val novos = if (alvo.all { it.id in favoritosIds }) {
            favoritosItens.filterNot { it.id in ids }
        } else {
            alvo.filter { it.id !in favoritosIds } + favoritosItens
        }
        gravarFavoritos(conta.email, novos)
        selecionados = emptySet()
    }

    /** Atualiza os metadados dos favoritos (miniaturas expiram) e marca os que sumiram do Drive. */
    fun carregarFavoritos() {
        val conta = contaAtual ?: return
        val atuais = favoritosItens
        if (atuais.isEmpty()) return
        carregandoFavoritos = true
        viewModelScope.launch {
            try {
                val sem = Semaphore(4)
                val resultados: List<Result<DriveFile?>> = coroutineScope {
                    atuais.map { f ->
                        async {
                            sem.withPermit {
                                try {
                                    Result.success(c.drive.arquivo(conta.email, f.id))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Result.failure(e)
                                }
                            }
                        }
                    }.awaitAll()
                }
                val sumidos = mutableSetOf<String>()
                val lista = atuais.mapIndexed { i, antigo ->
                    val r = resultados[i]
                    if (r.isSuccess) {
                        val novo = r.getOrNull()
                        if (novo == null) { sumidos += antigo.id; antigo } else novo
                    } else {
                        antigo
                    }
                }
                if (conta.email == contaAtual?.email) {
                    // A lista pode ter mudado enquanto as chamadas de rede rodavam (favoritou ou
                    // desfavoritou algo): parte da lista de agora e só troca os dados de quem ainda está nela.
                    val atualizados = lista.associateBy { it.id }
                    val agora = favoritosItens
                    gravarFavoritos(conta.email, agora.map { atualizados[it.id] ?: it })
                    val aindaFavoritos = agora.mapTo(HashSet()) { it.id }
                    indisponiveis = sumidos.filterTo(HashSet()) { it in aindaFavoritos }
                    token = c.auth.token(conta.email)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // sem rede: mantém o que já estava salvo
            } finally {
                carregandoFavoritos = false
            }
        }
    }

    fun removerIndisponiveis() {
        val conta = contaAtual ?: return
        gravarFavoritos(conta.email, favoritosItens.filterNot { it.id in indisponiveis })
        indisponiveis = emptySet()
    }

    // ---------- lixeira ----------

    fun carregarLixeira() {
        val conta = contaAtual
        jobLixeira?.cancel()
        lixeira = emptyList(); proximaLixeira = null; temMaisLixeira = false; erroLixeira = null
        if (conta == null) { carregandoLixeira = false; return }
        jobLixeira = viewModelScope.launch { paginaLixeira(conta, null) }
    }

    fun carregarMaisLixeira() {
        val conta = contaAtual ?: return
        val pagina = proximaLixeira ?: return
        if (carregandoLixeira) return
        jobLixeira = viewModelScope.launch { paginaLixeira(conta, pagina) }
    }

    private suspend fun paginaLixeira(conta: GoogleAccount, pagina: String?) {
        carregandoLixeira = true
        try {
            val p = c.drive.list(conta.email, "trashed = true", "folder,name_natural", pagina)
            lixeira = lixeira + p.files
            proximaLixeira = p.nextPageToken
            temMaisLixeira = proximaLixeira != null
            token = c.auth.token(conta.email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownHostException) {
            erroLixeira = "Sem conexão com a internet."
        } catch (e: Exception) {
            erroLixeira = e.message ?: "Falha ao carregar a lixeira."
        } finally {
            carregandoLixeira = false
        }
    }

    /** Aplica [acao] a cada item selecionado da lixeira; o que falhar continua selecionado. */
    private fun acaoNaLixeira(titulo: String, acao: suspend (String, String) -> Unit, aoConcluir: () -> Unit = {}) {
        val conta = contaAtual ?: return
        val ids = selecionados.filter { id -> lixeira.any { it.id == id } }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val falhas = mutableSetOf<String>()
            for (id in ids) {
                try {
                    acao(conta.email, id)
                    lixeira = lixeira.filterNot { it.id == id }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    falhas += id
                }
            }
            selecionados = falhas
            if (falhas.isNotEmpty()) erroLixeira = "Não foi possível $titulo ${falhas.size} item(ns)."
            aoConcluir()
        }
    }

    fun restaurarSelecionados() = acaoNaLixeira("restaurar", { email, id -> c.drive.restaurar(email, id) }) {
        cache.clear()
        recarregar()
    }

    fun excluirDefinitivoSelecionados() = acaoNaLixeira("excluir", { email, id -> c.drive.excluirDefinitivo(email, id) })

    fun esvaziarLixeira() {
        val conta = contaAtual ?: return
        viewModelScope.launch {
            try {
                c.drive.esvaziarLixeira(conta.email)
                lixeira = emptyList(); temMaisLixeira = false; selecionados = emptySet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erroLixeira = e.message ?: "Falha ao esvaziar a lixeira."
            }
        }
    }

    // ---------- contas ----------

    fun aoAutorizar(accessToken: String?) {
        if (accessToken == null) {
            erro = "O Google não devolveu a autorização. Tente de novo."
            return
        }
        viewModelScope.launch {
            try {
                val nova = c.drive.about(accessToken)
                c.auth.remember(nova.email, accessToken)
                contas = contas.filterNot { it.email == nova.email } + nova
                c.accounts.save(contas)
                selecionar(nova)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                erro = e.message ?: "Falha ao identificar a conta."
            }
        }
    }

    fun selecionar(conta: GoogleAccount) {
        contaAtual = conta
        c.midia.definirConta(conta.email)
        recarregarFavoritosLocais()
        c.accounts.currentEmail = conta.email
        pilha = listOf(raiz)
        buscando = false; termo = ""
        carregarPastaAtual(usarCache = true)
    }

    fun remover(conta: GoogleAccount) {
        c.auth.forget(conta.email)
        contas = contas.filterNot { it.email == conta.email }
        c.accounts.save(contas)
        if (contaAtual?.email == conta.email) {
            val outra = contas.firstOrNull()
            if (outra != null) {
                selecionar(outra)
            } else {
                contaAtual = null
                c.midia.definirConta(null)
                recarregarFavoritosLocais()
                c.accounts.currentEmail = null
                pilha = listOf(raiz)
                buscando = false; termo = ""
                carregarPastaAtual(usarCache = false)
            }
        }
    }
}

/** Cópias para compartilhar/abrir/copiar só são apagadas depois deste tempo. */
private const val IDADE_LIMPEZA_COPIAS_MS = 10 * 60 * 1000L
