package com.example.mascotforge.widget.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.example.mascotforge.widget.database.MemoEntity
import com.example.mascotforge.widget.database.MemoRepository
import java.text.SimpleDateFormat
import java.util.*

data class WidgetMemo(
    val id: String,
    val text: String,
    val timestamp: Long,
    val date: String,
    val showInWidget: Boolean
) {
    companion object {
        fun fromEntity(entity: MemoEntity): WidgetMemo {
            return WidgetMemo(
                id = entity.id,
                text = entity.text,
                timestamp = entity.timestamp,
                date = entity.date,
                showInWidget = entity.showInWidget
            )
        }
    }

    fun toEntity(): MemoEntity {
        return MemoEntity(
            id = id,
            text = text,
            timestamp = timestamp,
            date = date,
            showInWidget = showInWidget
        )
    }
}

class MemoCache(context: Context) {
    private val repository = MemoRepository(context)

    companion object {
        private const val TAG = "MemoCache"
    }

    /**
     * ウィジェット表示用のメモを取得（同期版）
     */
    fun getWidgetMemos(): List<WidgetMemo> {
        return runBlocking {
            try {
                repository.widgetMemos.first()
                    .map { WidgetMemo.fromEntity(it) }
                    .map { memo ->
                        // 表示用にテキストを短縮（12文字＋…）
                        val shortText = if (memo.text.length > 12) {
                            memo.text.take(12) + "…"
                        } else {
                            memo.text
                        }
                        memo.copy(text = shortText)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get widget memos", e)
                emptyList()
            }
        }
    }

    /**
     * すべてのメモを取得（同期版）
     */
    fun getAllMemos(): List<WidgetMemo> {
        return runBlocking {
            try {
                repository.allMemos.first()
                    .map { WidgetMemo.fromEntity(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get all memos", e)
                emptyList()
            }
        }
    }

    /**
     * メモを追加
     */
    fun addMemo(text: String) {
        runBlocking {
            try {
                repository.addMemo(text)
                Log.d(TAG, "Added memo: $text")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add memo", e)
            }
        }
    }

    /**
     * メモを更新
     */
    fun updateMemo(updatedMemo: WidgetMemo) {
        runBlocking {
            try {
                repository.updateMemo(updatedMemo.toEntity())
                Log.d(TAG, "Updated memo: ${updatedMemo.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update memo", e)
            }
        }
    }

    /**
     * メモを削除
     */
    fun deleteMemo(memoId: String) {
        runBlocking {
            try {
                repository.deleteMemo(memoId)
                Log.d(TAG, "Deleted memo: $memoId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete memo", e)
            }
        }
    }
}