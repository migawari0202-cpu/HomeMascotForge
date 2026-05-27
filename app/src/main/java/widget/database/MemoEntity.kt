package com.example.mascotforge.widget.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memos")
data class MemoEntity(
    @PrimaryKey
    val id: String,
    val text: String,
    val timestamp: Long,
    val date: String,
    val showInWidget: Boolean = false  // ★ デフォルトでfalseを追加
)