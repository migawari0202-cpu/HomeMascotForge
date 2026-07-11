package com.example.mascotforge

import java.time.LocalTime

// =============================================================================
// 時間帯判定ユーティリティ（単一の定義元）
//
// SpeechContext / SpeechContextFactory / Weather などから共通利用する。
// hour は 0〜23 を想定。
// =============================================================================

/**
 * 時間帯スロットを返す。
 *
 * - midnight:  0:00〜4:59
 * - morning:   5:00〜11:59
 * - afternoon: 12:00〜16:59
 * - evening:   17:00〜20:59
 * - night:     21:00〜23:59
 */
fun getTimeSlot(hour: Int): String = when (hour) {
    in 5..11 -> "morning"
    in 12..16 -> "afternoon"
    in 17..20 -> "evening"
    in 21..23 -> "night"
    else -> "midnight" // 0..4
}

/**
 * 昼間（6:00〜17:59）かどうか。
 * 引数省略時は現在時刻の時を使う。
 */
fun isDaytime(hour: Int = LocalTime.now().hour): Boolean = hour in 6..17

/** 深夜（1〜4時） */
fun isLateNight(hour: Int): Boolean = hour in 1..4

/** 早朝（5〜6時） */
fun isEarlyMorning(hour: Int): Boolean = hour in 5..6

/** 通勤ラッシュ時間（平日の 7〜9時 または 17〜19時） */
fun isRushHour(hour: Int, isWeekend: Boolean): Boolean =
    !isWeekend && (hour in 7..9 || hour in 17..19)

/** お昼休み時間（11:30〜12時台） */
fun isLunchTime(hour: Int, minute: Int): Boolean =
    hour == 12 || (hour == 11 && minute >= 30)

/** おやつ時間（10時 または 15時） */
fun isSnackTime(hour: Int): Boolean = hour == 15 || hour == 10

/** もうすぐ日付が変わる（23:50〜） */
fun isAlmostMidnight(hour: Int, minute: Int): Boolean =
    hour == 23 && minute >= 50

/** ちょうど 00:00 */
fun isExactMidnight(hour: Int, minute: Int): Boolean =
    hour == 0 && minute == 0

/** 就寝時間帯（22時〜翌2時） */
fun isBedtime(hour: Int): Boolean = hour >= 22 || hour <= 2

/** 起床時間帯（6時〜8時） */
fun isWakeupTime(hour: Int): Boolean = hour in 6..8

/** 夕暮れ時（17時〜19時） */
fun isDusk(hour: Int): Boolean = hour in 17..19

/** 就寝時刻に近い（21時〜翌1時） */
fun isNearBedtime(hour: Int): Boolean = hour >= 21 || hour <= 1

/** 起床時刻に近い（6時〜8時）。[isWakeupTime] と同義。 */
fun isNearWakeup(hour: Int): Boolean = isWakeupTime(hour)
