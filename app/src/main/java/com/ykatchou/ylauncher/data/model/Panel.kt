package com.ykatchou.ylauncher.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "panels")
data class Panel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val position: Int,
)
