package tachiyomi.core.common.storage

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Provides the root storage directories on iOS:
 * - User-accessible Documents folder ("On My iPhone > Mihon") for Downloads, Backups, and Local manga
 * - Shared App Group container ("group.app.mihon") for SQLite database and WidgetKit sharing
 */
class IosStorageFolderProvider {

    fun directoryPath(): Path {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        )
        val documentsPath = (paths.firstOrNull() as? String) ?: "/tmp"
        val mihonRoot = documentsPath.toPath()
        if (!FileSystem.SYSTEM.exists(mihonRoot)) {
            FileSystem.SYSTEM.createDirectories(mihonRoot)
        }
        return mihonRoot
    }

    fun path(): String = directoryPath().toString()

    fun appGroupDirectoryPath(groupId: String = "group.app.mihon"): Path {
        val containerUrl: NSURL? = NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(groupId)
        val containerPath = containerUrl?.path
        return if (containerPath != null) {
            val resolved = containerPath.toPath()
            if (!FileSystem.SYSTEM.exists(resolved)) {
                FileSystem.SYSTEM.createDirectories(resolved)
            }
            resolved
        } else {
            directoryPath()
        }
    }

    fun downloadsDir(): Path = ensureSubdirectory("downloads")
    fun localSourceDir(): Path = ensureSubdirectory("local")
    fun backupsDir(): Path = ensureSubdirectory("autobackup")
    fun coversCacheDir(): Path = ensureSubdirectory("covers")

    private fun ensureSubdirectory(name: String): Path {
        val sub = directoryPath() / name
        if (!FileSystem.SYSTEM.exists(sub)) {
            FileSystem.SYSTEM.createDirectories(sub)
        }
        return sub
    }
}

