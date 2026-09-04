package com.nutomic.syncthingandroid.http

import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Success callback delivered with the decoded response body.
 * Kotlin `fun interface` so both Kotlin lambdas and Java lambdas work.
 */
fun interface OnSuccessListener {
    fun onSuccess(result: String)
}

/**
 * Error callback mirroring the old ApiRequest.OnErrorListener, but carrying the underlying
 * [IOException] instead of a VolleyError (all former call sites ignored the error object).
 */
fun interface OnErrorListener {
    fun onError(error: IOException)
}

/**
 * Fire-and-forget callback adapter over [ApiClient] for Java callers (phase6a bridge between
 * the deleted Volley-based GetRequest/PostRequest classes and the suspend client).
 *
 * Behaviour parity with the old classes:
 *  - [get]/[post] return immediately; exactly one of onSuccess/onError is invoked later,
 *    on the main thread (Volley delivered responses on the main thread; the default scope
 *    is `Dispatchers.Main.immediate`, so continuations resumed from OkHttp threads dispatch
 *    to the main looper).
 *  - A `null` [OnErrorListener] means "log and swallow", like the old ApiRequest default
 *    error handler (ApiClient already logs each failure).
 *  - Cancellation is deliberately NOT wired up: the old code kept no request references and
 *    let late callbacks fire, and RestApi's public API has no cancellation either. Late
 *    callbacks after RestApi.shutdown() are as harmless as they were with Volley.
 *
 * Each URL gets one long-lived [ApiClient] (cheap instances, but each owns an OkHttpClient,
 * so they are cached per URL instead of being rebuilt per request; the GUI address can change
 * at runtime, which simply allocates a new cache entry).
 *
 * @param scope injectable for tests; pass a test dispatcher to get deterministic callbacks.
 */
class ApiClientBridge(
    private val httpsCertFile: File,
    private val apiKey: String,
    scope: CoroutineScope? = null,
) {

    private val scope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val clients = ConcurrentHashMap<String, ApiClient>()

    /** Mirrors the deleted GetRequest constructor: params/callbacks may be null. */
    @JvmOverloads
    fun get(
        url: URL,
        path: String,
        params: Map<String, String>? = null,
        onSuccess: OnSuccessListener? = null,
        onError: OnErrorListener? = null,
    ) {
        execute("GET", url, path, params, body = null, onSuccess, onError)
    }

    /** Mirrors the deleted PostRequest constructor: params/body/callback may be null. */
    @JvmOverloads
    fun post(
        url: URL,
        path: String,
        params: Map<String, String>? = null,
        body: String? = null,
        onSuccess: OnSuccessListener? = null,
    ) {
        execute("POST", url, path, params, body, onSuccess, onError = null)
    }

    private fun execute(
        method: String,
        url: URL,
        path: String,
        params: Map<String, String>?,
        body: String?,
        onSuccess: OnSuccessListener?,
        onError: OnErrorListener?,
    ) {
        scope.launch {
            try {
                val client = clientFor(url)
                val result = if (method == "POST") {
                    client.post(path, params ?: emptyMap(), body)
                } else {
                    client.get(path, params ?: emptyMap())
                }
                onSuccess?.onSuccess(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                onError?.onError(e)
            }
        }
    }

    private fun clientFor(url: URL): ApiClient =
        clients.getOrPut(url.toString()) { ApiClient(httpsCertFile, url, apiKey) }
}
