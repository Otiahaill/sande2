package com.example.sande.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sande.data.backup.BackupManager
import com.example.sande.data.backup.BackupValidation
import com.example.sande.data.backup.RestoreResult
import com.example.sande.data.backup.RestoreMode
import com.example.sande.data.prefs.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class BackupViewModel(
    private val context: Context,
    private val backupManager: BackupManager,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val _lastBackupFile = MutableStateFlow<File?>(null)
    val lastBackupFile: StateFlow<File?> = _lastBackupFile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _pendingRestore = MutableStateFlow<Pair<File, BackupValidation>?>(null)
    val pendingRestore: StateFlow<Pair<File, BackupValidation>?> = _pendingRestore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private val _restoreResult = MutableStateFlow<RestoreResult?>(null)
    val restoreResult: StateFlow<RestoreResult?> = _restoreResult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    /** Creates a local backup archive and returns it for sharing. */
    fun createBackup() {
        viewModelScope.launch {
            _isBusy.value = true
            _message.value = null
            try {
                val businessId = tokenManager.getBusinessId() ?: "default-business"
                val deviceCode = tokenManager.getDeviceCode() ?: "local"
                val businessName = "SANDE Business"

                val file = backupManager.createBackup(
                    businessId = businessId,
                    businessName = businessName,
                    deviceCode = deviceCode
                )
                _lastBackupFile.value = file
                _message.value = "Backup created (${file.length() / 1024} KB)"
            } catch (e: Exception) {
                _message.value = "Backup failed: ${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    /** Shares the last created backup via the system picker. */
    fun shareBackup(onShareReady: (Uri, String) -> Unit) {
        viewModelScope.launch {
            val file = _lastBackupFile.value
            if (file == null) {
                _message.value = "Create a backup first"
                return@launch
            }
            try {
                val uri = backupManager.prepareForSharing(file)
                onShareReady(Uri.parse(uri), "application/zip")
            } catch (e: Exception) {
                _message.value = "Could not share backup: ${e.message}"
            }
        }
    }

    /** Validates a picked file and stages it for restore. */
    fun inspectForRestore(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            _message.value = null
            try {
                val file = copyUriToCache(uri)
                val validation = backupManager.inspectBackup(file)
                if (validation.isValid) {
                    _pendingRestore.value = file to validation
                    val m = validation.manifest!!
                    _message.value = "Backup from ${m.createdAtIso.take(10)} — ${m.totalRows} records"
                } else {
                    _message.value = validation.errorMessage ?: "Invalid backup file"
                }
            } catch (e: Exception) {
                _message.value = "Could not read file: ${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    /** Applies the staged restore. */
    fun confirmRestore(mode: RestoreMode = RestoreMode.REPLACE) {
        viewModelScope.launch {
            val staged = _pendingRestore.value ?: return@launch
            _isBusy.value = true
            try {
                val result = backupManager.restoreBackup(staged.first, mode)
                _restoreResult.value = result
                _message.value = if (result.success) {
                    "Restored ${result.rowsRestored} of ${result.expectedRows} records"
                } else {
                    result.errorMessage ?: "Restore failed"
                }
                if (result.success) {
                    _pendingRestore.value = null
                }
            } catch (e: Exception) {
                _message.value = "Restore failed: ${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    /** Cancels a staged restore. */
    fun cancelRestore() {
        _pendingRestore.value = null
        _message.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Copies a content:// URI into the app cache so it can be read. */
    private fun copyUriToCache(uri: Uri): File {
        val file = File(context.cacheDir, "import-${System.currentTimeMillis()}.sande-backup")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Could not open file")
        return file
    }
}
