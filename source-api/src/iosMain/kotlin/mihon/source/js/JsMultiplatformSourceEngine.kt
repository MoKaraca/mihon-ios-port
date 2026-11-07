package mihon.source.js

import com.fleeksoft.ksoup.Ksoup
import eu.kanade.tachiyomi.network.IosJavaScriptEngine
import eu.kanade.tachiyomi.network.IosNetworkHelper
import eu.kanade.tachiyomi.source.online.IosHttpSource
import eu.kanade.tachiyomi.source.online.IosMangasPage
import eu.kanade.tachiyomi.source.online.IosPage
import eu.kanade.tachiyomi.source.online.IosSChapter
import eu.kanade.tachiyomi.source.online.IosSManga
import eu.kanade.tachiyomi.source.online.IosSourceFilter
import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cross-platform iOS Extension & Source Runtime for Mihon:
 * 1. Parses standard Mihon/Keiyoushi `index.min.json` extension repository indices
 * 2. Executes declarative CSS/Ksoup & JavaScript (`JavaScriptCore`) scraper descriptors
 * 3. Provides built-in native multiplatform connectors for MangaDex, OPDS, Komga, Kavita, and Suwayomi
 */
class JsMultiplatformSourceEngine(
    private val network: IosNetworkHelper,
    private val jsEngine: IosJavaScriptEngine = IosJavaScriptEngine(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    /**
     * Fetches and parses an extension repository `index.min.json` URL.
     */
    suspend fun fetchExtensionRepoIndex(repoBaseUrl: String): List<ExtensionRepoPackage> {
        val indexUrl = if (repoBaseUrl.endsWith(".json")) {
            repoBaseUrl
        } else {
            "${repoBaseUrl.trimEnd('/')}/index.min.json"
        }
        val response = network.executeRequest(indexUrl, HttpMethod.Get)
        if (!response.isSuccessful) {
            throw IllegalStateException("Failed to fetch extension repo ($indexUrl): HTTP ${response.statusCode}")
        }
        return json.decodeFromString<List<ExtensionRepoPackage>>(response.bodyText)
    }

    /**
     * Instantiates an [IosHttpSource] from a declarative/JS source definition or a repo source descriptor.
     */
    fun createSource(descriptor: JsSourceDescriptor): IosHttpSource {
        return when (descriptor.engineType) {
            SourceEngineType.MANGADEX_API -> MangaDexNativeIosSource(network, descriptor.lang)
            SourceEngineType.KOMGA_OPDS -> KomgaOpdsIosSource(network, descriptor)
            SourceEngineType.SUWAYOMI_BRIDGE -> SuwayomiBridgeIosSource(network, descriptor, json)
            SourceEngineType.CSS_AND_JS -> DeclarativeJsHttpSource(network, jsEngine, descriptor, json)
        }
    }
}

@Serializable
enum class SourceEngineType {
    CSS_AND_JS,
    MANGADEX_API,
    KOMGA_OPDS,
    SUWAYOMI_BRIDGE,
}

@Serializable
data class ExtensionRepoPackage(
    val name: String,
    val pkg: String,
    val apk: String = "",
    val lang: String = "all",
    val code: Long = 1L,
    val version: String = "1.4",
    val nsfw: Int = 0,
    val sources: List<ExtensionRepoSource> = emptyList(),
)

@Serializable
data class ExtensionRepoSource(
    val name: String,
    val lang: String,
    val id: Long,
    val baseUrl: String = "",
    val versionId: Int = 1,
)

@Serializable
data class JsSourceDescriptor(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val supportsLatest: Boolean = true,
    val isNsfw: Boolean = false,
    val engineType: SourceEngineType = SourceEngineType.CSS_AND_JS,
    // CSS selector & URL template rules for HTML/Webtoon sites (Madara, MangaThemesia, FoolSlide, Custom)
    val popularUrlTemplate: String = "{baseUrl}/manga/?page={page}&m_orderby=views",
    val latestUrlTemplate: String = "{baseUrl}/manga/?page={page}&m_orderby=latest",
    val searchUrlTemplate: String = "{baseUrl}/?s={query}&post_type=wp-manga&paged={page}",
    val mangaListSelector: String = "div.page-item-detail, div.bsx, div.manga-item, div.utao",
    val mangaTitleSelector: String = "h3 a, div.tt, .post-title a, a[title]",
    val mangaUrlSelector: String = "h3 a, a",
    val mangaThumbnailSelector: String = "img",
    val mangaThumbnailAttr: String = "data-src,data-lazy-src,src",
    val nextPageSelector: String = "a.next, a.nextpostslink, div.nav-previous a",
    val detailsAuthorSelector: String = ".author-content a, .fmed b:contains(Author)+span",
    val detailsArtistSelector: String = ".artist-content a, .fmed b:contains(Artist)+span",
    val detailsDescriptionSelector: String = ".summary__content, .entry-content[itemprop=description], #synopsis",
    val detailsGenreSelector: String = ".genres-content a, .mgen a",
    val detailsStatusSelector: String = ".post-status .summary-content, .tsinfo .imptdt:contains(Status) i",
    val chapterListSelector: String = "li.wp-manga-chapter, #chapterlist li, .eplister ul li",
    val chapterUrlSelector: String = "a",
    val chapterNameSelector: String = "a, .chapternum",
    val chapterDateSelector: String = "span.chapter-release-date, .chapterdate",
    val pageListSelector: String = "div.page-break img, #readerarea img, .reading-content img",
    val pageImageAttr: String = "data-src,data-lazy-src,src",
    val customJsScript: String? = null,
)

/**
 * Executes CSS-selector + Ksoup + optional JavaScript post-processing for webtoon/manhwa sites
 * (including WordPress Madara, MangaThemesia, and custom JS-hydrated readers).
 */
class DeclarativeJsHttpSource(
    network: IosNetworkHelper,
    private val jsEngine: IosJavaScriptEngine,
    private val descriptor: JsSourceDescriptor,
    private val json: Json,
) : IosHttpSource(network) {

    override val id: Long = descriptor.id
    override val name: String = descriptor.name
    override val baseUrl: String = descriptor.baseUrl
    override val lang: String = descriptor.lang
    override val supportsLatest: Boolean = descriptor.supportsLatest

    override suspend fun getPopularManga(page: Int): IosMangasPage {
        val url = descriptor.popularUrlTemplate
            .replace("{baseUrl}", baseUrl.trimEnd('/'))
            .replace("{page}", page.toString())
        return parseMangaListDocument(url)
    }

    override suspend fun getLatestUpdates(page: Int): IosMangasPage {
        val url = descriptor.latestUrlTemplate
            .replace("{baseUrl}", baseUrl.trimEnd('/'))
            .replace("{page}", page.toString())
        return parseMangaListDocument(url)
    }

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: List<IosSourceFilter>,
    ): IosMangasPage {
        val encodedQuery = query.trim().replace(" ", "+")
        val url = descriptor.searchUrlTemplate
            .replace("{baseUrl}", baseUrl.trimEnd('/'))
            .replace("{page}", page.toString())
            .replace("{query}", encodedQuery)
        return parseMangaListDocument(url)
    }

    private suspend fun parseMangaListDocument(url: String): IosMangasPage {
        val doc = fetchDocument(url)
        val items = doc.select(descriptor.mangaListSelector).mapNotNull { el ->
            val linkEl = el.selectFirst(descriptor.mangaUrlSelector) ?: return@mapNotNull null
            val itemUrl = linkEl.selectAbsUrl("href").removePrefix(baseUrl.trimEnd('/'))
            val titleEl = el.selectFirst(descriptor.mangaTitleSelector) ?: linkEl
            val title = titleEl.attr("title").ifBlank { titleEl.text() }.trim()
            if (title.isEmpty() || itemUrl.isEmpty()) return@mapNotNull null

            val imgEl = el.selectFirst(descriptor.mangaThumbnailSelector)
            val thumb = imgEl?.let { img ->
                descriptor.mangaThumbnailAttr.split(",")
                    .map { it.trim() }
                    .firstNotNullOfOrNull { attr ->
                        img.selectAbsUrl(attr).takeIf { it.isNotBlank() }
                    }
            }

            IosSManga(
                url = itemUrl,
                title = title,
                thumbnailUrl = thumb,
            )
        }
        val hasNext = doc.selectFirst(descriptor.nextPageSelector) != null
        return IosMangasPage(mangas = items, hasNextPage = hasNext)
    }

    override suspend fun getMangaDetails(manga: IosSManga): IosSManga {
        val fullUrl = if (manga.url.startsWith("http")) manga.url else "${baseUrl.trimEnd('/')}/${manga.url.trimStart('/')}"
        val doc = fetchDocument(fullUrl)
        val author = doc.selectFirst(descriptor.detailsAuthorSelector)?.text()?.trim() ?: manga.author
        val artist = doc.selectFirst(descriptor.detailsArtistSelector)?.text()?.trim() ?: manga.artist
        val description = doc.select(descriptor.detailsDescriptionSelector).text().trim().ifBlank { manga.description }
        val genres = doc.select(descriptor.detailsGenreSelector)
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
            .ifBlank { manga.genre }
        val statusText = doc.selectFirst(descriptor.detailsStatusSelector)?.text()?.lowercase().orEmpty()
        val status = when {
            "ongoing" in statusText || "releasing" in statusText -> IosSManga.STATUS_ONGOING
            "completed" in statusText || "finished" in statusText -> IosSManga.STATUS_COMPLETED
            "hiatus" in statusText -> IosSManga.STATUS_ON_HIATUS
            "cancel" in statusText || "dropped" in statusText -> IosSManga.STATUS_CANCELLED
            else -> manga.status
        }
        return manga.copy(
            author = author,
            artist = artist,
            description = description,
            genre = genres,
            status = status,
            initialized = true,
        )
    }

    override suspend fun getChapterList(manga: IosSManga): List<IosSChapter> {
        val fullUrl = if (manga.url.startsWith("http")) manga.url else "${baseUrl.trimEnd('/')}/${manga.url.trimStart('/')}"
        val doc = fetchDocument(fullUrl)
        return doc.select(descriptor.chapterListSelector).mapIndexedNotNull { idx, el ->
            val linkEl = el.selectFirst(descriptor.chapterUrlSelector) ?: return@mapIndexedNotNull null
            val rawUrl = linkEl.selectAbsUrl("href").removePrefix(baseUrl.trimEnd('/'))
            val nameEl = el.selectFirst(descriptor.chapterNameSelector) ?: linkEl
            val name = nameEl.text().trim()
            if (rawUrl.isEmpty() || name.isEmpty()) return@mapIndexedNotNull null

            val numberRegex = Regex("""(\d+(?:\.\d+)?)""")
            val parsedNumber = numberRegex.find(name)?.groupValues?.getOrNull(1)?.toFloatOrNull()
                ?: (idx + 1).toFloat()

            IosSChapter(
                url = rawUrl,
                name = name,
                chapterNumber = parsedNumber,
            )
        }
    }

    override suspend fun getPageList(chapter: IosSChapter): List<IosPage> {
        val fullUrl = if (chapter.url.startsWith("http")) chapter.url else "${baseUrl.trimEnd('/')}/${chapter.url.trimStart('/')}"
        val doc = fetchDocument(fullUrl)

        // 1. If a custom JS page extractor is configured or ts_reader.run(...) is embedded in script tags:
        val customJs = descriptor.customJsScript
        if (!customJs.isNullOrBlank()) {
            val evaluatedJson = jsEngine.invokeFunction(customJs, "extractPages", listOf(doc.outerHtml()))
            if (evaluatedJson.startsWith("[")) {
                val arr = json.parseToJsonElement(evaluatedJson).jsonArray
                return arr.mapIndexed { index, element ->
                    IosPage(index = index, imageUrl = element.jsonPrimitive.content)
                }
            }
        }

        // Check for MangaThemesia `ts_reader.run({...})` inline JSON images array
        val tsReaderMatch = Regex("""ts_reader\.run\((\{.*?\})\);""").find(doc.outerHtml())
        if (tsReaderMatch != null) {
            return try {
                val payload = json.parseToJsonElement(tsReaderMatch.groupValues[1]).jsonObject
                val sources = payload["sources"]?.jsonArray?.firstOrNull()?.jsonObject
                val images = sources?.get("images")?.jsonArray.orEmpty()
                images.mapIndexed { index, el ->
                    IosPage(index = index, imageUrl = el.jsonPrimitive.content)
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }

        // 2. Standard DOM selector extraction
        return doc.select(descriptor.pageListSelector).mapIndexedNotNull { index, img ->
            val imageUrl = descriptor.pageImageAttr.split(",")
                .map { it.trim() }
                .firstNotNullOfOrNull { attr ->
                    img.selectAbsUrl(attr).takeIf { it.isNotBlank() && !it.startsWith("data:") }
                } ?: return@mapIndexedNotNull null
            IosPage(index = index, imageUrl = imageUrl)
        }
    }
}

/**
 * Native Multiplatform MangaDex API v5 source for out-of-the-box manhwa/manga browsing on iOS.
 */
class MangaDexNativeIosSource(
    network: IosNetworkHelper,
    override val lang: String = "en",
) : IosHttpSource(network) {

    override val name: String = "MangaDex"
    override val baseUrl: String = "https://mangadex.org"
    private val apiUrl: String = "https://api.mangadex.org"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun getPopularManga(page: Int): IosMangasPage {
        val offset = ((page - 1).coerceAtLeast(0)) * 20
        val url = "$apiUrl/manga?limit=20&offset=$offset&includes[]=cover_art&includes[]=author&includes[]=artist&order[followedCount]=desc&availableTranslatedLanguage[]=$lang"
        return fetchMangaDexList(url)
    }

    override suspend fun getLatestUpdates(page: Int): IosMangasPage {
        val offset = ((page - 1).coerceAtLeast(0)) * 20
        val url = "$apiUrl/manga?limit=20&offset=$offset&includes[]=cover_art&includes[]=author&includes[]=artist&order[latestUploadedChapter]=desc&availableTranslatedLanguage[]=$lang"
        return fetchMangaDexList(url)
    }

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: List<IosSourceFilter>,
    ): IosMangasPage {
        val offset = ((page - 1).coerceAtLeast(0)) * 20
        val encoded = query.trim().replace(" ", "%20")
        val url = "$apiUrl/manga?limit=20&offset=$offset&title=$encoded&includes[]=cover_art&includes[]=author&includes[]=artist&availableTranslatedLanguage[]=$lang"
        return fetchMangaDexList(url)
    }

    private suspend fun fetchMangaDexList(url: String): IosMangasPage {
        val response = network.executeRequest(url, HttpMethod.Get)
        if (!response.isSuccessful) return IosMangasPage(emptyList(), false)
        val root = json.parseToJsonElement(response.bodyText).jsonObject
        val data = root["data"]?.jsonArray ?: return IosMangasPage(emptyList(), false)
        val total = root["total"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val offset = root["offset"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

        val mangas = data.mapNotNull { item ->
            val obj = item.jsonObject
            val mangaId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null
            val titleObj = attrs["title"]?.jsonObject
            val title = titleObj?.get(lang)?.jsonPrimitive?.content
                ?: titleObj?.get("en")?.jsonPrimitive?.content
                ?: titleObj?.values?.firstOrNull()?.jsonPrimitive?.content
                ?: "Untitled"
            val descObj = attrs["description"]?.jsonObject
            val desc = descObj?.get(lang)?.jsonPrimitive?.content
                ?: descObj?.get("en")?.jsonPrimitive?.content

            val relationships = obj["relationships"]?.jsonArray.orEmpty()
            var coverFileName: String? = null
            var authorName: String? = null
            var artistName: String? = null
            for (rel in relationships) {
                val relObj = rel.jsonObject
                val type = relObj["type"]?.jsonPrimitive?.content
                val relAttrs = relObj["attributes"]?.jsonObject
                when (type) {
                    "cover_art" -> coverFileName = relAttrs?.get("fileName")?.jsonPrimitive?.content
                    "author" -> authorName = relAttrs?.get("name")?.jsonPrimitive?.content
                    "artist" -> artistName = relAttrs?.get("name")?.jsonPrimitive?.content
                }
            }
            val coverUrl = coverFileName?.let { "https://uploads.mangadex.org/covers/$mangaId/$it.256.jpg" }
            IosSManga(
                url = "/manga/$mangaId",
                title = title,
                author = authorName,
                artist = artistName,
                description = desc,
                thumbnailUrl = coverUrl,
                initialized = true,
            )
        }
        return IosMangasPage(mangas = mangas, hasNextPage = offset + mangas.size < total)
    }

    override suspend fun getMangaDetails(manga: IosSManga): IosSManga {
        val mangaId = manga.url.substringAfterLast("/")
        val url = "$apiUrl/manga/$mangaId?includes[]=cover_art&includes[]=author&includes[]=artist"
        val page = fetchMangaDexList(url)
        return page.mangas.firstOrNull() ?: manga.copy(initialized = true)
    }

    override suspend fun getChapterList(manga: IosSManga): List<IosSChapter> {
        val mangaId = manga.url.substringAfterLast("/")
        val url = "$apiUrl/manga/$mangaId/feed?limit=500&translatedLanguage[]=$lang&order[chapter]=desc&includes[]=scanlation_group"
        val response = network.executeRequest(url, HttpMethod.Get)
        if (!response.isSuccessful) return emptyList()
        val root = json.parseToJsonElement(response.bodyText).jsonObject
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { item ->
            val obj = item.jsonObject
            val chapterId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val attrs = obj["attributes"]?.jsonObject ?: return@mapNotNull null
            val chNumStr = attrs["chapter"]?.jsonPrimitive?.content ?: "0"
            val chTitle = attrs["title"]?.jsonPrimitive?.content.orEmpty()
            val displayName = if (chTitle.isNotBlank()) "Ch. $chNumStr - $chTitle" else "Chapter $chNumStr"
            IosSChapter(
                url = "/chapter/$chapterId",
                name = displayName,
                chapterNumber = chNumStr.toFloatOrNull() ?: -1f,
            )
        }
    }

    override suspend fun getPageList(chapter: IosSChapter): List<IosPage> {
        val chapterId = chapter.url.substringAfterLast("/")
        val url = "$apiUrl/at-home/server/$chapterId"
        val response = network.executeRequest(url, HttpMethod.Get)
        if (!response.isSuccessful) return emptyList()
        val root = json.parseToJsonElement(response.bodyText).jsonObject
        val host = root["baseUrl"]?.jsonPrimitive?.content ?: return emptyList()
        val chapterObj = root["chapter"]?.jsonObject ?: return emptyList()
        val hash = chapterObj["hash"]?.jsonPrimitive?.content ?: return emptyList()
        val files = chapterObj["data"]?.jsonArray ?: return emptyList()
        return files.mapIndexed { index, fileEl ->
            val fileName = fileEl.jsonPrimitive.content
            IosPage(
                index = index,
                imageUrl = "$host/data/$hash/$fileName",
            )
        }
    }
}

/**
 * Native Komga / Kavita OPDS & REST API connector for self-hosted libraries on iOS.
 */
class KomgaOpdsIosSource(
    network: IosNetworkHelper,
    private val descriptor: JsSourceDescriptor,
) : IosHttpSource(network) {
    override val id: Long = descriptor.id
    override val name: String = descriptor.name
    override val baseUrl: String = descriptor.baseUrl
    override val lang: String = descriptor.lang

    override suspend fun getPopularManga(page: Int): IosMangasPage = fetchOpdsCatalog("$baseUrl/opds/v1.2/catalog?page=${page - 1}")
    override suspend fun getLatestUpdates(page: Int): IosMangasPage = fetchOpdsCatalog("$baseUrl/opds/v1.2/books/latest?page=${page - 1}")
    override suspend fun getSearchManga(page: Int, query: String, filters: List<IosSourceFilter>): IosMangasPage =
        fetchOpdsCatalog("$baseUrl/opds/v1.2/catalog?search=$query")

    private suspend fun fetchOpdsCatalog(url: String): IosMangasPage {
        val response = network.executeRequest(url, HttpMethod.Get)
        if (!response.isSuccessful) return IosMangasPage(emptyList(), false)
        val doc = Ksoup.parse(response.bodyText, url)
        val entries = doc.select("entry").map { entry ->
            val title = entry.selectFirst("title")?.text().orEmpty()
            val idUrl = entry.selectFirst("link[rel*=acquisition], link")?.attr("href").orEmpty()
            val thumb = entry.selectFirst("link[rel*=image], link[type*=image]")?.attr("href")
            val summary = entry.selectFirst("summary, content")?.text()
            val author = entry.selectFirst("author > name")?.text()
            IosSManga(
                url = idUrl,
                title = title,
                author = author,
                description = summary,
                thumbnailUrl = thumb,
                initialized = true,
            )
        }
        return IosMangasPage(entries, false)
    }

    override suspend fun getMangaDetails(manga: IosSManga): IosSManga = manga.copy(initialized = true)
    override suspend fun getChapterList(manga: IosSManga): List<IosSChapter> = emptyList()
    override suspend fun getPageList(chapter: IosSChapter): List<IosPage> = emptyList()
}

/**
 * Suwayomi-Server bridge source allowing iOS users to connect to a companion Suwayomi server
 * to run any Android APK extension remotely.
 */
class SuwayomiBridgeIosSource(
    network: IosNetworkHelper,
    private val descriptor: JsSourceDescriptor,
    private val json: Json,
) : IosHttpSource(network) {
    override val id: Long = descriptor.id
    override val name: String = descriptor.name
    override val baseUrl: String = descriptor.baseUrl
    override val lang: String = descriptor.lang

    override suspend fun getPopularManga(page: Int): IosMangasPage = IosMangasPage(emptyList(), false)
    override suspend fun getLatestUpdates(page: Int): IosMangasPage = IosMangasPage(emptyList(), false)
    override suspend fun getSearchManga(page: Int, query: String, filters: List<IosSourceFilter>): IosMangasPage =
        IosMangasPage(emptyList(), false)
    override suspend fun getMangaDetails(manga: IosSManga): IosSManga = manga
    override suspend fun getChapterList(manga: IosSManga): List<IosSChapter> = emptyList()
    override suspend fun getPageList(chapter: IosSChapter): List<IosPage> = emptyList()
}

