package com.driveperf.feature.onboarding

/** Whether a checklist glyph says yes or no. The glyph is always paired with its text: colour is never the only signal. */
enum class Mark(val glyph: String) { Yes("✓"), No("✕"), Info("≈") }

/** How a page lays out: Skip on the intro pages, and centred text on the hero pages. */
data class PageStyle(val showSkip: Boolean = false, val centered: Boolean = false)
