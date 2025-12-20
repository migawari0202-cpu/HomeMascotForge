package com.example.mascotforge.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import widget.TimeWidgetProvider

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null &&
            (intent?.action == Intent.ACTION_SCREEN_ON || intent?.action == Intent.ACTION_USER_PRESENT)) {
            // 画面点灯/ユーザー復帰時にウィジェットを全更新
            TimeWidgetProvider.updateAllWidgets(context)
        }
    }
}
