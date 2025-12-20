package widget.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * データベース定義
 *
 * 【機能】
 * - メモ機能（ウィジェットからメモを追加・削除）
 *
 * 【履歴】
 * - version 1: メモ機能のみ
 */
@Database(
    entities = [
        MemoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MemoDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao

    companion object {
        @Volatile
        private var INSTANCE: MemoDatabase? = null

        fun getDatabase(context: Context): MemoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoDatabase::class.java,
                    "memo_database"
                )
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}