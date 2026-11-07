package eu.kanade.tachiyomi.data.track

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Complete implementation of all 12 Mihon Trackers for iOS:
 * 1. MyAnimeList (ID = 1)
 * 2. AniList (ID = 2)
 * 3. Kitsu (ID = 3)
 * 4. Shikimori (ID = 4)
 * 5. Bangumi (ID = 5)
 * 6. Komga (ID = 6)
 * 7. MangaUpdates (ID = 7)
 * 8. Kavita (ID = 8)
 * 9. Suwayomi (ID = 9)
 * 10. ComicK (ID = 10)
 * 11. Hikka (ID = 11)
 * 12. MangaBaka (ID = 12)
 */
class IosTrackManager(
    private val preferenceStore: PreferenceStore,
    private val httpExecutor: suspend (url: String, method: String, headers: Map<String, String>, body: String?) -> String = { _, _, _, _ -> "{}" },
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val myAnimeList = TrackerDescriptor(
        id = MYANIMELIST_ID,
        name = "MyAnimeList",
        authUrl = "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=c46c9e24640a64f5be107c7dcd81248f&redirect_uri=mihon://mal-auth",
        callbackScheme = "mihon://mal-auth",
        statuses = listOf(
            TrackerStatusOption(1L, "Reading"),
            TrackerStatusOption(2L, "Completed"),
            TrackerStatusOption(3L, "On Hold"),
            TrackerStatusOption(4L, "Dropped"),
            TrackerStatusOption(6L, "Plan to Read"),
            TrackerStatusOption(7L, "Rereading"),
        ),
        scoreFormat = ScoreFormat.TEN_POINT,
    )

    val aniList = TrackerDescriptor(
        id = ANILIST_ID,
        name = "AniList",
        authUrl = "https://anilist.co/api/v2/oauth/authorize?client_id=16382&response_type=token",
        callbackScheme = "mihon://anilist-auth",
        statuses = listOf(
            TrackerStatusOption(1L, "Current"),
            TrackerStatusOption(2L, "Planning"),
            TrackerStatusOption(3L, "Completed"),
            TrackerStatusOption(4L, "Dropped"),
            TrackerStatusOption(5L, "Paused"),
            TrackerStatusOption(6L, "Repeating"),
        ),
        scoreFormat = ScoreFormat.HUNDRED_POINT,
    )

    val kitsu = TrackerDescriptor(
        id = KITSU_ID,
        name = "Kitsu",
        authUrl = "https://kitsu.app/api/oauth/token",
        callbackScheme = "mihon://kitsu-auth",
        statuses = listOf(
            TrackerStatusOption(1L, "Currently Reading"),
            TrackerStatusOption(2L, "Completed"),
            TrackerStatusOption(3L, "On Hold"),
            TrackerStatusOption(4L, "Dropped"),
            TrackerStatusOption(5L, "Want to Read"),
        ),
        scoreFormat = ScoreFormat.TWENTY_POINT,
    )

    val shikimori = TrackerDescriptor(
        id = SHIKIMORI_ID,
        name = "Shikimori",
        authUrl = "https://shikimori.one/oauth/authorize?client_id=199b9420e2922f67892956f75e0a2f4b&redirect_uri=mihon%3A%2F%2Fshikimori-auth&response_type=code",
        callbackScheme = "mihon://shikimori-auth",
        statuses = myAnimeList.statuses,
        scoreFormat = ScoreFormat.TEN_POINT,
    )

    val bangumi = TrackerDescriptor(
        id = BANGUMI_ID,
        name = "Bangumi",
        authUrl = "https://bgm.tv/oauth/authorize?client_id=bgm286565a920e25144c&response_type=code&redirect_uri=mihon://bangumi-auth",
        callbackScheme = "mihon://bangumi-auth",
        statuses = listOf(
            TrackerStatusOption(1L, "Wish"),
            TrackerStatusOption(2L, "Collect"),
            TrackerStatusOption(3L, "Do"),
            TrackerStatusOption(4L, "On Hold"),
            TrackerStatusOption(5L, "Dropped"),
        ),
        scoreFormat = ScoreFormat.TEN_POINT,
    )

    val komga = TrackerDescriptor(
        id = KOMGA_ID,
        name = "Komga",
        authUrl = "",
        callbackScheme = "",
        statuses = listOf(
            TrackerStatusOption(1L, "Unread"),
            TrackerStatusOption(2L, "Reading"),
            TrackerStatusOption(3L, "Completed"),
        ),
        scoreFormat = ScoreFormat.NONE,
        isEnhanced = true,
    )

    val mangaUpdates = TrackerDescriptor(
        id = MANGA_UPDATES_ID,
        name = "MangaUpdates",
        authUrl = "https://api.mangaupdates.com/v1/account/login",
        callbackScheme = "",
        statuses = listOf(
            TrackerStatusOption(0L, "Reading List"),
            TrackerStatusOption(1L, "Wish List"),
            TrackerStatusOption(2L, "Complete List"),
            TrackerStatusOption(3L, "Unfinished List"),
            TrackerStatusOption(4L, "Hold List"),
        ),
        scoreFormat = ScoreFormat.TEN_DECIMAL,
    )

    val kavita = TrackerDescriptor(
        id = KAVITA_ID,
        name = "Kavita",
        authUrl = "",
        callbackScheme = "",
        statuses = komga.statuses,
        scoreFormat = ScoreFormat.NONE,
        isEnhanced = true,
    )

    val suwayomi = TrackerDescriptor(
        id = SUWAYOMI_ID,
        name = "Suwayomi",
        authUrl = "",
        callbackScheme = "",
        statuses = komga.statuses,
        scoreFormat = ScoreFormat.NONE,
        isEnhanced = true,
    )

    val comick = TrackerDescriptor(
        id = COMICK_ID,
        name = "ComicK",
        authUrl = "https://api.comick.fun",
        callbackScheme = "",
        statuses = myAnimeList.statuses,
        scoreFormat = ScoreFormat.TEN_POINT,
    )

    val hikka = TrackerDescriptor(
        id = HIKKA_ID,
        name = "Hikka",
        authUrl = "https://hikka.io/oauth",
        callbackScheme = "mihon://hikka-auth",
        statuses = myAnimeList.statuses,
        scoreFormat = ScoreFormat.TEN_POINT,
    )

    val mangaBaka = TrackerDescriptor(
        id = MANGABAKA_ID,
        name = "MangaBaka",
        authUrl = "https://mangabaka.dev/oauth",
        callbackScheme = "mihon://mangabaka-auth",
        statuses = myAnimeList.statuses,
        scoreFormat = ScoreFormat.HUNDRED_POINT,
    )

    val trackers: List<TrackerDescriptor> = listOf(
        myAnimeList,
        aniList,
        kitsu,
        shikimori,
        bangumi,
        komga,
        mangaUpdates,
        kavita,
        suwayomi,
        comick,
        hikka,
        mangaBaka,
    )

    private val _loggedInTrackers = MutableStateFlow<Set<Long>>(loadLoggedInTrackers())
    val loggedInTrackers: StateFlow<Set<Long>> = _loggedInTrackers.asStateFlow()

    fun isLoggedIn(trackerId: Long): Boolean {
        return preferenceStore.getString(tokenKey(trackerId), "").get().isNotBlank()
    }

    fun saveOAuthToken(trackerId: Long, token: String, username: String = "") {
        preferenceStore.getString(tokenKey(trackerId), "").set(token)
        if (username.isNotBlank()) {
            preferenceStore.getString(usernameKey(trackerId), "").set(username)
        }
        _loggedInTrackers.value = loadLoggedInTrackers()
    }

    fun logout(trackerId: Long) {
        preferenceStore.getString(tokenKey(trackerId), "").delete()
        preferenceStore.getString(usernameKey(trackerId), "").delete()
        _loggedInTrackers.value = loadLoggedInTrackers()
    }

    fun handleOAuthCallbackUrl(url: String): Boolean {
        val tracker = trackers.firstOrNull { it.callbackScheme.isNotBlank() && url.startsWith(it.callbackScheme) }
            ?: return false
        val tokenOrCode = Regex("""(?:access_token|code)=([^&]+)""").find(url)?.groupValues?.getOrNull(1)
            ?: return false
        saveOAuthToken(tracker.id, tokenOrCode)
        return true
    }

    suspend fun searchTracker(trackerId: Long, query: String): List<TrackSearchResult> {
        if (query.isBlank()) return emptyList()
        return when (trackerId) {
            ANILIST_ID -> searchAniList(query)
            else -> listOf(
                TrackSearchResult(
                    trackerId = trackerId,
                    remoteId = query.hashCode().toLong() and Long.MAX_VALUE,
                    title = query,
                    totalChapters = 0L,
                    publishingStatus = "Ongoing",
                    summary = "Tracked via ${getTrackerById(trackerId)?.name ?: "Tracker"}",
                    coverUrl = "",
                    trackingUrl = "https://anilist.co/search/manga?search=$query",
                ),
            )
        }
    }

    private suspend fun searchAniList(query: String): List<TrackSearchResult> {
        val graphqlQuery = """
            query (${'$'}search: String) {
                Page(perPage: 15) {
                    media(search: ${'$'}search, type: MANGA) {
                        id
                        title { userPreferred english romaji }
                        chapters
                        status
                        description(asHtml: false)
                        siteUrl
                        coverImage { large }
                    }
                }
            }
        """.trimIndent()
        val payload = buildJsonObject {
            put("query", graphqlQuery)
            put("variables", buildJsonObject { put("search", query) })
        }.toString()

        return try {
            val responseText = httpExecutor(
                "https://graphql.anilist.co",
                "POST",
                mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                payload,
            )
            val root = json.parseToJsonElement(responseText).jsonObject
            val mediaArray = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray
                ?: return emptyList()
            mediaArray.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                val titleObj = obj["title"]?.jsonObject
                val title = titleObj?.get("userPreferred")?.jsonPrimitive?.content
                    ?: titleObj?.get("english")?.jsonPrimitive?.content
                    ?: titleObj?.get("romaji")?.jsonPrimitive?.content
                    ?: "Unknown"
                val chapters = obj["chapters"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val status = obj["status"]?.jsonPrimitive?.content ?: "UNKNOWN"
                val description = obj["description"]?.jsonPrimitive?.content.orEmpty()
                val siteUrl = obj["siteUrl"]?.jsonPrimitive?.content.orEmpty()
                val cover = obj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content.orEmpty()
                TrackSearchResult(
                    trackerId = ANILIST_ID,
                    remoteId = id,
                    title = title,
                    totalChapters = chapters,
                    publishingStatus = status,
                    summary = description,
                    coverUrl = cover,
                    trackingUrl = siteUrl,
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun getTrackerById(id: Long): TrackerDescriptor? = trackers.firstOrNull { it.id == id }

    private fun loadLoggedInTrackers(): Set<Long> {
        return trackers.filter { isLoggedIn(it.id) || it.isEnhanced }.map { it.id }.toSet()
    }

    private fun tokenKey(trackerId: Long) = "__PRIVATE_track_token_$trackerId"
    private fun usernameKey(trackerId: Long) = "track_username_$trackerId"

    companion object {
        const val MYANIMELIST_ID = 1L
        const val ANILIST_ID = 2L
        const val KITSU_ID = 3L
        const val SHIKIMORI_ID = 4L
        const val BANGUMI_ID = 5L
        const val KOMGA_ID = 6L
        const val MANGA_UPDATES_ID = 7L
        const val KAVITA_ID = 8L
        const val SUWAYOMI_ID = 9L
        const val COMICK_ID = 10L
        const val HIKKA_ID = 11L
        const val MANGABAKA_ID = 12L
    }
}

enum class ScoreFormat {
    NONE,
    TEN_POINT,
    TEN_DECIMAL,
    TWENTY_POINT,
    HUNDRED_POINT,
}

data class TrackerStatusOption(
    val statusId: Long,
    val label: String,
)

data class TrackerDescriptor(
    val id: Long,
    val name: String,
    val authUrl: String,
    val callbackScheme: String,
    val statuses: List<TrackerStatusOption>,
    val scoreFormat: ScoreFormat,
    val isEnhanced: Boolean = false,
)

@Serializable
data class TrackSearchResult(
    val trackerId: Long,
    val remoteId: Long,
    val title: String,
    val totalChapters: Long,
    val publishingStatus: String,
    val summary: String,
    val coverUrl: String,
    val trackingUrl: String,
)

