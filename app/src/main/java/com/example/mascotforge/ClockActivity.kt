package com.example.mascotforge

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MVP用キャラ時計画面。
 * 中央に現在時刻を表示し、設定ボタンからキャラ選択へ遷移する。
 */
class ClockActivity : AppCompatActivity() {

    private lateinit var textClock: TextView
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private val updateClockRunnable = object : Runnable {
        override fun run() {
            textClock.text = timeFormat.format(Date())
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clock)

        textClock = findViewById(R.id.text_clock)
        val settingsButton = findViewById<Button>(R.id.button_settings)
        val rootLayout = findViewById<View>(R.id.root_layout)

        // ノッチ / ステータスバー / ナビバーに合わせて設定ボタン位置を動的調整
        applySafeAreaInsets(rootLayout, settingsButton)

        settingsButton.setOnClickListener {
            startActivity(Intent(this, CharacterSelectorActivity::class.java))
        }
    }

    /**
     * systemBars + displayCutout のインセットを読み取り、
     * 設定ボタンを安全領域内に配置する。
     */
    private fun applySafeAreaInsets(root: View, settingsButton: Button) {
        val baseMarginPx = (16 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )

            settingsButton.updateLayoutParams<FrameLayout.LayoutParams> {
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
    }

    override fun onPause() {
        handler.removeCallbacks(updateClockRunnable)
        super.onPause()
    }
}
