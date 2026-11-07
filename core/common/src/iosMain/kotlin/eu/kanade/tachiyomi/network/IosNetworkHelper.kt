package eu.kanade.tachiyomi.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL
import platform.WebKit.WKWebsiteDataStore
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Multiplatform iOS network stack replacing Android's OkHttp + WebView CookieManager.
 * - Uses Ktor's native Apple [Darwin] (`NSURLSession`) engine with HTTP/2 & TLS 1.3
 * - Synchronizes cookies with `NSHTTPCookieStorage` & `WKWebsiteDataStore` for Cloudflare bypass
 * - Implements per-host token-bucket rate limiting and configurable User-Agent
 */
class IosNetworkHelper(
    preferenceStore: PreferenceStore,
    isDebugBuild: Boolean = false,
) {
    val networkPreferences = NetworkPreferences(preferenceStore, isDebugBuild)

    val cookieManager = IosCookieJar()
    private val hostRateLimiters = mutableMapOf<String, HostRateLimiter>()
    private val rateLimiterMutex = Mutex()

    /**
     * Callback invoked when a Cloudflare challenge requires interactive WKWebView resolution.
     */
    var onCloudflareChallenge: (suspend (url: String, userAgent: String) -> Boolean)? = null

    val defaultUserAgentProvider: () -> String = {
        networkPreferences.defaultUserAgent.get().ifBlank { DEFAULT_IOS_USER_AGENT }
    }

    val client: HttpClient = HttpClient(Darwin) {
        expectSuccess = false

        install(HttpTimeout) {
            connectTimeoutMillis = 30_000L
            requestTimeoutMillis = 60_000L
            socketTimeoutMillis = 30_000L
        }

        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }

        defaultRequest {
            header("User-Agent", defaultUserAgentProvider())
        }

        engine {
            configureRequest {
                setAllowsCellularAccess(true)
            }
        }
    }

    suspend fun executeRequest(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        headers: Map<String, String> = emptyMap(),
        bodyBytes: ByteArray? = null,
    ): IosHttpResponse {
        val host = extractHost(url)
        if (host.isNotEmpty()) {
            getOrCreateRateLimiter(host).acquire()
        }

        val mergedHeaders = buildMap {
            put("User-Agent", defaultUserAgentProvider())
            val cookieHeader = cookieManager.getCookieHeader(url)
            if (cookieHeader.isNotBlank()) {
                put("Cookie", cookieHeader)
            }
            putAll(headers)
        }

        var response = performRawRequest(url, method, mergedHeaders, bodyBytes)

        // Check for Cloudflare anti-bot challenge (HTTP 403 / 503 with cf-mitigated or server: cloudflare)
        if (isCloudflareChallenge(response)) {
            val solver = onCloudflareChallenge
            if (solver != null) {
                val solved = solver(url, mergedHeaders["User-Agent"] ?: defaultUserAgentProvider())
                if (solved) {
                    val refreshedHeaders = mergedHeaders.toMutableMap()
                    val updatedCookies = cookieManager.getCookieHeader(url)
                    if (updatedCookies.isNotBlank()) {
                        refreshedHeaders["Cookie"] = updatedCookies
                    }
                    response = performRawRequest(url, method, refreshedHeaders, bodyBytes)
                }
            }
        }

        return response
    }

    private suspend fun performRawRequest(
        url: String,
        method: HttpMethod,
        headers: Map<String, String>,
        bodyBytes: ByteArray?,
    ): IosHttpResponse {
        val httpResponse: HttpResponse = client.request(url) {
            this.method = method
            headers.forEach { (k, v) -> header(k, v) }
            if (bodyBytes != null) {
                setBody(bodyBytes)
            }
        }

        val responseHeaders = mutableMapOf<String, List<String>>()
        httpResponse.headers.entries().forEach { (k, v) ->
            responseHeaders[k] = v
        }

        // Persist Set-Cookie headers into NSHTTPCookieStorage
        responseHeaders.entries
            .firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }
            ?.value
            ?.forEach { rawCookie ->
                cookieManager.saveCookieFromHeader(url, rawCookie)
            }

        return IosHttpResponse(
            url = url,
            statusCode = httpResponse.status.value,
            headers = responseHeaders,
            bodyBytes = httpResponse.bodyAsBytes(),
        )
    }

    suspend fun downloadBytesWithProgress(
        url: String,
        headers: Map<String, String> = emptyMap(),
        onProgress: (bytesRead: Long, contentLength: Long) -> Unit = { _, _ -> },
    ): ByteArray {
        val response = executeRequest(url = url, method = HttpMethod.Get, headers = headers)
        if (response.statusCode !in 200..299) {
            throw Exception("HTTP error ${response.statusCode} when fetching $url")
        }
        onProgress(response.bodyBytes.size.toLong(), response.bodyBytes.size.toLong())
        return response.bodyBytes
    }

    private fun isCloudflareChallenge(response: IosHttpResponse): Boolean {
        if (response.statusCode != 403 && response.statusCode != 503) return false
        val server = response.header("Server").orEmpty().lowercase()
        val cfMitigated = response.header("cf-mitigated").orEmpty().lowercase()
        if (cfMitigated.contains("challenge")) return true
        if (server.contains("cloudflare")) {
            val bodySnippet = response.bodyText.take(2048)
            return bodySnippet.contains("Just a moment...") ||
                bodySnippet.contains("cf-browser-verification") ||
                bodySnippet.contains("__cf_chl_opt")
        }
        return false
    }

    private suspend fun getOrCreateRateLimiter(host: String): HostRateLimiter {
        return rateLimiterMutex.withLock {
            hostRateLimiters.getOrPut(host) { HostRateLimiter(permitsPerSecond = 3) }
        }
    }

    private fun extractHost(url: String): String {
        return NSURL.URLWithString(url)?.host ?: ""
    }

    companion object {
        const val DEFAULT_IOS_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
    }
}

data class IosHttpResponse(
    val url: String,
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val bodyBytes: ByteArray,
) {
    val isSuccessful: Boolean
        get() = statusCode in 200..299

    val bodyText: String
        get() = bodyBytes.decodeToString()

    fun header(name: String): String? {
        return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    }
}

/**
 * Synchronizes cookies between Ktor HTTP requests, `NSHTTPCookieStorage`, and `WKWebsiteDataStore`.
 */
class IosCookieJar {
    private val storage: NSHTTPCookieStorage = NSHTTPCookieStorage.sharedHTTPCookieStorage

    @Suppress("UNCHECKED_CAST")
    fun getCookieHeader(url: String): String {
        val nsUrl = NSURL.URLWithString(url) ?: return ""
        val cookies = storage.cookiesForURL(nsUrl) as? List<NSHTTPCookie> ?: emptyList()
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    @Suppress("UNCHECKED_CAST")
    fun saveCookieFromHeader(url: String, setCookieHeader: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        val cookies = NSHTTPCookie.cookiesWithResponseHeaderFields(
            headerFields = mapOf("Set-Cookie" to setCookieHeader),
            forURL = nsUrl,
        ) as? List<NSHTTPCookie> ?: emptyList()
        cookies.forEach { cookie ->
            storage.setCookie(cookie)
        }
    }

    fun syncFromWkWebView(onComplete: () -> Unit = {}) {
        val wkStore = WKWebsiteDataStore.defaultDataStore().httpCookieStore
        wkStore.getAllCookies { cookies ->
            @Suppress("UNCHECKED_CAST")
            val cookieList = cookies as? List<NSHTTPCookie> ?: emptyList()
            cookieList.forEach { cookie ->
                storage.setCookie(cookie)
            }
            onComplete()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun clearAllCookies() {
        val all = storage.cookies as? List<NSHTTPCookie> ?: emptyList()
        all.forEach { storage.deleteCookie(it) }
    }
}

private class HostRateLimiter(
    private val permitsPerSecond: Int,
) {
    private val mutex = Mutex()
    private var lastRequestAtMillis: Long = 0L

    suspend fun acquire() {
        mutex.withLock {
            val minIntervalMs = (1000L / permitsPerSecond.coerceAtLeast(1))
            val now = (platform.Foundation.NSDate().timeIntervalSince1970 * 1000.0).toLong()
            val elapsed = now - lastRequestAtMillis
            if (elapsed < minIntervalMs) {
                delay(minIntervalMs - elapsed)
            }
            lastRequestAtMillis = (platform.Foundation.NSDate().timeIntervalSince1970 * 1000.0).toLong()
        }
    }
}

