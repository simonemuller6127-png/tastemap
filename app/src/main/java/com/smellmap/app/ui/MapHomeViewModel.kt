package com.smellmap.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smellmap.app.data.backup.BackupManager
import com.smellmap.app.data.repository.MapRepository
import com.smellmap.app.data.repository.RecordRepository
import com.smellmap.app.data.repository.ShopPin
import com.smellmap.app.data.repository.TasteRepository
import com.smellmap.app.data.db.TasteTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 长按地图选中的待新建位置 */
data class PendingPin(val latitude: Double, val longitude: Double)

@HiltViewModel
class MapHomeViewModel @Inject constructor(
    tasteRepository: TasteRepository,
    mapRepository: MapRepository,
    private val recordRepository: RecordRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    val pins: StateFlow<List<ShopPin>> = mapRepository.observePins()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tastes: StateFlow<List<TasteTag>> = tasteRepository.tastes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingPin = MutableStateFlow<PendingPin?>(null)
    val pendingPin: StateFlow<PendingPin?> = _pendingPin

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch { tasteRepository.ensureSeeded() }
    }

    fun onMapLongPress(latitude: Double, longitude: Double) {
        _pendingPin.value = PendingPin(latitude, longitude)
    }

    fun dismissPinSheet() {
        _pendingPin.value = null
    }

    /** F02 最小记录流：保存后 Flow 自动刷新图钉 */
    fun saveRecord(
        shopName: String,
        address: String,
        dishName: String,
        rating: Int,
        comment: String,
        tasteIds: List<Long>,
    ) {
        val pending = _pendingPin.value ?: return
        viewModelScope.launch {
            recordRepository.addRecord(
                shopName = shopName,
                latitude = pending.latitude,
                longitude = pending.longitude,
                address = address,
                dishName = dishName,
                rating = rating,
                comment = comment,
                tasteIds = tasteIds,
            )
            _pendingPin.value = null
            _message.value = "已记录「$shopName」"
        }
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
