package tachiyomi.source.local

import eu.kanade.tachiyomi.source.online.IosMangasPage
import eu.kanade.tachiyomi.source.online.IosPage
import eu.kanade.tachiyomi.source.online.IosSChapter
import eu.kanade.tachiyomi.source.online.IosSManga
import kotlinx.serialization.json.Json
import mihon.core.archive.IosArchiveReader
import mihon.core.archive.IosEpubReader
import okio.FileSystem
import okio.Path
import tachiyomi.core.common.storage.IosStorageFolderProvider
import tachiyomi.core.common.storage.IosUniFile
import tachiyomi.core.common.util.system.IosImageUtil

/**
 * Multiplatform iOS implementation of Mihon's [LocalSource] (`ID = 0L`).
 * Reads manga directories, `.cbz`/`.zip` archives, and `.epub` books from the
 * user-accessible `On My iPhone > Mihon > local` folder in the iOS Files app.
 */
class IosLocalSource(
    private val folderProvider: IosStorageFolderProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    val id: Long = ID
    val name: String = "Local source"
    val lang: String = "other"
    val supportsLatest: Boolean = true

    private val baseDirectory: IosUniFile
        get() = IosUniFile(folderProvider.localSourceDir(), fileSystem = fileSystem)

    fun getMangaList(query: String = "", sortByLatest: Boolean = false): IosMangasPage {
        val dirs = baseDirectory.listFiles()
            .filter { it.isDirectory() && !it.name.startsWith('.') }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .let { list ->
                if (sortByLatest) {
                    list.sortedByDescending { it.lastModified() }
                } else {
                    list.sortedBy { it.name.lowercase() }
                }
            }

        val mangas = dirs.map { dir ->
            val coverFile = dir.listFiles().firstOrNull { file ->
                file.isFile() &&
                    file.nameWithoutExtension.equals("cover", ignoreCase = true) &&
                    IosImageUtil.isImage(file.name)
            }
            IosSManga(
                url = dir.name,
                title = dir.name,
                thumbnailUrl = coverFile?.uriString,
                initialized = true,
            )
        }
        return IosMangasPage(mangas = mangas, hasNextPage = false)
    }

    fun getChapterList(manga: IosSManga): List<IosSChapter> {
        val mangaDir = baseDirectory.findFile(manga.url) ?: return emptyList()
        return mangaDir.listFiles()
            .filterNot { it.name.startsWith('.') }
            .filter {
                it.isDirectory() ||
                    it.extension.lowercase() in setOf("cbz", "zip", "epub", "pdf")
            }
            .mapIndexed { index, file ->
                val chName = if (file.isDirectory()) file.name else file.nameWithoutExtension
                val numMatch = Regex("""(\d+(?:\.\d+)?)""").find(chName)
                val chNumber = numMatch?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: (index + 1).toFloat()
                IosSChapter(
                    url = "${manga.url}/${file.name}",
                    name = chName,
                    dateUpload = file.lastModified(),
                    chapterNumber = chNumber,
                )
            }
            .sortedByDescending { it.chapterNumber }
    }

    fun getPageList(chapter: IosSChapter): List<IosPage> {
        val parts = chapter.url.split('/', limit = 2)
        if (parts.size < 2) return emptyList()
        val mangaDir = baseDirectory.findFile(parts[0]) ?: return emptyList()
        val chapterFile = mangaDir.findFile(parts[1]) ?: return emptyList()

        return when {
            chapterFile.isDirectory() -> {
                chapterFile.listFiles()
                    .filter { it.isFile() && IosImageUtil.isImage(it.name) }
                    .sortedBy { it.name.lowercase() }
                    .mapIndexed { index, img ->
                        IosPage(index = index, url = img.filePath, imageUrl = img.uriString)
                    }
            }
            chapterFile.extension.equals("epub", ignoreCase = true) -> {
                val archive = IosArchiveReader(chapterFile.path, fileSystem)
                val epub = IosEpubReader(archive)
                epub.getImagesFromPages().mapIndexed { index, entryPath ->
                    IosPage(
                        index = index,
                        url = "${chapterFile.filePath}!/$entryPath",
                        imageUrl = "archive://${chapterFile.filePath}!/$entryPath",
                    )
                }
            }
            else -> {
                val archive = IosArchiveReader(chapterFile.path, fileSystem)
                archive.listEntries()
                    .filter { !it.isDirectory && IosImageUtil.isImage(it.name) }
                    .sortedBy { it.name.lowercase() }
                    .mapIndexed { index, entry ->
                        IosPage(
                            index = index,
                            url = "${chapterFile.filePath}!/$entry.name",
                            imageUrl = "archive://${chapterFile.filePath}!/${entry.name}",
                        )
                    }
            }
        }
    }

    fun readArchivePageBytes(archivePath: Path, entryName: String): ByteArray? {
        return IosArchiveReader(archivePath, fileSystem).readEntryBytes(entryName)
    }

    companion object {
        const val ID = 0L
    }
}

