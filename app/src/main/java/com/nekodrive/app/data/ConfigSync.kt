package com.nekodrive.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.nekodrive.app.data.auth.AccountStore
import com.nekodrive.app.data.drive.DriveFile
import com.nekodrive.app.data.drive.DriveHttpException
import com.nekodrive.app.data.drive.DriveRepository
import com.nekodrive.app.diagnostico.AppLogger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda no Drive de cada conta (pasta "etc", arquivo "configNkapp.txt") o que só o app enxerga: ajustes, favoritos
 * e capas de pasta. O arquivo é um JSON compactado (gzip), feito para ocupar o mínimo, não para ser lido.
 *
 * Quando carrega: ao abrir o app (depois de alguns segundos, em segundo plano) e ao adicionar uma conta.
 * Quando salva: 30 segundos depois da última mudança e quando o app vai para segundo plano. Só sobe se o conteúdo
 * mudou de verdade (renovar miniaturas, por exemplo, não conta).
 *
 * Regras de conflito: os ajustes valem para o app todo e vence o mais recente. Favoritos e capas são por conta:
 * se só o Drive mudou, ele vence; se os dois lados mudaram, juntam-se (nada se perde). O que não entra: token,
 * ID do cliente do Google, limite do cache (depende do espaço de cada aparelho) e o aviso de notificação.
 */
class ConfigSync(
    context: Context,
    private val drive: DriveRepository,
    private val contas: AccountStore,
    private val favoritos: FavoritosStore,
    private val capas: CapasStore,
    private val prefs: ViewPrefs,
    private val escopo: CoroutineScope,
) {
    private val app = context.applicationContext
    private val viewP = app.getSharedPreferences("view", Context.MODE_PRIVATE)
    private val meta = app.getSharedPreferences("sync_meta", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val conferidas = ConcurrentHashMap.newKeySet<String>() // contas já comparadas com o Drive neste processo
    private val ids = ConcurrentHashMap<String, Pair<String, String?>>() // pasta e arquivo de cada conta
    private val principal = Handler(Looper.getMainLooper())

    @Volatile private var agendado: Job? = null
    @Volatile private var ultimaConferencia = 0L

    /** Chamado (na thread principal) com o e-mail da conta cujos favoritos/capas vieram do Drive. */
    @Volatile var aoDadosAplicados: ((String) -> Unit)? = null

    /** Chamado (na thread principal) quando os ajustes vieram do Drive. */
    @Volatile var aoConfigAplicada: (() -> Unit)? = null

    // o SharedPreferences guarda o ouvinte só por referência fraca: precisa ficar em um campo
    private val ouvinte = SharedPreferences.OnSharedPreferenceChangeListener { _, chave ->
        if (chave == null || chave !in IGNORADAS) agendar()
    }

    init {
        viewP.registerOnSharedPreferenceChangeListener(ouvinte)
        app.getSharedPreferences("favoritos", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(ouvinte)
        app.getSharedPreferences("capas", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(ouvinte)
    }

    // ---------- pontos de entrada ----------

    /** Ao abrir o app (ou voltar a ele): confere as contas ainda não conferidas e sobe o que estiver pendente. */
    fun iniciar() {
        val agora = SystemClock.elapsedRealtime()
        if (ultimaConferencia != 0L && agora - ultimaConferencia < INTERVALO_MIN_MS) return
        ultimaConferencia = agora
        escopo.launch {
            delay(ESPERA_INICIAL_MS)
            sincronizar()
        }
    }

    /** Quando o app vai para segundo plano: não espera o prazo para salvar. */
    fun salvarAgora() {
        agendado?.cancel()
        escopo.launch { sincronizar() }
    }

    /** Conta nova (ou de volta): traz o que ela já tem guardado no Drive. */
    fun carregarConta(email: String) {
        escopo.launch {
            mutex.withLock {
                try {
                    conferidas.remove(email)
                    conferir(email)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    registrar("carregar conta: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    /** Conta removida do app: esquece o controle local (o arquivo continua no Drive dela). */
    fun esquecer(email: String) {
        conferidas.remove(email)
        ids.remove(email)
        meta.edit().remove("dt:$email").remove("dh:$email").remove("ds:$email").remove("cu:$email").apply()
    }

    private fun agendar() {
        agendado?.cancel()
        agendado = escopo.launch {
            delay(ESPERA_SALVAR_MS)
            sincronizar()
        }
    }

    private suspend fun sincronizar() {
        mutex.withLock {
            detectar()
            for (conta in contas.load()) {
                try {
                    if (conta.email !in conferidas) conferir(conta.email) else if (pendente(conta.email)) subir(conta.email)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    registrar("sincronizar: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    // ---------- o que mudou aqui ----------

    private fun cfgLocal(): Map<String, Any> {
        val mapa = HashMap<String, Any>()
        for ((chave, valor) in viewP.all) {
            if (chave in IGNORADAS || valor == null) continue
            if (valor is Boolean || valor is String || valor is Number) mapa[chave] = valor
        }
        return mapa
    }

    private fun hash(texto: String): String =
        MessageDigest.getInstance("SHA-1").digest(texto.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun hashCfg(cfg: Map<String, Any>): String = hash(cfg.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" })

    // Só o que é sincronizado entra na conta (miniaturas e md5 dos favoritos são renovados pelo app)
    private fun hashDados(favs: List<DriveFile>, mapa: Map<String, String>): String = hash(
        favs.joinToString("\n") { "${it.id}|${it.name}|${it.mimeType}|${it.size}|${it.modifiedTime}" } +
            "\n--\n" + mapa.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" },
    )

    /** Compara o estado de agora com o da última sincronização e marca o que ficou para subir. */
    private fun detectar() {
        val agora = System.currentTimeMillis()
        val ed = meta.edit()
        val cfg = cfgLocal()
        val hc = hashCfg(cfg)
        val hcAntigo = meta.getString("ch", null)
        if (hcAntigo == null) {
            // primeira vez: o que já existe é o ponto de partida, e só conta como "mudança" se houver ajustes
            ed.putString("ch", hc).putLong("ct", if (cfg.isNotEmpty()) agora else 0L)
        } else if (hcAntigo != hc) {
            ed.putString("ch", hc).putLong("ct", agora)
        }
        for (conta in contas.load()) {
            val favs = favoritos.ler(conta.email)
            val mapa = capas.todas(conta.email)
            val h = hashDados(favs, mapa)
            val antigo = meta.getString("dh:${conta.email}", null)
            if (antigo == null) {
                ed.putString("dh:${conta.email}", h).putBoolean("ds:${conta.email}", favs.isNotEmpty() || mapa.isNotEmpty())
            } else if (antigo != h) {
                ed.putString("dh:${conta.email}", h).putBoolean("ds:${conta.email}", true)
            }
        }
        ed.apply()
    }

    private fun pendente(email: String): Boolean =
        meta.getBoolean("ds:$email", false) || meta.getLong("ct", 0L) > meta.getLong("cu:$email", 0L)

    // ---------- Drive -> aparelho ----------

    private class Foto(val ct: Long, val dt: Long, val cfg: Map<String, Any>, val fav: List<DriveFile>, val capas: Map<String, String>)

    private class Leitura(val foto: Foto?, val futura: Boolean)

    private suspend fun conferir(email: String) {
        val achado = drive.acharConfig(email)
        if (achado != null) ids[email] = achado
        val idArquivo = achado?.second
        val leitura = if (idArquivo != null) decodificar(drive.lerBytes(email, idArquivo)) else null
        // arquivo de uma versão mais nova do app: não mexe nele
        if (leitura?.futura == true) {
            conferidas.add(email)
            return
        }
        val remoto = leitura?.foto
        if (remoto == null) {
            // nada aproveitável no Drive: o que existe aqui sobe
            if (temDados(email) || cfgLocal().isNotEmpty()) {
                meta.edit().putBoolean("ds:$email", true).putLong("cu:$email", 0L).apply()
            }
            conferidas.add(email)
            if (pendente(email)) subir(email)
            return
        }
        aplicar(email, remoto)
        conferidas.add(email)
        if (pendente(email)) subir(email)
    }

    private fun temDados(email: String): Boolean = favoritos.ler(email).isNotEmpty() || capas.todas(email).isNotEmpty()

    private fun aplicar(email: String, remoto: Foto) {
        // ajustes: vale o mais recente
        val ctLocal = meta.getLong("ct", 0L)
        if (remoto.ct > ctLocal) {
            aplicarCfg(remoto)
            meta.edit().putLong("cu:$email", remoto.ct).apply()
        } else if (remoto.ct == ctLocal) {
            meta.edit().putLong("cu:$email", ctLocal).apply()
        }
        // favoritos e capas desta conta
        val dtLocal = meta.getLong("dt:$email", 0L)
        val sujo = meta.getBoolean("ds:$email", false)
        when {
            remoto.dt > dtLocal -> aplicarDados(email, remoto, mesclar = sujo || dtLocal == 0L)
            remoto.dt < dtLocal -> meta.edit().putBoolean("ds:$email", true).apply() // o Drive está atrasado: sobe o daqui
        }
    }

    private fun aplicarCfg(remoto: Foto) {
        val atuais = viewP.all
        val ed = viewP.edit()
        atuais.keys.filter { it !in IGNORADAS && it !in remoto.cfg }.forEach { ed.remove(it) }
        for ((chave, valor) in remoto.cfg) {
            if (chave in IGNORADAS) continue
            when {
                valor is Boolean -> ed.putBoolean(chave, valor)
                valor is String -> ed.putString(chave, valor)
                valor is Number -> {
                    val decimal = atuais[chave] is Float || chave in CHAVES_DECIMAIS
                    if (decimal) ed.putFloat(chave, valor.toFloat()) else ed.putInt(chave, valor.toInt())
                }
            }
        }
        ed.apply()
        // o tema é um estado do Compose lido uma vez: precisa ser atualizado à mão
        runCatching { prefs.tema = viewP.getInt("tema", 0) }
        meta.edit().putLong("ct", remoto.ct).putString("ch", hashCfg(cfgLocal())).apply()
        principal.post { aoConfigAplicada?.invoke() }
    }

    private fun aplicarDados(email: String, remoto: Foto, mesclar: Boolean) {
        val favLocal = favoritos.ler(email)
        val capasLocal = capas.todas(email)
        // mantém o objeto local de quem não mudou (ele traz o link da miniatura)
        val doDrive = remoto.fav.map { r -> favLocal.firstOrNull { it.id == r.id && it.modifiedTime == r.modifiedTime } ?: r }
        val favs: List<DriveFile>
        val mapa: Map<String, String>
        if (mesclar) {
            val idsLocais = favLocal.mapTo(HashSet()) { it.id }
            favs = favLocal + doDrive.filter { it.id !in idsLocais }
            mapa = remoto.capas + capasLocal
        } else {
            favs = doDrive
            mapa = remoto.capas
        }
        favoritos.salvar(email, favs)
        capas.salvar(email, mapa)
        val h = hashDados(favs, mapa)
        meta.edit()
            .putLong("dt:$email", remoto.dt)
            .putString("dh:$email", h)
            .putBoolean("ds:$email", mesclar && h != hashDados(remoto.fav, remoto.capas))
            .apply()
        principal.post { aoDadosAplicados?.invoke(email) }
    }

    // ---------- aparelho -> Drive ----------

    private suspend fun subir(email: String) {
        val favs = favoritos.ler(email)
        val mapa = capas.todas(email)
        val cfg = cfgLocal()
        if (favs.isEmpty() && mapa.isEmpty() && cfg.isEmpty()) {
            meta.edit().putBoolean("ds:$email", false).apply()
            return
        }
        val agora = System.currentTimeMillis()
        var ct = meta.getLong("ct", 0L)
        if (ct == 0L && cfg.isNotEmpty()) {
            ct = agora
            meta.edit().putLong("ct", ct).apply()
        }
        val bytes = codificar(Foto(ct, agora, cfg, favs, mapa))
        val conhecido = ids[email] ?: drive.acharConfig(email)
        val (pasta, arquivo) = try {
            drive.gravarConfig(email, conhecido?.first, conhecido?.second, bytes)
        } catch (e: DriveHttpException) {
            if (e.code != 404) throw e
            // o arquivo ou a pasta sumiu do Drive: procura de novo e grava
            ids.remove(email)
            val novo = drive.acharConfig(email)
            drive.gravarConfig(email, novo?.first, novo?.second, bytes)
        }
        ids[email] = pasta to arquivo
        meta.edit()
            .putLong("dt:$email", agora)
            .putLong("cu:$email", ct)
            .putString("dh:$email", hashDados(favs, mapa))
            .putBoolean("ds:$email", false)
            .apply()
    }

    // ---------- formato do arquivo ----------

    private fun codificar(f: Foto): ByteArray {
        val favs = JSONArray()
        f.fav.forEach {
            favs.put(JSONArray().put(it.id).put(it.name).put(it.mimeType).put(it.size ?: -1L).put(it.modifiedTime ?: ""))
        }
        val o = JSONObject()
            .put("v", VERSAO_ARQUIVO)
            .put("ct", f.ct)
            .put("dt", f.dt)
            .put("c", JSONObject(f.cfg as Map<*, *>))
            .put("f", favs)
            .put("p", JSONObject(f.capas as Map<*, *>))
        val saida = ByteArrayOutputStream()
        GZIPOutputStream(saida).use { it.write(o.toString().toByteArray(Charsets.UTF_8)) }
        return saida.toByteArray()
    }

    private fun decodificar(bytes: ByteArray): Leitura = try {
        val texto = String(GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }, Charsets.UTF_8)
        val o = JSONObject(texto)
        if (o.optInt("v", 0) > VERSAO_ARQUIVO) {
            Leitura(null, true)
        } else {
            val c = o.optJSONObject("c") ?: JSONObject()
            val cfg = HashMap<String, Any>()
            for (chave in c.keys()) cfg[chave] = c.get(chave)
            val arr = o.optJSONArray("f") ?: JSONArray()
            val favs = (0 until arr.length()).map { i ->
                val a = arr.getJSONArray(i)
                DriveFile(
                    id = a.getString(0),
                    name = a.optString(1),
                    mimeType = a.optString(2),
                    size = a.optLong(3, -1L).takeIf { it >= 0 },
                    modifiedTime = a.optString(4).ifEmpty { null },
                    thumbnailLink = null,
                )
            }
            val p = o.optJSONObject("p") ?: JSONObject()
            val capasRemotas = HashMap<String, String>()
            for (chave in p.keys()) capasRemotas[chave] = p.getString(chave)
            Leitura(Foto(o.optLong("ct", 0L), o.optLong("dt", 0L), cfg, favs, capasRemotas), false)
        }
    } catch (e: Exception) {
        Leitura(null, false)
    }

    private fun registrar(mensagem: String) {
        AppLogger.log(app, "ConfigSync: $mensagem")
    }

    private companion object {
        const val VERSAO_ARQUIVO = 1
        const val ESPERA_SALVAR_MS = 30_000L
        const val ESPERA_INICIAL_MS = 2_500L
        const val INTERVALO_MIN_MS = 60_000L

        /** Ajustes só deste aparelho: não viajam. */
        val IGNORADAS = setOf("pediuNotificacao", "limiteGb")

        /** Ajustes guardados como decimal (o JSON devolve 2.0 como 2). */
        val CHAVES_DECIMAIS = setOf("turbo")
    }
}
