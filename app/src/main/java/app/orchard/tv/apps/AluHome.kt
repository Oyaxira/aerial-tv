package app.orchard.tv.apps

/** Personal Home is a small shelf; the complete, editable arrangement remains the app grid. */
object AluHome {
    const val ALL_APPS = "navigation:all-apps"
    val favorites = listOf(
        "com.xiaodianshi.tv.yst",
        "com.jamdeo.tv.mediacenter",
        "com.hisense.multiscreen.screenhelper",
        "com.hisense.singlelisten",
    )

    fun <T> shelf(items: List<T>, allApps: T): List<T> = items.take(5) + allApps

    /** Grid navigation clamps the last, incomplete row instead of losing focus below the viewport. */
    fun verticalTarget(index: Int, count: Int, columns: Int, down: Boolean): Int {
        if (count == 0) return -1
        if (down && index / columns == (count - 1) / columns) return index
        return (index + if (down) columns else -columns).coerceIn(0, count - 1)
    }

    /** Minimal scroll needed for a focused row, keeping already-visible rows stationary. */
    fun scrollToReveal(current: Float, rowTop: Float, rowBottom: Float,
        viewportTop: Float, viewportBottom: Float, maximum: Float): Float {
        val wanted = when {
            rowBottom - current > viewportBottom -> rowBottom - viewportBottom
            rowTop - current < viewportTop -> rowTop - viewportTop
            else -> current
        }
        return wanted.coerceIn(0f, maximum.coerceAtLeast(0f))
    }

}
