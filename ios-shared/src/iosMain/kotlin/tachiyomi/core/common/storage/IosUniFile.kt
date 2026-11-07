package tachiyomi.core.common.storage

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.NSURL

/**
 * Multiplatform iOS replacement for Android's SAF `UniFile`, backed by Okio [FileSystem.SYSTEM]
 * and iOS security-scoped [NSURL] resources.
 */
class IosUniFile(
    val path: Path,
    private val securityScopedUrl: NSURL? = null,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    val name: String
        get() = path.name

    val nameWithoutExtension: String
        get() = path.name.substringBeforeLast('.')

    val extension: String
        get() = path.name.substringAfterLast('.', "")

    val uriString: String
        get() = securityScopedUrl?.absoluteString ?: "file://$path"

    val filePath: String
        get() = path.toString()

    fun exists(): Boolean = withSecurityScope {
        fileSystem.exists(path)
    }

    fun isDirectory(): Boolean = withSecurityScope {
        fileSystem.metadataOrNull(path)?.isDirectory == true
    }

    fun isFile(): Boolean = withSecurityScope {
        fileSystem.metadataOrNull(path)?.isRegularFile == true
    }

    fun length(): Long = withSecurityScope {
        fileSystem.metadataOrNull(path)?.size ?: 0L
    }

    fun lastModified(): Long = withSecurityScope {
        fileSystem.metadataOrNull(path)?.lastModifiedAtMillis ?: 0L
    }

    fun listFiles(): List<IosUniFile> = withSecurityScope {
        if (!isDirectory()) return@withSecurityScope emptyList()
        fileSystem.listOrNull(path)
            ?.map { child -> IosUniFile(child, fileSystem = fileSystem) }
            ?: emptyList()
    }

    fun findFile(displayName: String, ignoreCase: Boolean = false): IosUniFile? = withSecurityScope {
        if (!isDirectory()) return@withSecurityScope null
        listFiles().firstOrNull { it.name.equals(displayName, ignoreCase = ignoreCase) }
    }

    fun createFile(displayName: String): IosUniFile? = withSecurityScope {
        if (!exists()) fileSystem.createDirectories(path)
        val childPath = path / displayName
        if (!fileSystem.exists(childPath)) {
            fileSystem.write(childPath) { }
        }
        IosUniFile(childPath, fileSystem = fileSystem)
    }

    fun createDirectory(displayName: String): IosUniFile? = withSecurityScope {
        val childPath = path / displayName
        if (!fileSystem.exists(childPath)) {
            fileSystem.createDirectories(childPath)
        }
        IosUniFile(childPath, fileSystem = fileSystem)
    }

    fun delete(): Boolean = withSecurityScope {
        return@withSecurityScope try {
            fileSystem.deleteRecursively(path, mustExist = false)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun renameTo(newName: String): Boolean = withSecurityScope {
        val parent = path.parent ?: return@withSecurityScope false
        val target = parent / newName
        return@withSecurityScope try {
            fileSystem.atomicMove(path, target)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun openSource(): BufferedSource = withSecurityScope {
        fileSystem.source(path).buffer()
    }

    fun openSink(append: Boolean = false): BufferedSink = withSecurityScope {
        val parent = path.parent
        if (parent != null && !fileSystem.exists(parent)) {
            fileSystem.createDirectories(parent)
        }
        if (append && fileSystem.exists(path)) {
            fileSystem.appendingSink(path).buffer()
        } else {
            fileSystem.sink(path).buffer()
        }
    }

    fun readBytes(): ByteArray = openSource().use { it.readByteArray() }

    fun writeBytes(bytes: ByteArray) {
        openSink().use { it.write(bytes) }
    }

    private inline fun <T> withSecurityScope(block: () -> T): T {
        val url = securityScopedUrl
        val accessed = url?.startAccessingSecurityScopedResource() ?: false
        return try {
            block()
        } finally {
            if (accessed && url != null) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }

    companion object {
        fun fromPath(pathString: String): IosUniFile = IosUniFile(pathString.toPath())

        fun fromNsUrl(url: NSURL): IosUniFile {
            val localPath = url.path ?: "/tmp"
            return IosUniFile(localPath.toPath(), securityScopedUrl = url)
        }

        fun fromBuffer(name: String, bytes: ByteArray): BufferedSource {
            return Buffer().write(bytes)
        }
    }
}
