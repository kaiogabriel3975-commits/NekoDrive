package com.nekodrive.app.data

import okhttp3.OkHttpClient

/**
 * Cliente HTTP base do app. Quem precisa de regras próprias (interceptors, cookies) parte dele com newBuilder():
 * assim todos compartilham o mesmo pool de conexões e o mesmo despachante, em vez de cada um ter os seus.
 */
object Rede {
    val base: OkHttpClient by lazy { OkHttpClient() }
}
