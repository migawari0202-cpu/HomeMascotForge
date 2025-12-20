package com.example.mascotforge

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mascotforge.R
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import widget.TimeWidgetProvider
import widget.database.MemoEntity
import widget.database.MemoRepository
import kotlinx.coroutines.launch

class MemoInputActivity : AppCompatActivity() {

    private lateinit var titleTextView: TextView
    private lateinit var editTextMemo: EditText
    private lateinit var buttonSave: Button
    private lateinit var buttonCancel: Button
    private lateinit var memoRepository: MemoRepository

    // ★ 編集モード用
    private var editingMemo: MemoEntity? = null
    private var isEditMode = false

    companion object {
        private const val MAX_MEMO_LENGTH = 500  // 最大文字数

        // ★ Intentで渡すキー
        const val EXTRA_MEMO_ID = "extra_memo_id"              // String
        const val EXTRA_MEMO_TEXT = "extra_memo_text"          // String
        const val EXTRA_MEMO_TIMESTAMP = "extra_memo_timestamp" // Long
        const val EXTRA_MEMO_DATE = "extra_memo_date"          // String
        const val EXTRA_MEMO_SHOW_IN_WIDGET = "extra_memo_show_in_widget" // Boolean
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memo_input)

        // ルートビューに安全領域（ノッチ／ステータスバー）を反映
        val root = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(v.paddingLeft, sysInsets.top, v.paddingRight, v.paddingBottom)
            insets
        }

        titleTextView = findViewById(R.id.title_text)  // ★ タイトル用TextView追加が必要
        editTextMemo = findViewById(R.id.editTextMemo)
        buttonSave = findViewById(R.id.btn_done)
        buttonCancel = findViewById(R.id.buttonCancel)

        // ★ Room Repository を初期化
        memoRepository = MemoRepository(this)

        // ★ 編集モードかどうかチェック
        checkEditMode()

        buttonSave.setOnClickListener { saveMemo() }
        buttonCancel.setOnClickListener { finish() }
    }

    /**
     * ★ 編集モードかどうかチェック
     */
    private fun checkEditMode() {
        val memoId = intent.getStringExtra(EXTRA_MEMO_ID)  // ★ String で取得

        if (memoId != null) {
            // 編集モード
            isEditMode = true
            titleTextView.text = "メモを編集"
            buttonSave.text = "更新"

            val memoText = intent.getStringExtra(EXTRA_MEMO_TEXT) ?: ""
            val memoTimestamp = intent.getLongExtra(EXTRA_MEMO_TIMESTAMP, System.currentTimeMillis())
            val memoDate = intent.getStringExtra(EXTRA_MEMO_DATE) ?: ""
            val showInWidget = intent.getBooleanExtra(EXTRA_MEMO_SHOW_IN_WIDGET, false)

            editingMemo = MemoEntity(
                id = memoId,
                text = memoText,
                timestamp = memoTimestamp,
                date = memoDate,
                showInWidget = showInWidget
            )

            // テキストをセット
            editTextMemo.setText(memoText)
            editTextMemo.setSelection(memoText.length)  // カーソルを末尾に
        } else {
            // 新規作成モード
            isEditMode = false
            titleTextView.text = "メモを追加"
            buttonSave.text = "保存"
        }
    }

    private fun saveMemo() {
        val memoText = editTextMemo.text.toString().trim()

        // バリデーション
        if (memoText.isEmpty()) {
            Toast.makeText(this, "メモを入力してください", Toast.LENGTH_SHORT).show()
            return
        }

        if (memoText.length > MAX_MEMO_LENGTH) {
            Toast.makeText(this, "メモは${MAX_MEMO_LENGTH}文字以内で入力してください", Toast.LENGTH_SHORT).show()
            return
        }

        // ★ コルーチンで Room に保存 or 更新
        lifecycleScope.launch {
            try {
                if (isEditMode && editingMemo != null) {
                    // ★ 編集モード: 更新
                    val updatedMemo = editingMemo!!.copy(text = memoText)
                    memoRepository.updateMemo(updatedMemo)
                    Toast.makeText(this@MemoInputActivity, "メモを更新しました", Toast.LENGTH_SHORT).show()
                } else {
                    // ★ 新規作成モード: 追加
                    memoRepository.addMemo(memoText)
                    Toast.makeText(this@MemoInputActivity, "メモを保存しました", Toast.LENGTH_SHORT).show()
                }

                // ウィジェット更新
                TimeWidgetProvider.notifyMemoChanged(this@MemoInputActivity)
                finish()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MemoInputActivity,
                    "保存に失敗しました: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}