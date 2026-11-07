package tachiyomi.data

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import tachiyomi.core.common.storage.IosStorageFolderProvider

/**
 * iOS SQLDelight driver and [Database] factory using [NativeSqliteDriver].
 * Places `tachiyomi.db` inside the shared App Group container (`group.app.mihon`)
 * so both the main iOS app and the `UpdatesWidget` WidgetKit extension share the database.
 */
object IosDatabaseBindings {

    fun createSqlDriver(
        folderProvider: IosStorageFolderProvider,
        dbName: String = "tachiyomi.db",
    ): SqlDriver {
        val basePath = folderProvider.appGroupDirectoryPath().toString()
        val syncSchema = Database.Schema.synchronous()

        return NativeSqliteDriver(
            schema = syncSchema,
            name = dbName,
            onConfiguration = { config: DatabaseConfiguration ->
                config.copy(
                    extendedConfig = DatabaseConfiguration.Extended(
                        foreignKeyConstraints = true,
                        basePath = basePath,
                        busyTimeout = 3000,
                    ),
                )
            },
        )
    }

    fun createDatabase(driver: SqlDriver): Database {
        return Database(
            driver = driver,
            historyAdapter = History.Adapter(
                read_atAdapter = InstantColumnAdapter,
            ),
            mangaAdapter = Manga.Adapter(
                remote_genreAdapter = StringListColumnAdapter,
                remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_favorite_atAdapter = InstantColumnAdapter,
                state_chapter_last_updateAdapter = InstantColumnAdapter,
                state_chapter_next_updateAdapter = InstantColumnAdapter,
                state_cover_last_modifiedAdapter = InstantColumnAdapter,
            ),
            chapterAdapter = Chapter.Adapter(
                remote_date_uploadAdapter = InstantColumnAdapter,
                remote_memoAdapter = MemoColumnAdapter,
                user_last_page_readAdapter = IntColumnAdapter,
                state_date_fetchAdapter = InstantColumnAdapter,
            ),
            manga_trackAdapter = Manga_track.Adapter(
                start_dateAdapter = InstantColumnAdapter,
                finish_dateAdapter = InstantColumnAdapter,
            ),
        )
    }
}

