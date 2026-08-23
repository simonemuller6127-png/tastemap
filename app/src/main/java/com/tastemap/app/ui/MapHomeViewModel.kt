package com.tastemap.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tastemap.app.data.backup.BackupManager
import com.tastemap.app.data.repository.MapRepository
import com.tastemap.app.data.repository.ShopPin
import com.tastemap.app.data.repository.TasteRepository
import com.tastemap.app.data.db.TasteTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapHomeViewModel @Inject constructor(
    tasteRepository: TasteRepository,
    mapRepository: MapRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    val pins: StateFlow<List<ShopPin>> = mapRepository.observePins()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tastes: StateFlow<List<TasteTag>> = tasteRepository.tastes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch { tasteRepository.ensureSeeded() }
    }

    /** F06 备份 */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupManager.exportTo(uri) }
                .onSuccess { _message.value = "备份已导出" }
                .onFailure { _message.value = "导出失败：${it.message}" }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupManager.importFrom(uri) }
                .onSuccess { _message.value = "已导入 ${it.shops} 家店 / ${it.records} 条记录" }
                .onFailure { _message.value = "导入失败：${it.message}" }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
