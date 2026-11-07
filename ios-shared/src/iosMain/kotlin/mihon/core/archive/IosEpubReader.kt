package mihon.core.archive

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.parser.Parser
import okio.Path.Companion.toPath

/**
 * Multiplatform iOS EPUB reader using [IosArchiveReader] and [Ksoup] XML/HTML parser.
 * Parses `META-INF/container.xml`, the OPF package document (`content.opf`), spine order,
 * cover image, and ComicInfo/DublinCore metadata.
 */
class IosEpubReader(
    private val reader: IosArchiveReader,
) {
    private val pathSeparator = detectPathSeparator()

    fun getImagesFromPages(): List<String> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref) ?: return emptyList()
        val pages = getPagesFromDocument(doc)
        return getImagesFromPages(pages, ref)
    }

    fun getPackageHref(): String {
        val metaBytes = reader.readEntryBytes(resolveZipPath("META-INF", "container.xml"))
        if (metaBytes != null) {
            val metaDoc = Ksoup.parse(html = metaBytes.decodeToString(), parser = Parser.xmlParser(), baseUri = "")
            val path = metaDoc.select("rootfile").firstOrNull()?.attr("full-path")
            if (!path.isNullOrBlank()) {
                return path
            }
        }
        return resolveZipPath("OEBPS", "content.opf")
    }

    fun getPackageDocument(ref: String): Document? {
        val bytes = reader.readEntryBytes(ref) ?: return null
        return Ksoup.parse(html = bytes.decodeToString(), parser = Parser.xmlParser(), baseUri = "")
    }

    private fun getPagesFromDocument(document: Document): List<String> {
        val pages = document.select("manifest > item")
            .filter { node -> "application/xhtml+xml" == node.attr("media-type") }
            .associateBy { it.attr("id") }

        val spine = document.select("spine > itemref").map { it.attr("idref") }
        return spine.mapNotNull { pages[it] }.map { it.attr("href") }
    }

    private fun getImagesFromPages(pages: List<String>, packageHref: String): List<String> {
        val result = mutableListOf<String>()
        val basePath = getParentDirectory(packageHref)
        pages.forEach { page ->
            val entryPath = resolveZipPath(basePath, page)
            val entryBytes = reader.readEntryBytes(entryPath) ?: return@forEach
            val document = Ksoup.parse(html = entryBytes.decodeToString(), baseUri = "")
            val imageBasePath = getParentDirectory(entryPath)

            document.getAllElements().forEach {
                when (it.tagName()) {
                    "img" -> result.add(resolveZipPath(imageBasePath, it.attr("src")))
                    "image" -> result.add(resolveZipPath(imageBasePath, it.attr("xlink:href")))
                }
            }
        }
        return result
    }

    private fun detectPathSeparator(): String {
        return if (reader.readEntryBytes("META-INF\\container.xml") != null) "\\" else "/"
    }

    private fun resolveZipPath(basePath: String, relativePath: String): String {
        if (relativePath.startsWith(pathSeparator)) {
            return relativePath.removePrefix(pathSeparator)
        }
        val normalizedBase = basePath.replace(pathSeparator, "/").trim('/')
        val normalizedRel = relativePath.replace(pathSeparator, "/")
        val combined = if (normalizedBase.isEmpty()) {
            "/$normalizedRel".toPath(normalize = true)
        } else {
            "/$normalizedBase/$normalizedRel".toPath(normalize = true)
        }
        return combined.toString().removePrefix("/").replace("/", pathSeparator)
    }

    private fun getParentDirectory(path: String): String {
        val separatorIndex = path.lastIndexOf(pathSeparator)
        return if (separatorIndex >= 0) {
            path.substring(0, separatorIndex)
        } else {
            ""
        }
    }
}
