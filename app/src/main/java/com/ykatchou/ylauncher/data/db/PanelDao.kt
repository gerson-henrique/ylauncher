package com.ykatchou.ylauncher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ykatchou.ylauncher.data.model.Panel
import kotlinx.coroutines.flow.Flow

@Dao
interface PanelDao {
    @Query("SELECT * FROM panels ORDER BY position ASC")
    fun getAllPanels(): Flow<List<Panel>>

    @Query("SELECT * FROM panels ORDER BY position ASC")
    suspend fun getAllPanelsOnce(): List<Panel>

    @Insert
    suspend fun insertPanel(panel: Panel): Long

    @Update
    suspend fun updatePanel(panel: Panel)

    @Query("UPDATE panels SET name = :name WHERE id = :id")
    suspend fun renamePanel(id: Long, name: String)

    @Query("UPDATE panels SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM panels WHERE id = :id")
    suspend fun deletePanel(id: Long)

    @Query("DELETE FROM panels")
    suspend fun deleteAll()
}
