package io.rank5.app.net

object ServerEndpoints {
    const val PRODUCTION = "https://rank5-production.up.railway.app"
    const val LOCAL_EMULATOR = "http://10.0.2.2:8080"

    fun baseUrl(debug: Boolean): String = if (debug) LOCAL_EMULATOR else PRODUCTION

    fun privacyUrl(httpBase: String): String =
        httpBase.trimEnd('/') + "/privacy"

    fun webSocketBase(httpBase: String): String =
        httpBase.replace("https://", "wss://").replace("http://", "ws://")
}
