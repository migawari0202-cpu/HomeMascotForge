package com.example.mascotforge

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
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
import com.example.mascotforge.installer.CommonInstaller
import com.example.mascotforge.installer.InstallResult
import com.example.mascotforge.widget.WidgetUpdateCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * キャラクター選択画面。
 *
 * MVP: Widget / Activity 共通のグローバル選択のみ。
 * 選択結果は [CharacterPreferences.setSelectedCharacterId] に保存し、
 * 配置中の Widget があればまとめて更新する。
 */
class CharacterSelectorActivity : AppCompatActivity() {

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

        // 安全領域(WindowInsets)適用 — ノッチ/ナビバー対応
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val currentTopPadding = headerLayout.paddingTop
            val requiredTop = systemBars.top.coerceAtLeast(currentTopPadding)
            headerLayout.setPadding(
                headerLayout.paddingLeft,
                requiredTop,
                headerLayout.paddingRight,
                headerLayout.paddingBottom
            )

            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                view.paddingBottom + systemBars.bottom
            )

            insets
        }

        factories = CharacterRegistry.getFactories(this)
        currentId = CharacterPreferences.getSelectedCharacterId(this)

        Log.d("CharacterSelector", "=== キャラクター選択画面 ===")
        Log.d("CharacterSelector", "現在選択中: $currentId")
        Log.d("CharacterSelector", "利用可能なキャラ数: ${factories.size}")
        factories.forEachIndexed { index, factory ->
            Log.d(
                "CharacterSelector",
                "  [$index] ${factory.getCharacterId()} - ${factory.getDisplayName(this)}"
            )
        }

        adapter = CharacterAdapter(factories, currentId)
        listView.adapter = adapter

        // クリックで共通選択を適用（Widget / Activity とも同じ ID）
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedFactory = factories[position]
            val newId = selectedFactory.getCharacterId()

            Log.d("CharacterSelector", "キャラクター選択(共通): $newId")
            CharacterPreferences.setSelectedCharacterId(this, newId)

            lifecycleScope.launch {
                WidgetUpdateCoordinator(this@CharacterSelectorActivity).updateAllWidgets()
            }

            finish()
        }

        // 長押しで削除
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val factory = factories[position]
            val entry = CharacterRegistry.getEntries(this@CharacterSelectorActivity)
                .find { it.factory.getCharacterId() == factory.getCharacterId() }

            if (entry != null && !entry.isBuiltIn) {
                showDeleteDialog(factory)
            } else {
                Toast.makeText(
                    this@CharacterSelectorActivity,
                    "内蔵キャラクターは削除できません",
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        }

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
            .setMessage("インストールしています...")
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    CommonInstaller(this@CharacterSelectorActivity).install(uri)
                }

                progressDialog.dismiss()

                result.onSuccess { installResult ->
                    val message = when (installResult) {
                        is InstallResult.Character ->
                            "「${installResult.info.name}」をインストールしました"
                        is InstallResult.Shell ->
                            "Shell「${installResult.info.name}」をインストールしました"
                        is InstallResult.Both ->
                            "「${installResult.character.name}」と Shell「${installResult.shell.name}」をインストールしました"
                    }
                    runOnUiThread {
                        refreshCharacterList()
                        Toast.makeText(this@CharacterSelectorActivity, message, Toast.LENGTH_SHORT).show()
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

    private inner class CharacterAdapter(
        var factories: List<CharacterFactory>,
        var selectedId: String
    ) : ArrayAdapter<CharacterFactory>(
        this@CharacterSelectorActivity,
        R.layout.character_list_item,
        factories
    ) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.character_list_item, parent, false)

            val factory = factories[position]

            val thumbnailView = view.findViewById<ImageView>(R.id.character_thumbnail)
            val thumbnail = factory.getThumbnail(context)
            if (thumbnail != null) {
                thumbnailView.setImageDrawable(thumbnail)
            } else {
                thumbnailView.setImageDrawable(null)
                thumbnailView.setBackgroundColor(0xFFE0E0E0.toInt())
            }

            val checkMark = view.findViewById<TextView>(R.id.check_mark)
            checkMark.visibility = if (factory.getCharacterId() == selectedId) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }

            view.findViewById<TextView>(R.id.character_name)
                .text = factory.getDisplayName(context)

            val descView = view.findViewById<TextView>(R.id.character_description)
            val description = factory.getDescription(context)
            descView.text = description
            descView.visibility = if (description.isNotEmpty()) View.VISIBLE else View.GONE

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
