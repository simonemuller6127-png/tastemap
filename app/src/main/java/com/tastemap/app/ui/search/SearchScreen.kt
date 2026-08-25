package com.tastemap.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tastemap.app.data.db.TasteTag
import com.tastemap.app.data.repository.SearchFilter
import com.tastemap.app.data.repository.SearchHit
import com.tastemap.app.map.MarkerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    searchDataSource: SearchDataSource,
) : ViewModel() {

    val query = MutableStateFlow("")
    val selectedTastes = MutableStateFlow<Set<Long>>(emptySet())

    /** 评分下限：0 不过滤，1-5 档 */
    val minRating = MutableStateFlow(0)

    val tastes: StateFlow<List<TasteTag>> = searchDataSource.tastes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hits: StateFlow<List<SearchHit>> = combine(
        searchDataSource.data, query, selectedTastes, minRating,
    ) { data, q, tasteIds, ratingFloor ->
        SearchFilter.filter(data.shops, data.records, data.refs, data.tastes, q, tasteIds, ratingFloor)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleTaste(id: Long) {
        selectedTastes.value = selectedTastes.value.let { if (id in it) it - id else it + id }
    }

    fun setMinRating(r: Int) {
        minRating.value = if (minRating.value == r) 0 else r
    }
}

/** 搜索数据源（Hilt 注入，便于测试替换） */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object SearchModule {

    @dagger.Provides
    @javax.inject.Singleton
    fun provideSearchDataSource(db: com.tastemap.app.data.db.AppDatabase): SearchDataSource =
        SearchDataSource(db)
}

class SearchDataSource(db: com.tastemap.app.data.db.AppDatabase) {
    data class Data(
        val shops: List<com.tastemap.app.data.db.Shop>,
        val records: List<com.tastemap.app.data.db.MealRecord>,
        val refs: Map<Long, Set<Long>>,
        val tastes: List<TasteTag>,
    )

    val tastes = db.tasteTagDao().observeAll()

    val data = kotlinx.coroutines.flow.combine(
        db.shopDao().observeAll(),
        db.mealRecordDao().observeAll(),
        db.mealRecordDao().observeAllTasteRefs(),
        db.tasteTagDao().observeAll(),
    ) { shops, records, refs, tastes ->
        // 同一记录多个口味：合并 recordId -> tasteIds 集合
        val refMap = HashMap<Long, MutableSet<Long>>()
        refs.forEach { refMap.getOrPut(it.recordId) { mutableSetOf() }.add(it.tasteId) }
        Data(shops, records, refMap, tastes)
    }
}

/** F05 搜索筛选：关键词全文 + 口味 + 评分 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onOpenShop: (Long) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val tastes by vm.tastes.collectAsStateWithLifecycle()
    val selected by vm.selectedTastes.collectAsStateWithLifecycle()
    val minRating by vm.minRating.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("搜索") }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                label = { Text("店名 / 菜名 / 评价（如：咸蛋黄）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                tastes.forEach { taste ->
                    FilterChip(
                        selected = taste.id in selected,
                        onClick = { vm.toggleTaste(taste.id) },
                        label = { Text(taste.name) },
                        leadingIcon = {
                            Row(Modifier.size(10.dp).background(color = Color(MarkerFactory.parseColor(taste.colorHex)), shape = CircleShape)) {}
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { r ->
                    FilterChip(
                        selected = minRating == r,
                        onClick = { vm.setMinRating(r) },
                        label = { Text("★$r+") },
                    )
                }
            }
            Spacer(Modifier.padding(4.dp))
            if (hits.isEmpty()) {
                Text(
                    if (query.isBlank() && selected.isEmpty()) "输入关键词开始找你的店" else "没有匹配的店",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                ) {
                    items(hits.size) { index ->
                        val hit = hits[index]
                        Card(Modifier.clickable { onOpenShop(hit.shop.id) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(hit.shop.name, style = MaterialTheme.typography.titleMedium)
                                Text(hit.matchedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (hit.tasteNames.isNotEmpty()) {
                                        Text(hit.tasteNames.joinToString(" · "), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                                        Spacer(Modifier.padding(horizontal = 4.dp))
                                    }
                                    Text("${hit.recordCount} 次 · 均分 %.1f".format(hit.avgRating), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
