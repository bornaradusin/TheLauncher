package app.thelauncher.seerr

/** Whether a search adds to Seerr or deletes from it. */
enum class SeerrMode { ADD, DELETE }

/** A search that starts with "add" or "request" (adding) or "delete" or "remove" (deleting). */
data class SeerrQuery(val mode: SeerrMode, val term: String) {
    companion object {
        private val ADD_WORDS = listOf("add", "request")
        private val DELETE_WORDS = listOf("delete", "remove")

        fun parse(query: String): SeerrQuery? {
            val text = query.trimStart()
            val space = text.indexOf(' ')
            if (space <= 0) return null
            val word = text.substring(0, space).lowercase()
            val term = text.substring(space + 1).trim()
            if (term.length < 2) return null
            return when (word) {
                in ADD_WORDS -> SeerrQuery(SeerrMode.ADD, term)
                in DELETE_WORDS -> SeerrQuery(SeerrMode.DELETE, term)
                else -> null
            }
        }
    }
}
