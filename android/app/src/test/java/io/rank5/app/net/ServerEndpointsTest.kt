package io.rank5.app.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerEndpointsTest {
    @Test
    fun debugUsesEmulatorLoopback() {
        assertEquals("http://10.0.2.2:8080", ServerEndpoints.baseUrl(debug = true))
    }

    @Test
    fun releaseUsesRailwayHttps() {
        assertEquals(
            "https://rank5-production.up.railway.app",
            ServerEndpoints.baseUrl(debug = false),
        )
    }

    @Test
    fun privacyUrlAppendsPathWithoutDoubleSlash() {
        assertEquals(
            "https://rank5-production.up.railway.app/privacy",
            ServerEndpoints.privacyUrl("https://rank5-production.up.railway.app/"),
        )
    }

    @Test
    fun websocketBaseMapsHttpsToWssAndHttpToWs() {
        assertEquals(
            "wss://rank5-production.up.railway.app",
            ServerEndpoints.webSocketBase("https://rank5-production.up.railway.app"),
        )
        assertEquals(
            "ws://10.0.2.2:8080",
            ServerEndpoints.webSocketBase("http://10.0.2.2:8080"),
        )
    }
}
