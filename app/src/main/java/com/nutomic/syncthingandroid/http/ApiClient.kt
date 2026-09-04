package com.nutomic.syncthingandroid.http

import android.util.Log
import com.nutomic.syncthingandroid.service.Constants
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * OkHttp-based, coroutine-friendly client for the local Syncthing REST API.
 *
 * This is the phase2 replacement for the Volley-based ApiRequest/GetRequest/PostRequest family
 * (deleted in phase6a). Behaviour is aligned with the old implementation:
 *
 *  - Every request carries the `X-API-Key` header.
 *  - [forceLoopbackHost] pins the connection to the loopback interface regardless of the
 *    configured GUI listen address (see that method's security rationale).
 *  - TLS trusts the local instance's self-signed certificate via [SyncthingTrustManager],
 *    falling back to the OS trust store; hostname verification is skipped. Both relaxations
 *    are only safe because of the loopback pinning above.
 *  - Retry policy mirrors Volley's `DefaultRetryPolicy(5000, 5, DEFAULT_BACKOFF_MULT)` as
 *    implemented by Volley 1.2.1: socket timeouts are retried (6 attempts x 5 s per attempt,
 *    no backoff growth), failures while streaming the response body are retried, everything
 *    else fails fast. One deliberate divergence: Volley also retried HTTP 401/403 responses;
 *    that quirk is not replicated because the API key never changes within a process, so
 *    retrying cannot succeed.
 *  - Response bodies are decoded with the same charset rules as `ApiRequest.connect`:
 *    explicit `charset=` parameter, else UTF-8 for `application/json`, else ISO-8859-1.
 *    (Volley's legacy default, kept for byte-for-byte compatibility.)
 *
 * Cancelling the calling coroutine cancels the underlying OkHttp call, which the old
 * Volley-based code could not do.
 *
 * Instances are cheap but each owns an [OkHttpClient]; callers should keep one instance per
 * URL/api-key pair instead of building one per request. The certificate file is read when the
 * SSL socket factory is created; like the old static Volley queue, a replaced certificate
 * requires a new instance to be picked up.
 */
class ApiClient(
    httpsCertFile: File,
    url: URL,
    private val apiKey: String,
    maxAttempts: Int = 1 + MAX_RETRIES,
    timeoutMs: Long = REQUEST_TIMEOUT_MS,
    /** Set to false for high-frequency polling whose failures are logged (or throttled) by the caller. */
    private val logFailures: Boolean = true,
) {
    private val maxAttempts = maxAttempts.coerceAtLeast(1)

    /** Security-relevant loopback pinning; see [forceLoopbackHost]. */
    private val baseUrl: URL = forceLoopbackHost(url)

    private val okHttpClient: OkHttpClient = buildOkHttpClient(httpsCertFile, timeoutMs)

    /**
     * Performs a GET request and returns the decoded response body.
     *
     * @throws IOException on transport failure or non-2xx response ([ApiHttpException]).
     */
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): String =
        execute("GET", path, params, body = null)

    /**
     * Performs a POST request and returns the decoded response body.
     *
     * @param body raw request payload (typically JSON), or null for a body-less POST.
     * @throws IOException on transport failure or non-2xx response ([ApiHttpException]).
     */
    suspend fun post(path: String, params: Map<String, String> = emptyMap(), body: String? = null): String =
        execute("POST", path, params, body)

    private suspend fun execute(method: String, path: String, params: Map<String, String>, body: String?): String {
        val httpUrl = buildHttpUrl(path, params)
        // Volley sent POST bodies with StringRequest's default content type; Syncthing ignores
        // the header and parses the payload as JSON either way, so declare it correctly here.
        // OkHttp forbids request bodies on GET, so only POST carries one.
        val requestBody = if (method == "POST") {
            body?.toRequestBody(JSON_MEDIA_TYPE) ?: ByteArray(0).toRequestBody(null)
        } else {
            null
        }
        val request = Request.Builder()
            .url(httpUrl)
            .header(HEADER_API_KEY, apiKey)
            .method(method, requestBody)
            .build()

        var lastCause: IOException? = null
        repeat(maxAttempts) { attempt ->
            val call = okHttpClient.newCall(request)
            val response = try {
                call.await()
            } catch (e: IOException) {
                // No response headers arrived. Volley 1.2.1 only retried socket timeouts in this
                // situation; connection failures failed fast (shouldRetryConnectionErrors=false).
                if (e is SocketTimeoutException && attempt < maxAttempts - 1) {
                    lastCause = e
                    return@repeat
                }
                if (logFailures) {
                    Log.w(TAG, "Request to $httpUrl failed: $e")
                }
                throw e
            }
            val bytes = try {
                response.body?.bytes() ?: ByteArray(0)
            } catch (e: IOException) {
                response.close()
                // Volley treats a connection reset while reading the body as a retriable
                // NetworkError; keep that behaviour.
                if (attempt < maxAttempts - 1) {
                    lastCause = e
                    return@repeat
                }
                throw e
            }
            if (!response.isSuccessful) {
                response.close()
                // HTTP errors fail fast (Volley's default: no client/server error retries).
                if (logFailures) {
                    Log.w(TAG, "Request to $httpUrl failed, code=${response.code}")
                }
                throw ApiHttpException(response.code, httpUrl)
            }
            return decodeBody(bytes, response.header("Content-Type"))
        }
        throw IOException("Request to $httpUrl failed after $maxAttempts attempts", lastCause)
    }

    private fun decodeBody(raw: ByteArray, contentTypeHeader: String?): String =
        String(raw, resolveCharset(contentTypeHeader))

    /**
     * Builds the request URL: the path replaces the base URL's path entirely (mirroring the old
     * `Uri.Builder.buildUpon().path()` behaviour, where an empty path means "no path"), and query
     * parameters are appended with URL encoding.
     */
    private fun buildHttpUrl(path: String, params: Map<String, String>): HttpUrl {
        val baseUrl = this.baseUrl.toHttpUrlOrNull()
            ?: throw IllegalStateException("Invalid base URL: ${this.baseUrl}")
        val builder = baseUrl.newBuilder()
        builder.encodedPath(path.ifEmpty { "/" })
        for ((key, value) in params) {
            builder.addQueryParameter(key, value)
        }
        return builder.build()
    }

    private fun buildOkHttpClient(httpsCertFile: File, timeoutMs: Long): OkHttpClient {
        val trustManager = SyncthingTrustManager(httpsCertFile)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return OkHttpClient.Builder()
            .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            // The retry loop above owns attempt counting; OkHttp's internal failover (silent
            // re-routing on connection failures) would blur it.
            .retryOnConnectionFailure(false)
            // Same TLS relaxations as the old Volley NetworkStack; safe only because
            // forceLoopbackHost pins every connection to the loopback interface.
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /** A non-2xx HTTP response, which the old Volley path surfaced as a plain VolleyError. */
    class ApiHttpException(val statusCode: Int, url: HttpUrl) :
        IOException("HTTP $statusCode for $url")

    /**
     * REST endpoint paths of the Syncthing REST API.
     *
     * Formerly defined on the Volley-based GetRequest/PostRequest classes (deleted in phase6a);
     * the names are unchanged so call sites only swap the qualifier.
     */
    companion object {
        // Endpoints formerly on GetRequest.
        const val URI_CONFIG           = "/rest/system/config"
        const val URI_SYSTEM_DISCOVERY = "/rest/system/discovery"
        const val URI_SYSTEM_LOGLEVELS = "/rest/system/loglevels"
        const val URI_VERSION          = "/rest/system/version"
        const val URI_SYSTEM_STATUS    = "/rest/system/status"
        const val URI_CONNECTIONS      = "/rest/system/connections"
        const val URI_PENDING_DEVICES  = "/rest/cluster/pending/devices"
        const val URI_PENDING_FOLDERS  = "/rest/cluster/pending/folders"
        const val URI_DEBUG_SUPPORT    = "/rest/debug/support"
        const val URI_DB_COMPLETION    = "/rest/db/completion"
        const val URI_DB_IGNORES       = "/rest/db/ignores"
        const val URI_DB_STATUS        = "/rest/db/status"
        const val URI_REPORT           = "/rest/svc/report"
        const val URI_EVENTS           = "/rest/events"
        const val URI_EVENTS_DISK      = "/rest/events/disk"
        const val URI_STATS_DEVICE     = "/rest/stats/device"

        // Endpoints formerly on PostRequest.
        // URI_DB_IGNORES is shared with the GET endpoint above (same path, different method).
        const val URI_DB_OVERRIDE      = "/rest/db/override"
        const val URI_DB_REVERT        = "/rest/db/revert"
        const val URI_DB_SCAN          = "/rest/db/scan"
        const val URI_SYSTEM_CONFIG    = "/rest/system/config"
        const val URI_SYSTEM_SHUTDOWN  = "/rest/system/shutdown"

        private const val HEADER_API_KEY = "X-API-Key"
        private const val REQUEST_TIMEOUT_MS = 5000L
        private const val MAX_RETRIES = 5

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Rewrites the host of the given URL to 127.0.0.1, preserving the scheme and port. The port
 * comes from the configured GUI listen address; falls back to the default web GUI port.
 *
 * <p>Forcing loopback is intentional and security-relevant, not merely a convenience:
 * <ul>
 *   <li>The app only ever sets the GUI address to {@code 127.0.0.1} or {@code 0.0.0.0} (the
 *       "listen on all interfaces" setting). {@code 0.0.0.0} always includes loopback, so the
 *       local instance is reachable on {@code 127.0.0.1} in every app-managed config.</li>
 *   <li>It keeps the API key and configuration off any routable interface.</li>
 *   <li>It is the precondition that makes the two TLS relaxations safe: disabling hostname
 *       verification and falling back to the OS trust store / user-installed CAs
 *       ([SyncthingTrustManager]). On loopback there is no network position for a MITM to
 *       occupy, so neither relaxation can be abused.</li>
 * </ul>
 * Do not "simplify" this by connecting to the configured address directly: {@code 0.0.0.0} is
 * not a valid destination (and modern WebView blocks it), and targeting a routable address
 * would break the trust model above.
 */
internal fun forceLoopbackHost(url: URL): URL {
    // Constants.DEFAULT_WEBGUI_TCP_PORT is a boxed Integer on the Java side.
    val port = if (url.port != -1) url.port else Constants.DEFAULT_WEBGUI_TCP_PORT.toInt()
    return try {
        URL(url.protocol, LOOPBACK_HOST, port, url.file)
    } catch (e: MalformedURLException) {
        Log.w(TAG, "forceLoopbackHost: Failed to rewrite host, using original URL", e)
        url
    }
}

/**
 * Resolves the response charset the same way `ApiRequest.connect` did (mirroring Volley's
 * `HttpHeaderParser.parseCharset`, which skips the first Content-Type part and is
 * case-sensitive about the `charset=` prefix):
 * explicit `charset=` parameter wins, else `application/json` means UTF-8, else ISO-8859-1.
 *
 * Unlike the old code, an invalid charset name falls back to ISO-8859-1 instead of throwing.
 */
internal fun resolveCharset(contentType: String?): Charset {
    if (contentType != null) {
        val parts = contentType.split(";")
        for (part in parts.drop(1)) {
            val trimmed = part.trim()
            if (trimmed.startsWith("charset=")) {
                return try {
                    Charset.forName(trimmed.substring("charset=".length))
                } catch (e: Exception) {
                    Log.w(TAG, "resolveCharset: unknown charset in '$contentType', using ISO-8859-1")
                    Charsets.ISO_8859_1
                }
            }
        }
        if (contentType.lowercase(Locale.US).startsWith("application/json")) {
            return Charsets.UTF_8
        }
    }
    return Charsets.ISO_8859_1
}

internal const val LOOPBACK_HOST = "127.0.0.1"

internal val TAG = "ApiClient"

/**
 * Suspends until the call completes, returning the [Response] or throwing the transport
 * [IOException]. Cancelling the coroutine cancels the call; if the response arrives concurrently
 * with cancellation its body is closed to avoid leaking the connection.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { response.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation { cancel() }
}
