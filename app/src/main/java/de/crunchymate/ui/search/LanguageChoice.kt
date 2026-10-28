package de.crunchymate.ui.search

import de.crunchymate.core.ffi.LanguageFilter

/** Fasst die beiden Sub/Dub-Chips zum Kern-Filter zusammen; kein Chip aktiv heißt keine Einschränkung. */
fun languageFilterOf(
    sub: Boolean,
    dub: Boolean,
): LanguageFilter =
    when {
        sub && dub -> LanguageFilter.BOTH
        dub -> LanguageFilter.DUB
        sub -> LanguageFilter.SUB
        else -> LanguageFilter.ALL
    }

// ANY wird in der UI nicht angeboten und erscheint wie ALL (beide Chips aus); der Wert bleibt bis zum nächsten Tippen erhalten
fun LanguageFilter.requiresSub(): Boolean = this == LanguageFilter.SUB || this == LanguageFilter.BOTH

fun LanguageFilter.requiresDub(): Boolean = this == LanguageFilter.DUB || this == LanguageFilter.BOTH
