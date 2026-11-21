package com.nekodrive.app.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import coil.imageLoader
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Versão leve dos GIFs animados para as miniaturas.
 *
 * Na primeira vez a miniatura baixa o GIF inteiro. Depois que ele chega, este objeto gera, em segundo plano,
 * um GIF animado pequeno (até 256 px, 10 quadros por segundo, no máximo 5 segundos) e o guarda em
 * cacheDir/gif-leve. Das próximas vezes a miniatura usa só esse arquivo, e o GIF original sai do cache de imagens.
 *
 * GIFs com menos de 1 MB já são leves e ficam como estão. Se algo falhar (ou o resultado não ficar menor),
 * nada muda: a miniatura continua usando o original. Quem decide é só o arquivo existir ou não.
 */
object GifLeve {
    private const val LIMITE_ORIGINAL = 1_000_000L
    private const val LADO = 256
    private const val QUADRO_MS = 100
    private const val MAX_QUADROS = 50
    private const val LIMITE_TOTAL = 64L * 1024 * 1024
    private const val ALVO_TOTAL = 48L * 1024 * 1024

    // Um GIF por vez e com prioridade baixa: não disputa CPU com a rolagem.
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "NekoDrive-GifLeve").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }
    private val tentados = ConcurrentHashMap.newKeySet<String>()

    private fun pasta(context: Context) = File(context.cacheDir, "gif-leve")

    private fun base(id: String, modificado: String?) =
        id + "_" + Integer.toHexString((modificado ?: "").hashCode())

    /** O GIF leve deste arquivo (na versão atual dele), se já existir. */
    fun pronto(context: Context, id: String, modificado: String?): File? {
        val arquivo = File(pasta(context), base(id, modificado) + ".gif")
        return if (arquivo.isFile && arquivo.length() > 0L) arquivo else null
    }

    /**
     * Chamar depois que a miniatura carregou do GIF original. [chaveOriginal] é a chave do GIF no cache de
     * imagens (a mesma do diskCacheKey do pedido). Não bloqueia: o trabalho roda numa thread própria.
     */
    fun agendar(context: Context, id: String, modificado: String?, chaveOriginal: String) {
        val nome = base(id, modificado)
        if (!tentados.add(nome)) return // uma tentativa por arquivo em cada execução do app
        val app = context.applicationContext
        try {
            executor.execute { runCatching { processar(app, nome, chaveOriginal) } }
        } catch (e: Exception) {
            tentados.remove(nome)
        }
    }

    private fun processar(app: Context, nome: String, chaveOriginal: String) {
        val dir = pasta(app).apply { mkdirs() }
        val leve = File(dir, "$nome.gif")
        val marca = File(dir, "$nome.sem")
        if (leve.exists() || marca.exists()) return
        val disco = app.imageLoader.diskCache ?: return
        val snapshot = disco.openSnapshot(chaveOriginal) ?: return
        val criado = try {
            val origem = snapshot.data.toFile()
            if (origem.length() < LIMITE_ORIGINAL) false else gerar(origem, leve)
        } finally {
            snapshot.close()
        }
        if (criado) {
            // o original só atrapalha agora: libera o espaço (o GIF leve já foi conferido)
            disco.remove(chaveOriginal)
            aparar(dir)
        } else {
            // pequeno, sem animação ou sem ganho: não tenta de novo
            runCatching { marca.createNewFile() }
        }
    }

    @Suppress("DEPRECATION")
    private fun gerar(origem: File, destino: File): Boolean {
        val tmp = File(destino.parentFile, destino.name + ".tmp")
        var bmp: Bitmap? = null
        try {
            val filme = Movie.decodeFile(origem.path) ?: return false
            val w = filme.width()
            val h = filme.height()
            val duracao = filme.duration()
            if (w <= 0 || h <= 0 || duracao < 2 * QUADRO_MS) return false // sem animação: nada a reduzir
            if (w.toLong() * h > 4096L * 4096L) return false
            val escala = min(1f, LADO.toFloat() / max(w, h))
            val lw = max(1, (w * escala).roundToInt())
            val lh = max(1, (h * escala).roundToInt())
            val n = min(MAX_QUADROS, duracao / QUADRO_MS)

            val imagem = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
            bmp = imagem
            val tela = Canvas(imagem)
            val px = IntArray(lw * lh)
            fun desenhar(i: Int) {
                imagem.eraseColor(0)
                filme.setTime(i * QUADRO_MS)
                tela.save()
                tela.scale(escala, escala)
                filme.draw(tela, 0f, 0f)
                tela.restore()
                imagem.getPixels(px, 0, lw, 0, 0, lw, lh)
            }

            // 1ª passada: as 255 cores mais usadas (5 bits por canal) viram a paleta; o índice 0 é transparente
            val contagem = IntArray(CORES)
            val somaR = LongArray(CORES)
            val somaG = LongArray(CORES)
            val somaB = LongArray(CORES)
            for (i in 0 until n) {
                desenhar(i)
                for (p in px) {
                    if ((p ushr 24) < 128) continue
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val k = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                    contagem[k]++
                    somaR[k] += r.toLong()
                    somaG[k] += g.toLong()
                    somaB[k] += b.toLong()
                }
            }
            val escolhidas = (0 until CORES).filter { contagem[it] > 0 }.sortedByDescending { contagem[it] }.take(255)
            if (escolhidas.isEmpty()) return false
            val tabela = ByteArray(768)
            val mapa = IntArray(CORES) { -1 }
            escolhidas.forEachIndexed { i, k ->
                val indice = i + 1
                tabela[indice * 3] = (somaR[k] / contagem[k]).toByte()
                tabela[indice * 3 + 1] = (somaG[k] / contagem[k]).toByte()
                tabela[indice * 3 + 2] = (somaB[k] / contagem[k]).toByte()
                mapa[k] = indice
            }
            // cor fora da paleta: usa a mais próxima (calculado uma vez por cor)
            fun maisProxima(k: Int): Int {
                val r = ((k shr 10) and 31) * 255 / 31
                val g = ((k shr 5) and 31) * 255 / 31
                val b = (k and 31) * 255 / 31
                var melhor = 1
                var menor = Int.MAX_VALUE
                for (i in 1..escolhidas.size) {
                    val dr = (tabela[i * 3].toInt() and 0xFF) - r
                    val dg = (tabela[i * 3 + 1].toInt() and 0xFF) - g
                    val db = (tabela[i * 3 + 2].toInt() and 0xFF) - b
                    val d = dr * dr + dg * dg + db * db
                    if (d < menor) { menor = d; melhor = i }
                }
                return melhor
            }

            // 2ª passada: desenha de novo e grava quadro a quadro
            BufferedOutputStream(FileOutputStream(tmp)).use { out ->
                out.write("GIF89a".toByteArray(Charsets.US_ASCII))
                escreverShort(out, lw)
                escreverShort(out, lh)
                out.write(0xF7) // tabela de cores global com 256 entradas
                out.write(0)
                out.write(0)
                out.write(tabela)
                // repetir para sempre
                out.write(0x21); out.write(0xFF); out.write(0x0B)
                out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
                out.write(0x03); out.write(0x01); escreverShort(out, 0); out.write(0)
                val lzw = SaidaLzw(out)
                val indices = ByteArray(lw * lh)
                for (i in 0 until n) {
                    desenhar(i)
                    for (j in px.indices) {
                        val p = px[j]
                        if ((p ushr 24) < 128) {
                            indices[j] = 0
                        } else {
                            val k = ((((p shr 16) and 0xFF) shr 3) shl 10) or
                                ((((p shr 8) and 0xFF) shr 3) shl 5) or
                                ((p and 0xFF) shr 3)
                            var m = mapa[k]
                            if (m < 0) { m = maisProxima(k); mapa[k] = m }
                            indices[j] = m.toByte()
                        }
                    }
                    // controle do quadro: apaga antes do próximo, índice 0 transparente, 10 centésimos
                    out.write(0x21); out.write(0xF9); out.write(0x04)
                    out.write((2 shl 2) or 1)
                    escreverShort(out, QUADRO_MS / 10)
                    out.write(0)
                    out.write(0)
                    out.write(0x2C)
                    escreverShort(out, 0); escreverShort(out, 0)
                    escreverShort(out, lw); escreverShort(out, lh)
                    out.write(0)
                    lzw.codificar(indices, indices.size)
                }
                out.write(0x3B)
            }

            // só vale se ficou menor e o próprio Android consegue abrir
            if (tmp.length() <= 0L || tmp.length() >= origem.length()) { tmp.delete(); return false }
            val abre = try {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(tmp)).intrinsicWidth > 0
            } catch (e: Exception) {
                false
            }
            if (!abre || !tmp.renameTo(destino)) { tmp.delete(); return false }
            return true
        } catch (e: Exception) {
            tmp.delete()
            return false
        } catch (e: OutOfMemoryError) {
            tmp.delete()
            return false
        } finally {
            bmp?.recycle()
        }
    }

    /** Mantém a pasta abaixo do limite, apagando os arquivos mais antigos primeiro. */
    private fun aparar(dir: File) {
        val arquivos = dir.listFiles() ?: return
        var total = arquivos.sumOf { it.length() }
        if (total <= LIMITE_TOTAL) return
        for (f in arquivos.sortedBy { it.lastModified() }) {
            if (total <= ALVO_TOTAL) break
            total -= f.length()
            f.delete()
        }
    }

    private fun escreverShort(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private const val CORES = 32768
}

/** Compressão LZW do formato GIF (códigos de 9 a 12 bits, em blocos de até 255 bytes). */
private class SaidaLzw(private val out: OutputStream) {
    private val bloco = ByteArray(255)
    private var usados = 0
    private var acumulado = 0
    private var bits = 0
    private val chaves = IntArray(TABELA)
    private val valores = IntArray(TABELA)

    private fun gravar(b: Int) {
        bloco[usados++] = b.toByte()
        if (usados == 255) {
            out.write(255)
            out.write(bloco, 0, 255)
            usados = 0
        }
    }

    private fun emitir(codigo: Int, tamanho: Int) {
        acumulado = acumulado or (codigo shl bits)
        bits += tamanho
        while (bits >= 8) {
            gravar(acumulado and 0xFF)
            acumulado = acumulado ushr 8
            bits -= 8
        }
    }

    private fun posicao(chave: Int) = (chave * -1640531535) ushr 18

    private fun buscar(chave: Int): Int {
        var p = posicao(chave)
        while (true) {
            val c = chaves[p]
            if (c == 0) return -1
            if (c == chave + 1) return valores[p]
            p = (p + 1) and (TABELA - 1)
        }
    }

    private fun inserir(chave: Int, valor: Int) {
        var p = posicao(chave)
        while (chaves[p] != 0) p = (p + 1) and (TABELA - 1)
        chaves[p] = chave + 1
        valores[p] = valor
    }

    /** Grava um quadro: tamanho mínimo do código, os blocos de dados e o terminador. */
    fun codificar(indices: ByteArray, total: Int) {
        val minimo = 8
        val limpar = 1 shl minimo
        val fim = limpar + 1
        out.write(minimo)
        var proximo = fim + 1
        var tamanho = minimo + 1
        chaves.fill(0)
        acumulado = 0
        bits = 0
        emitir(limpar, tamanho)
        var atual = indices[0].toInt() and 0xFF
        for (i in 1 until total) {
            val k = indices[i].toInt() and 0xFF
            val chave = (atual shl 8) or k
            val achado = buscar(chave)
            if (achado < 0) {
                emitir(atual, tamanho)
                if (proximo == 4096) {
                    // tabela cheia: manda limpar e recomeça
                    emitir(limpar, tamanho)
                    proximo = fim + 1
                    tamanho = minimo + 1
                    chaves.fill(0)
                } else {
                    if (proximo >= (1 shl tamanho)) tamanho++
                    inserir(chave, proximo)
                    proximo++
                }
                atual = k
            } else {
                atual = achado
            }
        }
        emitir(atual, tamanho)
        emitir(fim, tamanho)
        if (bits > 0) gravar(acumulado and 0xFF)
        if (usados > 0) {
            out.write(usados)
            out.write(bloco, 0, usados)
            usados = 0
        }
        out.write(0)
        acumulado = 0
        bits = 0
    }

    private companion object {
        const val TABELA = 16384
    }
}
