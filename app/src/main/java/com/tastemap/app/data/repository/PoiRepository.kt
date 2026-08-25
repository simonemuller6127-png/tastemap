package com.tastemap.app.data.repository

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** 高德 POI 搜索命中（R3 反馈：记录页"地图搜索定位"） */
data class PoiHit(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val poiId: String?,
)

/**
 * 高德搜索 SDK 封装（D16 补充：同属高德唯一网络依赖）。
 * 以地图中心为圆心就近搜索，结果直接可落卡。
 */
@Singleton
class PoiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun search(keyword: String, centerLat: Double, centerLng: Double, radiusM: Int = 5000): List<PoiHit> {
        if (keyword.isBlank()) return emptyList()
        return suspendCancellableCoroutine { cont ->
            runCatching {
                val query = PoiSearch.Query(keyword.trim(), "").apply {
                    pageSize = 15
                    pageNum = 1
                }
                val search = PoiSearch(context, query)
                search.bound = PoiSearch.SearchBound(LatLonPoint(centerLat, centerLng), radiusM)
                search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiSearched(result: PoiResult?, code: Int) {
                        val hits = result?.pois.orEmpty().map { poi ->
                            PoiHit(
                                name = poi.title ?: "",
                                address = poi.snippet ?: "",
                                latitude = poi.latLonPoint?.latitude ?: 0.0,
                                longitude = poi.latLonPoint?.longitude ?: 0.0,
                                poiId = poi.poiId,
                            )
                        }.filter { it.latitude != 0.0 }
                        if (cont.isActive) cont.resume(hits)
                    }

                    override fun onPoiItemSearched(item: com.amap.api.services.core.PoiItem?, code: Int) = Unit
                })
                search.searchPOIAsyn()
            }.onFailure {
                if (cont.isActive) cont.resume(emptyList())
            }
        }
    }
}
