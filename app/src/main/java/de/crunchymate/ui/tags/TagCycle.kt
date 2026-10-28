package de.crunchymate.ui.tags

import de.crunchymate.core.ffi.TagSelection
import de.crunchymate.core.ffi.TagState

// Kein else-Zweig: ein neuer TagState soll die Kompilierung brechen
fun nextTagState(current: TagState?): TagState? =
    when (current) {
        null -> TagState.PREFER
        TagState.PREFER -> TagState.MUST
        TagState.MUST -> TagState.EXCLUDE
        TagState.EXCLUDE -> null
    }

fun List<TagSelection>.stateOf(name: String): TagState? = firstOrNull { it.name == name }?.state

/** Schaltet das Tag einen Zustand weiter; die Position in der Liste bleibt erhalten. */
fun List<TagSelection>.cycle(name: String): List<TagSelection> {
    val next = nextTagState(stateOf(name))
    return when {
        next == null -> filterNot { it.name == name }
        stateOf(name) == null -> this + TagSelection(name, next)
        else -> map { if (it.name == name) it.copy(state = next) else it }
    }
}
