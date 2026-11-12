package app.thelauncher.extras

import kotlinx.serialization.Serializable

@Serializable
data class DrawerTab(
    val name: String,
    val apps: Set<String> = emptySet(),
)
