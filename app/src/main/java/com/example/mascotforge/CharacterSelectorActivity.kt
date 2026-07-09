package com.example.mascotforge

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.mascotforge.characters.CharacterRegistry
import com.example.mascotforge.installer.CharacterInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.mascotforge.widget.TimeWidgetProvider
import com.example.mascotforge.widget.WidgetUpdateCoordinator

class CharacterSelectorActivity : AppCompatActivity() {

    private val selectedWidgets = mutableSetOf<Int>()
    private lateinit var widgetIds: IntArray
    private lateinit var adapter: CharacterAdapter
    private var factories: List<CharacterFactory> = emptyList()
    private var currentId: String = ""

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            installCharacterFromZip(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_selector)

        val listView = findViewById<ListView>(R.id.character_list)
        val headerLayout = findViewById<View>(R.id.header_layout)
        val rootLayout = findViewById<View>(R.id.root_layout)
        val manageButton = findViewById<Button>(R.id.manage_button)

        // ✅ 安全領域(WindowInsets)適用 — ノッチ/ナビバー対応
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 上部(ノッチ・ステータスバー) — XMLのpaddingTopと最大値を取る
            val currentTopPadding = headerLayout.paddingTop
            val requiredTop = systemBars.top.coerceAtLeast(currentTopPadding)
            headerLayout.setPadding(
                headerLayout.paddingLeft,
                requiredTop,
                headerLayout.paddingRight,
                headerLayout.paddingBottom
            )

            // 下部(ナビゲーションバー)
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                view.paddingBottom + systemBars.bottom
            )

            insets
        }

        // ウィジェットIDを取得
        val appWidgetManager = AppWidgetManager.getInstance(this)
        widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this, TimeWidgetProvider::class.java)
        )

                // CharacterFactoryの一覧を取得
        factories = CharacterRegistry.getFactories(this)
        currentId = CharacterPreferences.getSelectedCharacterId(this)

        // 🐛 デバッグログ追加
        Log.d("CharacterSelector", "=== キャラクター選択画面 ===")
        Log.d("CharacterSelector", "現在選択中: $currentId")
        Log.d("CharacterSelector", "利用可能なキャラ数: ${factories.size}")
        Log.d("CharacterSelector", "ウィジェット数: ${widgetIds.size}")
        factories.forEachIndexed { index, factory ->
            Log.d("CharacterSelector", "  [$index] ${factory.getCharacterId()} - ${factory.getDisplayName(this)}")
        }

        // カスタムアダプター
        adapter = CharacterAdapter(factories, currentId, widgetIds.size)
        listView.adapter = adapter

        // クリックで選択
        listView.setOnItemClickListener { _, view, position, _ ->
            val selectedFactory = factories[position]
            val newId = selectedFactory.getCharacterId()

            Log.d("CharacterSelector", "キャラクター選択: $newId")

            // ウィジェットが1個以下の場合は直接適用
            if (widgetIds.size <= 1) {
                if (widgetIds.isEmpty()) {
                    CharacterPreferences.setSelectedCharacterId(this, newId)
                } else {
                    CharacterPreferences.setCharacterIdForWidget(this, widgetIds[0], newId)
                }

                lifecycleScope.launch {
                    WidgetUpdateCoordinator(this@CharacterSelectorActivity).updateAllWidgets()
                }

                finish()
                return@setOnItemClickListener
            }

            // 複数ウィジェットの場合は選択画面を表示
            showWidgetSelectionForCharacter(view, newId)
        }

        // 長押しで削除
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val factory = factories[position]
            val entry = CharacterRegistry.getEntries(this@CharacterSelectorActivity)
                .find { it.factory.getCharacterId() == factory.getCharacterId() }

            if (entry != null && !entry.isBuiltIn) {
                showDeleteDialog(factory)
            } else {
                Toast.makeText(this@CharacterSelectorActivity, "内蔵キャラクターは削除できません", Toast.LENGTH_SHORT).show()
            }
            true
        }

        // キャラ管理ボタン
        manageButton.setOnClickListener {
            try {
                filePickerLauncher.launch("application/zip")
            } catch (e: Exception) {
                Log.e("CharacterSelector", "ファイルピッカー起動失敗", e)
                Toast.makeText(this, "ファイルピッカーを開けませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCharacterList()
    }

    private fun refreshCharacterList() {
        factories = CharacterRegistry.getFactories(this)
        currentId = CharacterPreferences.getSelectedCharacterId(this)
        adapter.factories = factories
        adapter.selectedId = currentId
        adapter.notifyDataSetChanged()
    }

    private fun installCharacterFromZip(uri: Uri) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("インストール中")
            .setMessage("キャラクターをインストールしています...")
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch {
            try {
                val installer = CharacterInstaller(this@CharacterSelectorActivity)
                val result = withContext(Dispatchers.IO) {
                    installer.installFromZip(uri)
                }

                progressDialog.dismiss()

                result.onSuccess { info ->
                    runOnUiThread {
                        refreshCharacterList()
                        Toast.makeText(
                            this@CharacterSelectorActivity,
                            "「${info.name}」をインストールしました",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                result.onFailure { e ->
                    runOnUiThread {
                        val message = e.message ?: "インストールに失敗しました"
                        AlertDialog.Builder(this@CharacterSelectorActivity)
                            .setTitle("インストール失敗")
                            .setMessage(message)
                            .setPositiveButton("OK") { d, _ -> d.dismiss() }
                            .show()
                    }
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                runOnUiThread {
                    Toast.makeText(
                        this@CharacterSelectorActivity,
                        "エラー: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showDeleteDialog(factory: CharacterFactory) {
        val charId = factory.getCharacterId()
        val charName = factory.getDisplayName(this)

        AlertDialog.Builder(this)
            .setTitle("キャラクター削除")
            .setMessage("「$charName」を削除してもよろしいですか？\nこの操作は元に戻せません。")
            .setPositiveButton("削除") { _, _ ->
                deleteCharacter(charId)
            }
            .setNegativeButton("キャンセル") { d, _ -> d.dismiss() }
            .show()
    }

    private fun deleteCharacter(charId: String) {
        lifecycleScope.launch {
            try {
                val installer = CharacterInstaller(this@CharacterSelectorActivity)
                val success = withContext(Dispatchers.IO) {
                    installer.uninstall(charId)
                }

                if (success) {
                    runOnUiThread {
                        refreshCharacterList()
                        Toast.makeText(
                            this@CharacterSelectorActivity,
                            "削除しました",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@CharacterSelectorActivity,
                            "削除に失敗しました",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@CharacterSelectorActivity,
                        "エラー: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showWidgetSelectionForCharacter(itemView: View, characterId: String) {
        // すでにウィジェット選択UIが表示されているか確認
        val existingLayout = itemView.findViewById<View>(R.id.widget_selection_container)
        if (existingLayout != null) {
            // 既に表示されている場合は閉じる
            (itemView as ViewGroup).removeView(existingLayout)
            return
        }

        // ウィジェット選択UIを追加
        val widgetSelectionLayout = layoutInflater.inflate(
            R.layout.widget_selection_inline,
            itemView as ViewGroup,
            false
        )

        val characterManager = CharacterManager(this)
        val container = widgetSelectionLayout.findViewById<ViewGroup>(R.id.widget_checkboxes)

        // ウィジェットごとにチェックボックスを作成
        widgetIds.forEach { widgetId ->
            val checkBox = CheckBox(this).apply {
                val currentCharId = characterManager.getCharacterIdForWidget(widgetId)
                val currentCharName = CharacterRegistry.getFactories(this@CharacterSelectorActivity)
                    .find { it.getCharacterId() == currentCharId }
                    ?.getDisplayName(this@CharacterSelectorActivity) ?: "不明"

                text = "ウィジェット $widgetId (現在: $currentCharName)"
                textSize = 14f
                setPadding(16, 8, 16, 8)

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedWidgets.add(widgetId)
                    } else {
                        selectedWidgets.remove(widgetId)
                    }
                }
            }
            container.addView(checkBox)
        }

        // 適用ボタン
        val applyButton = widgetSelectionLayout.findViewById<Button>(R.id.apply_button)
        applyButton.setOnClickListener {
            if (selectedWidgets.isNotEmpty()) {
                CharacterPreferences.setCharacterIdForWidgets(
                    this,
                    selectedWidgets.toList(),
                    characterId
                )

                lifecycleScope.launch {
                    Log.d("CharacterSelector", "ウィジェット更新開始: $selectedWidgets")
                    WidgetUpdateCoordinator(this@CharacterSelectorActivity).updateAllWidgets()
                    Log.d("CharacterSelector", "ウィジェット更新完了")
                }

                selectedWidgets.clear()
                finish()
            }
        }

        // キャンセルボタン
        val cancelButton = widgetSelectionLayout.findViewById<Button>(R.id.cancel_button)
        cancelButton.setOnClickListener {
            selectedWidgets.clear()
            (itemView as ViewGroup).removeView(widgetSelectionLayout)
        }

        itemView.addView(widgetSelectionLayout)
    }

    /**
     * キャラクター一覧用のカスタムアダプター
     */
        private inner class CharacterAdapter(
        var factories: List<CharacterFactory>,
        var selectedId: String,
        private val widgetCount: Int
    ) : ArrayAdapter<CharacterFactory>(
        this@CharacterSelectorActivity,
        R.layout.character_list_item,
        factories
    ) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.character_list_item, parent, false)

            val factory = factories[position]

            // サムネイル
            val thumbnailView = view.findViewById<ImageView>(R.id.character_thumbnail)
            val thumbnail = factory.getThumbnail(context)
            if (thumbnail != null) {
                thumbnailView.setImageDrawable(thumbnail)
            } else {
                thumbnailView.setImageDrawable(null)
                thumbnailView.setBackgroundColor(0xFFE0E0E0.toInt())
            }

            // チェックマーク（ウィジェットが1個以下の場合のみ表示）
            val checkMark = view.findViewById<TextView>(R.id.check_mark)
            if (widgetCount <= 1) {
                checkMark.visibility = if (factory.getCharacterId() == selectedId) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }
            } else {
                checkMark.visibility = View.GONE
            }

            // 名前
            view.findViewById<TextView>(R.id.character_name)
                .text = factory.getDisplayName(context)

            // 説明
            val descView = view.findViewById<TextView>(R.id.character_description)
            val description = factory.getDescription(context)
            descView.text = description
            descView.visibility = if (description.isNotEmpty()) View.VISIBLE else View.GONE

            // 作者
            val authorView = view.findViewById<TextView>(R.id.character_author)
            val author = factory.getAuthor(context)
            if (author.isNotEmpty()) {
                authorView.text = "作者: $author"
                authorView.visibility = View.VISIBLE
            } else {
                authorView.visibility = View.GONE
            }

            return view
        }
    }
}