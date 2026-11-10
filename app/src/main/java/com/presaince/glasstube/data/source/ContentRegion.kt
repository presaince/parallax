/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.source

import android.content.Context
import com.presaince.glasstube.core.AppLog
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

private const val PREFS = "glasstube"
private const val KEY_REGION = "content_region"
private const val KEY_REGIONS = "content_regions"

/** Three is the cap: each extra region is another extraction pass on every search. */
const val MAX_CONTENT_REGIONS = 3

/**
 * The choice that is not a country: ask as the phone. It is the default, because guessing a
 * country for someone is worse than asking none, and a phone whose language is English (US) is
 * not evidence that its owner is in the United States.
 */
const val SYSTEM_REGION = "SYSTEM"

/** Every option the picker draws, in the order it draws them: the phone, then the countries. */
val regionChoices: List<ContentRegion> =
    listOf(ContentRegion(SYSTEM_REGION, "System")) + contentRegions

/**
 * The country codes to actually ask as. System resolves to the phone's own country; a phone with
 * no country yields one empty code, which means "send no gl at all" rather than pretending to be
 * somewhere.
 */
fun resolvedRegions(context: Context): List<String> {
    val chosen = readContentRegions(context)
    if (SYSTEM_REGION !in chosen) return chosen
    val country = Locale.getDefault().country.uppercase()
    return if (country.length == 2) listOf(country) else listOf("")
}

/** The main region, resolved. Empty means the request carries no country. */
fun readContentRegion(context: Context): String =
    resolvedRegions(context).firstOrNull().orEmpty()

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
        ?.filter { code -> code == SYSTEM_REGION || contentRegions.any { it.code == code } }
        ?.distinct()
        .orEmpty()
    if (codes.isNotEmpty()) return codes.take(MAX_CONTENT_REGIONS)
    return listOf(SYSTEM_REGION)
}

fun writeContentRegions(context: Context, codes: List<String>) {
    val clean = codes
        .map { it.trim().uppercase() }
        .filter { code -> code == SYSTEM_REGION || contentRegions.any { it.code == code } }
        .distinct()
        .take(MAX_CONTENT_REGIONS)
    val chosen = clean.ifEmpty { listOf(SYSTEM_REGION) }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_REGIONS, chosen.joinToString(","))
        .putString(KEY_REGION, chosen.first())
        .apply()
}

/** Single-region write, kept for callers that only ever set one. Clears the extras. */
fun writeContentRegion(context: Context, code: String) {
    writeContentRegions(context, listOf(code))
}

/**
 * What one tap on a region card does: off when it is on, on when there is room, and no change
 * when the cap is reached. Turning the last country off lands on System rather than refusing,
 * because "no country" is a real answer and the only one that never guesses. Shared by Settings
 * and the wizard.
 */
fun toggleRegion(
    chosen: List<String>,
    code: String,
    max: Int = MAX_CONTENT_REGIONS,
): List<String> = when {
    code == SYSTEM_REGION -> listOf(SYSTEM_REGION)
    SYSTEM_REGION in chosen -> listOf(code)
    code in chosen -> if (chosen.size <= 1) {
        listOf(SYSTEM_REGION)
    } else {
        chosen.filterNot { it == code }
    }
    chosen.size < max -> chosen + code
    else -> chosen
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
