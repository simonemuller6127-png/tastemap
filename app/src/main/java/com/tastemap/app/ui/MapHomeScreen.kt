package com.tastemap.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableStateMapOf
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
import com.tastemap.app.data.db.TasteTag
import com.tastemap.app.data.repository.ShopPin
import com.tastemap.app.map.MarkerFactory
import com.tastemap.app.map.StickerFactory
import com.tastemap.app.map.StickerMath
import com.tastemap.app.sticker.StickerFilters
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 定位失败时的兜底视野（M0：默认武汉，M1 记住上次视野） */
private val DEFAULT_CENTER = LatLng(30.59276, 114.30525)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapHomeScreen(
    onCreateRecord: (latitude: Double, longitude: Double, shopName: String?) -> Unit,
    onOpenShop: (shopId: Long) -> Unit,
    onOpenSettings: () -> Unit,
    vm: MapHomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val pins by vm.pins.collectAsStateWithLifecycle()
    val tastes by vm.tastes.collectAsStateWithLifecycle()
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

    // F18b 连续缩放（R3 二次反馈）：贴纸尺寸随 zoom 连续插值，滑动中节流 120ms 刷新，
    // 就地 setIcon——消灭"一跳一跳"的档位切换
    var zoom by remember { mutableStateOf(13.5) }
    var lastIconRefresh by remember { mutableStateOf(0L) }

    // 贴纸引擎 + 照片位图缓存（D17）。贴纸照片先过水彩滤镜（D13 降级链的手绘化）
    val stickerFactory = remember { StickerFactory(density) }
    val photoBitmapCache = remember { HashMap<String, android.graphics.Bitmap?>() }
    val filesDir = context.filesDir
    fun stickerSource(pin: ShopPin): android.graphics.Bitmap? {
        val path = pin.firstPhotoPath ?: return null
        if (!photoBitmapCache.containsKey(path)) {
            photoBitmapCache[path] = runCatching {
                val raw = android.graphics.BitmapFactory.decodeFile(
                    java.io.File(filesDir, path).path,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 },
                )
                raw?.let { StickerFilters.apply(it, StickerFilters.Style.WATERCOLOR) }
            }.getOrNull()
        }
        return photoBitmapCache[path]
    }

    val markerShopIds = remember { mutableStateMapOf<Marker, Long>() }

    /** 低倍视野只显示打卡 Top N，其余淡出（F18b：避免贴纸糊满屏） */
    fun visiblePins(): List<ShopPin> {
        val limit = StickerMath.visibleLimitForZoom(zoom)
        if (pins.size <= limit) return pins
        return pins.sortedWith(
            compareByDescending<ShopPin> { it.recordCount }.thenByDescending { it.avgRating },
        ).take(limit)
    }

    /** 按当前 zoom 的目标尺寸增量同步贴纸（就地 setIcon，不重建 Marker） */
    fun syncMarkers(map: AMap) {
        val targetPx = (StickerMath.sizeDpForZoom(zoom) * density).toInt().coerceAtLeast(12)
        val visible = visiblePins()
        val visibleIds = visible.map { it.shop.id }.toSet()
        markers.removeAll { marker ->
            val id = markerShopIds[marker]
            if (id == null || id !in visibleIds) {
                marker.remove()
                markerShopIds.remove(marker)
                true
            } else {
                false
            }
        }
        visible.forEach { pin ->
            val icon = stickerFactory.descriptorAt(pin.shop.id, pin.colorHex, stickerSource(pin), targetPx)
            val existing = markers.firstOrNull { markerShopIds[it] == pin.shop.id }
            if (existing == null) {
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(pin.shop.latitude, pin.shop.longitude))
                        .icon(icon)
                        .anchor(0.5f, 0.5f)
                        .title(pin.shop.name)
                        .snippet(pinSnippet(pin)),
                )?.let { marker ->
                    markers.add(marker)
                    markerShopIds[marker] = pin.shop.id
                }
            } else {
                existing.setIcon(icon)
            }
        }
    }

    LaunchedEffect(pins, aMapRef) {
        aMapRef?.let(::syncMarkers)
    }
    LaunchedEffect(zoom) {
        aMapRef?.let(::syncMarkers)
    }

    // F18 手绘纸面底图：开关在设置页，样式文件随包分发（R3 现场调参）
    val styleEnabled by remember {
        com.tastemap.app.util.Prefs(context).handdrawnMapStyle
    }.collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(aMapRef, styleEnabled) {
        val map = aMapRef ?: return@LaunchedEffect
        runCatching {
            val options = com.amap.api.maps.model.CustomMapStyleOptions()
            if (styleEnabled) {
                // 样式字节直读 assets：随包版本走，无 filesDir 落地拷贝的陈旧文件问题
                val data = context.assets.open("mapstyle/handdrawn.json").use { it.readBytes() }
                options.setEnable(true).setStyleData(data)
            } else {
                options.setEnable(false)
            }
            map.setCustomMapStyle(options)
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

    // R3 反馈：一键在当前位置建卡（不用长按找位置）
    fun recordHere() {
        if (!context.hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return
        }
        // 先用系统最后已知位置秒回（GPS/网络缓存），拿不到再异步定位
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val last = runCatching {
            lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER)
        }.getOrNull()
        if (last != null) {
            onCreateRecord(last.latitude, last.longitude, null)
            return
        }
        runCatching {
            val client = AMapLocationClient(context)
            client.setLocationListener { loc ->
                if (loc.errorCode == 0) onCreateRecord(loc.latitude, loc.longitude, null)
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
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = ::recordHere,
                icon = { Icon(Icons.Outlined.MyLocation, contentDescription = null) },
                text = { Text("在此记录") },
            )
        },
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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "口味管理")
                    }
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
                            onCreateRecord(latLng.latitude, latLng.longitude, null)
                        }
                        map.setOnInfoWindowClickListener { marker ->
                            markerShopIds[marker]?.let(onOpenShop)
                        }
                        // R3 反馈：点击地图上任何 POI（店铺名）直接建卡，预填店名
                        map.setOnPOIClickListener { poi ->
                            onCreateRecord(poi.coordinate.latitude, poi.coordinate.longitude, poi.name)
                        }
                        // R3 反馈④：定位蓝点 + SDK 托管定位（首次定位成功自动把相机移过去）
                        if (ctx.hasLocationPermission()) {
                            runCatching {
                                map.myLocationStyle = com.amap.api.maps.model.MyLocationStyle()
                                    .myLocationType(com.amap.api.maps.model.MyLocationStyle.LOCATION_TYPE_LOCATE)
                                map.isMyLocationEnabled = true
                                map.uiSettings.isMyLocationButtonEnabled = false
                            }
                        }
                        map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
                            override fun onCameraChange(position: com.amap.api.maps.model.CameraPosition?) {
                                position ?: return
                                val now = System.currentTimeMillis()
                                if (now - lastIconRefresh > 120) {
                                    lastIconRefresh = now
                                    zoom = position.zoom.toDouble()
                                }
                            }

                            override fun onCameraChangeFinish(position: com.amap.api.maps.model.CameraPosition?) {
                                position ?: return
                                zoom = position.zoom.toDouble()
                                lastIconRefresh = 0L
                            }
                        })
                        aMapRef = map
                        mapViewRef = this
                    }
                },
            )
            // F18 纸面感（R3 二次反馈）：样式换色之上再叠纸纹（正片叠底），纹理才是纸感的来源
            if (styleEnabled) {
                val overlayTile = remember { com.tastemap.app.ui.widget.mapPaperOverlayTile() }
                Canvas(Modifier.fillMaxSize()) {
                    val tileInt = (96.dp.toPx()).toInt().coerceAtLeast(8)
                    var y = 0
                    while (y < size.height.toInt()) {
                        var x = 0
                        while (x < size.width.toInt()) {
                            drawImage(
                                overlayTile,
                                dstOffset = androidx.compose.ui.unit.IntOffset(x, y),
                                dstSize = androidx.compose.ui.unit.IntSize(tileInt, tileInt),
                                alpha = 0.5f,
                                blendMode = androidx.compose.ui.graphics.BlendMode.Multiply,
                            )
                            x += tileInt
                        }
                        y += tileInt
                    }
                }
            }
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
