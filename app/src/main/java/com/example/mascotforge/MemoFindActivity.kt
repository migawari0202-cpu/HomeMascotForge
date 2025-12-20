package com.example.mascotforge

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import widget.TimeWidgetProvider
import widget.database.MemoEntity
import widget.database.MemoRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MemoFindActivity : AppCompatActivity() {

    private lateinit var memoRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var addButton: FloatingActionButton
    private lateinit var searchEditText: TextInputEditText
    private lateinit var memoAdapter: MemoAdapter
    private lateinit var memoRepository: MemoRepository

    // 検索クエリをFlowで管理
    private val searchQuery = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.memo_find_activity)

        initViews()
        setupWindowInsets()  // ★ WindowInsets対応
        memoRepository = MemoRepository(this)

        setupRecyclerView()
        setupSearch()
        setupFab()
        observeMemos()
    }

    private fun initViews() {
        memoRecyclerView = findViewById(R.id.memo_recycler_view)
        emptyState = findViewById(R.id.empty_state)
        addButton = findViewById(R.id.btn_add_memo)
        searchEditText = findViewById(R.id.search_edit_text)
    }

    /**
     * ★ 安全表示領域（WindowInsets）対応
     */
    private fun setupWindowInsets() {
        val rootView = findViewById<View>(android.R.id.content)
        val toolbar = findViewById<View>(R.id.toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // ツールバーに上部の余白を追加（ノッチ・ステータスバー対応）
            toolbar?.setPadding(
                toolbar.paddingLeft,
                toolbar.paddingTop + systemBars.top,
                toolbar.paddingRight,
                toolbar.paddingBottom
            )

            // FABに下部の余白を追加（ナビゲーションバー対応）
            addButton.apply {
                val params = layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = 16.dpToPx() + systemBars.bottom
                params.rightMargin = 16.dpToPx() + systemBars.right
                layoutParams = params
            }

            insets
        }
    }

    /**
     * ★ dp→pxの変換ヘルパー
     */
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun setupRecyclerView() {
        memoAdapter = MemoAdapter(
            onItemClick = ::editMemo,
            onDeleteClick = ::showDeleteConfirmDialog,
            onWidgetToggle = ::toggleWidgetDisplay
        )
        memoRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MemoFindActivity)
            adapter = memoAdapter
            // パフォーマンス改善
            setHasFixedSize(true)
        }
    }

    private fun setupFab() {
        addButton.setOnClickListener {
            startActivity(Intent(this, MemoInputActivity::class.java))
        }
    }

    /**
     * ✨ 検索のセットアップ（デバウンス付き）
     */
    @OptIn(FlowPreview::class)
    private fun setupSearch() {
        // リアルタイム検索クエリの更新
        searchEditText.addTextChangedListener { text ->
            searchQuery.value = text?.toString().orEmpty()
        }

        // デバウンス: 400ms待ってから検索実行
        lifecycleScope.launch {
            searchQuery
                .debounce(400)
                .distinctUntilChanged()
                .collect { query ->
                    updateEmptyStateMessage(query)
                }
        }
    }

    /**
     * ✨ Flowでメモの変更を監視 + 検索フィルター適用
     */
    @OptIn(FlowPreview::class)
    private fun observeMemos() {
        lifecycleScope.launch {
            memoRepository.allMemos.collect { allMemos ->
                val query = searchQuery.value
                val filtered = if (query.isBlank()) {
                    allMemos
                } else {
                    allMemos.filter { memo ->
                        memo.text.contains(query, ignoreCase = true) ||
                                memo.date.contains(query, ignoreCase = true)
                    }
                }

                // ListAdapterに渡す（DiffUtilが自動で差分更新）
                memoAdapter.submitList(filtered)

                // 空状態の表示制御
                updateEmptyState(filtered.isEmpty(), query)
            }
        }
    }

    /**
     * ✨ 空状態の表示を更新
     */
    private fun updateEmptyState(isEmpty: Boolean, query: String) {
        if (isEmpty) {
            emptyState.visibility = View.VISIBLE
            memoRecyclerView.visibility = View.GONE
            updateEmptyStateMessage(query)
        } else {
            emptyState.visibility = View.GONE
            memoRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun updateEmptyStateMessage(query: String) {
        val messageView = emptyState.findViewById<TextView>(R.id.empty_title)
        val subMessageView = emptyState.findViewById<TextView>(R.id.empty_message)

        if (query.isBlank()) {
            messageView.text = "メモがありません"
            subMessageView.text = "右下の + ボタンで\n最初のメモを作成しましょう"
        } else {
            messageView.text = "該当するメモがありません"
            subMessageView.text = "別のキーワードで検索してみてください"
        }
    }

    /**
     * メモをタップして編集
     */
    private fun editMemo(memo: MemoEntity) {
        val intent = Intent(this, MemoInputActivity::class.java).apply {
            putExtra(MemoInputActivity.EXTRA_MEMO_ID, memo.id)
            putExtra(MemoInputActivity.EXTRA_MEMO_TEXT, memo.text)
            putExtra(MemoInputActivity.EXTRA_MEMO_TIMESTAMP, memo.timestamp)
            putExtra(MemoInputActivity.EXTRA_MEMO_DATE, memo.date)
            putExtra(MemoInputActivity.EXTRA_MEMO_SHOW_IN_WIDGET, memo.showInWidget)
        }
        startActivity(intent)
    }

    private fun showDeleteConfirmDialog(memo: MemoEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("メモを削除")
            .setMessage("このメモを削除しますか？")
            .setPositiveButton("削除") { _, _ -> deleteMemo(memo) }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun toggleWidgetDisplay(memo: MemoEntity, isChecked: Boolean) {
        if (isChecked) {
            // 現在表示中のメモ数をカウント（submitList経由で取得）
            val currentCount = memoAdapter.currentList.count { it.showInWidget }
            if (currentCount >= 2) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("制限に達しました")
                    .setMessage("ウィジェットには最大2個までしか表示できません。\n他のメモの表示を解除してから選択してください。")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
        }
        updateMemoWidget(memo, isChecked)
    }

    private fun updateMemoWidget(memo: MemoEntity, showInWidget: Boolean) {
        lifecycleScope.launch {
            try {
                val updatedMemo = memo.copy(showInWidget = showInWidget)
                memoRepository.updateMemo(updatedMemo)
                TimeWidgetProvider.notifyMemoChanged(this@MemoFindActivity)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MemoFindActivity,
                    "更新に失敗しました",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun deleteMemo(memo: MemoEntity) {
        lifecycleScope.launch {
            try {
                memoRepository.deleteMemo(memo.id)
                TimeWidgetProvider.notifyMemoChanged(this@MemoFindActivity)
                Toast.makeText(
                    this@MemoFindActivity,
                    "メモを削除しました",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MemoFindActivity,
                    "削除に失敗しました",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

// ============================================
// ✨ ListAdapter + DiffUtil（モダンなAdapter）
// ============================================
class MemoAdapter(
    private val onItemClick: (MemoEntity) -> Unit,
    private val onDeleteClick: (MemoEntity) -> Unit,
    private val onWidgetToggle: (MemoEntity, Boolean) -> Unit
) : ListAdapter<MemoEntity, MemoAdapter.MemoViewHolder>(MemoDiffCallback()) {

    class MemoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
        val dateView: TextView = view.findViewById(android.R.id.text2)
        val widgetToggle: MaterialCheckBox = view.findViewById(R.id.widget_toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.memo_item, parent, false)
        return MemoViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        val memo = getItem(position)

        holder.textView.text = memo.text
        holder.dateView.text = memo.date

        // チェックボックスの状態を設定（リスナーを一旦外す）
        holder.widgetToggle.setOnCheckedChangeListener(null)
        holder.widgetToggle.isChecked = memo.showInWidget
        holder.widgetToggle.text = if (memo.showInWidget) "★" else "☆"

        holder.widgetToggle.setOnCheckedChangeListener { _, isChecked ->
            holder.widgetToggle.text = if (isChecked) "★" else "☆"
            onWidgetToggle(memo, isChecked)
        }

        holder.itemView.setOnClickListener {
            onItemClick(memo)
        }

        holder.itemView.setOnLongClickListener {
            onDeleteClick(memo)
            true
        }
    }
}

// ============================================
// ✨ DiffUtil.ItemCallback（効率的な差分更新）
// ============================================
class MemoDiffCallback : DiffUtil.ItemCallback<MemoEntity>() {
    override fun areItemsTheSame(oldItem: MemoEntity, newItem: MemoEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: MemoEntity, newItem: MemoEntity): Boolean {
        return oldItem == newItem
    }
}