package com.ykatchou.ylauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ykatchou.ylauncher.data.model.Folder
import com.ykatchou.ylauncher.data.model.FolderApp
import kotlinx.coroutines.flow.Flow

/**
 * `folder_apps.folderId` is a foreign key onto `folders(id)`. Unlike a favorite's panel there
 * is no sane substitute for a vanished folder, so [insertFolderApp] drops the row instead of
 * throwing SQLITE_CONSTRAINT_FOREIGNKEY — the folder (and its cascade-deleted contents) is
 * gone, and the app the user was adding to it simply has nowhere to go.
 */
@Dao
abstract class FolderDao {
    @Query("SELECT * FROM folders ORDER BY position ASC")
    abstract fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    abstract suspend fun getFolderById(folderId: Long): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFolder(folder: Folder): Long

    @Update
    abstract suspend fun updateFolder(folder: Folder)

    @Query("DELETE FROM folders WHERE id = :folderId")
    abstract suspend fun deleteFolder(folderId: Long)

    @Query("SELECT * FROM folder_apps WHERE folderId = :folderId ORDER BY position ASC")
    abstract fun getAppsInFolder(folderId: Long): Flow<List<FolderApp>>

    @Query("SELECT * FROM folder_apps WHERE folderId = :folderId ORDER BY position ASC")
    abstract suspend fun getAppsInFolderOnce(folderId: Long): List<FolderApp>

    /** No-ops when the target folder no longer exists. Returns true when the row was written. */
    @Transaction
    open suspend fun insertFolderApp(folderApp: FolderApp): Boolean {
        if (existingFolderId(folderApp.folderId) == null) return false
        insertFolderAppInternal(folderApp)
        return true
    }

    @Query("DELETE FROM folder_apps WHERE folderId = :folderId AND packageName = :packageName")
    abstract suspend fun removeFolderApp(folderId: Long, packageName: String)

    @Query("DELETE FROM folder_apps WHERE folderId = :folderId")
    abstract suspend fun deleteAllAppsInFolder(folderId: Long)

    @Query("DELETE FROM folder_apps WHERE packageName = :packageName")
    abstract suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM folders")
    abstract suspend fun deleteAllFolders()

    @Query("DELETE FROM folder_apps")
    abstract suspend fun deleteAllFolderApps()

    // --- internals: never call these directly, they can violate the folderId foreign key ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertFolderAppInternal(folderApp: FolderApp)

    @Query("SELECT id FROM folders WHERE id = :folderId LIMIT 1")
    protected abstract suspend fun existingFolderId(folderId: Long): Long?
}
