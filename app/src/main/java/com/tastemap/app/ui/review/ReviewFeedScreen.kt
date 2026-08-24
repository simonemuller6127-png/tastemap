package com.tastemap.app.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.tastemap.app.data.photo.PhotoStore
import com.tastemap.app.data.repository.ReviewCard
import com.tastemap.app.data.repository.ReviewRepository
import com.tastemap.app.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.Random
import javax.inject.Inject

@HiltViewModel
class ReviewFeedViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val scheduleRepository: ScheduleRepository,
    private val photoStore: PhotoStore,
) : ViewModel() {

    private val seed = MutableStateFlow(0)

    /** 每天固定洗牌种子 + 手动刷新递增：每天有新鲜感，刷新换一批（F07 验收） */
    val cards: StateFlow<List<ReviewCard>> = combine(reviewRepository.observeCards(), seed) { list, s ->
        list.shuffled(Random(LocalDate.now().toEpochDay() * 31 + s))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun refresh() {
        seed.value += 1
    }

    /** PRD 故事 2 的闭环：看馋了 → 加入明天午餐 */
    fun addToTomorrowLunch(card: ReviewCard) {
        val shopId = card.shop?.id ?: return
        viewModelScope.launch {
            scheduleRepository.add(date = LocalDate.now().plusDays(1), mealSlot = "午餐", shopId = shopId)
            _message.value = "已加入明天午餐日程"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun photoFile(relativePath: String): File = photoStore.fileOf(relativePath)
}

/** F07 今日回味：随机抽取历史记录的卡片流 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewFeedScreen(
    onOpenShop: (Long) -> Unit,
    vm: ReviewFeedViewModel = hiltViewModel(),
) {
    val cards by vm.cards.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今日回味") },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, contentDescription = "换一批") }
                },
            )
        },
    ) { padding ->
        if (cards.isEmpty()) {
            Text(
                "还没有记录——去地图上长按打卡，攒出你的第一张回味卡片",
                Modifier.padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards.size) { index ->
                val card = cards[index]
                Card {
                    Column(Modifier.padding(12.dp)) {
                        card.firstPhoto?.let { path ->
                            AsyncImage(
                                model = vm.photoFile(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(card.shop?.name ?: "未知店铺", style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (card.tasteNames.isNotEmpty()) {
                                Text(card.tasteNames.joinToString(" · "), color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.padding(horizontal = 4.dp))
                            }
                            Text("★".repeat(card.record.rating), color = MaterialTheme.colorScheme.primary)
                        }
                        if (card.record.comment.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(card.record.comment, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            OutlinedButton(onClick = { vm.addToTomorrowLunch(card) }) { Text("再去一次") }
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            card.shop?.let {
                                OutlinedButton(onClick = { onOpenShop(it.id) }) { Text("看店") }
                            }
                        }
                    }
                }
            }
        }
    }
}
