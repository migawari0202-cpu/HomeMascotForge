package com.example.mascotforge.widget.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * BatteryManager の CQS 動作とキャッシュ更新を検証する単体テスト
 */
class BatteryManagerTest {

    private lateinit var batteryManager: BatteryManager

    @Before
    fun setUp() {
        batteryManager = BatteryManager()
    }

    @Test
    fun testInitialValues() {
        // キャッシュ未初期化時はデフォルト値を返し、充電中フラグは false になること
        assertEquals(BatteryManager.FALLBACK_BATTERY_LEVEL, batteryManager.getBatteryLevel())
        assertFalse(batteryManager.isCharging())
    }

    @Test
    fun testUpdateBatteryInfo() {
        // バッテリー情報を更新（80%, 充電中）
        batteryManager.updateBatteryInfo(80, true)

        assertEquals(80, batteryManager.getBatteryLevel())
        assertTrue(batteryManager.isCharging())

        // バッテリー情報を更新（45%, 通常）
        batteryManager.updateBatteryInfo(45, false)

        assertEquals(45, batteryManager.getBatteryLevel())
        assertFalse(batteryManager.isCharging())
    }

    @Test
    fun testClearCache() {
        // 値を設定
        batteryManager.updateBatteryInfo(90, true)
        assertEquals(90, batteryManager.getBatteryLevel())
        assertTrue(batteryManager.isCharging())

        // キャッシュクリアを実行
        batteryManager.clearCache()

        // クリア後はデフォルト値に戻ること
        assertEquals(BatteryManager.FALLBACK_BATTERY_LEVEL, batteryManager.getBatteryLevel())
        assertFalse(batteryManager.isCharging())
    }
}
