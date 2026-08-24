package com.tastemap.app.ui.record

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tastemap.app.data.db.TasteTag
import com.tastemap.app.data.photo.PhotoStore
import com.tastemap.app.data.repository.RecordRepository
import com.tastemap.app.data.repository.TasteRepository
import com.tastemap.app.data.repository.WishlistRepository
import com.tastemap.app.map.MarkerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecordEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    tasteRepository: TasteRepository,
    private val recordRepository: RecordRepository,
    private val wishlistRepository: WishlistRepository,
    private val photoStore: PhotoStore,
) : ViewModel() {

    val latitude: Double = savedStateHandle.get<String>("lat")?.toDoubleOrNull() ?: 0.0
    val longitude: Double = savedStateHandle.get<String>("lng")?.toDoubleOrNull() ?: 0.0

    /** 想吃清单"打卡"进入时预填店名（F09 → F02 转换） */
    val initialShopName: String = savedStateHandle.get<String>("name") ?: ""
    private val fromWishlistId: Long = savedStateHandle.get<String>("wid")?.toLongOrNull() ?: 0L

    val tastes: StateFlow<List<TasteTag>> = tasteRepository.tastes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class PhotoUi(val relativePath: String, val isOriginal: Boolean)

    private val _photos = MutableStateFlow<List<PhotoUi>>(emptyList())
    val photos: StateFlow<List<PhotoUi>> = _photos

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch { tasteRepository.ensureSeeded() }
    }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (_photos.value.size + uris.size > MAX_PHOTOS) {
            _error.value = "最多 $MAX_PHOTOS 张照片"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            uris.forEach { uri ->
                runCatching { photoStore.store(uri) }
                    .onSuccess { stored -> _photos.update { it + PhotoUi(stored.relativePath, stored.isOriginal) } }
                    .onFailure { _error.value = "照片读取失败：${it.message}" }
            }
            _busy.value = false
        }
    }

    fun removePhoto(index: Int) {
        val photo = _photos.value.getOrNull(index) ?: return
        viewModelScope.launch { runCatching { photoStore.delete(photo.relativePath) } }
        _photos.update { it.filterIndexed { i, _ -> i != index } }
    }

    fun save(shopName: String, dishName: String, rating: Int, comment: String, tasteIds: List<Long>) {
        if (shopName.isBlank() || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                recordRepository.addRecord(
                    shopName = shopName.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    address = "",
                    dishName = dishName.trim(),
                    rating = rating,
                    comment = comment.trim(),
                    tasteIds = tasteIds,
                    photos = _photos.value.map { it.relativePath },
                    isOriginalPhoto = _photos.value.all { it.isOriginal },
                )
            }.onSuccess {
                // 从想吃清单打卡进入：保存成功即完成"想吃→吃过"转换，移除清单项
                if (fromWishlistId > 0) runCatching { wishlistRepository.remove(fromWishlistId) }
                _saved.value = true
            }.onFailure {
                _error.value = "保存失败：${it.message}"
                _busy.value = false
            }
        }
    }

    fun consumeError() {
        _error.value = null
    }

    /** 供 UI 加载缩略图：相对路径 → 私有目录绝对路径 */
    fun photoFile(relativePath: String): File = photoStore.fileOf(relativePath)

    companion object {
        const val MAX_PHOTOS = 9
    }
}

/** F02 完整记录表单（R2 记录流切片）：照片（1-9 张，EXIF 原图角标）+ 口味 + 评分 + 评价 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordEditScreen(
    onDone: () -> Unit,
    vm: RecordEditViewModel = hiltViewModel(),
) {
    val tastes by vm.tastes.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var shopName by rememberSaveable { mutableStateOf(vm.initialShopName) }
    var dishName by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf(4f) }
    var comment by rememberSaveable { mutableStateOf("") }
    val selectedTastes = remember { mutableStateListOf<Long>() }

    LaunchedEffect(saved) { if (saved) onDone() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(RecordEditViewModel.MAX_PHOTOS),
    ) { uris -> vm.addPhotos(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建美食记录") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "坐标：%.5f, %.5f（离线保存）".format(vm.latitude, vm.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = shopName,
                onValueChange = { shopName = it },
                label = { Text("店名 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dishName,
                onValueChange = { dishName = it },
                label = { Text("菜名（如：咸蛋黄焗虾）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 照片区：缩略图 + 删除 + EXIF 角标 + 添加按钮
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(photos) { index, photo ->
                    Box {
                        AsyncImage(
                            model = vm.photoFile(photo.relativePath),
                            contentDescription = "照片 ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除照片",
                                modifier = Modifier.clickable { vm.removePhoto(index) }.size(18.dp).padding(2.dp),
                            )
                        }
                        Text(
                            if (photo.isOriginal) "原图" else "疑似修图",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (photo.isOriginal) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                        )
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(96.dp).clickable {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加照片")
                            Text(
                                "${photos.size}/${RecordEditViewModel.MAX_PHOTOS}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("评分")
                Text(
                    " ${rating.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(value = rating, onValueChange = { rating = it }, valueRange = 1f..5f, steps = 3)

            Text("口味（可多选，决定贴纸/图钉颜色）")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tastes.forEach { taste ->
                    FilterChip(
                        selected = taste.id in selectedTastes,
                        onClick = {
                            if (taste.id in selectedTastes) selectedTastes.remove(taste.id)
                            else selectedTastes.add(taste.id)
                        },
                        label = { Text(taste.name) },
                        leadingIcon = {
                            Box(
                                Modifier.size(10.dp).background(
                                    color = Color(MarkerFactory.parseColor(taste.colorHex)),
                                    shape = CircleShape,
                                ),
                            )
                        },
                    )
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("一句话评价") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                AssistChip(onClick = vm::consumeError, label = { Text(error ?: "") })
            }

            Button(
                onClick = { vm.save(shopName, dishName, rating.toInt(), comment, selectedTastes.toList()) },
                enabled = shopName.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (busy) "保存中…" else "保存")
            }
        }
    }
}

