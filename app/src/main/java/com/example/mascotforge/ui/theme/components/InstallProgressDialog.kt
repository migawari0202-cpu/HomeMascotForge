package mascotforge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.example.mascotforge.ui.InstallPhase
import com.example.mascotforge.ui.InstallProgress
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*

@Composable
fun InstallProgressDialog(
    progress: InstallProgress,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            // インストール中はキャンセル不可
            if (progress.phase == InstallPhase.COMPLETED) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = progress.phase == InstallPhase.COMPLETED,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = getPhaseTitle(progress.phase),
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(24.dp))

                // プログレスバー
                LinearProgressIndicator(
                    progress = progress.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 進行状況メッセージ
                Text(
                    text = progress.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // パーセンテージ表示
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${(progress.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // 完了時のみOKボタン表示
                if (progress.phase == InstallPhase.COMPLETED) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

private fun getPhaseTitle(phase: InstallPhase): String {
    return when (phase) {
        InstallPhase.READING -> "ファイル読み込み中"
        InstallPhase.EXTRACTING -> "解凍中"
        InstallPhase.VALIDATING -> "検証中"
        InstallPhase.INSTALLING -> "インストール中"
        InstallPhase.COMPLETED -> "完了"
    }
}