package com.ollie.iptv.ui.adaptive

/**
 * Width breakpoints in dp. The thresholds are intentionally independent of
 * device names: a resizable window can move between classes at runtime.
 */
enum class OllieWindowClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

fun ollieWindowClassForWidth(widthDp: Int): OllieWindowClass = when {
    widthDp < 600 -> OllieWindowClass.COMPACT
    widthDp < 840 -> OllieWindowClass.MEDIUM
    else -> OllieWindowClass.EXPANDED
}

/** Live TV gets a split channel/EPG layout once the content width is practical. */
fun ollieUsesTwoPaneLiveLayout(widthDp: Int): Boolean = widthDp >= 720
