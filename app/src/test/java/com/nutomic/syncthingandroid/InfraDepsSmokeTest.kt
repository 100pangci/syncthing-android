package com.nutomic.syncthingandroid

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Phase0 infrastructure smoke tests: proves kotlinx-coroutines and OkHttp are
 * usable from the unit-test classpath (deps added in phase0, used from phase2 on).
 */
class InfraDepsSmokeTest {

    @Test
    fun coroutines_runBlockingExecutes() = runBlocking {
        val result = async { 21 * 2 }.await()
        assertEquals(42, result)
    }

    @Test
    fun okhttp_clientBuildsWithVolleyEquivalentTimeouts() {
        // 5s timeout mirrors the former ApiRequest.java timeout before retry.
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        assertEquals(5000, client.connectTimeoutMillis)
        assertEquals(5000, client.readTimeoutMillis)
        assertTrue(client.retryOnConnectionFailure)
    }

    @Test
    fun okhttp_requestBuilder_parsesLoopbackUrl() {
        val request = Request.Builder().url("http://127.0.0.1:8384/rest/system/status").build()
        assertEquals("http", request.url.scheme)
        assertEquals("127.0.0.1", request.url.host)
        assertEquals(8384, request.url.port)
        assertEquals("/rest/system/status", request.url.encodedPath)
    }
}
