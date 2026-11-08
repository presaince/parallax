/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.source

import android.content.Context
import com.presaince.parallax.core.AppLog
import java.util.Locale
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/** One YouTube content region: the ISO code the extractor sends and the name people read. */
data class ContentRegion(val code: String, val name: String)

/** The flag emoji for a region code, built from its two regional indicators. */
fun ContentRegion.flag(): String = code.uppercase().map { letter ->
    val index = letter - 'A'
    if (index in 0..25) String(Character.toChars(0x1F1E6 + index)) else ""
}.joinToString("")

/**
 * The regions the app offers. Not the full ISO list: YouTube does not serve meaningful content
 * for every country, and a list of two hundred is a worse choice than a list of the usual thirty.
 */
val contentRegions: List<ContentRegion> = listOf(
    ContentRegion("UA", "Ukraine"),
    ContentRegion("GB", "United Kingdom"),
    ContentRegion("US", "United States"),
    ContentRegion("CA", "Canada"),
    ContentRegion("AU", "Australia"),
    ContentRegion("IE", "Ireland"),
    ContentRegion("DE", "Germany"),
    ContentRegion("FR", "France"),
    ContentRegion("ES", "Spain"),
    ContentRegion("IT", "Italy"),
    ContentRegion("NL", "Netherlands"),
    ContentRegion("BE", "Belgium"),
    ContentRegion("PL", "Poland"),
    ContentRegion("PT", "Portugal"),
    ContentRegion("SE", "Sweden"),
    ContentRegion("NO", "Norway"),
    ContentRegion("DK", "Denmark"),
    ContentRegion("FI", "Finland"),
    ContentRegion("CZ", "Czechia"),
    ContentRegion("SK", "Slovakia"),
    ContentRegion("HU", "Hungary"),
    ContentRegion("RO", "Romania"),
    ContentRegion("BG", "Bulgaria"),
    ContentRegion("GR", "Greece"),
    ContentRegion("TR", "Türkiye"),
    ContentRegion("IL", "Israel"),
    ContentRegion("IN", "India"),
    ContentRegion("JP", "Japan"),
    ContentRegion("KR", "South Korea"),
    ContentRegion("BR", "Brazil"),
    ContentRegion("MX", "Mexico"),
    ContentRegion("AR", "Argentina"),
    ContentRegion("ZA", "South Africa"),
    ContentRegion("NG", "Nigeria"),
    ContentRegion("EG", "Egypt"),
    ContentRegion("RU", "Russia"),
)

private const val PREFS = "parallax"
private const val KEY_REGION = "content_region"
private const val KEY_REGIONS = "content_regions"

/** Three is the cap: each extra region is another extraction pass on every search. */
const val MAX_CONTENT_REGIONS = 3

/** The chosen region, or the device's own country when the user has never chosen one. */
fun readContentRegion(context: Context): String {
    val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_REGION, null)
    if (!stored.isNullOrBlank()) return stored
    val locale = java.util.Locale.getDefault()
    return locale.country.ifBlank { "US" }
}

/**
 * Every region the app asks as, in order. The first is the main one: it is what the feed and
 * channel pages use, and it is the baseline the merged search results are ordered against.
 * Older builds stored one code under [KEY_REGION]; that reads as a list of one.
 */
fun readContentRegions(context: Context): List<String> {
    val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_REGIONS, null)
    val codes = stored
        ?.split(',')
        ?.map { it.trim().uppercase() }
        ?.filter { code -> contentRegions.any { it.code == code } }
        ?.distinct()
        .orEmpty()
    if (codes.isNotEmpty()) return codes.take(MAX_CONTENT_REGIONS)
    return listOf(readContentRegion(context))
}

fun writeContentRegions(context: Context, codes: List<String>) {
    val clean = codes
        .map { it.trim().uppercase() }
        .filter { code -> contentRegions.any { it.code == code } }
        .distinct()
        .take(MAX_CONTENT_REGIONS)
    if (clean.isEmpty()) return
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_REGIONS, clean.joinToString(","))
        .putString(KEY_REGION, clean.first())
        .apply()
}

/** Single-region write, kept for callers that only ever set one. Clears the extras. */
fun writeContentRegion(context: Context, code: String) {
    writeContentRegions(context, listOf(code))
}

/**
 * Points the extractor at [code]. NewPipe keeps the downloader, language and country globally,
 * so this is the one way any caller changes them; the search fan-out reuses it per region.
 */
fun initContentCountry(http: OkHttpClient, code: String, log: AppLog? = null) {
    log?.info("selfrepair", "init extractor region=$code")
    NewPipe.init(
        OkHttpDownloader(http, log),
        Localization.fromLocale(Locale.getDefault()),
        ContentCountry(code),
    )
}
