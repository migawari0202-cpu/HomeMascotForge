package com.example.mascotforge.character

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache

/**
 * キャラクター画像のメモリキャッシュ
 *
 * - LruCache ベース：合計 4MB を超えると古いものから自動削除
 * - キーは "$charId:$imagePath" で感情ごとに個別キャッシュ
 * - isRecycled チェックで破棄済みBitmapの返却を防ぐ
 */
object CharacterBitmapCache {

    private const val TAG = "CharacterBitmapCache"
    private const val MAX_SIZE_BYTES = 4 * 1024 * 1024 // 4MB

    private val cache = object : LruCache<String, Bitmap>(MAX_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? {
        val bitmap = cache.get(key) ?: return null
        if (bitmap.isRecycled) {
            cache.remove(key)
            Log.d(TAG, "Removed recycled bitmap: $key")
            return null
        }
        Log.v(TAG, "Cache HIT: $key")
        return bitmap
    }

    fun put(key: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            cache.put(key, bitmap)
            Log.v(TAG, "Cached: $key (${bitmap.byteCount / 1024}KB, total=${cache.size() / 1024}KB)")
        }
    }

    fun clear() {
        cache.evictAll()
        Log.d(TAG, "Cache cleared")
    }

    fun remove(key: String) {
        cache.remove(key)
    }
}
