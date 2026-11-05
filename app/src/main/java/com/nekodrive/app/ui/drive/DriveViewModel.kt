package com.nekodrive.app.ui.drive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.nekodrive.app.data.AppContainer
import com.nekodrive.app.data.auth.GoogleAccount
import com.nekodrive.app.data.drive.DriveFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.UnknownHostException

data class Pasta(val id: String, val nome: String)

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

    private var proxima: String? = null
    private var job: Job? = null

    init {
        contaAtual = contas.firstOrNull { it.email == c.accounts.currentEmail } ?: contas.firstOrNull()
        recarregar()
    }

    fun mostrarErro(msg: String) { erro = msg }

    fun recarregar() {
        job?.cancel()
        itens = emptyList(); proxima = null; temMais = false; erro = null
        val conta = contaAtual ?: return
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
            val p = c.drive.list(conta.email, pilha.last().id, pagina)
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

    fun abrirPasta(f: DriveFile) {
        pilha = pilha + Pasta(f.id, f.name)
        recarregar()
    }

    fun voltar(): Boolean {
        if (pilha.size <= 1) return false
        pilha = pilha.dropLast(1)
        recarregar()
        return true
    }

    fun aoAutorizar(r: AuthorizationResult) {
        val google = r.toGoogleSignInAccount()
        val email = google?.email
        val accessToken = r.accessToken
        if (email == null || accessToken == null) {
            erro = "Não foi possível identificar a conta."
            return
        }
        c.auth.remember(email, accessToken)
        val nova = GoogleAccount(email, google.displayName, google.photoUrl?.toString())
        contas = contas.filterNot { it.email == email } + nova
        c.accounts.save(contas)
        selecionar(nova)
    }

    fun selecionar(conta: GoogleAccount) {
        contaAtual = conta
        c.accounts.currentEmail = conta.email
        pilha = listOf(raiz)
        recarregar()
    }

    fun remover(conta: GoogleAccount) {
        c.auth.forget(conta.email)
        contas = contas.filterNot { it.email == conta.email }
        c.accounts.save(contas)
        if (contaAtual?.email == conta.email) {
            val proximaConta = contas.firstOrNull()
            if (proximaConta != null) {
                selecionar(proximaConta)
            } else {
                contaAtual = null
                c.accounts.currentEmail = null
                pilha = listOf(raiz)
                recarregar()
            }
        }
    }
}
