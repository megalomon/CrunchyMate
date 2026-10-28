package de.crunchymate.ui

import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute

@Serializable
data object WatchedRoute

@Serializable
data object MehRoute

@Serializable
data object SettingsRoute

@Serializable
data class DetailRoute(
    val id: String,
)
