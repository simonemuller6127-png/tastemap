package com.smellmap.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.smellmap.app.data.db.TasteTag
import com.smellmap.app.data.repository.ShopPin
import com.smellmap.app.map.MarkerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 定位失败时的兜底视野（M0：默认武汉，M1 记住上次视野） */
private val DEFAULT_CENTER = LatLng(30.59276, 114.30525)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapHomeScreen(vm: MapHomeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val pins by vm.pins.collectAsStateWithLifecycle()
    val tastes by vm.tastes.collectAsStateWithLifecycle()
    val pending by vm.pendingPin.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    // F06 备份导出/导入（SAF，无任何存储权限）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(vm::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importBackup) }

    var aMapRef by remember { mutableStateOf<AMap?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var awaitingLocationMove by remember { mutableStateOf(false) }
    val markers = remember { mutableStateListOf<Marker>() }

    // 定位权限：仅前台（PRD 隐私要求），授权后把相机移到当前位置
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted.values.any { it }) awaitingLocationMove = true }

    LaunchedEffect(Unit) {
        if (context.hasLocationPermission()) {
            awaitingLocationMove = true
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
    LaunchedEffect(aMapRef, awaitingLocationMove) {
        if (awaitingLocationMove) {
            awaitingLocationMove = false
            moveToCurrentLocation(context, aMapRef)
        }
    }

    // 图钉随数据流刷新。M0 全量重画（数据量小），M3 按 D12 做 diff + 分档位图
    LaunchedEffect(pins, aMapRef) {
        val map = aMapRef ?: return@LaunchedEffect
        markers.forEach { it.remove() }
        markers.clear()
        pins.forEach { pin ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(pin.shop.latitude, pin.shop.longitude))
                    .icon(MarkerFactory.descriptor(pin.colorHex, 34, density))
                    .anchor(0.5f, 0.95f)
                    .title(pin.shop.name)
                    .snippet(pinSnippet(pin)),
            )?.let(markers::add)
        }
    }

    // MapView 生命周期桥接 Compose
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapViewRef?.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapViewRef?.onDestroy() }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("味觉地图") },
                actions = {
                    IconButton(onClick = {
                        val name = "tastemap-" +
                            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".tastemap"
                        exportLauncher.launch(name)
                    }) { Icon(Icons.Outlined.SaveAlt, contentDescription = "导出备份") }
                    IconButton(onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                            ),
                        )
                    }) { Icon(Icons.Outlined.FileOpen, contentDescription = "导入备份") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        onCreate(null)
                        map.uiSettings.isRotateGesturesEnabled = false
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 11f))
                        map.setOnMapLongClickListener { latLng ->
                            vm.onMapLongPress(latLng.latitude, latLng.longitude)
                        }
                        aMapRef = map
                        mapViewRef = this
                    }
                },
            )
            if (pins.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        "长按地图任意位置，新建一条美食记录",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    pending?.let { p ->
        NewRecordSheet(
            latitude = p.latitude,
            longitude = p.longitude,
            tastes = tastes,
            onDismiss = vm::dismissPinSheet,
            onSave = vm::saveRecord,
        )
    }
}

/** F02 最小新建记录表单：店名/菜名/评分/口味多选/一句话评价 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewRecordSheet(
    latitude: Double,
    longitude: Double,
    tastes: List<TasteTag>,
    onDismiss: () -> Unit,
    onSave: (shopName: String, address: String, dishName: String, rating: Int, comment: String, tasteIds: List<Long>) -> Unit,
) {
    var shopName by remember { mutableStateOf("") }
    var dishName by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(4f) }
    var comment by remember { mutableStateOf("") }
    val selectedTastes = remember { mutableStateListOf<Long>() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("新建美食记录", style = MaterialTheme.typography.titleLarge)
            Text(
                "坐标：%.5f, %.5f（离线保存）".format(latitude, longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = shopName,
                onValueChange = { shopName = it },
                label = { Text("店名 *") },
                singleLine = true,
            )
            OutlinedTextField(
                value = dishName,
                onValueChange = { dishName = it },
                label = { Text("菜名（如：咸蛋黄焗虾）") },
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("评分")
                Text(
                    " ${rating.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = rating,
                onValueChange = { rating = it },
                valueRange = 1f..5f,
                steps = 3,
            )
            Text("口味（可多选，决定图钉颜色）")
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
                                Modifier
                                    .size(10.dp)
                                    .background(
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
            )
            Button(
                onClick = {
                    onSave(
                        shopName.trim(),
                        "",
                        dishName.trim(),
                        rating.toInt(),
                        comment.trim(),
                        selectedTastes.toList(),
                    )
                },
                enabled = shopName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
}

private fun pinSnippet(pin: ShopPin): String = buildString {
    pin.tasteName?.let { append(it).append(" · ") }
    append("${pin.recordCount} 次打卡")
    if (pin.recordCount > 0) append(" · 评分 %.1f".format(pin.avgRating))
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** 一次性定位并移动相机；失败回默认视野（离线可用性：F02 离线建点不依赖定位成功） */
private fun moveToCurrentLocation(context: Context, map: AMap?) {
    if (map == null) return
    if (!context.hasLocationPermission()) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 11f))
        return
    }
    try {
        val client = AMapLocationClient(context)
        client.setLocationListener { location ->
            if (location.errorCode == 0) {
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        15f,
                    ),
                )
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 11f))
            }
            client.stopLocation()
            client.onDestroy()
        }
        client.setLocationOption(
            AMapLocationClientOption().apply {
                isOnceLocation = true
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            },
        )
        client.startLocation()
    } catch (_: Exception) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 11f))
    }
}
