package com.tastemap.app.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.tastemap.app.data.db.WishlistItem
import com.tastemap.app.data.repository.TasteRepository
import com.tastemap.app.data.repository.WishlistRepository
import com.tastemap.app.map.MarkerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val wishlistRepository: WishlistRepository,
    tasteRepository: TasteRepository,
) : ViewModel() {

    val items: StateFlow<List<WishlistItem>> = wishlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tastes: StateFlow<List<TasteTag>> = tasteRepository.tastes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String, tasteIds: List<Long>) {
        if (text.isBlank()) return
        viewModelScope.launch { wishlistRepository.add(text = text.trim(), tasteIds = tasteIds) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { wishlistRepository.remove(id) }
    }
}

/** F09 想吃清单：与"吃过"分离，打卡即转记录 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WishlistScreen(
    onCheckIn: (item: WishlistItem) -> Unit,
    vm: WishlistViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val tastes by vm.tastes.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("想吃清单") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = "添加想吃") }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Text(
                "半夜想吃的东西，先记在这里",
                Modifier.padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items.size) { index ->
                    val item = items[index]
                    Card {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.text, style = MaterialTheme.typography.titleMedium)
                                if (item.note.isNotBlank()) {
                                    Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Button(onClick = { onCheckIn(item) }) { Text("打卡") }
                            IconButton(onClick = { vm.remove(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddWishlistDialog(
            tastes = tastes,
            onDismiss = { showAdd = false },
            onConfirm = { text, tasteIds ->
                vm.add(text, tasteIds)
                showAdd = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddWishlistDialog(
    tastes: List<TasteTag>,
    onDismiss: () -> Unit,
    onConfirm: (text: String, tasteIds: List<Long>) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Long>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加到想吃清单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("想吃什么 / 哪家店") },
                    singleLine = true,
                )
                if (tastes.isNotEmpty()) {
                    Text("口味（可选）", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tastes.forEach { taste ->
                            FilterChip(
                                selected = taste.id in selected,
                                onClick = {
                                    if (taste.id in selected) selected.remove(taste.id) else selected.add(taste.id)
                                },
                                label = { Text(taste.name) },
                                leadingIcon = {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier.size(10.dp).background(
                                            color = Color(MarkerFactory.parseColor(taste.colorHex)),
                                            shape = CircleShape,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text, selected.toList()) }, enabled = text.isNotBlank()) { Text("加入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
