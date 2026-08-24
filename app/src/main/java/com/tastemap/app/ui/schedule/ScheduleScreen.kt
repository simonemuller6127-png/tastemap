package com.tastemap.app.ui.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tastemap.app.data.db.ScheduleItem
import com.tastemap.app.data.db.WishlistItem
import com.tastemap.app.data.repository.ScheduleRepository
import com.tastemap.app.data.repository.WishlistRepository
import com.tastemap.app.data.schedule.ReminderScheduler
import com.tastemap.app.data.db.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val wishlistRepository: WishlistRepository,
    private val reminderScheduler: ReminderScheduler,
    db: AppDatabase,
) : ViewModel() {

    data class SlotItem(val item: ScheduleItem, val displayName: String)

    val selectedDate = MutableStateFlow(LocalDate.now())
    val slots = ScheduleRepository.MEAL_SLOTS
    private val shopDao = db.shopDao()

    val dayItems: StateFlow<Map<String, List<SlotItem>>> = selectedDate.flatMapLatest { date ->
        combine(scheduleRepository.observeByDate(date), shopDao.observeAll()) { items, shops ->
            val names = shops.associate { it.id to it.name }
            items.groupBy { it.mealSlot }.mapValues { (_, list) ->
                list.map { SlotItem(it, it.shopId?.let(names::get) ?: it.note.ifBlank { "未命名" }) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val wishlist: StateFlow<List<WishlistItem>> = wishlistRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun addNote(slot: String, note: String) {
        if (note.isBlank()) return
        viewModelScope.launch { scheduleRepository.add(date = selectedDate.value, mealSlot = slot, note = note.trim()) }
    }

    fun addFromWishlist(slot: String, wish: WishlistItem) {
        viewModelScope.launch {
            scheduleRepository.add(date = selectedDate.value, mealSlot = slot, note = wish.text)
        }
    }

    fun setReminder(item: ScheduleItem, on: Boolean) {
        viewModelScope.launch {
            scheduleRepository.setReminder(item.id, on)
            if (on) {
                reminderScheduler.schedule(item.id, LocalDate.parse(item.date), item.mealSlot, item.note.ifBlank { "美食日程" })
            } else {
                reminderScheduler.cancel(item.id)
            }
        }
    }

    fun remove(item: ScheduleItem) {
        viewModelScope.launch {
            reminderScheduler.cancel(item.id)
            scheduleRepository.remove(item.id)
        }
    }
}

/** F08 美食日程：按日期的早/午/晚/夜宵四餐格 + 到点本地提醒 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(vm: ScheduleViewModel = hiltViewModel()) {
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val dayItems by vm.dayItems.collectAsStateWithLifecycle()
    val wishlist by vm.wishlist.collectAsStateWithLifecycle()
    var addSlot by remember { mutableStateOf<String?>(null) }
    var pendingReminder by remember { mutableStateOf<ScheduleItem?>(null) }
    val context = LocalContext.current

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val item = pendingReminder
        pendingReminder = null
        if (granted && item != null) vm.setReminder(item, true)
    }

    LaunchedEffect(Unit) { ReminderScheduler.ensureChannel(context) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("美食日程") }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 日期条：昨天起 7 天
            Row(
                Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (-1..5).forEach { offset ->
                    val d = LocalDate.now().plusDays(offset.toLong())
                    FilterChip(
                        selected = d == date,
                        onClick = { vm.selectDate(d) },
                        label = { Text(dateLabel(d)) },
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.slots.size) { slotIndex ->
                    val slot = vm.slots[slotIndex]
                    val slotList = dayItems[slot].orEmpty()
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(slot, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { addSlot = slot }) {
                                    Icon(Icons.Default.Add, contentDescription = "添加到$slot")
                                }
                            }
                            if (slotList.isEmpty()) {
                                Text("空着", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                slotList.forEach { slotItem ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(slotItem.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        Switch(
                                            checked = slotItem.item.reminderOn,
                                            onCheckedChange = { on ->
                                                if (on && Build.VERSION.SDK_INT >= 33 &&
                                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                                ) {
                                                    pendingReminder = slotItem.item
                                                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                } else {
                                                    vm.setReminder(slotItem.item, on)
                                                }
                                            },
                                        )
                                        IconButton(onClick = { vm.remove(slotItem.item) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    addSlot?.let { slot ->
        AddScheduleDialog(
            slot = slot,
            wishlist = wishlist,
            onDismiss = { addSlot = null },
            onNote = { note ->
                vm.addNote(slot, note)
                addSlot = null
            },
            onWish = { wish ->
                vm.addFromWishlist(slot, wish)
                addSlot = null
            },
        )
    }
}

@Composable
private fun AddScheduleDialog(
    slot: String,
    wishlist: List<WishlistItem>,
    onDismiss: () -> Unit,
    onNote: (String) -> Unit,
    onWish: (WishlistItem) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安排到$slot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wishlist.isNotEmpty()) {
                    Text("从想吃清单选：", style = MaterialTheme.typography.labelMedium)
                    wishlist.take(5).forEach { wish ->
                        TextButton(onClick = { onWish(wish) }) { Text(wish.text) }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("或直接写（如：公司楼下的热干面）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onNote(note) }, enabled = note.isNotBlank()) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private val shortFormat = DateTimeFormatter.ofPattern("M/d")

private fun dateLabel(d: LocalDate): String = when (d) {
    LocalDate.now() -> "今天"
    LocalDate.now().plusDays(1) -> "明天"
    else -> d.format(shortFormat)
}
