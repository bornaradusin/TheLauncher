package app.thelauncher.hotseat

/**
 * Hyperion's two dock styles, as presets over the dock background's corner radius and insets.
 * "Flat" is a plain bar to the screen edges; "Round" is a rounded panel floating above the
 * navigation bar.
 */
const val DOCK_SHAPE_FLAT = "flat"
const val DOCK_SHAPE_ROUND = "round"
const val DOCK_SHAPE_CUSTOM = "custom"

data class DockShape(
    val cornerRadiusDp: Float,
    val insetLeft: Int,
    val insetTop: Int,
    val insetRight: Int,
    val insetBottom: Int,
)

val dockShapePresets = mapOf(
    DOCK_SHAPE_FLAT to DockShape(cornerRadiusDp = 0f, insetLeft = 0, insetTop = 0, insetRight = 0, insetBottom = 0),
    DOCK_SHAPE_ROUND to DockShape(cornerRadiusDp = 28f, insetLeft = 40, insetTop = 0, insetRight = 40, insetBottom = 40),
)

/** The preset these values match, or [DOCK_SHAPE_CUSTOM] once the user has changed any of them. */
fun DockShape.presetName(): String =
    dockShapePresets.entries.firstOrNull { it.value == this }?.key ?: DOCK_SHAPE_CUSTOM
