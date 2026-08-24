package com.tastemap.app.ui.card

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tastemap.app.data.photo.PhotoStore
import com.tastemap.app.data.repository.ShopDetailRepository
import com.tastemap.app.data.repository.ShopDetailUi
import com.tastemap.app.data.repository.TasteRepository
import com.tastemap.app.share.CardRenderer
import com.tastemap.app.share.CardShareCodec
import com.tastemap.app.share.ShareCardPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CardComposerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shopDetailRepository: ShopDetailRepository,
    private val tasteRepository: TasteRepository,
    private val photoStore: PhotoStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val shopId: Long = savedStateHandle.get<String>("shopId")?.toLongOrNull() ?: -1L

    val detail: StateFlow<ShopDetailUi?> = shopDetailRepository.observe(shopId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _card = MutableStateFlow<Bitmap?>(null)
    val card: StateFlow<Bitmap?> = _card

    init {
        // 详情就绪后渲染一次（预览即所得）
        viewModelScope.launch {
            detail.first { it?.shop != null }?.let { renderInternal(it) }
        }
    }

    fun rerender() {
        detail.value?.takeIf { it.shop != null }?.let {
            viewModelScope.launch { renderInternal(it) }
        }
    }

    private suspend fun renderInternal(d: ShopDetailUi) = withContext(Dispatchers.Default) {
        val shop = d.shop ?: return@withContext
        val tasteColor = tasteRepository.tastes.first()
            .firstOrNull { it.name == d.dominantTasteName }?.colorHex ?: "#4A4238"
        val lastRecord = d.records.firstOrNull()
        val payload = ShareCardPayload(
            name = shop.name,
            lat = shop.latitude,
            lng = shop.longitude,
            tastes = d.records.firstOrNull()?.tasteNames ?: emptyList(),
            rating = lastRecord?.record?.rating ?: 0,
            note = lastRecord?.record?.comment ?: "",
        )
        _card.value = CardRenderer.render(
            appContext,
            CardRenderer.Input(
                shopName = shop.name,
                tasteNames = payload.tastes,
                tasteColorHex = tasteColor,
                rating = payload.rating,
                note = payload.note,
                photoFile = lastRecord?.photoPaths?.firstOrNull()?.let { photoStore.fileOf(it) },
                qrContent = CardShareCodec.encode(payload),
            ),
        )
    }

    /** 导出 PNG 到 cache 并返回文件（供 FileProvider 分享） */
    suspend fun exportPng(): File? = withContext(Dispatchers.IO) {
        val bitmap = _card.value ?: return@withContext null
        val dir = File(appContext.cacheDir, "cards").apply { mkdirs() }
        val file = File(dir, "tastemap_card_$shopId.png")
        runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        }.getOrNull()
    }
}

/** F13 卡片生成与分享 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardComposerScreen(
    onBack: () -> Unit,
    vm: CardComposerViewModel = hiltViewModel(),
) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    val card by vm.card.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(detail?.shop?.id) { vm.rerender() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("美食卡片") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val bmp = card
            if (bmp == null) {
                CircularProgressIndicator(Modifier.padding(top = 48.dp))
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "卡片预览",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
                Row {
                    OutlinedButton(onClick = vm::rerender) { Text("换个口味色") }
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Button(
                        onClick = {
                            sharing = true
                            vm.viewModelScope.launch {
                                sharePng(context, vm)
                                sharing = false
                            }
                        },
                        enabled = !sharing,
                    ) { Text(if (sharing) "生成中…" else "分享 / 保存图片") }
                }
            }
        }
    }
}

private suspend fun sharePng(context: Context, vm: CardComposerViewModel) {
    val file = vm.exportPng() ?: return
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "分享美食卡片")) }
}
