package tachiyomi.domain.storage.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import okio.FileSystem
import tachiyomi.core.common.storage.IosStorageFolderProvider
import tachiyomi.core.common.storage.IosUniFile

/**
 * iOS implementation of [StorageManager] managing the `autobackup`, `downloads`, and `local`
 * directories inside `On My iPhone > Mihon`.
 */
class IosStorageManager(
    scope: CoroutineScope,
    private val folderProvider: IosStorageFolderProvider,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        ensureDirectories()
    }

    fun ensureDirectories() {
        folderProvider.backupsDir()
        folderProvider.localSourceDir()
        folderProvider.downloadsDir()
    }

    fun getAutomaticBackupsDirectory(): IosUniFile {
        return IosUniFile(folderProvider.backupsDir(), fileSystem = fileSystem)
    }

    fun getDownloadsDirectory(): IosUniFile {
        return IosUniFile(folderProvider.downloadsDir(), fileSystem = fileSystem)
    }

    fun getLocalSourceDirectory(): IosUniFile {
        return IosUniFile(folderProvider.localSourceDir(), fileSystem = fileSystem)
    }
}
