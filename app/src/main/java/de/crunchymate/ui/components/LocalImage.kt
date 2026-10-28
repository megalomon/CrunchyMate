package de.crunchymate.ui.components

import java.io.File

/** Coil-Modell für eine lokale Bilddatei; ohne Pfad (noch nicht geladen) zeigt Coil den Platzhalter. */
fun localImageModel(path: String): File? = path.takeIf { it.isNotEmpty() }?.let(::File)
