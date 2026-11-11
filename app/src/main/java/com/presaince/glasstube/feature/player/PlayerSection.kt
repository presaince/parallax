/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.player

/**
 * The parts of the video page below the picture. The order is the reader's, kept in Settings, and
 * this enum is the one name each part has: the player draws it, the settings list shows it, and a
 * backup carries the name rather than an index, so inserting a section later cannot silently
 * reshuffle everyone's page.
 */
enum class PlayerSection(val label: String) {
    Description("Description"),
    Chapters("Chapters"),
    Sponsor("Sponsor segments"),
    Notes("My notes"),
    Comments("Comments"),
    UpNext("Up next"),
}

/** What a page shows before anyone reorders it: the description first, then the rest. */
val DefaultSectionOrder: List<PlayerSection> = listOf(
    PlayerSection.Description,
    PlayerSection.Chapters,
    PlayerSection.Sponsor,
    PlayerSection.Notes,
    PlayerSection.Comments,
    PlayerSection.UpNext,
)

/**
 * The stored order, made safe: names that are not sections are dropped (a build that removed one,
 * a hand-edited pref), duplicates keep their first place, and a section the stored list never
 * mentions is appended in its default position relative to the others. A page can therefore never
 * lose a section to a stale pref.
 */
fun resolveSectionOrder(stored: Collection<String>): List<PlayerSection> {
    val known = PlayerSection.entries.associateBy { it.name }
    val out = LinkedHashSet<PlayerSection>()
    for (name in stored) {
        known[name]?.let { out += it }
    }
    DefaultSectionOrder.filter { it !in out }.forEach { out += it }
    return out.toList()
}

/** The pref's shape: names, comma-separated, in the reader's order. */
fun sectionOrderPref(order: List<PlayerSection>): String = order.joinToString(",") { it.name }
