package widget.database

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class MemoRepository(context: Context) {
    private val memoDao = MemoDatabase.getDatabase(context).memoDao()

    // メモ一覧（Flow）
    val allMemos: Flow<List<MemoEntity>> = memoDao.getAllMemos()
    val widgetMemos: Flow<List<MemoEntity>> = memoDao.getWidgetMemos()

    // ★ 追加: ウィジェット用メモを同期的に取得
    suspend fun getMemosForWidgetSync(): List<MemoEntity> {
        return memoDao.getWidgetMemosSync()
    }

    // メモ追加
    suspend fun addMemo(text: String) {
        val dateFormatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        val memo = MemoEntity(
            id = UUID.randomUUID().toString(),
            text = text,
            timestamp = System.currentTimeMillis(),
            date = dateFormatter.format(Date()),
            showInWidget = false
        )
        memoDao.insert(memo)

        // 古いメモを削除（1000件以上なら削除）
        memoDao.deleteOldMemos(1000)
    }

    // メモ更新
    suspend fun updateMemo(memo: MemoEntity) {
        memoDao.update(memo)
    }

    // メモ削除
    suspend fun deleteMemo(memoId: String) {
        memoDao.deleteById(memoId)
    }
}