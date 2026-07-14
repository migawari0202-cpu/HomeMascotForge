package com.example.mascotforge.ui

import android.content.Context
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mascotforge.CharacterPreferences
import com.example.mascotforge.characters.CharacterRegistry
import com.example.mascotforge.installer.CharacterInstaller
import com.example.mascotforge.installer.CommonInstaller
import com.example.mascotforge.installer.InstallResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CharacterInstallViewModel : ViewModel() {

    /** キャラ削除専用。Shellの削除はShellSelectorViewModelが担当する。 */
    private lateinit var characterInstaller: CharacterInstaller
    private lateinit var commonInstaller: CommonInstaller
    private lateinit var appContext: Context

    // ---- UI State ----
    private val _uiState = MutableStateFlow(CharacterInstallUiState())
    val uiState: StateFlow<CharacterInstallUiState> = _uiState

    /** 初期化 */
    fun initialize(context: Context) {
        if (this::commonInstaller.isInitialized) return

        appContext = context.applicationContext
        characterInstaller = CharacterInstaller(appContext)
        commonInstaller = CommonInstaller(appContext)

        viewModelScope.launch {
            loadInstalledCharacters()
        }
    }

    /** キャラ一覧を読み込み */
    private suspend fun loadInstalledCharacters() {
        _uiState.value = _uiState.value.copy(isLoading = true)

        val list = CharacterRegistry.getEntries(appContext)
        _uiState.value = _uiState.value.copy(
            characters = list.map {
                CharacterDisplayData(
                    id = it.metadata.id,
                    name = it.metadata.name,
                    version = it.metadata.version,
                    author = it.metadata.author,
                    description = it.metadata.description,
                    isBuiltIn = it.isBuiltIn,
                    thumbnail = it.factory.getThumbnail(appContext)?.toBitmap()
                )
            },
            selectedCharId = CharacterPreferences.getSelectedCharacterId(appContext),
            isLoading = false
        )
    }

    /** キャラ選択 */
    fun selectCharacter(charId: String) {
        if (this::appContext.isInitialized) {
            CharacterPreferences.setSelectedCharacterId(appContext, charId)
        }
        _uiState.value = _uiState.value.copy(selectedCharId = charId)
    }

    /** キャラ削除 */
    fun deleteCharacter(charId: String) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                characterInstaller.uninstall(charId)
            }
            if (!success) {
                _uiState.value = _uiState.value.copy(
                    error = InstallError("削除に失敗しました")
                )
                return@launch
            }

            loadInstalledCharacters()

            _uiState.value = _uiState.value.copy(
                successMessage = "「$charId」を削除しました"
            )
        }
    }

    /**
     * ZIPからキャラ / Shell / 両方をインストールする。
     * CommonInstallerがZIP内の character.json / shell.json を見て自動判別する。
     */
    fun installCharacter(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                installProgress = InstallProgress(
                    phase = InstallPhase.READING,
                    progress = 0f,
                    message = "インストールを開始しています"
                )
            )

            val result = withContext(Dispatchers.IO) {
                commonInstaller.install(uri)
            }

            result.onSuccess { installResult ->
                val message = when (installResult) {
                    is InstallResult.Character ->
                        "「${installResult.info.name}」をインストールしました"
                    is InstallResult.Shell ->
                        "Shell「${installResult.info.name}」をインストールしました"
                    is InstallResult.Both ->
                        "「${installResult.character.name}」と Shell「${installResult.shell.name}」をインストールしました"
                }
                _uiState.value = _uiState.value.copy(
                    installProgress = null,
                    successMessage = message
                )
                loadInstalledCharacters()
            }

            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    installProgress = null,
                    error = InstallError(
                        message = "インストールに失敗しました",
                        details = e.message
                    )
                )
            }
        }
    }

    fun dismissProgress() {
        _uiState.value = _uiState.value.copy(installProgress = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}

/** UI 表示用のまとめ */
data class CharacterInstallUiState(
    val characters: List<CharacterDisplayData> = emptyList(),
    val selectedCharId: String? = null,
    val isLoading: Boolean = false,
    val installProgress: InstallProgress? = null,  // <- ここを修正
    val error: InstallError? = null,
    val successMessage: String? = null
)
