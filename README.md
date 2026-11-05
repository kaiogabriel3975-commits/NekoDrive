# nekodrive

App Android (Kotlin + Jetpack Compose) para ver vídeos e fotos do Google Drive.
Etapa 1: projeto base (tema Material You, cabeçalho, barra inferior, ícones e build no GitHub).

## Compilar pelo GitHub Actions

1. Crie um repositório **privado** e envie o conteúdo desta pasta (branch `main`).
2. O workflow `Build APK` roda a cada push e também manualmente (aba Actions).
3. O APK fica em **Artifacts > nekodrive-apk** ao fim do build.

Sem os secrets abaixo, o APK sai assinado com a chave de debug. Funciona para testar o visual,
mas o login do Google (etapa 2) exige a chave fixa.

## Chave de assinatura (fazer uma vez, guardar bem)

    keytool -genkeypair -v -keystore nekodrive.jks -alias nekodrive \
      -keyalg RSA -keysize 2048 -validity 10000
    base64 -w0 nekodrive.jks        # copie a saída para o secret KEYSTORE_BASE64

Em Settings > Secrets and variables > Actions, crie:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (nekodrive) e `KEY_PASSWORD`.

SHA-1 para o cliente OAuth do Google Cloud (etapa 2):

    keytool -list -v -keystore nekodrive.jks -alias nekodrive

Nunca envie o `.jks` ao repositório (já está no `.gitignore`).

## Estado atual

- minSdk 31 (Android 12), pacote `com.nekodrive.app`
- Barras translúcidas; o blur real (Haze) entra no polimento
- Botões de conta e configurações ainda sem ação
