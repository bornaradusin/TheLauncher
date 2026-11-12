package app.thelauncher.smartspace.model

/** Details providers put in [SmartspaceAction.extras] for the At a Glance widget. */
object GlanceExtras {
    /** When a calendar event starts (Long, ms). */
    const val BEGIN = "glance_begin"

    /** When an alarm goes off (Long, ms). */
    const val TRIGGER = "glance_trigger"

    /** Battery level in percent (Int). */
    const val LEVEL = "glance_level"

    /** Whether the phone is charging (Boolean). */
    const val CHARGING = "glance_charging"

    /** When something started, such as the flashlight (Long, ms). */
    const val SINCE = "glance_since"

    /** Weather condition with the day's high and low (String). */
    const val DETAIL = "glance_detail"

    /** Album art (Bitmap). */
    const val ART = "glance_art"
}
