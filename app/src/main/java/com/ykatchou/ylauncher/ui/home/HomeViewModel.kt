package com.ykatchou.ylauncher.ui.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykatchou.ylauncher.data.db.FavoriteDao
import com.ykatchou.ylauncher.data.db.FolderDao
import com.ykatchou.ylauncher.data.db.PanelDao
import com.ykatchou.ylauncher.data.model.AppInfo
import com.ykatchou.ylauncher.data.model.FavoriteApp
import com.ykatchou.ylauncher.data.model.Folder
import com.ykatchou.ylauncher.data.model.FolderApp
import com.ykatchou.ylauncher.data.model.Panel
import com.ykatchou.ylauncher.data.repository.AppRepository
import com.ykatchou.ylauncher.data.repository.PrefsRepository
import com.ykatchou.ylauncher.util.UsageStatsHelper
import com.ykatchou.ylauncher.widget.LauncherWidgetHost
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ykatchou.ylauncher.util.YLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val favoriteDao: FavoriteDao,
    private val folderDao: FolderDao,
    private val panelDao: PanelDao,
    private val prefsRepository: PrefsRepository,
    val widgetHost: LauncherWidgetHost,
) : ViewModel() {

    private val _usageStatsVersion = MutableStateFlow(0)

    fun refreshUsageStats() {
        _usageStatsVersion.value++
    }

    fun refreshAppsIfEmpty() {
        if (appRepository.appList.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout(10_000) { appRepository.refreshApps() }
            } catch (e: Exception) {
                YLogger.e(TAG, "refreshAppsIfEmpty failed", e)
            }
        }
    }

    // All favorites (unfiltered, used by suggestions/recent to exclude all fav packages)
    private val allFavorites = favoriteDao.getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Panel state
    val activePanel = prefsRepository.activePanel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val panels: StateFlow<List<Panel>> = panelDao.getAllPanels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favorites filtered by active panel
    val favorites: StateFlow<List<FavoriteApp>> = combine(
        allFavorites,
        prefsRepository.activePanel,
    ) { favs, panel ->
        favs.filter { it.panelId == panel }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suggestedApps: StateFlow<List<AppInfo>> = combine(
        allFavorites,
        prefsRepository.suggestionCount,
        appRepository.appList,
        _usageStatsVersion,
    ) { favs, count, _, _ ->
        if (count == 0) return@combine emptyList()
        val favPackages = favs.map { it.packageName }.toSet()
        val topApps = UsageStatsHelper.getTopApps(context, appRepository, count = count + 10)
        topApps.filter { it.packageName !in favPackages }.take(count)
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentApps: StateFlow<List<AppInfo>> = combine(
        allFavorites,
        prefsRepository.recentAppsCount,
        suggestedApps,
        _usageStatsVersion,
    ) { favs, recentCount, suggested, _ ->
        if (recentCount == 0) return@combine emptyList()
        val favPackages = favs.map { it.packageName }.toSet()
        val suggestedPackages = suggested.map { it.packageName }.toSet()
        UsageStatsHelper.getRecentApps(
            context, appRepository, count = recentCount,
            excludePackages = favPackages + suggestedPackages,
        )
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeWidgetIds = prefsRepository.homeWidgetIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All display/swipe/HAL prefs in one snapshot — single collectAsState() in the UI. */
    val homePrefs: StateFlow<com.ykatchou.ylauncher.data.repository.HomePrefs> =
        prefsRepository.homePrefs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.ykatchou.ylauncher.data.repository.HomePrefs())

    // Magic-button (HAL) actions are global, shared across all panels.
    val halTapAction: StateFlow<String> = homePrefs
        .map { it.halTapActionRaw }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ASSISTANT")

    val halLongPressAction: StateFlow<String> = homePrefs
        .map { it.halLongPressActionRaw }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SETTINGS")

    val halDoubleTapAction: StateFlow<String> = homePrefs
        .map { it.halDoubleTapActionRaw }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "APP_DRAWER")

    private val dbWriteMutex = Mutex()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    init {
        reconcileLegacyPanelNamesIfNeeded()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout(10_000) { appRepository.refreshApps() }
            } catch (e: TimeoutCancellationException) {
                YLogger.e(TAG, "refreshApps timed out", e)
            } catch (e: Exception) {
                YLogger.e(TAG, "refreshApps failed", e)
            }
            appRepository.registerCallback()
            try {
                autoPopulateFavoritesIfNeeded()
            } catch (e: Exception) {
                YLogger.e(TAG, "autoPopulate failed", e)
            }
            try {
                prefsRepository.recordFirstLaunchIfNeeded()
            } catch (e: Exception) {
                YLogger.e(TAG, "recordFirstLaunch failed", e)
            }
        }
        viewModelScope.launch {
            com.ykatchou.ylauncher.MainActivity.homePressed.collect { closeDrawer() }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private suspend fun autoPopulateFavoritesIfNeeded() {
        if (favoriteDao.count() > 0) return

        // Try usage stats first (imports your real most-used apps)
        val topApps = UsageStatsHelper.getTopApps(context, appRepository, count = 6)
        if (topApps.isNotEmpty()) {
            val favorites = topApps.mapIndexed { index, app ->
                FavoriteApp(index, app.packageName, app.activityClassName, app.appLabel, app.userHandle.toString())
            }
            favoriteDao.insertAll(favorites)
            prefsRepository.setFirstLaunchDone()
            return
        }

        // Fallback: resolve default apps by intent category
        val defaults = mutableListOf<FavoriteApp>()
        var position = 0

        // Phone
        appRepository.resolveDefaultApp(Intent(Intent.ACTION_DIAL))?.let {
            defaults.add(FavoriteApp(position++, it.packageName, it.activityClassName, "Phone", it.userHandle.toString()))
        }
        // Messages
        appRepository.resolveDefaultApp(Intent(Intent.ACTION_SENDTO).apply { data = android.net.Uri.parse("smsto:") })?.let {
            defaults.add(FavoriteApp(position++, it.packageName, it.activityClassName, "Messages", it.userHandle.toString()))
        }
        // Browser
        appRepository.resolveDefaultApp(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com")))?.let {
            defaults.add(FavoriteApp(position++, it.packageName, it.activityClassName, "Browser", it.userHandle.toString()))
        }
        // Camera
        appRepository.resolveDefaultApp(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))?.let {
            defaults.add(FavoriteApp(position++, it.packageName, it.activityClassName, "Camera", it.userHandle.toString()))
        }
        // Gallery
        appRepository.resolveDefaultApp(Intent(Intent.ACTION_VIEW).apply { type = "image/*" })?.let {
            defaults.add(FavoriteApp(position++, it.packageName, it.activityClassName, "Gallery", it.userHandle.toString()))
        }
        // Settings
        appRepository.resolveDefaultApp(Intent(android.provider.Settings.ACTION_SETTINGS))?.let {
            defaults.add(FavoriteApp(position++, it.packageName, it.activityClassName, "Settings", it.userHandle.toString()))
        }

        if (defaults.isNotEmpty()) {
            favoriteDao.insertAll(defaults)
        }
        prefsRepository.setFirstLaunchDone()
    }

    fun reimportFromUsageStats() {
        viewModelScope.launch {
            val topApps = UsageStatsHelper.getTopApps(context, appRepository, count = 6)
            if (topApps.isNotEmpty()) {
                dbWriteMutex.withLock {
                    val panelId = activePanel.value
                    favoriteDao.deleteByPanel(panelId)
                    val otherMaxPos = favoriteDao.getAllFavoritesOnce()
                        .maxOfOrNull { it.position } ?: -1
                    val basePos = otherMaxPos + 1
                    val favorites = topApps.mapIndexed { index, app ->
                        FavoriteApp(basePos + index, app.packageName, app.activityClassName, app.appLabel, app.userHandle.toString(), panelId = panelId)
                    }
                    favoriteDao.insertAll(favorites)
                }
            }
        }
    }

    fun hasUsageStatsPermission(): Boolean = UsageStatsHelper.hasPermission(context)

    fun requestUsageStatsPermission() = UsageStatsHelper.requestPermission(context)

    fun removeWidget(widgetId: Int) {
        widgetHost.deleteAppWidgetId(widgetId)
        viewModelScope.launch { prefsRepository.removeHomeWidgetId(widgetId) }
    }

    fun removeAllWidgets() {
        viewModelScope.launch {
            val ids = prefsRepository.homeWidgetIdsOnce()
            ids.forEach { widgetHost.deleteAppWidgetId(it) }
            prefsRepository.clearHomeWidgetIds()
        }
    }

    fun openDrawer() { _isDrawerOpen.value = true }
    fun closeDrawer() { _isDrawerOpen.value = false }

    fun saveFavorites(favorites: List<FavoriteApp>) {
        viewModelScope.launch {
            dbWriteMutex.withLock {
                val panelId = activePanel.value
                // Delete only this panel's favorites
                favoriteDao.deleteByPanel(panelId)
                // Compute a safe starting position that won't collide with other panels
                val otherMaxPos = favoriteDao.getAllFavoritesOnce()
                    .maxOfOrNull { it.position } ?: -1
                val basePos = otherMaxPos + 1
                favoriteDao.insertAll(favorites.mapIndexed { index, fav ->
                    fav.copy(position = basePos + index, panelId = panelId)
                })
            }
        }
    }

    // Panel operations

    fun switchPanel(panelId: Long) {
        viewModelScope.launch { prefsRepository.setActivePanel(panelId) }
    }

    fun moveFavoriteToPanel(favorite: FavoriteApp, targetPanelId: Long) {
        viewModelScope.launch {
            val targetFavs = favoriteDao.getAllFavoritesOnce().filter { it.panelId == targetPanelId }
            val nextPosition = (targetFavs.maxOfOrNull { it.position } ?: -1) + 1
            favoriteDao.deleteFavoriteAt(favorite.position)
            favoriteDao.insertFavorite(favorite.copy(position = nextPosition, panelId = targetPanelId))
        }
    }

    fun addPanel(name: String) {
        viewModelScope.launch {
            val nextPosition = (panels.value.maxOfOrNull { it.position } ?: -1) + 1
            val newId = panelDao.insertPanel(Panel(name = name, position = nextPosition))
            prefsRepository.setActivePanel(newId)
        }
    }

    fun renamePanel(id: Long, newName: String) {
        viewModelScope.launch { panelDao.renamePanel(id, newName) }
    }

    fun reorderPanels(newOrder: List<Panel>) {
        viewModelScope.launch {
            newOrder.forEachIndexed { index, panel ->
                if (panel.position != index) panelDao.updatePanel(panel.copy(position = index))
            }
        }
    }

    fun deletePanel(id: Long) {
        viewModelScope.launch {
            val remaining = panels.value.filterNot { it.id == id }
            if (remaining.isEmpty()) return@launch
            panelDao.deletePanel(id) // FK cascade removes this panel's favorite_apps rows — installed apps untouched
            if (activePanel.value == id) {
                prefsRepository.setActivePanel(remaining.first().id)
            }
        }
    }

    /**
     * One-time reconciliation: after MIGRATION_3_4 seeds `panels` with placeholder names
     * (Room migrations can't read DataStore), copy the user's real legacy panel names
     * (if any) from DataStore onto the matching Panel rows by id. Safe to call on every
     * launch — no-ops once `markLegacyPanelNamesMigrated()` has run.
     */
    fun reconcileLegacyPanelNamesIfNeeded() {
        viewModelScope.launch {
            if (prefsRepository.legacyPanelNamesMigrated()) return@launch
            val legacyNames = prefsRepository.legacyPanelNamesCsvOnce()
                ?.split("|")?.filter { it.isNotBlank() }
            if (legacyNames != null) {
                val existingIds = panelDao.getAllPanelsOnce().map { it.id }.toSet()
                legacyNames.forEachIndexed { index, name ->
                    if (index.toLong() in existingIds) panelDao.renamePanel(index.toLong(), name)
                }
            }
            prefsRepository.markLegacyPanelNamesMigrated()
        }
    }

    // Folder operations

    fun createFolder(name: String = "New Folder", emoji: String = "📁") {
        viewModelScope.launch {
            val panelId = activePanel.value
            val currentFavs = favoriteDao.getAllFavoritesOnce()
            val nextPosition = (currentFavs.maxOfOrNull { it.position } ?: -1) + 1
            val folderId = folderDao.insertFolder(Folder(name = name, position = nextPosition, iconEmoji = emoji))
            favoriteDao.insertFavorite(
                FavoriteApp(
                    position = nextPosition,
                    packageName = "",
                    displayName = name,
                    folderId = folderId,
                    iconEmoji = emoji,
                    panelId = panelId,
                )
            )
        }
    }

    fun getFolderApps(folderId: Long): kotlinx.coroutines.flow.Flow<List<FolderApp>> {
        return folderDao.getAppsInFolder(folderId)
    }

    suspend fun getFolderAppsOnce(folderId: Long): List<FolderApp> {
        return folderDao.getAppsInFolderOnce(folderId)
    }

    fun updateFolder(folderId: Long, name: String, emoji: String, apps: List<FolderApp>) {
        viewModelScope.launch {
            // Update folder entity
            val existing = folderDao.getFolderById(folderId) ?: return@launch
            folderDao.updateFolder(existing.copy(name = name, iconEmoji = emoji))

            // Update the favorite entry to keep display in sync
            val favs = favoriteDao.getAllFavoritesOnce()
            val folderFav = favs.find { it.folderId == folderId }
            if (folderFav != null) {
                favoriteDao.updateFavorite(folderFav.copy(displayName = name, iconEmoji = emoji))
            }

            // Replace folder apps
            folderDao.deleteAllAppsInFolder(folderId)
            apps.forEachIndexed { index, app ->
                folderDao.insertFolderApp(app.copy(folderId = folderId, position = index))
            }
        }
    }

    fun addAppToFolder(folderId: Long, app: AppInfo) {
        viewModelScope.launch {
            val existingApps = folderDao.getAppsInFolderOnce(folderId)
            val nextPos = (existingApps.maxOfOrNull { it.position } ?: -1) + 1
            folderDao.insertFolderApp(
                FolderApp(
                    folderId = folderId,
                    packageName = app.packageName,
                    activityClassName = app.activityClassName,
                    displayName = app.appLabel,
                    position = nextPos,
                    userHandleString = app.userHandle.toString(),
                )
            )
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            folderDao.deleteFolder(folderId)
            // Remove the favorite entry
            val favs = favoriteDao.getAllFavoritesOnce()
            val folderFav = favs.find { it.folderId == folderId }
            if (folderFav != null) {
                favoriteDao.deleteFavoriteAt(folderFav.position)
            }
        }
    }

    fun getAllFolders(): kotlinx.coroutines.flow.Flow<List<Folder>> {
        return folderDao.getAllFolders()
    }

    fun moveFavoriteToFolder(favorite: FavoriteApp, folderId: Long) {
        viewModelScope.launch {
            // Add the app into the folder
            val existingApps = folderDao.getAppsInFolderOnce(folderId)
            val nextPos = (existingApps.maxOfOrNull { it.position } ?: -1) + 1
            folderDao.insertFolderApp(
                FolderApp(
                    folderId = folderId,
                    packageName = favorite.packageName,
                    activityClassName = favorite.activityClassName,
                    displayName = favorite.displayName,
                    position = nextPos,
                    userHandleString = favorite.userHandleString,
                )
            )
            // Remove it from favorites
            favoriteDao.deleteFavoriteAt(favorite.position)
        }
    }

    override fun onCleared() {
        super.onCleared()
        appRepository.unregisterCallback()
    }
}
