package com.ykatchou.ylauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ykatchou.ylauncher.data.model.FavoriteApp
import com.ykatchou.ylauncher.data.model.Panel
import kotlinx.coroutines.flow.Flow

/**
 * `favorite_apps.panelId` is a real foreign key onto `panels(id)`, so any write carrying a
 * panel id that no longer exists (a stale DataStore `active_panel`, the hardcoded `0`
 * default after a config restore renumbered the panels, a panel deleted on another
 * coroutine between read and write) used to blow up with SQLITE_CONSTRAINT_FOREIGNKEY.
 * Every insert/update therefore goes through [resolvePanelId] inside one transaction, which
 * clamps the id to a panel that provably exists — falling back to the first panel and, if
 * the table is somehow empty, to a freshly created default panel. Callers can hand us any
 * panel id; the favorite lands somewhere sane instead of crashing the launcher.
 */
@Dao
abstract class FavoriteDao {
    @Query("SELECT * FROM favorite_apps ORDER BY position ASC")
    abstract fun getAllFavorites(): Flow<List<FavoriteApp>>

    @Query("SELECT * FROM favorite_apps ORDER BY position ASC")
    abstract suspend fun getAllFavoritesOnce(): List<FavoriteApp>

    @Transaction
    open suspend fun insertFavorite(favorite: FavoriteApp) {
        insertFavoriteInternal(favorite.copy(panelId = resolvePanelId(favorite.panelId)))
    }

    @Transaction
    open suspend fun insertAll(favorites: List<FavoriteApp>) {
        if (favorites.isEmpty()) return
        insertAllInternal(withResolvedPanels(favorites))
    }

    @Transaction
    open suspend fun updateFavorite(favorite: FavoriteApp) {
        updateFavoriteInternal(favorite.copy(panelId = resolvePanelId(favorite.panelId)))
    }

    @Query("DELETE FROM favorite_apps WHERE position = :position")
    abstract suspend fun deleteFavoriteAt(position: Int)

    @Query("DELETE FROM favorite_apps")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM favorite_apps WHERE panelId = :panelId")
    abstract suspend fun deleteByPanel(panelId: Long)

    @Query("DELETE FROM favorite_apps WHERE packageName = :packageName")
    abstract suspend fun deleteByPackage(packageName: String)

    @Query("SELECT COUNT(*) FROM favorite_apps")
    abstract suspend fun count(): Int

    // --- internals: never call these directly, they can violate the panelId foreign key ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertFavoriteInternal(favorite: FavoriteApp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAllInternal(favorites: List<FavoriteApp>)

    @Update
    protected abstract suspend fun updateFavoriteInternal(favorite: FavoriteApp)

    @Query("SELECT id FROM panels WHERE id = :panelId LIMIT 1")
    protected abstract suspend fun existingPanelId(panelId: Long): Long?

    @Query("SELECT id FROM panels ORDER BY position ASC, id ASC LIMIT 1")
    protected abstract suspend fun firstPanelId(): Long?

    @Insert
    protected abstract suspend fun insertPanel(panel: Panel): Long

    /** Maps every distinct panel id in [favorites] once, then rewrites the list. */
    private suspend fun withResolvedPanels(favorites: List<FavoriteApp>): List<FavoriteApp> {
        val resolved = mutableMapOf<Long, Long>()
        for (favorite in favorites) {
            if (favorite.panelId !in resolved) {
                resolved[favorite.panelId] = resolvePanelId(favorite.panelId)
            }
        }
        return favorites.map { it.copy(panelId = resolved.getValue(it.panelId)) }
    }

    /** The requested panel if it exists, else the first panel, else a newly created default one. */
    private suspend fun resolvePanelId(panelId: Long): Long =
        existingPanelId(panelId)
            ?: firstPanelId()
            ?: insertPanel(Panel(name = DEFAULT_PANEL_NAME, position = 0))

    companion object {
        const val DEFAULT_PANEL_NAME = "Perso"
    }
}
