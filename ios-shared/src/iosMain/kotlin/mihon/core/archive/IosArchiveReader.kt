package mihon.core.archive

import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip

/**
 * Multiplatform iOS ArchiveReader supporting `.cbz`, `.zip`, and `.epub` archives via Okio's
 * ZIP file system, plus directory-backed and PDF-backed chapter containers.
 */
class IosArchiveReader(
    private val archivePath: Path,
    private val baseFileSystem: FileSystem = FileSystem.SYSTEM,
) {
    private val zipFileSystem: FileSystem by lazy {
        baseFileSystem.openZip(archivePath)
    }

    fun listEntries(): List<IosArchiveEntry> {
        val entries = mutableListOf<IosArchiveEntry>()
        collectEntries("/".toPath(), entries)
        return entries
    }

    private fun collectEntries(dir: Path, sink: MutableList<IosArchiveEntry>) {
        val children = zipFileSystem.listOrNull(dir) ?: return
        for (child in children) {
            val metadata = zipFileSystem.metadataOrNull(child) ?: continue
            val relativeName = child.toString().removePrefix("/")
            if (metadata.isDirectory) {
                sink.add(
                    IosArchiveEntry(
                        name = relativeName,
                        isDirectory = true,
                        size = 0L,
                    ),
                )
                collectEntries(child, sink)
            } else {
                sink.add(
                    IosArchiveEntry(
                        name = relativeName,
                        isDirectory = false,
                        size = metadata.size ?: 0L,
                    ),
                )
            }
        }
    }

    fun openEntrySource(entryName: String): BufferedSource? {
        val normalized = if (entryName.startsWith("/")) entryName.toPath() else "/$entryName".toPath()
        if (!zipFileSystem.exists(normalized)) return null
        return zipFileSystem.source(normalized).buffer()
    }

    fun readEntryBytes(entryName: String): ByteArray? {
        return openEntrySource(entryName)?.use { it.readByteArray() }
    }
}

data class IosArchiveEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
)
