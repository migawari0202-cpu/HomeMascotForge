package com.example.mascotforge

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.example.mascotforge.character.SafeCharacterLoader
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MVP用キャラ時計画面。
 * 右上に日付・曜日と現在時刻を表示し、左上の設定ボタンからキャラ選択へ遷移する。
 * 下部にキャラクター画像とセリフを表示する。
 */
class ClockActivity : AppCompatActivity() {

    private lateinit var textClock: TextView
    private lateinit var characterImage: ImageView
    private lateinit var speechText: TextView

    /** 例: 2026/07/25(土)\n14:30:45 */
    private val dateTimeFormat = SimpleDateFormat("yyyy/MM/dd(E)\nHH:mm:ss", Locale.JAPANESE)
    private val handler = Handler(Looper.getMainLooper())
    private val updateClockRunnable = object : Runnable {
        override fun run() {
            textClock.text = dateTimeFormat.format(Date())
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clock)

        textClock = findViewById(R.id.text_clock)
        characterImage = findViewById(R.id.widget_character_image_compact)
        speechText = findViewById(R.id.widget_speech_compact)
        val settingsButton = findViewById<Button>(R.id.button_settings)
        val rootLayout = findViewById<View>(R.id.root_layout)

        // ノッチ / ステータスバー / ナビバーに合わせて左右の表示位置を動的調整
        applySafeAreaInsets(rootLayout, settingsButton, textClock)

        settingsButton.setOnClickListener {
            startActivity(Intent(this, CharacterSelectorActivity::class.java))
        }
    }

    /**
     * systemBars + displayCutout のインセットを読み取り、
     * 設定ボタンと時計を安全領域内に配置する。
     */
    private fun applySafeAreaInsets(
        root: View,
        settingsButton: Button,
        textClock: TextView
    ) {
        val baseMarginPx = (16 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )

            settingsButton.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = safe.top + baseMarginPx
                marginStart = safe.left + baseMarginPx
            }
            textClock.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = safe.top + baseMarginPx
                marginEnd = safe.right + baseMarginPx
            }

            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateClockRunnable)
        // CharacterSelectorActivity から戻った際にも選択結果を反映する
        loadCharacterAndSpeech()
    }

    override fun onPause() {
        handler.removeCallbacks(updateClockRunnable)
        super.onPause()
    }

    /**
     * 現在選択中のキャラクター画像とセリフを読み込んで表示する。
     * ClockActivity は widgetId を持たないため、MVP では Deprecated な
     * getCurrentProvider / getCurrentCharacterId を利用する。
     */
    private fun loadCharacterAndSpeech() {
        lifecycleScope.launch {
            try {
                val characterManager = CharacterManager(this@ClockActivity)
                val provider = characterManager.getCurrentProvider()
                val characterId = characterManager.getCurrentCharacterId()
                val contextLoader = SafeCharacterLoader(this@ClockActivity)
                val speechContext = contextLoader.getCurrentContext(characterId)

                val speech = provider.getSpeech(speechContext)
                speechText.text = speech.orEmpty()

                val bitmap = provider.getCharaImage(speechContext)
                characterImage.setImageBitmap(bitmap)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load character and speech", t)
            }
        }
    }

    companion object {
        private const val TAG = "ClockActivity"
    }
}
