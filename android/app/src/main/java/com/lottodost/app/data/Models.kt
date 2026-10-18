package com.lottodost.app.data

import kotlinx.serialization.Serializable

/** Mirrors pipeline/lottery_pipeline/build.py output (schema 2). Unknown keys are ignored. */
@Serializable
data class ResultFeed(
    val schema: Int,
    val generatedAt: String,
    val date: String? = null,
    val draws: List<Draw>,
)

@Serializable
data class DayFeed(val schema: Int, val date: String, val draws: List<Draw>)

@Serializable
data class IndexFeed(
    val schema: Int,
    val generatedAt: String,
    val dates: List<String> = emptyList(),
    val noDraw: List<String> = emptyList(),
    val upcoming: List<Upcoming> = emptyList(),
)

/** Last ~90 days of published numbers, for "when did my number win" search. */
@Serializable
data class HistoryFeed(val schema: Int, val generatedAt: String, val days: Int = 0, val draws: List<Draw> = emptyList())

@Serializable
data class Upcoming(val name: String, val date: String)

@Serializable
data class RemoteConfig(
    val schema: Int = 2,
    val generatedAt: String = "",
    val minVersionCode: Int = 0,
    val latestVersionCode: Int = 0,
    val mirrors: List<String> = emptyList(),
    val liveDrawUrl: String? = null,
    val officialResultsUrl: String? = null,
    val announcement: Announcement? = null,
    val claimFormUrl: String? = null,
)

@Serializable
data class Announcement(val text: String, val url: String? = null)

@Serializable
data class Draw(
    val id: String,
    val slot: String,
    val date: String,          // yyyy-MM-dd, IST
    val time: String,          // HH:mm, IST
    val state: String,
    val name: String,
    val drawNo: Int? = null,
    val format: String,        // "D5" (34A 12345), "D4" (Laxmi), "B6" (bumper A 123456)
    val price: Int,
    val special: Boolean = false,
    val verified: Boolean = false,
    val status: String = "OK", // "OK" or "PDF_ONLY" (numbers not machine-readable yet)
    val ocr: Boolean = false,  // numbers were OCR'd from the official scan: show caution
    val tiers: List<Tier> = emptyList(),
    val sources: List<Source> = emptyList(),
) {
    val hasNumbers: Boolean get() = status == "OK" && tiers.isNotEmpty()
    val officialPdf: String? get() = sources.firstOrNull { it.kind == "official" }?.url
    val sheetUrl: String? get() = sources.firstOrNull { it.kind == "lotterysambad" }?.url
    val firstPrize: String? get() = tiers.firstOrNull()?.numbers?.firstOrNull()
}

@Serializable
data class Tier(
    val rank: String,          // "1", "CONS", "2".."9"
    val prize: Long,           // rupees for the winner
    val match: String,         // FULL | CONS | EXACT | LAST5 | LAST4
    val numbers: List<String>,
)

@Serializable
data class Source(val kind: String, val url: String, val ocr: Boolean = false)
