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
import com.example.mascotforge.character.CharacterStateManager
import com.example.mascotforge.character.DynamicCharacter
import com.example.mascotforge.character.SafeCharacterLoader
import com.example.mascotforge.characters.CharacterRegistry
import com.example.mascotforge.speech.SpeechContextFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MVP用キャラ時計画面。
 * - 時計表示（1秒）
 * - キャラ画像 / セリフ（Widget と共通の選択キャラ）
 * - セリフは 2 分ごとに更新
 * - キャラゾーンのタッチで当たり判定（Widget と同様の touch 記録 + ON_TOUCH）
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
            handler.postDelayed(this, CLOCK_INTERVAL_MS)
        }
    }

    /** セリフ定期更新（2分固定） */
    private val updateSpeechRunnable = object : Runnable {
        override fun run() {
            loadCharacterAndSpeech()
            handler.postDelayed(this, SPEECH_INTERVAL_MS)
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

        applySafeAreaInsets(rootLayout, settingsButton, textClock)

        settingsButton.setOnClickListener {
            startActivity(Intent(this, CharacterSelectorActivity::class.java))
        }

        // キャラゾーンの当たり判定（Widget の ACTION_RECORD_TOUCH 相当）
        characterImage.isClickable = true
        characterImage.setOnClickListener {
            handleCharacterTouch()
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
        // 表示復帰時に即時読み込み + 2分周期を張り直す
        handler.removeCallbacks(updateSpeechRunnable)
        loadCharacterAndSpeech()
        handler.postDelayed(updateSpeechRunnable, SPEECH_INTERVAL_MS)
    }

    override fun onPause() {
        handler.removeCallbacks(updateClockRunnable)
        handler.removeCallbacks(updateSpeechRunnable)
        super.onPause()
    }

    /**
     * 現在選択中のキャラクター画像とセリフを読み込んで表示する。
     * Widget と同じグローバル選択を使う。
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

    /**
     * キャラ画像タップ時の当たり判定。
     * Widget の handleTouchAction と同じく:
     * 1. CharacterStateManager にタッチ記録
     * 2. DynamicCharacter なら ON_TOUCH ルール発火
     * 3. セリフ・画像を再読み込み
     */
    private fun handleCharacterTouch() {
        lifecycleScope.launch {
            try {
                val characterId = CharacterManager(this@ClockActivity).getCurrentCharacterId()

                val stateManager = CharacterStateManager(this@ClockActivity)
                val currentState = stateManager.getState(characterId)
                stateManager.recordTouch(currentState)
                stateManager.saveState(characterId, currentState)

                Log.d(
                    TAG,
                    "Touch recorded: character=$characterId, " +
                        "count=${currentState.touchCount}, today=${currentState.touchCountToday}, " +
                        "consecutive=${currentState.consecutiveTouchCount}"
                )

                val character = CharacterRegistry.getCharacterById(this@ClockActivity, characterId)
                if (character is DynamicCharacter) {
                    val ctx = SpeechContextFactory.create(this@ClockActivity, characterId)
                    character.triggerTouchRules(ctx)
                    Log.d(TAG, "ON_TOUCH rules fired for character $characterId")
                }

                // タッチ直後にセリフ・画像を反映
                val provider = CharacterManager(this@ClockActivity).getCurrentProvider()
                val contextLoader = SafeCharacterLoader(this@ClockActivity)
                val speechContext = contextLoader.getCurrentContext(characterId)

                val speech = provider.getSpeech(speechContext)
                speechText.text = speech.orEmpty()

                val bitmap = provider.getCharaImage(speechContext)
                characterImage.setImageBitmap(bitmap)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle character touch", t)
            }
        }
    }

    companion object {
        private const val TAG = "ClockActivity"
        private const val CLOCK_INTERVAL_MS = 1_000L
        private const val SPEECH_INTERVAL_MS = 2 * 60 * 1_000L // 2分固定
    }
}
