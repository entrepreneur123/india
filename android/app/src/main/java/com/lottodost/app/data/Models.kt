package com.lottodost.app.data

import kotlinx.serialization.Serializable

/** Mirrors pipeline/lottery_pipeline/build.py output (schema 1). Unknown keys are ignored. */
@Serializable
data class ResultFeed(
    val schema: Int,
    val generatedAt: String,
    val draws: List<Draw>,
)

@Serializable
data class Draw(
    val id: String,
    val slot: String,
    val date: String,          // yyyy-MM-dd, IST
    val time: String,          // HH:mm, IST
    val state: String,
    val name: String,
    val drawNo: Int? = null,
    val format: String,        // "D5" (34A 12345) or "D4" (4-digit Laxmi)
    val price: Int,
    val verified: Boolean = false,
    val status: String = "OK", // "OK" or "PDF_ONLY" (numbers not machine-readable yet)
    val ocr: Boolean = false,  // numbers were OCR'd from the official scan: show caution
    val tiers: List<Tier> = emptyList(),
    val sources: List<Source> = emptyList(),
) {
    val hasNumbers: Boolean get() = status == "OK" && tiers.isNotEmpty()
    val officialPdf: String? get() = sources.firstOrNull { it.kind == "official" }?.url
    val firstPrize: String? get() = tiers.firstOrNull()?.numbers?.firstOrNull()
}

@Serializable
data class Tier(
    val rank: String,          // "1", "CONS", "2".."5"
    val prize: Long,           // rupees for the winner
    val match: String,         // FULL | CONS | LAST5 | LAST4
    val numbers: List<String>,
)

@Serializable
data class Source(val kind: String, val url: String, val ocr: Boolean = false)
