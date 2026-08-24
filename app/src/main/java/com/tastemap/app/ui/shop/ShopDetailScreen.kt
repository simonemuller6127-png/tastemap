package com.tastemap.app.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tastemap.app.data.photo.PhotoStore
import com.tastemap.app.data.repository.ShopDetailRepository
import com.tastemap.app.data.repository.ShopDetailUi
import com.tastemap.app.deeplink.DeeplinkRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ShopDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val photoStore: PhotoStore,
    shopDetailRepository: ShopDetailRepository,
) : ViewModel() {

    val shopId: Long = savedStateHandle.get<String>("shopId")?.toLongOrNull() ?: -1L

    val detail: StateFlow<ShopDetailUi?> = shopDetailRepository.observe(shopId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun photoFile(relativePath: String): File = photoStore.fileOf(relativePath)
}

/** F04 店铺详情 + 打卡时间线（同一店多条记录，按时间倒序） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDetailScreen(
    onBack: () -> Unit,
    onComposeCard: (shopId: Long) -> Unit,
    vm: ShopDetailViewModel = hiltViewModel(),
) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.shop?.name ?: "店铺详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        val d = detail
        if (d == null) {
            Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.padding(top = 48.dp))
            }
            return@Scaffold
        }
        if (d.shop == null) {
            Text("店铺不存在", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(d.shop.name, style = MaterialTheme.typography.titleLarge)
                    if (d.shop.address.isNotBlank()) {
                        Text(d.shop.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "%.5f, %.5f".format(d.shop.latitude, d.shop.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${d.records.size} 次打卡", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("均分 %.1f".format(d.avgRating), style = MaterialTheme.typography.titleMedium)
                        d.dominantTasteName?.let {
                            Spacer(Modifier.width(12.dp))
                            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Spacer(Modifier.size(4.dp))
                    // F10 跳转 + F13 卡片入口
                    Row {
                        androidx.compose.material3.OutlinedButton(onClick = {
                            DeeplinkRouter.navigateToShop(context, d.shop.name, d.shop.latitude, d.shop.longitude)
                        }) { Text("导航") }
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.OutlinedButton(onClick = {
                            DeeplinkRouter.openMeituanWithShopName(context, d.shop.name)
                        }) { Text("外卖") }
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.OutlinedButton(onClick = { onComposeCard(d.shop.id) }) { Text("做卡片") }
                    }
                }
            }
            itemsIndexed(d.records) { _, recordUi ->
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatTime(recordUi.record.tastedAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Text("★".repeat(recordUi.record.rating), color = MaterialTheme.colorScheme.primary)
                        }
                        if (recordUi.record.dishName.isNotBlank()) {
                            Text(recordUi.record.dishName, style = MaterialTheme.typography.titleMedium)
                        }
                        if (recordUi.tasteNames.isNotEmpty()) {
                            Text(recordUi.tasteNames.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        }
                        if (recordUi.record.comment.isNotBlank()) {
                            Text(recordUi.record.comment, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (recordUi.photoPaths.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(recordUi.photoPaths) { _, path ->
                                    AsyncImage(
                                        model = vm.photoFile(path),
                                        contentDescription = "记录照片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)),
                                    )
                                }
                            }
                        }
                        if (!recordUi.record.isOriginalPhoto) {
                            Text(
                                "含疑似修图照片",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

private fun formatTime(epochMillis: Long): String = timeFormat.format(Date(epochMillis))
