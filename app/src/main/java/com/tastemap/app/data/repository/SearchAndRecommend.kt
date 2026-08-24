package com.tastemap.app.data.repository

import com.tastemap.app.data.db.MealRecord
import com.tastemap.app.data.db.Shop
import com.tastemap.app.data.db.TasteTag

/** F05 搜索结果：店铺粒度 + 命中摘要 */
data class SearchHit(
    val shop: Shop,
    val matchedText: String,
    val tasteNames: List<String>,
    val avgRating: Double,
    val recordCount: Int,
)

/**
 * F05 搜索筛选的纯逻辑（可单测）：关键词（店名/菜名/评价全文，不区分大小写）
 * + 口味筛选（任一记录挂了所选口味）+ 评分下限（店铺均分）。
 */
object SearchFilter {

    fun filter(
        shops: List<Shop>,
        records: List<MealRecord>,
        refs: Map<Long, Set<Long>>, // recordId -> tasteIds
        tastes: List<TasteTag>,
        query: String,
        tasteIds: Set<Long>,
        minRating: Int,
    ): List<SearchHit> {
        val recordsByShop = records.groupBy { it.shopId }
        val tasteNameById = tastes.associate { it.id to it.name }
        val q = query.trim().lowercase()

        return shops.mapNotNull { shop ->
            val shopRecords = recordsByShop[shop.id].orEmpty()
            val tasteNameSet = shopRecords.flatMap { refs[it.id].orEmpty() }.mapNotNull { tasteNameById[it] }.distinct()

            val textMatch = q.isEmpty() || shop.name.lowercase().contains(q) ||
                shopRecords.any { r ->
                    r.dishName.lowercase().contains(q) || r.comment.lowercase().contains(q)
                }
            val tasteMatch = tasteIds.isEmpty() ||
                shopRecords.any { r -> refs[r.id].orEmpty().any { it in tasteIds } }
            val avg = if (shopRecords.isEmpty()) 0.0 else shopRecords.sumOf { it.rating }.toDouble() / shopRecords.size
            val ratingMatch = minRating <= 0 || avg >= minRating

            if (!textMatch || !tasteMatch || ratingMatch.not()) return@mapNotNull null

            val matchedRecord = shopRecords.firstOrNull {
                q.isNotEmpty() && (it.dishName.lowercase().contains(q) || it.comment.lowercase().contains(q))
            }
            SearchHit(
                shop = shop,
                matchedText = matchedRecord?.let { listOf(it.dishName, it.comment).firstOrNull { s -> s.isNotBlank() } }
                    ?: shop.name,
                tasteNames = tasteNameSet,
                avgRating = avg,
                recordCount = shopRecords.size,
            )
        }
    }
}

/** F12 每日口味推荐：可解释的轮换规则（非算法黑盒），按日期轮换 + 手动换一批 */
object DailyTasteRecommender {

    /**
     * @param tasteUsage tasteId -> 该口味的记录数
     * @param offset 用户点"换一批"的次数
     * @return 推荐口味与推荐语（可解释：今天轮到它 / 你吃过多少次）
     */
    fun recommend(
        tastes: List<TasteTag>,
        tasteUsage: Map<Long, Int>,
        dayEpoch: Long,
        offset: Int,
    ): Pair<TasteTag, String>? {
        val used = tastes.filter { (tasteUsage[it.id] ?: 0) > 0 }
        val pool = used.ifEmpty { tastes }
        val pick = pool.getOrNull(((dayEpoch + offset).mod(pool.size.toLong())).toInt()) ?: return null
        val count = tasteUsage[pick.id] ?: 0
        val reason = if (count > 0) {
            "你打过 ${count} 次${pick.name}味，最近试试换个心情？"
        } else {
            "还没试过${pick.name}味，今天安排一顿？"
        }
        return pick to reason
    }
}
