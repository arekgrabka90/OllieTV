package com.ollie.iptv.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

class OllieWindowClassTest {
    @Test fun compactWindowsUseBottomNavigationBreakpoint() {
        assertEquals(OllieWindowClass.COMPACT, ollieWindowClassForWidth(0))
        assertEquals(OllieWindowClass.COMPACT, ollieWindowClassForWidth(599))
    }

    @Test fun mediumWindowsUseNavigationRailBreakpoint() {
        assertEquals(OllieWindowClass.MEDIUM, ollieWindowClassForWidth(600))
        assertEquals(OllieWindowClass.MEDIUM, ollieWindowClassForWidth(839))
    }

    @Test fun tabletLiveLayoutUsesTwoPaneAtSevenHundredTwentyDp() {
        assertEquals(false, ollieUsesTwoPaneLiveLayout(719))
        assertEquals(true, ollieUsesTwoPaneLiveLayout(720))
        assertEquals(true, ollieUsesTwoPaneLiveLayout(840))
    }

    @Test fun expandedWindowsUseFullNavigationRail() {
        assertEquals(OllieWindowClass.EXPANDED, ollieWindowClassForWidth(840))
        assertEquals(OllieWindowClass.EXPANDED, ollieWindowClassForWidth(1200))
    }
}
