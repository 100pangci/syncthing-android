package com.nutomic.syncthingandroid.http

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [PollWebGuiAvailableTask] after its phase6a rewrite (Volley/ApiRequest base
 * class replaced by the suspend [ApiClient] with a coroutine poll loop). The public API and
 * the poll-until-available behaviour must stay the same.
 *
 * The poll scope runs on an [UnconfinedTestDispatcher] so callbacks are awaited with latches
 * instead of depending on the Robolectric main looper; the production scope (Main.immediate)
 * is exercised on-device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PollWebGuiAvailableTaskTest {

    private val testScheduler = TestCoroutineScheduler()
    private val pollScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    private lateinit var server: MockWebServer
    private lateinit var certFile: File
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Plain-HTTP tests never trigger a TLS handshake, so the trust manager never reads it.
        certFile = File.createTempFile("https-cert", ".pem")
        certFile.deleteOnExit()
    }

    @After
    fun tearDown() {
        pollScope.cancel()
        server.shutdown()
        certFile.delete()
    }

    @Test
    fun pollsUntilWebGuiAvailable_thenDeliversSingleSuccess() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("not yet"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("still not"))
        server.enqueue(MockResponse().setBody("up"))

        val results = java.util.Collections.synchronizedList(ArrayList<String>())
        val task = PollWebGuiAvailableTask(
                context, server.url("/").toUrl(), "test-api-key",
                { result -> results.add(result) },
                pollScope,
        )

        // The 150 ms poll delays run on the injected scheduler's virtual time; advance it
        // while the real-time OkHttp calls complete in the background.
        val deadline = System.currentTimeMillis() + 10_000
        while (results.isEmpty() && System.currentTimeMillis() < deadline) {
            testScheduler.advanceTimeBy(150)
            testScheduler.runCurrent()
            Thread.sleep(10)
        }
        Thread.sleep(100) // give any stray duplicate callback a chance to (wrongly) arrive

        assertEquals(listOf("up"), results)
        assertEquals(3, server.requestCount)
        task.cancelRequestsAndCallback()
    }

    @Test
    fun cancelRequestsAndCallback_stopsCallbacksAfterCancel() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("not yet"))

        val results = java.util.Collections.synchronizedList(ArrayList<String>())
        val task = PollWebGuiAvailableTask(
                context, server.url("/").toUrl(), "test-api-key",
                { result -> results.add(result) },
                pollScope,
        )

        // Wait for the first (failed) attempt to be recorded, then cancel before success.
        val deadline = System.currentTimeMillis() + 10_000
        while (server.requestCount < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        task.cancelRequestsAndCallback()

        server.enqueue(MockResponse().setBody("late success"))
        Thread.sleep(200)

        assertTrue("cancelled task must not deliver results", results.isEmpty())
        assertEquals(1, server.requestCount)
    }
}
