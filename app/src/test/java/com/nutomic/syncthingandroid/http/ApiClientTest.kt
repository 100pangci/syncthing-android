package com.nutomic.syncthingandroid.http

import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.URL
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ApiClient], the phase2 OkHttp replacement for the Volley-based
 * [ApiRequest]/[GetRequest]/[PostRequest] family. The retry/fail-fast expectations encode the
 * exact Volley 1.2.1 semantics the old implementation had (verified against Volley's bytecode):
 * socket timeouts are retried (1 + MAX_RETRIES attempts), connection failures and HTTP error
 * responses fail fast.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var certFile: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Content is irrelevant: plain-HTTP tests never trigger a TLS handshake, so the trust
        // manager never reads the file.
        certFile = File.createTempFile("https-cert", ".pem")
        certFile.deleteOnExit()
    }

    @After
    fun tearDown() {
        server.shutdown()
        certFile.delete()
    }

    private fun client(
        url: URL = server.url("/").toUrl(),
        maxAttempts: Int = 1 + MAX_RETRIES,
        timeoutMs: Long = 5000,
    ) = ApiClient(certFile, url, API_KEY, maxAttempts, timeoutMs)

    @Test
    fun get_sendsApiKeyHeader_params_andReturnsBody() {
        server.enqueue(MockResponse().setBody("hello"))

        val result = runBlocking {
            client().get("/rest/system/version", params = mapOf("limit" to "5", "timeout" to "1"))
        }

        assertEquals("hello", result)
        val recorded = server.takeRequest()
        assertEquals(API_KEY, recorded.getHeader("X-API-Key"))
        assertEquals("/rest/system/version", recorded.path!!.substringBefore('?'))
        assertEquals("5", recorded.requestUrl!!.queryParameter("limit"))
        assertEquals("1", recorded.requestUrl!!.queryParameter("timeout"))
        assertEquals("GET", recorded.method)
    }

    @Test
    fun post_sendsBody() {
        server.enqueue(MockResponse().setBody("ok"))

        val result = runBlocking {
            client().post("/rest/system/config", body = "{\"x\":1}")
        }

        assertEquals("ok", result)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("{\"x\":1}", recorded.body.readUtf8())
    }

    @Test
    fun post_withoutBody_sendsEmptyBody() {
        server.enqueue(MockResponse().setBody("ok"))

        val result = runBlocking { client().post("/rest/db/scan") }

        assertEquals("ok", result)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(0L, recorded.bodySize)
    }

    @Test(timeout = 30000)
    fun httpError_failsFast_withoutRetrying() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        // Must never be consumed: Volley (default config) and the new client both fail fast on
        // HTTP error responses.
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom2"))

        val exception = assertThrows(ApiClient.ApiHttpException::class.java) {
            runBlocking { client().get("/rest/x") }
        }
        assertEquals(500, exception.statusCode)
        assertEquals(1, server.requestCount)
    }

    @Test(timeout = 30000)
    fun timeout_retriesUpToSixAttempts_thenFails() {
        repeat(1 + MAX_RETRIES) {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        }

        assertThrows(IOException::class.java) {
            runBlocking { client(timeoutMs = 200).get("/rest/x") }
        }
        assertEquals(1 + MAX_RETRIES, server.requestCount)
    }

    @Test(timeout = 30000)
    fun timeout_retryEventuallySucceeds() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.enqueue(MockResponse().setBody("recovered"))

        val result = runBlocking { client(timeoutMs = 200).get("/rest/x") }

        assertEquals("recovered", result)
        assertEquals(3, server.requestCount)
    }

    @Test(timeout = 30000)
    fun connectFailure_failsFast_likeVolley() {
        val url = server.url("/").toUrl()
        server.shutdown()

        val startedAt = System.currentTimeMillis()
        assertThrows(IOException::class.java) {
            runBlocking { client(url = url, timeoutMs = 1000).get("/rest/x") }
        }
        val elapsed = System.currentTimeMillis() - startedAt
        // Volley 1.2.1 does NOT retry connection failures (shouldRetryConnectionErrors=false);
        // six 5s attempts would take ~30s.
        assertTrue("connect failure must fail fast, took ${elapsed}ms", elapsed < 5000)
    }

    @Test(timeout = 30000)
    fun hostIsPinnedToLoopback_regardlessOfConfiguredAddress() {
        server.enqueue(MockResponse().setBody("lb"))

        // A non-resolvable host: the request can only reach the (loopback) server if
        // forceLoopbackHost rewrote it to 127.0.0.1.
        val result = runBlocking {
            client(url = URL("http://example.invalid:${server.port}")).get("/rest/x")
        }

        assertEquals("lb", result)
        val recorded = server.takeRequest()
        assertEquals("127.0.0.1:${server.port}", recorded.getHeader("Host"))
    }

    @Test
    fun forceLoopbackHost_preservesSchemePortAndFile() {
        val rewritten = forceLoopbackHost(URL("https://0.0.0.0:8384/rest/system/config"))
        assertEquals(URL("https://127.0.0.1:8384/rest/system/config"), rewritten)

        val withQuery = forceLoopbackHost(URL("http://example.invalid:1234/p?x=1"))
        assertEquals(URL("http://127.0.0.1:1234/p?x=1"), withQuery)
    }

    @Test
    fun forceLoopbackHost_defaultsToDefaultWebGuiPort() {
        val rewritten = forceLoopbackHost(URL("https://example.invalid"))
        assertEquals(URL("https://127.0.0.1:8384"), rewritten)
    }

    @Test
    fun resolveCharset_explicitCharsetWins() {
        assertEquals(Charset.forName("GBK"), resolveCharset("application/json; charset=GBK"))
        assertEquals(Charsets.UTF_8, resolveCharset("text/plain; charset=utf-8"))
    }

    @Test
    fun resolveCharset_jsonWithoutCharsetIsUtf8() {
        assertEquals(Charsets.UTF_8, resolveCharset("application/json"))
        assertEquals(Charsets.UTF_8, resolveCharset("APPLICATION/JSON"))
    }

    @Test
    fun resolveCharset_nonJsonDefaultsToIso8859_1_likeVolley() {
        assertEquals(Charsets.ISO_8859_1, resolveCharset(null))
        assertEquals(Charsets.ISO_8859_1, resolveCharset("text/html"))
    }

    @Test
    fun resolveCharset_skipsFirstPart_likeVolley() {
        // HttpHeaderParser.parseCharset only scans parts after the first one.
        assertEquals(Charsets.ISO_8859_1, resolveCharset("charset=UTF-8"))
    }

    @Test
    fun resolveCharset_invalidCharsetFallsBackInsteadOfThrowing() {
        assertEquals(Charsets.ISO_8859_1, resolveCharset("application/json; charset=NOT-A-CHARSET"))
    }

    @Test(timeout = 30000)
    fun decode_charsetIsAppliedOnTheWire() {
        // application/json without charset -> UTF-8
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody(okio.Buffer().write("äöü".toByteArray(Charsets.UTF_8)))
        )
        // text/html without charset -> ISO-8859-1 (Volley's legacy default)
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html")
                .setBody(okio.Buffer().write(byteArrayOf(0xE4.toByte(), 0xF6.toByte(), 0xFC.toByte())))
        )

        assertEquals("äöü", runBlocking { client().get("/rest/a") })
        assertEquals("äöü", runBlocking { client().get("/rest/b") })
    }

    private companion object {
        const val API_KEY = "test-api-key"

        /** Mirrors ApiClient's private MAX_RETRIES: 5 retries after the initial attempt. */
        const val MAX_RETRIES = 5
    }
}
