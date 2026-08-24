package com.tastemap.app

import com.tastemap.app.data.db.MealRecord
import com.tastemap.app.data.db.Shop
import com.tastemap.app.data.db.TasteTag
import com.tastemap.app.data.repository.DailyTasteRecommender
import com.tastemap.app.data.repository.SearchFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndRecommendTest {

    private fun shop(id: Long, name: String) = Shop(id = id, name = name, latitude = 0.0, longitude = 0.0)
    private fun record(id: Long, shopId: Long, dish: String, comment: String, rating: Int) =
        MealRecord(id = id, shopId = shopId, dishName = dish, comment = comment, rating = rating)
    private fun taste(id: Long, name: String) = TasteTag(id = id, name = name, colorHex = "#000000")

    private val shops = listOf(shop(1, "咸蛋黄大排档"), shop(2, "老王甜品"), shop(3, "川味小馆"))
    private val records = listOf(
        record(1, 1, "咸蛋黄焗虾", "流沙很香", 5),
        record(2, 2, "杨枝甘露", "甜而不腻", 4),
        record(3, 3, "水煮鱼", "麻上头", 5),
        record(4, 3, "担担面", "", 4),
    )
    private val refs = mapOf(1L to setOf(1L), 2L to setOf(2L), 3L to setOf(1L), 4L to setOf(1L))
    private val tastes = listOf(taste(1, "咸"), taste(2, "甜"), taste(3, "辣"))

    @Test
    fun `keyword matches shop dish and comment`() {
        val hits = SearchFilter.filter(shops, records, refs, tastes, "咸蛋黄", emptySet(), 0)
        assertEquals(listOf(1L), hits.map { it.shop.id })

        val byComment = SearchFilter.filter(shops, records, refs, tastes, "麻上头", emptySet(), 0)
        assertEquals(listOf(3L), byComment.map { it.shop.id })

        val blank = SearchFilter.filter(shops, records, refs, tastes, "", emptySet(), 0)
        assertEquals(3, blank.size)
    }

    @Test
    fun `taste filter and rating filter compose`() {
        val salty = SearchFilter.filter(shops, records, refs, tastes, "", setOf(2L), 0) // 甜
        assertEquals(listOf(2L), salty.map { it.shop.id })

        val highRated = SearchFilter.filter(shops, records, refs, tastes, "", emptySet(), 5) // 均分>=5（3号店均分4.5不算）
        assertEquals(listOf(1L), highRated.map { it.shop.id }.sorted())

        val saltyAndHigh = SearchFilter.filter(shops, records, refs, tastes, "", setOf(1L), 5)
        assertEquals(listOf(1L), saltyAndHigh.map { it.shop.id })
    }

    @Test
    fun `daily recommender rotates by day and offset`() {
        val usage = mapOf(1L to 3, 3L to 2)
        val a = checkNotNull(DailyTasteRecommender.recommend(tastes, usage, dayEpoch = 100, offset = 0))
        val b = checkNotNull(DailyTasteRecommender.recommend(tastes, usage, dayEpoch = 100, offset = 1))
        val c = checkNotNull(DailyTasteRecommender.recommend(tastes, usage, dayEpoch = 101, offset = 0))
        assertTrue(a.first.id != b.first.id) // 换一批生效
        assertTrue(a.first.id != c.first.id) // 日期轮换生效（used 池 2 个口味，必然不同）
        assertTrue(a.second.contains("咸") || a.second.contains("辣"))
    }

    @Test
    fun `recommender falls back to all tastes when no usage`() {
        val none = checkNotNull(DailyTasteRecommender.recommend(tastes, emptyMap(), dayEpoch = 1, offset = 0))
        assertTrue(none.second.contains("还没试过"))
    }
}
