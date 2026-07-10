package com.example.mascotforge.speech

import com.example.mascotforge.character.CharacterState

private fun isDaytime(): Boolean {
    val hour = java.time.LocalTime.now().hour
    return hour in 6..17
}

/**
 * エンジンが「必ず知ってる」情報だけ渡す
 * → キャラパックはこれを元に「自分で拡張」
 */
data class SpeechContext(
    // === 時刻系 ===
    val timeSlot: String,        // "morning", "afternoon", "evening", "night", "midnight"
    val hour: Int,               // 0〜23
    val minute: Int,             // 0〜59

    // === 日付系 ===
    val month: Int,              // 1〜12
    val day: Int,                // 1〜31
    val dayOfWeek: String,       // "月曜日", "火曜日" など
    val isWeekend: Boolean,      // 土日か？
    val isHoliday: Boolean,      // 祝日判定
    val holidayName: String?,    // "元日", "こどもの日" など

    // === 季節・特殊日 ===
    val season: String,          // "春", "梅雨", "夏", "秋", "冬"
    val isSpecialDay: Boolean,   // バレンタイン、ハロウィンなど
    val specialDayName: String?, // "バレンタイン", "クリスマス" など

    // === 天気系 ===
    val weatherEmoji: String,    // "☀️", "☁️", "🌧️" など
    val weatherCode: String,     // "晴れ", "曇り", "雨" など
    val temperature: Int,        // 気温（℃）
    val temperatureFeeling: String, // "暑い", "ちょうどいい", "寒い"
    val humidity: Int?,          // 湿度%、取れない場合null

    // === デバイス状態 ===
    val batteryLevel: Int,       // バッテリー残量（%）
    val batteryStatus: String,   // "充電中", "通常", "省電力モード"
    val isCharging: Boolean,
    val isLowBattery: Boolean,   // 20%以下など

    // === アプリ使用状況 ===
    val launchCount: Int,        // 今日何回目の起動か
    val lastLaunchHoursAgo: Int?, // 前回起動から何時間経過、初回null
    val isFirstLaunchToday: Boolean,
    val consecutiveDays: Int,    // 連続起動日数

    // === ユーザー情報 ===
    val userName: String = "ユーザー",
    val userGender: String? = null, // "male", "female", "other", null

    // === その他コンテキスト ===
    val isNearBedtime: Boolean,  // 就寝時刻に近い？
    val isNearWakeup: Boolean,   // 起床時刻に近い？
    val moonPhase: String?,      // "新月", "満月" など、取れない場合null（基本使わん）

    //タッチ関連
    val wasTouched: Boolean,           // 今タッチされた
    val touchCount: Int,               // 累計タッチ回数
    val touchCountToday: Int,          // 今日のタッチ回数
    val lastTouchMinutesAgo: Int,      // 最後にタッチされてから何分
    val consecutiveTouchCount: Int,    // 短時間に続けてタッチされた回数
    val pettingLevel: Int,             // 撫で段階 0..3
    val isBeingPetted: Boolean,        // 短時間の連続タッチ中

    // === キャラクター状態（追加） ===
    val characterState: CharacterState  // カスタム変数を含む状態

) {
    /**
     * キャラ作成者向けヘルパー関数
     */

    // ===== カスタム変数アクセス =====

    /**
     * カスタム変数を名前で取得
     */
    fun getCustomVar(name: String): Int = characterState.getCustomVar(name)

    /**
     * カスタム変数を名前で設定
     */
    fun setCustomVar(name: String, value: Int) = characterState.setCustomVar(name, value)

    /**
     * カスタム変数を名前で調整（+5, -3, *2, /2, =50など）
     */
    fun adjustCustomVar(name: String, operation: String) = characterState.adjustCustomVar(name, operation)

    // ===== 挨拶関連 =====

    /** 挨拶すべきタイミングか */
    fun shouldGreet(): Boolean = isFirstLaunchToday || (lastLaunchHoursAgo ?: 0) >= 6

    /** おかえり挨拶か（短時間で戻ってきた） */
    fun isWelcomeBack(): Boolean = (lastLaunchHoursAgo ?: 0) in 1..3

    /** 久しぶり挨拶か */
    fun isLongTimeNoSee(): Boolean = (lastLaunchHoursAgo ?: 0) >= 24

    /** 超久しぶり（1週間以上） */
    fun isVeryLongTimeNoSee(): Boolean = (lastLaunchHoursAgo ?: 0) >= 168


    // ===== 時間帯判定 =====

    /** 深夜（1〜4時） */
    fun isLateNight(): Boolean = hour in 1..4

    /** 早朝（5〜6時） */
    fun isEarlyMorning(): Boolean = hour in 5..6

    /** 通勤ラッシュ時間 */
    fun isRushHour(): Boolean = !isWeekend && (hour in 7..9 || hour in 17..19)

    /** お昼休み時間 */
    fun isLunchTime(): Boolean = hour == 12 || (hour == 11 && minute >= 30)

    /** おやつ時間 */
    fun isSnackTime(): Boolean = hour == 15 || hour == 10

    /** もうすぐ日付が変わる */
    fun isAlmostMidnight(): Boolean = hour == 23 && minute >= 50

    /** ちょうど00:00 */
    fun isExactMidnight(): Boolean = hour == 0 && minute == 0

    /** 就寝時間帯（22時〜翌2時） */
    fun isBedtime(): Boolean = hour >= 22 || hour <= 2

    /** 起床時間帯（6時〜8時） */
    fun isWakeupTime(): Boolean = hour in 6..8

    /** 夕暮れ時（17時〜19時） */
    fun isDusk(): Boolean = hour in 17..19


    // ===== 季節イベント =====

    /** お正月（1/1〜1/3） */
    fun isNewYear(): Boolean = month == 1 && day in 1..3

    /** クリスマスシーズン（12/20〜） */
    fun isChristmasSeason(): Boolean = month == 12 && day >= 20

    /** クリスマス当日 */
    fun isChristmas(): Boolean = month == 12 && day == 25

    /** クリスマスイブ */
    fun isChristmasEve(): Boolean = month == 12 && day == 24

    /** ハロウィン */
    fun isHalloween(): Boolean = month == 10 && day == 31

    /** バレンタイン */
    fun isValentine(): Boolean = month == 2 && day == 14

    /** ホワイトデー */
    fun isWhiteDay(): Boolean = month == 3 && day == 14

    /** 七夕 */
    fun isTanabata(): Boolean = month == 7 && day == 7

    /** 月末 */
    fun isEndOfMonth(): Boolean = day >= 28

    /** 月初 */
    fun isBeginningOfMonth(): Boolean = day <= 3

    /** 大晦日 */
    fun isNewYearsEve(): Boolean = month == 12 && day == 31

    /** 節分（2/3） */
    fun isSetsubun(): Boolean = month == 2 && day == 3

    /** ひな祭り（3/3） */
    fun isHinamatsuri(): Boolean = month == 3 && day == 3

    /** こどもの日（5/5） */
    fun isChildrensDay(): Boolean = month == 5 && day == 5


    // ===== 天気・体調 =====

    /** 体調を気遣うべきか */
    fun shouldCareAboutHealth(): Boolean =
        temperatureFeeling in listOf("暑い", "寒い") || (humidity ?: 0) >= 80

    /** 熱中症警戒レベル（湿度データがない場合は保守的にリスクありとみなす） */
    fun isHeatStrokeRisk(): Boolean = temperature >= 30 && (humidity == null || humidity >= 60)

    /** 雨が降っている */
    fun isRaining(): Boolean = weatherCode.contains("雨") || weatherEmoji.contains("🌧")

    /** 雪が降っている */
    fun isSnowing(): Boolean = weatherCode.contains("雪") || weatherEmoji.contains("⛄")

    /** 快晴 */
    fun isSunny(): Boolean =
        (weatherCode == "晴れ" && isDaytime()) || weatherEmoji == "☀️"

    /** 曇り */
    fun isCloudy(): Boolean = weatherCode.contains("曇") || weatherEmoji.contains("☁")

    /** 嵐・台風（雷雨も含む） */
    fun isStormy(): Boolean = weatherCode.contains("嵐") || weatherCode.contains("台風") || weatherCode == "雷雨"

    /** 真夏日（30度以上） */
    fun isHotDay(): Boolean = temperature >= 30

    /** 猛暑日（35度以上） */
    fun isExtremelyHotDay(): Boolean = temperature >= 35

    /** 真冬日（0度以下） */
    fun isFreezingDay(): Boolean = temperature <= 0

    /** 寒い（5度以下） */
    fun isColdDay(): Boolean = temperature <= 5

    /** 快適な気温（18〜25度） */
    fun isComfortableTemperature(): Boolean = temperature in 18..25

    /** ジメジメ（湿度70%以上） */
    fun isHumid(): Boolean = (humidity ?: 0) >= 70


    // ===== バッテリー =====

    /** バッテリー警告すべきか */
    fun shouldWarnBattery(): Boolean = isLowBattery && !isCharging

    /** バッテリー満タン */
    fun isFullyCharged(): Boolean = batteryLevel >= 95 && isCharging

    /** バッテリー危機的 */
    fun isCriticalBattery(): Boolean = batteryLevel <= 10

    /** バッテリー余裕あり */
    fun isHealthyBattery(): Boolean = batteryLevel >= 50


    // ===== モチベーション・褒める =====

    /** 継続を褒めるべきか */
    fun shouldPraiseContinuation(): Boolean = consecutiveDays >= 3

    /** 記念日レベルの継続 */
    fun isMilestoneContinuation(): Boolean =
        consecutiveDays in listOf(7, 14, 30, 50, 100, 365)

    /** 頻繁に使ってくれている */
    fun isFrequentUser(): Boolean = launchCount >= 5

    /** 今日初めての起動 */
    fun isFirstToday(): Boolean = launchCount == 1


    // ===== 曜日・週関連 =====

    /** 月曜の朝（ブルーマンデー） */
    fun isMondayMorning(): Boolean =
        dayOfWeek == "月曜日" && timeSlot == "morning"

    /** 金曜の夜（華金） */
    fun isFridayNight(): Boolean =
        dayOfWeek == "金曜日" && timeSlot in listOf("evening", "night")

    /** 土曜の朝 */
    fun isSaturdayMorning(): Boolean =
        dayOfWeek == "土曜日" && timeSlot == "morning"

    /** 日曜の夜（サザエさん症候群） */
    fun isSundayNight(): Boolean =
        dayOfWeek == "日曜日" && timeSlot in listOf("evening", "night")

    /** 平日 */
    fun isWeekday(): Boolean = !isWeekend

    /** 平日の朝 */
    fun isWeekdayMorning(): Boolean = isWeekday() && timeSlot == "morning"

    /** 平日の夜 */
    fun isWeekdayNight(): Boolean = isWeekday() && timeSlot in listOf("evening", "night")


    // ===== その他 =====

    /** 満月の夜 */
    fun isFullMoonNight(): Boolean =
        moonPhase == "満月" && timeSlot in listOf("night", "midnight")

    /** 新月 */
    fun isNewMoon(): Boolean = moonPhase == "新月"


    // ===== 条件チェック用ヘルパー（テキストファイル用） =====

    /**
     * 時間範囲チェック (例: "7-9" → 7時〜9時)
     */
    fun isHourInRange(range: String): Boolean {
        return when {
            range.contains("-") -> {
                val (start, end) = range.split("-").map { it.toInt() }
                if (start <= end) hour in start..end
                else hour >= start || hour <= end  // 日付またぎ (例: "22-6")
            }
            else -> hour == range.toIntOrNull()
        }
    }

        /**
     * 複合条件の簡易チェック（テキストファイルパーサー用）
     *
     * カスタム変数は以下の2形式で指定可能:
     * - "customVars[favorability]" — 従来の明示形式
     * - "favorability" — 変数名を直接指定（customVarsマップ内に存在すれば一致）
     *
     * boolean型カスタム変数:
     * - value が "true" / "false" の場合、整数値 1 / 0 にマッピングして比較
     * - 例: customVars["is_happy"] = 1 のとき matches("is_happy", "true") → true
     */
    fun matches(key: String, value: String, customValues: Map<String, String> = emptyMap()): Boolean {
        return when (key) {
            // 時間系
            "hour" -> isHourInRange(value)
            "timeSlot" -> timeSlot == value
            "minute" -> minute == value.toIntOrNull()

            // 日付系
            "month" -> month == value.toIntOrNull()
            "day" -> {
                if (value.contains("-")) {
                    val (start, end) = value.split("-").map { it.toInt() }
                    day in start..end
                } else {
                    day == value.toIntOrNull()
                }
            }
            "dayOfWeek" -> dayOfWeek == value
            "isWeekend" -> isWeekend == value.toBoolean()
            "isHoliday" -> isHoliday == value.toBoolean()

            // 季節・イベント
            "season" -> season == value
            "isSpecialDay" -> isSpecialDay == value.toBoolean()
            "isChristmas" -> isChristmas() == value.toBoolean()
            "isNewYear" -> isNewYear() == value.toBoolean()
            "isValentine" -> isValentine() == value.toBoolean()
            "isHalloween" -> isHalloween() == value.toBoolean()


            // 天気
            "weatherCode" -> {
                // 英語・日本語両対応
                val japaneseMap = mapOf(
                    "sunny" to "晴れ",
                    "clear" to "晴れ",
                    "rain" to "雨",
                    "cloudy" to "曇り",
                    "snow" to "雪",
                    "wind" to "強風",
                    "hail" to "あられ"
                )
                weatherCode == value || japaneseMap[value] == weatherCode
            }
            "weatherEmoji" -> weatherEmoji == value
            "temperature" -> checkNumberCondition(temperature, value)
            "temperatureFeeling" -> temperatureFeeling == value
            "humidity" -> humidity?.let { checkNumberCondition(it, value) } ?: false
            "isRaining" -> isRaining() == value.toBoolean()
            "isSnowing" -> isSnowing() == value.toBoolean()
            "isSunny" -> isSunny() == value.toBoolean()
            "isCloudy" -> isCloudy() == value.toBoolean()

            // バッテリー
            "batteryLevel" -> checkNumberCondition(batteryLevel, value)
            "isLowBattery" -> isLowBattery == value.toBoolean()
            "isCharging" -> isCharging == value.toBoolean()
            "isCriticalBattery" -> isCriticalBattery() == value.toBoolean()

            // 使用状況
            "launchCount" -> checkNumberCondition(launchCount, value)
            "isFirstLaunchToday" -> isFirstLaunchToday == value.toBoolean()
            "wasTouched" -> wasTouched == value.toBoolean()
            "touchCount" -> checkNumberCondition(touchCount, value)
            "touchCountToday" -> checkNumberCondition(touchCountToday, value)
            "lastTouchMinutesAgo" -> checkNumberCondition(lastTouchMinutesAgo, value)
            "consecutiveTouchCount" -> checkNumberCondition(consecutiveTouchCount, value)
            "pettingLevel" -> checkNumberCondition(pettingLevel, value)
            "isBeingPetted" -> isBeingPetted == value.toBoolean()
            "lastLaunchHoursAgo" -> lastLaunchHoursAgo?.let {
                checkNumberCondition(it, value)
            } ?: false
            "consecutiveDays" -> checkNumberCondition(consecutiveDays, value)
            "isLongTimeNoSee" -> isLongTimeNoSee() == value.toBoolean()
            "isWelcomeBack" -> isWelcomeBack() == value.toBoolean()

            // 時間帯ヘルパー
            "isLateNight" -> isLateNight() == value.toBoolean()
            "isEarlyMorning" -> isEarlyMorning() == value.toBoolean()
            "isRushHour" -> isRushHour() == value.toBoolean()
            "isLunchTime" -> isLunchTime() == value.toBoolean()
            "isMondayMorning" -> isMondayMorning() == value.toBoolean()
            "isFridayNight" -> isFridayNight() == value.toBoolean()
            "isSundayNight" -> isSundayNight() == value.toBoolean()

            // カスタム変数 — customVars[favorability] 形式 または 直接変数名
            // boolean条件 "true"/"false" は自動的に整数 1/0 にマッピング
            else -> {
                val isCustomVarFormat = key.startsWith("customVars[") && key.endsWith("]")
                val varName = if (isCustomVarFormat) {
                    key.substring(11, key.length - 1)
                } else if (characterState.customVars.containsKey(key) || customValues.containsKey(key)) {
                    key
                } else {
                    null
                }

                if (varName != null && varName.isNotEmpty()) {
                    val strVal = customValues[varName] ?: getCustomVar(varName).toString()
                    val isBooleanCondition = value == "true" || value == "false"
                    if (isBooleanCondition) {
                        val rawVal = getCustomVar(varName)
                        val expectedInt = if (value == "true") 1 else 0
                        rawVal == expectedInt
                    } else {
                        val isNumericCondition = value.startsWith(">=") ||
                                value.startsWith("<=") ||
                                value.startsWith(">") ||
                                value.startsWith("<") ||
                                (value.contains("-") && !value.startsWith("-"))

                        if (isNumericCondition || value.toIntOrNull() != null) {
                            val actualInt = strVal.toIntOrNull() ?: 0
                            checkNumberCondition(actualInt, value)
                        } else {
                            strVal == value
                        }
                    }
                } else {
                    false
                }
            }
        }
    }

    /**
     * 数値条件チェック (>=30, <=5, >10, <20 など)
     */
    private fun checkNumberCondition(actual: Int, condition: String): Boolean {
        return when {
            condition.startsWith(">=") -> actual >= condition.substring(2).toInt()
            condition.startsWith("<=") -> actual <= condition.substring(2).toInt()
            condition.startsWith(">") -> actual > condition.substring(1).toInt()
            condition.startsWith("<") -> actual < condition.substring(1).toInt()
            condition.contains("-") -> {
                val (start, end) = condition.split("-").map { it.toInt() }
                actual in start..end
            }
            else -> actual == condition.toIntOrNull()
        }
    }

    /**
     * 複数条件をまとめてチェック
     */
    fun matchesAll(conditions: Map<String, String>, customValues: Map<String, String> = emptyMap()): Boolean {
        return conditions.all { (key, value) -> matches(key, value, customValues) }
    }

    /**
     * いずれかの条件に合致するか
     */
    fun matchesAny(conditions: Map<String, String>, customValues: Map<String, String> = emptyMap()): Boolean {
        return conditions.any { (key, value) -> matches(key, value, customValues) }
    }


    // ===== デバッグ・ログ用 =====

    /**
     * 現在の状況を文字列で出力（デバッグ用）
     */
    fun toDebugString(): String = """
        |=== SpeechContext ===
        |時刻: $hour:${minute.toString().padStart(2, '0')} ($timeSlot)
        |日付: $month/$day ($dayOfWeek) ${if (isWeekend) "週末" else "平日"}
        |季節: $season ${if (isHoliday) "($holidayName)" else ""}
        |天気: $weatherEmoji $weatherCode ${temperature}℃ (${temperatureFeeling})
        |湿度: ${humidity ?: "不明"}%
        |バッテリー: $batteryLevel% ($batteryStatus)
        |起動: 今日${launchCount}回目 / 連続${consecutiveDays}日
        |前回: ${lastLaunchHoursAgo ?: "初回"}時間前
        |特殊: ${if (isSpecialDay) specialDayName else "なし"}
        |カスタム変数: ${characterState.customVars.entries.take(5).joinToString { "${it.key}=${it.value}" }}
    """.trimMargin()
}
