package com.example.mascotforge.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mascotforge.ui.components.CharacterCard
import mascotforge.ui.components.InstallProgressDialog

class CharacterInstallActivity : AppCompatActivity() {

    private lateinit var permissionHandler: StoragePermissionHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 権限ハンドラー初期化
        permissionHandler = requestStoragePermission(
            onGranted = {
                // 権限許可済み
            },
            onDenied = {
                Toast.makeText(this, "ストレージへのアクセスが拒否されました", Toast.LENGTH_SHORT).show()
            }
        )

        setContent {
            MaterialTheme {
                // Compose内で ViewModel を取得
                val viewModel: CharacterInstallViewModel = viewModel()

                // ✅ ViewModel 初期化（1回だけ実行）
                LaunchedEffect(Unit) {
                    viewModel.initialize(applicationContext)
                }

                CharacterInstallScreen(
                    onBack = { finish() },
                    onRequestPermission = { permissionHandler.checkAndRequestPermissions() },
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterInstallScreen(
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    viewModel: CharacterInstallViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // ファイルピッカーランチャー
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.installCharacter(uri, context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("キャラクター管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // 権限チェック後にファイルピッカー起動
                    onRequestPermission()
                    filePickerLauncher.launch("application/zip")
                },
                icon = { Icon(Icons.Default.Add, "追加") },
                text = { Text("キャラ追加") }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                uiState.isLoading && uiState.characters.isEmpty() -> {
                    LoadingScreen()
                }
                uiState.characters.isEmpty() -> {
                    EmptyScreen()
                }
                else -> {
                    CharacterList(
                        characters = uiState.characters,
                        selectedCharId = uiState.selectedCharId,
                        onSelect = { charId -> viewModel.selectCharacter(charId) },
                        onDelete = { charId -> viewModel.deleteCharacter(charId) }
                    )
                }
            }

// インストール進行状況ダイアログ
            uiState.installProgress?.let { progress ->
                InstallProgressDialog(
                    progress = progress,
                    onDismiss = { viewModel.dismissProgress() }
                )
            }

            // エラーダイアログ
            if (uiState.error != null) {
                ErrorDialog(
                    error = uiState.error!!,
                    onDismiss = { viewModel.clearError() }
                )
            }

            // 成功ダイアログ
            if (uiState.successMessage != null) {
                SuccessDialog(
                    message = uiState.successMessage!!,
                    onDismiss = { viewModel.clearSuccess() }
                )
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("読み込み中...")
        }
    }
}

@Composable
fun EmptyScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "キャラクターがありません",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "右下のボタンからキャラクターを追加できます",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CharacterList(
    characters: List<CharacterDisplayData>,
    selectedCharId: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(characters, key = { it.id }) { character ->
            CharacterCard(
                character = character,
                isSelected = character.id == selectedCharId,
                onSelect = { onSelect(character.id) },
                onDelete = { onDelete(character.id) }
            )
        }
    }
}

@Composable
fun ErrorDialog(
    error: InstallError,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("インストールに失敗しました") },
        text = {
            Column {
                Text(
                    text = error.message,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (error.details != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun SuccessDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("インストール完了") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

// データクラス
data class CharacterDisplayData(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val isBuiltIn: Boolean
)

data class InstallError(
    val message: String,
    val details: String? = null
)
