package com.tastemap.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tastemap.app.data.db.TasteTag
import com.tastemap.app.data.repository.TasteRepository
import com.tastemap.app.map.MarkerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tasteRepository: TasteRepository,
    private val wishlistRepository: com.tastemap.app.data.repository.WishlistRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    val tastes: StateFlow<List<TasteTag>> = tasteRepository.tastes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = mutableStateOf<String?>(null)
    val message: androidx.compose.runtime.State<String?> = _message

    fun addTaste(name: String, colorHex: String) {
        viewModelScope.launch { tasteRepository.addCustom(name, colorHex) }
    }

    fun removeTaste(id: Long) {
        viewModelScope.launch { tasteRepository.removeCustom(id) }
    }

    /** F14 v1：从相册的卡片截图识别二维码 → 店卡加入想吃清单（无服务器闭环） */
    fun importCard(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching {
                val bmp = android.graphics.BitmapFactory.decodeStream(
                    appContext.contentResolver.openInputStream(uri),
                ) ?: error("图片读取失败")
                val raw = com.tastemap.app.share.QrCodec.decodeFromBitmap(bmp) ?: error("未识别到二维码")
                com.tastemap.app.share.CardShareCodec.decode(raw) ?: error("不是味觉地图卡片")
            }
            result.onSuccess { payload ->
                wishlistRepository.add(
                    text = payload.name,
                    note = payload.tastes.joinToString(" · ").ifBlank { "来自分享卡片" },
                    latitude = payload.lat,
                    longitude = payload.lng,
                )
                _message.value = "已把「${payload.name}」加入想吃清单"
            }.onFailure { _message.value = "导入失败：${it.message}" }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/** 设置：口味管理（F03：自定义口味增删 + 配色），色板与 AGENTS.md 色值表同源 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val tastes by vm.tastes.collectAsStateWithLifecycle()
    val message by vm.message
    var showAdd by remember { mutableStateOf(false) }

    // F14 v1：从相册卡片截图识别二维码导入
    val cardPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(vm::importCard) }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("口味管理") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = "新增口味") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text("分享", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "从美食卡片导入（识别二维码图片）",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.clickable {
                                cardPicker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            }.padding(vertical = 8.dp),
                        )
                    }
                }
            }
            items(tastes.size) { index ->
                val taste = tastes[index]
                Card {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(14.dp).background(color = Color(MarkerFactory.parseColor(taste.colorHex)), shape = CircleShape))
                        Text(
                            "  ${taste.name}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (!taste.isPreset) {
                            Text("自定义", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { vm.removeTaste(taste.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text("预置", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTasteDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, color ->
                vm.addTaste(name, color)
                showAdd = false
            },
        )
    }
}

private val PALETTE = listOf(
    "#D9482B", "#E9C46A", "#4C90A8", "#6BA292", "#C9B458", "#A8B5A2", "#C77B4F",
    "#8E7CC3", "#B56576", "#6D597A",
)

@Composable
private fun AddTasteDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(PALETTE.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义口味") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("口味名（如：麻、酸辣）") },
                    singleLine = true,
                )
                Text("选个颜色", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.take(5).forEach { hex ->
                        ColorSwatch(hex, selected = color == hex) { color = hex }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.drop(5).forEach { hex ->
                        ColorSwatch(hex, selected = color == hex) { color = hex }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) { Text("加入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ColorSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(if (selected) 34.dp else 28.dp)
            .background(color = Color(MarkerFactory.parseColor(hex)), shape = CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}
