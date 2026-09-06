package com.tracksnatcher.app.ui.navigation

/** How MainActivity was entered — drives whether we jump straight into capture. */
sealed interface CaptureLaunch {
    data object None : CaptureLaunch
    /** From widget / App Action; [targetPlaylistName] pre-selects a playlist when provided. */
    data class Immediate(val targetPlaylistName: String?) : CaptureLaunch
}

/** Navigation destinations (kept as simple string routes for a small app). */
object Routes {
    const val CAPTURE = "capture"
    const val MANUAL = "manual"
    const val SETTINGS = "settings"
}
