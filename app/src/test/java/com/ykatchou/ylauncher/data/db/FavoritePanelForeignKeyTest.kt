package com.ykatchou.ylauncher.data.db

import android.os.Build
import androidx.room.Room
import com.ykatchou.ylauncher.data.model.FavoriteApp
import com.ykatchou.ylauncher.data.model.FolderApp
import com.ykatchou.ylauncher.data.model.Panel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Regression tests for the SQLITE_CONSTRAINT_FOREIGNKEY crash on favorite writes: any panel id
 * a caller hands the DAO must resolve to a panel that exists, whatever the state of `panels`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class FavoritePanelForeignKeyTest {

    private lateinit var database: YLauncherDatabase
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var folderDao: FolderDao
    private lateinit var panelDao: PanelDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            YLauncherDatabase::class.java,
        ).allowMainThreadQueries().build()
        favoriteDao = database.favoriteDao()
        folderDao = database.folderDao()
        panelDao = database.panelDao()
    }

    @After
    fun tearDown() = database.close()

    private fun favorite(position: Int, panelId: Long) = FavoriteApp(
        position = position,
        packageName = "com.example.app$position",
        displayName = "App $position",
        panelId = panelId,
    )

    @Test
    fun foreignKeysAreEnforced_soTheClampIsWhatKeepsWritesAlive() {
        val cursor = database.openHelper.readableDatabase.query("PRAGMA foreign_keys")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun insertFavorite_withNoPanelsAtAll_createsDefaultPanel() = runTest {
        favoriteDao.insertFavorite(favorite(0, panelId = 0))

        val panels = panelDao.getAllPanelsOnce()
        assertEquals(1, panels.size)
        assertEquals(FavoriteDao.DEFAULT_PANEL_NAME, panels.first().name)
        assertEquals(panels.first().id, favoriteDao.getAllFavoritesOnce().single().panelId)
    }

    @Test
    fun insertFavorite_withUnknownPanelId_fallsBackToFirstPanel() = runTest {
        val firstId = panelDao.insertPanel(Panel(name = "Perso", position = 0))
        panelDao.insertPanel(Panel(name = "Pro", position = 1))

        favoriteDao.insertFavorite(favorite(0, panelId = 9999))

        assertEquals(firstId, favoriteDao.getAllFavoritesOnce().single().panelId)
        assertEquals(2, panelDao.getAllPanelsOnce().size)
    }

    @Test
    fun insertFavorite_withKnownPanelId_keepsIt() = runTest {
        panelDao.insertPanel(Panel(name = "Perso", position = 0))
        val proId = panelDao.insertPanel(Panel(name = "Pro", position = 1))

        favoriteDao.insertFavorite(favorite(0, panelId = proId))

        assertEquals(proId, favoriteDao.getAllFavoritesOnce().single().panelId)
    }

    @Test
    fun insertAll_withMixedValidAndStalePanelIds_writesEveryRow() = runTest {
        val persoId = panelDao.insertPanel(Panel(name = "Perso", position = 0))
        val proId = panelDao.insertPanel(Panel(name = "Pro", position = 1))

        favoriteDao.insertAll(
            listOf(
                favorite(0, panelId = proId),
                favorite(1, panelId = 4242),
                favorite(2, panelId = 0),
            ),
        )

        val favorites = favoriteDao.getAllFavoritesOnce()
        assertEquals(3, favorites.size)
        assertEquals(proId, favorites[0].panelId)
        assertEquals(persoId, favorites[1].panelId)
        assertEquals(persoId, favorites[2].panelId)
    }

    @Test
    fun insertFavorite_afterItsPanelWasDeleted_survives() = runTest {
        val persoId = panelDao.insertPanel(Panel(name = "Perso", position = 0))
        val proId = panelDao.insertPanel(Panel(name = "Pro", position = 1))
        favoriteDao.insertFavorite(favorite(0, panelId = proId))

        // Cascade wipes the Pro favorites; a queued write still carrying proId must not crash.
        panelDao.deletePanel(proId)
        favoriteDao.insertFavorite(favorite(1, panelId = proId))

        assertEquals(persoId, favoriteDao.getAllFavoritesOnce().single().panelId)
    }

    @Test
    fun updateFavorite_ontoAStalePanelId_survives() = runTest {
        val persoId = panelDao.insertPanel(Panel(name = "Perso", position = 0))
        favoriteDao.insertFavorite(favorite(0, panelId = persoId))
        val stored = favoriteDao.getAllFavoritesOnce().single()

        favoriteDao.updateFavorite(stored.copy(displayName = "Renamed", panelId = 777))

        val updated = favoriteDao.getAllFavoritesOnce().single()
        assertEquals("Renamed", updated.displayName)
        assertEquals(persoId, updated.panelId)
    }

    @Test
    fun insertFolderApp_intoMissingFolder_isDroppedInsteadOfThrowing() = runTest {
        val app = FolderApp(folderId = 123, packageName = "com.example.app", displayName = "App", position = 0)

        assertFalse(folderDao.insertFolderApp(app))
        assertTrue(folderDao.getAppsInFolderOnce(123).isEmpty())
    }

    @Test
    fun insertFolderApp_intoExistingFolder_isWritten() = runTest {
        val folderId = folderDao.insertFolder(
            com.ykatchou.ylauncher.data.model.Folder(name = "Work", position = 0),
        )
        val app = FolderApp(folderId = folderId, packageName = "com.example.app", displayName = "App", position = 0)

        assertTrue(folderDao.insertFolderApp(app))
        assertEquals(1, folderDao.getAppsInFolderOnce(folderId).size)
    }
}
