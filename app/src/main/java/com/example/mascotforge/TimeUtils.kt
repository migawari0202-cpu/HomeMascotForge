package com.example.mascotforge

/**
 * 現在時刻が昼間（6:00〜17:59）かどうかを判定する
 */
fun isDaytime(): Boolean {
    val hour = java.time.LocalTime.now().hour
    return hour in 6..17
}