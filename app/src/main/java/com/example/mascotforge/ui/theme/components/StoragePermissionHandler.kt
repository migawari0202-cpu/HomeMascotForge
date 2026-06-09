package com.example.mascotforge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ストレージ権限ハンドラー
 *
 * Android バージョンごとに必要な権限が異なるため、
 * 適切な権限をリクエストする
 */
class StoragePermissionHandler(
    private val activity: AppCompatActivity,
    private val onDenied: () -> Unit
) {

    companion object {
        private const val TAG = "StoragePermission"
    }

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    private val _permissionEvent = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1)
    val permissionEvent: SharedFlow<Boolean> = _permissionEvent.asSharedFlow()

    private fun emitEvent(granted: Boolean) {
        val success = _permissionEvent.tryEmit(granted)
        if (!success) {
            Log.w(TAG, "Failed to emit permission event (granted=$granted). Buffer might be full.")
        }
    }

    /**
     * すべての必要な権限が許可されているか
     */
    fun hasPermissions(): Boolean {
        return hasAllPermissions(getRequiredPermissions())
    }

    /**
     * 初期化（onCreate で呼ぶ）
     */
    fun init() {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }

            if (allGranted) {
                emitEvent(true)
            } else {
                // 権限拒否時の処理
                handlePermissionDenied(permissions)
                emitEvent(false)
            }
        }
    }

    /**
     * 必要な権限を確認してリクエスト
     */
    fun checkAndRequestPermissions() {
        val requiredPermissions = getRequiredPermissions()

        if (hasAllPermissions(requiredPermissions)) {
            // すでに権限がある
            emitEvent(true)
        } else {
            // 権限リクエスト
            if (shouldShowRationale(requiredPermissions)) {
                // 説明ダイアログを表示してからリクエスト
                showPermissionRationaleDialog {
                    requestPermissions(requiredPermissions)
                }
            } else {
                // 直接リクエスト
                requestPermissions(requiredPermissions)
            }
        }
    }

    /**
     * Android バージョンごとの必要権限を取得
     */
    private fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13以上：メディア権限
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11-12：READ_EXTERNAL_STORAGE
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                // Android 10以下：READ + WRITE
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    /**
     * すべての権限が許可されているか確認
     */
    private fun hasAllPermissions(permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(
                activity,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 説明が必要か確認
     */
    private fun shouldShowRationale(permissions: Array<String>): Boolean {
        return permissions.any { permission ->
            activity.shouldShowRequestPermissionRationale(permission)
        }
    }

    /**
     * 権限リクエスト実行
     */
    private fun requestPermissions(permissions: Array<String>) {
        permissionLauncher?.launch(permissions)
    }

    /**
     * 権限の説明ダイアログ
     */
    private fun showPermissionRationaleDialog(onPositive: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("ストレージへのアクセス")
            .setMessage(
                "キャラクターパックをインストールするために、" +
                        "ストレージへのアクセス権限が必要です。\n\n" +
                        "この権限は以下の目的でのみ使用されます：\n" +
                        "• キャラクターZIPファイルの読み込み\n" +
                        "• キャラクターデータの保存"
            )
            .setPositiveButton("許可する") { _, _ ->
                onPositive()
            }
            .setNegativeButton("キャンセル") { dialog, _ ->
                dialog.dismiss()
                onDenied()
                emitEvent(false)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 権限拒否時の処理
     */
    private fun handlePermissionDenied(permissions: Map<String, Boolean>) {
        val permanentlyDenied = permissions.any { (permission, granted) ->
            !granted && !activity.shouldShowRequestPermissionRationale(permission)
        }

        if (permanentlyDenied) {
            // 「今後表示しない」を選択された場合
            showSettingsDialog()
        } else {
            // 通常の拒否
            AlertDialog.Builder(activity)
                .setTitle("権限が必要です")
                .setMessage("キャラクターパックをインストールするには、ストレージへのアクセス権限が必要です。")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    onDenied()
                    emitEvent(false)
                }
                .show()
        }
    }

    /**
     * 設定画面への誘導ダイアログ
     */
    private fun showSettingsDialog() {
        AlertDialog.Builder(activity)
            .setTitle("設定から権限を許可してください")
            .setMessage(
                "キャラクターパックをインストールするには、" +
                        "設定からストレージへのアクセス権限を許可してください。\n\n" +
                        "設定 → アプリ → Home Mascot Forge → 権限"
            )
            .setPositiveButton("設定を開く") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("キャンセル") { dialog, _ ->
                dialog.dismiss()
                onDenied()
                emitEvent(false)
            }
            .show()
    }

    /**
     * アプリ設定画面を開く
     */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
    }
}

/**
 * 使いやすいヘルパー拡張関数
 */
fun AppCompatActivity.requestStoragePermission(
    onDenied: () -> Unit = {}
): StoragePermissionHandler {
    return StoragePermissionHandler(this, onDenied).apply {
        init()
    }
}