package eu.kanade.tachiyomi.source.online

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import eu.kanade.tachiyomi.network.IosHttpResponse
import eu.kanade.tachiyomi.network.IosNetworkHelper
import io.ktor.http.HttpMethod
import okio.Buffer

/**
 * Multiplatform iOS equivalent of `HttpSource` and `ParsedHttpSource`, using [IosNetworkHelper]
 * and [Ksoup] while preserving exact 64-bit MD5 source ID compatibility with Android Mihon.
 */
abstract class IosHttpSource(
    protected val network: IosNetworkHelper,
) {
    abstract val name: String
    abstract val baseUrl: String
    abstract val lang: String
    open val supportsLatest: Boolean = true
    open val versionId: Int = 1

    open val id: Long by lazy { generateSourceId(name, lang, versionId) }

    open fun getHomeUrl(): String = baseUrl

    open fun headers(): Map<String, String> = mapOf(
        "User-Agent" to network.defaultUserAgentProvider(),
        "Referer" to "$baseUrl/",
    )

    abstract suspend fun getPopularManga(page: Int): IosMangasPage
    abstract suspend fun getLatestUpdates(page: Int): IosMangasPage
    abstract suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: List<IosSourceFilter>,
    ): IosMangasPage

    abstract suspend fun getMangaDetails(manga: IosSManga): IosSManga
    abstract suspend fun getChapterList(manga: IosSManga): List<IosSChapter>
    abstract suspend fun getPageList(chapter: IosSChapter): List<IosPage>

    open suspend fun downloadPageImage(
        page: IosPage,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): ByteArray {
        val imageUrl = page.imageUrl ?: error("Page ${page.index} has no imageUrl")
        return network.downloadBytesWithProgress(
            url = imageUrl,
            headers = headers(),
            onProgress = onProgress,
        )
    }

    protected suspend fun fetchDocument(
        url: String,
        method: HttpMethod = HttpMethod.Get,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Document {
        val response: IosHttpResponse = network.executeRequest(
            url = url,
            method = method,
            headers = headers() + extraHeaders,
        )
        return Ksoup.parse(response.bodyText, url)
    }

    protected fun Element.selectAbsUrl(attr: String): String {
        val raw = attr(attr).trim()
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "${baseUrl.trimEnd('/')}$raw"
            raw.isEmpty() -> ""
            else -> "${baseUrl.trimEnd('/')}/$raw"
        }
    }

    companion object {
        /**
         * Generates the exact 64-bit source ID used by Mihon/Tachiyomi on Android:
         * first 8 bytes of `MD5("${name.lowercase()}/$lang/$versionId")` with sign bit cleared.
         */
        fun generateSourceId(name: String, lang: String, versionId: Int = 1): Long {
            val key = "${name.lowercase()}/$lang/$versionId"
            val buffer = Buffer().writeUtf8(key)
            val md5Bytes = buffer.md5().toByteArray()
            var result = 0L
            for (i in 0..7) {
                result = result or ((md5Bytes[i].toLong() and 0xffL) shl (8 * (7 - i)))
            }
            return result and Long.MAX_VALUE
        }
    }
}

data class IosSManga(
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val status: Int = STATUS_UNKNOWN,
    val thumbnailUrl: String? = null,
    val updateStrategy: Int = 0,
    val initialized: Boolean = false,
) {
    companion object {
        const val STATUS_UNKNOWN = 0
        const val STATUS_ONGOING = 1
        const val STATUS_COMPLETED = 2
        const val STATUS_LICENSED = 3
        const val STATUS_PUBLISHING_FINISHED = 4
        const val STATUS_CANCELLED = 5
        const val STATUS_ON_HIATUS = 6
    }
}

data class IosSChapter(
    val url: String,
    val name: String,
    val dateUpload: Long = 0L,
    val chapterNumber: Float = -1f,
    val scanlator: String? = null,
)

data class IosPage(
    val index: Int,
    val url: String = "",
    val imageUrl: String? = null,
)

data class IosMangasPage(
    val mangas: List<IosSManga>,
    val hasNextPage: Boolean,
)

sealed class IosSourceFilter(open val name: String) {
    data class Header(override val name: String) : IosSourceFilter(name)
    data class Text(override val name: String, val value: String = "") : IosSourceFilter(name)
    data class CheckBox(override val name: String, val state: Boolean = false) : IosSourceFilter(name)
    data class TriState(override val name: String, val state: Int = STATE_IGNORE) : IosSourceFilter(name) {
        companion object {
            const val STATE_IGNORE = 0
            const val STATE_INCLUDE = 1
            const val STATE_EXCLUDE = 2
        }
    }
    data class Select(
        override val name: String,
        val values: List<String>,
        val selectedIndex: Int = 0,
    ) : IosSourceFilter(name)
    data class Sort(
        override val name: String,
        val values: List<String>,
        val selectedIndex: Int = 0,
        val ascending: Boolean = false,
    ) : IosSourceFilter(name)
}
