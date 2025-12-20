package widget.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    // 全メモ取得（新しい順）
    @Query("SELECT * FROM memos ORDER BY timestamp DESC")
    fun getAllMemos(): Flow<List<MemoEntity>>

    // ウィジェット表示用メモ取得（Flow版）
    @Query("SELECT * FROM memos WHERE showInWidget = 1 ORDER BY timestamp DESC")
    fun getWidgetMemos(): Flow<List<MemoEntity>>

    // ウィジェット表示用メモ取得（同期版 - 最大2件）
    @Query("SELECT * FROM memos WHERE showInWidget = 1 ORDER BY timestamp DESC LIMIT 2")
    suspend fun getWidgetMemosSync(): List<MemoEntity>

    // メモ追加
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memo: MemoEntity)

    // メモ更新
    @Update
    suspend fun update(memo: MemoEntity)

    // メモ削除
    @Delete
    suspend fun delete(memo: MemoEntity)

    // ID指定で削除
    @Query("DELETE FROM memos WHERE id = :memoId")
    suspend fun deleteById(memoId: String)

    // 古いメモを削除（件数制限用）
    @Query("DELETE FROM memos WHERE id NOT IN (SELECT id FROM memos ORDER BY timestamp DESC LIMIT :maxCount)")
    suspend fun deleteOldMemos(maxCount: Int)
}