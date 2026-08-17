package com.ykatchou.ylauncher.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ykatchou.ylauncher.data.model.FavoriteApp
import com.ykatchou.ylauncher.data.model.Folder
import com.ykatchou.ylauncher.data.model.FolderApp
import com.ykatchou.ylauncher.data.model.Panel

@Database(
    entities = [FavoriteApp::class, Folder::class, FolderApp::class, Panel::class],
    version = 5,
    exportSchema = false,
)
abstract class YLauncherDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun folderDao(): FolderDao
    abstract fun panelDao(): PanelDao
}
