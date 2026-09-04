package com.nutomic.syncthingandroid.http

import android.content.Context
import android.util.Log
import com.nutomic.syncthingandroid.service.Constants
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Polls to load the web interface, until it is available.
 *
 * Formerly a Volley-based ApiRequest subclass; rewritten in phase6a as a self-contained
 * OkHttp/coroutines poller so the ApiRequest base class could be deleted. The public API
 * (constructor + [cancelRequestsAndCallback]) is unchanged; the only caller
 * (SyncthingService, still Java) is unaffected. Phase7 replaces this class entirely.
 *
 * Behaviour parity with the old implementation:
 *  - Polls `GET ""` every [WEB_GUI_POLL_INTERVAL] until one attempt succeeds, then delivers
 *    exactly one success callback. Responses are delivered on the main thread (Volley did
 *    the same; the scope runs on `Dispatchers.Main.immediate`).
 *  - Transport failures (syncthing not up yet) retry silently: connection failures and
 *    timeouts log at most once every 10 attempts via [logIncidence], everything else warns.
 *    ApiClient is constructed with logFailures = false so the poller owns the log policy.
 *
 * Divergence from the old implementation: [cancelRequestsAndCallback] also cancels the
 * in-flight request. The old code could not cancel Volley requests and only muted the
 * listener; cancelling is strictly safer (phase3's EventPoller made the same trade-off).
 */
class PollWebGuiAvailableTask @JvmOverloads constructor(
    context: Context,
    url: URL,
    apiKey: String,
    listener: OnSuccessListener,
    scope: CoroutineScope? = null,
) {

    private val mListenerLock = Any()
    private var mListener: OnSuccessListener? = listener
    private var logIncidence = 0

    private val client = ApiClient(
        httpsCertFile = Constants.getHttpsCertFile(context),
        url = url,
        apiKey = apiKey,
        logFailures = false,
    )
    private val pollScope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null

    init {
        Log.i(TAG, "Starting to poll for web gui availability")
        pollJob = pollScope.launch { pollLoop() }
    }

    fun cancelRequestsAndCallback() {
        synchronized(mListenerLock) {
            mListener = null
        }
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun pollLoop() {
        while (coroutineContext.isActive) {
            try {
                val result = client.get("")
                synchronized(mListenerLock) {
                    val listener = mListener
                    if (listener != null) {
                        listener.onSuccess(result)
                    } else {
                        Log.v(TAG, "Cancelled callback and outstanding requests")
                    }
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                logPollError(e)
                delay(WEB_GUI_POLL_INTERVAL)
            }
        }
    }

    /**
     * Old Volley wrapped every transport failure in a VolleyError whose cause was null or
     * a ConnectException; those were logged quietly. OkHttp surfaces the raw exception:
     * ConnectException (not listening yet) and SocketTimeoutException (accepting but slow)
     * are the "expected while waiting" cases.
     */
    private fun logPollError(error: IOException) {
        if (error is ConnectException || error is SocketTimeoutException) {
            logIncidence++
            if (logIncidence == 1 || logIncidence % 10 == 0) {
                Log.v(TAG, "Polling web gui ... ($logIncidence)")
            }
        } else {
            Log.w(TAG, "Unexpected error while polling web gui", error)
        }
    }

    private companion object {
        private const val TAG = "PollWebGuiAvailableTask"

        /**
         * Interval in ms, at which connections to the web gui are performed on first start
         * to find out if it's online.
         */
        private const val WEB_GUI_POLL_INTERVAL = 150L
    }
}
