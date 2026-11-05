package com.nekodrive.app.ui.drive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nekodrive.app.data.AppContainer
import com.nekodrive.app.data.auth.GoogleAccount
import com.nekodrive.app.data.drive.DriveFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.UnknownHostException

data class Pasta(val id: String, val nome: String)

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

    private var proxima: String? = null
    private var job: Job? = null
    /** Listagens já carregadas por pasta, para voltar instantaneamente. */
    private val cache = HashMap<String, Pair<List<DriveFile>, String?>>()

    init {
        contaAtual = contas.firstOrNull { it.email == c.accounts.currentEmail } ?: contas.firstOrNull()
        recarregar()
    }

    fun mostrarErro(msg: String) { erro = msg }

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
        job?.cancel()
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
                    itens = itens.filterNot { it.id == id }
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
                c.accounts.currentEmail = null
                pilha = listOf(raiz)
                buscando = false; termo = ""
                carregarPastaAtual(usarCache = false)
            }
        }
    }
}
