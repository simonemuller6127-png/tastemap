package com.tastemap.app.ui.studio

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tastemap.app.sticker.StickerComposer
import com.tastemap.app.sticker.StickerFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class StickerStudioViewModel @Inject constructor() : ViewModel() {

    data class State(
        val source: Bitmap? = null,
        val style: StickerFilters.Style = StickerFilters.Style.WATERCOLOR,
        val label: String = "",
        val sticker: Bitmap? = null,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun load(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    // 先量尺寸，再按需抽样（上限边 1280，工坊不需要全尺寸）
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)!!.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sample = 1
                    while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
                    context.contentResolver.openInputStream(uri)!!.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }.getOrNull()
            }
            _state.value = State(source = bmp, style = _state.value.style)
            rerender(context)
        }
    }

    fun setStyle(style: StickerFilters.Style, context: android.content.Context) {
        _state.value = _state.value.copy(style = style)
        rerender(context)
    }

    fun setLabel(label: String, context: android.content.Context) {
        _state.value = _state.value.copy(label = label)
        rerender(context)
    }

    fun rerender(context: android.content.Context) {
        val s = _state.value
        val src = s.source ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val styled = StickerFilters.apply(src, s.style)
            _state.value = _state.value.copy(
                sticker = StickerComposer.compose(context, styled, s.label, seed = s.label.hashCode().toLong() + s.style.ordinal),
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** 导出透明底 PNG 到 cache 供分享 */
    suspend fun exportPng(context: android.content.Context): File? = withContext(Dispatchers.IO) {
        val sticker = _state.value.sticker ?: return@withContext null
        val dir = File(context.cacheDir, "cards").apply { mkdirs() }
        val file = File(dir, "tastemap_sticker_${System.currentTimeMillis()}.png")
        runCatching {
            file.outputStream().use { sticker.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        }.getOrNull()
    }
}

/** F22-F24 贴纸工坊：选图 → 4 种风格 → 白边旋转标签 → 透明底 PNG 分享 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerStudioScreen(
    onBack: () -> Unit,
    vm: StickerStudioViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { vm.load(it, context) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("贴纸工坊") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.source == null) {
                Button(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("选一张美食照片") }
                Text(
                    "拍的照片在这里变成手绘贴纸，可以贴给朋友或存作地图贴纸素材",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StickerFilters.Style.entries.forEach { style ->
                        FilterChip(
                            selected = state.style == style,
                            onClick = { vm.setStyle(style, context) },
                            label = { Text(style.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        vm.setLabel(it, context)
                    },
                    label = { Text("贴纸标签（店名/菜名）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.sticker?.let { sticker ->
                    Image(
                        bitmap = sticker.asImageBitmap(),
                        contentDescription = "贴纸预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                    )
                }
                Row {
                    Button(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("换一张") }
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Button(
                        enabled = state.sticker != null,
                        onClick = {
                            vm.viewModelScope.launch {
                                val file = vm.exportPng(context) ?: return@launch
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(Intent.createChooser(intent, "分享贴纸")) }
                            }
                        },
                    ) { Text("分享贴纸 PNG") }
                }
            }
        }
    }
}
