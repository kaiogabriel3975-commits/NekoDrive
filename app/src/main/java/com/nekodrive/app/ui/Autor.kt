package com.nekodrive.app.ui

/**
 * Dados mostrados em Configurações > Sobre o autor.
 * A foto é res/drawable-nodpi/autor_foto.png e o QR code é res/drawable-nodpi/qr_carteira.png.
 * Se trocar a carteira, gere um QR novo com o texto "bitcoin:" + endereço e troque o arquivo.
 */
object Autor {
    const val NOME = "Kyle_Neeko"
    const val GITHUB = "https://github.com/kaiogabriel3975-commits"
    const val EMAIL = "kaiogabriel3975@gmail.com"

    /** Rede da carteira (aparece acima do endereço). */
    const val REDE = "Bitcoin"

    /** Endereço da carteira. Vazio esconde a parte de apoio. */
    const val CARTEIRA = "bc1qsqhs8y3rj3cs9uaqtm844w79err9sq533nw64g"
}
