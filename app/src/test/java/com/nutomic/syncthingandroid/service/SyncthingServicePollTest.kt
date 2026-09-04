package com.nutomic.syncthingandroid.service

import java.net.ConnectException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the web gui availability poll loop inlined into SyncthingService (phase7), which
 * replaces the deleted PollWebGuiAvailableTask. The loop's delay() runs on the runTest
 * scheduler's virtual time, so no real waiting happens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class SyncthingServicePollTest {

    @Test
    fun retriesUntilAvailable_andDeliversCallbackExactlyOnce() = runTest {
        val attemptTimes = mutableListOf<Long>()
        var attempts = 0
        val results = mutableListOf<String>()

        pollWebGuiUntilAvailable(
            fetch = {
                attemptTimes.add(testScheduler.currentTime)
                attempts++
                if (attempts < 4) throw ConnectException("not up yet")
                "up"
            },
            webGuiUrl = "https://127.0.0.1:8384",
            onAvailable = { results.add("up") },
        )

        assertEquals(4, attempts)
        // Exactly one callback, even though the loop retried three times before.
        assertEquals(listOf("up"), results)
        // First attempt is immediate; the retries are spaced by the 150 ms poll interval.
        assertEquals(0L, attemptTimes[0])
        val deltas = attemptTimes.zipWithNext { a, b -> b - a }
        assertEquals(listOf(150L, 150L, 150L), deltas)
    }

    @Test
    fun unexpectedIoErrors_stillRetry() = runTest {
        var attempts = 0
        pollWebGuiUntilAvailable(
            fetch = {
                attempts++
                if (attempts < 2) throw java.io.IOException("weird transport error")
                "up"
            },
            webGuiUrl = "https://127.0.0.1:8384",
            onAvailable = { },
        )
        assertEquals(2, attempts)
    }

    @Test
    fun cancellation_stopsFurtherPolling() = runTest {
        var attempts = 0
        val job = launch {
            pollWebGuiUntilAvailable(
                fetch = { attempts++; throw ConnectException("no") },
                webGuiUrl = "https://127.0.0.1:8384",
                onAvailable = { },
            )
        }
        // Virtual time: attempts land at t=0,150,...,900 -> 7 attempts by t=1000.
        advanceTimeBy(1000)
        runCurrent()
        val attemptsAtCancel = attempts
        assertEquals(7, attemptsAtCancel)

        job.cancel()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("Polling must stop after cancellation", attemptsAtCancel, attempts)
    }
}
