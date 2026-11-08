package com.nekodrive.app.ui.drive

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nekodrive.app.data.AppContainer
import com.nekodrive.app.data.Progresso
import com.nekodrive.app.data.auth.GoogleAccount
import com.nekodrive.app.data.drive.DriveFile
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
import java.net.UnknownHostException

data class Pasta(val id: String, val nome: String)

/** Situação de um arquivo na lista: salvo no cache, assistido, ou baixando (progresso de 0 a 1). */
data class EstadoArquivo(val noCache: Boolean, val assistido: Boolean, val progresso: Float?, val favorito: Boolean = false)

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

    /** Muda quando o cache ou o progresso mudam, para as listas se atualizarem. */
    var versaoCache by mutableIntStateOf(0); private set
    var limiteGb by mutableIntStateOf(c.prefs.limiteGb); private set
    val progressos = mutableStateMapOf<String, Float>()
    private val tarefas = HashMap<String, Job>()
    val midia get() = c.midia

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
    /** Listagens já carregadas por pasta, para voltar instantaneamente. */
    private val cache = HashMap<String, Pair<List<DriveFile>, String?>>()

    init {
        contaAtual = contas.firstOrNull { it.email == c.accounts.currentEmail } ?: contas.firstOrNull()
        c.midia.definirConta(contaAtual?.email)
        recarregarFavoritosLocais()
        recarregar()
    }

    fun mostrarErro(msg: String) { erro = msg }

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

    private fun ordenacao(): String = "folder," + campo.chaveApi + if (crescente) "" else " desc"

    private fun chave(id: String) = "${contaAtual?.email}|$id"

    private fun salvarCache() {
        if (!buscando) cache[chave(pilha.last().id)] = itens to proxima
    }

    fun recarregar() = carregarPastaAtual(usarCache = false)

    private fun carregarPastaAtual(usarCache: Boolean) {
        job?.cancel()
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
        } catch (e: Exception) {
            erro = e.message ?: "Falha ao carregar a pasta."
        } finally {
            carregando = false
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

    /** Toque no botão Drive: volta para a raiz (e para o topo da lista). */
    fun irParaInicio() {
        if (buscando) { job?.cancel(); buscando = false; termo = "" }
        if (pilha.size > 1) {
            pilha = listOf(raiz)
            carregarPastaAtual(usarCache = true)
        } else if (itens.isEmpty()) {
            carregarPastaAtual(usarCache = true)
        }
        selecionados = emptySet()
        viewModelScope.launch {
            val chave = chaveRolagemDrive()
            if (grade) estadoGrade(chave).animateScrollToItem(0) else estadoLista(chave).animateScrollToItem(0)
        }
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
        job?.cancel()
        reiniciarRolagem("busca")
        buscando = true; termo = ""
        itens = emptyList(); proxima = null; temMais = false; carregando = false
        erro = null; selecionados = emptySet()
    }

    fun definirTermo(t: String) {
        termo = t
        job?.cancel()
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
        job?.cancel()
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
        if (grade) {
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

    fun fecharDetalhes() { detalhes = null }

    /** Baixa o arquivo para a pasta de cópias do cache (usada pelo botão copiar) e devolve o arquivo. */
    suspend fun copiarParaCache(f: DriveFile, pasta: String = "copiar"): File {
        val conta = contaAtual ?: error("Nenhuma conta selecionada")
        val dir = File(c.cacheDir, pasta)
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
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
        )
    }

    fun baixar(f: DriveFile) {
        val conta = contaAtual ?: return
        if (f.isFolder || tarefas[f.id]?.isActive == true || c.midia.completo(conta.email, f)) return
        progressos[f.id] = 0f
        tarefas[f.id] = c.escopo.launch {
            try {
                c.semaforo.withPermit { c.midia.baixar(conta.email, f) { p -> progressos[f.id] = p } }
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

    fun baixarSelecionados() {
        itens.filter { it.id in selecionados && !it.isFolder }.forEach { baixar(it) }
        selecionados = emptySet()
    }

    fun tamanhoCache(): Long = c.midia.tamanhoTotal()

    fun definirLimiteGb(gb: Int) {
        limiteGb = gb
        c.prefs.limiteGb = gb
        c.midia.definirLimite(gb)
        versaoCache++
    }

    fun limparCache() {
        c.midia.limpar()
        versaoCache++
    }

    // ---------- favoritos ----------

    private fun recarregarFavoritosLocais() {
        val lista = contaAtual?.let { c.favoritos.ler(it.email) } ?: emptyList()
        favoritosItens = lista
        favoritosIds = lista.map { it.id }.toSet()
        indisponiveis = emptySet()
    }

    private fun gravarFavoritos(email: String, lista: List<DriveFile>) {
        c.favoritos.salvar(email, lista)
        favoritosItens = lista
        favoritosIds = lista.map { it.id }.toSet()
    }

    fun alternarFavorito(f: DriveFile) {
        val conta = contaAtual ?: return
        if (f.isFolder) return
        val novos = if (f.id in favoritosIds) favoritosItens.filterNot { it.id == f.id } else listOf(f) + favoritosItens
        gravarFavoritos(conta.email, novos)
    }

    /** Se todos os selecionados já são favoritos, remove; senão, adiciona os que faltam. */
    fun favoritarSelecao(origem: List<DriveFile>) {
        val conta = contaAtual ?: return
        val alvo = origem.filter { it.id in selecionados && !it.isFolder }
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
                    gravarFavoritos(conta.email, lista)
                    indisponiveis = sumidos
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
