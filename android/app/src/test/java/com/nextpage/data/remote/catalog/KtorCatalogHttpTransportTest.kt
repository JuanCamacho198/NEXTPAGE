package com.nextpage.data.remote.catalog

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Real-Ktor wiring check for [KtorCatalogHttpTransport] against a JDK loopback
 * server: proves the identified UA + JSON accept headers actually go out and
 * that a dead endpoint surfaces NETWORK_ERROR. Uses the cached OkHttp engine;
 * production wires the CIO engine over the same transport.
 */
class KtorCatalogHttpTransportTest {

    private lateinit var server: HttpServer
    private var lastUserAgent: String? = null
    private var lastAccept: String? = null
    private lateinit var client: HttpClient

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/books/") { exchange ->
            lastUserAgent = exchange.requestHeaders.getFirst("User-Agent")
            lastAccept = exchange.requestHeaders.getFirst("Accept")
            val body = """{"count":1,"results":[]}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.executor = Executors.newSingleThreadExecutor()
        server.start()
        client = HttpClient(OkHttp)
    }

    @After
    fun tearDown() {
        client.close()
        server.stop(0)
    }

    @Test fun get_sendsIdentifiedUaAndParsesBody() = runTest {
        val transport = KtorCatalogHttpTransport(client)
        val res = transport.get("http://127.0.0.1:${server.address.port}/books/?search=x")
        assertEquals(200, res.status)
        assertTrue(res.body.contains("\"count\":1"))
        assertEquals(ANDROID_USER_AGENT, lastUserAgent)
        assertEquals("application/json", lastAccept)
    }

    @Test fun get_mapsDeadEndpointToNetworkError() = runTest {
        val transport = KtorCatalogHttpTransport(client)
        val deadPort = server.address.port
        server.stop(0)
        try {
            transport.get("http://127.0.0.1:$deadPort/books/")
            error("expected CatalogException")
        } catch (err: CatalogException) {
            assertEquals(CatalogErrorCode.NETWORK_ERROR, err.code)
        }
    }
}
