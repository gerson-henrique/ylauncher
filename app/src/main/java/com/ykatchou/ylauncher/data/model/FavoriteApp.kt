package com.ykatchou.ylauncher.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_apps",
    foreignKeys = [
        ForeignKey(
            entity = Panel::class,
            parentColumns = ["id"],
            childColumns = ["panelId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("panelId")],
)
data class FavoriteApp(
    @PrimaryKey
    val position: Int,
    val packageName: String,
    val activityClassName: String? = null,
    val displayName: String,
    val userHandleString: String = "",
    val folderId: Long? = null,
    val iconEmoji: String? = null,
    val panelId: Long = 0,
) {
    val isFolder: Boolean get() = folderId != null
}
