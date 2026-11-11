package com.driveperf.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.PathEffect

/**
 * The "estimated" token (A6, F-DQ-05 AC2): estimated values look different everywhere. Inline numbers get a "≈"
 * prefix at 60 % opacity (`.dp-est`); lines and strips are dashed 4/4 at 60 % (`--dp-est-dash`,
 * `--dp-data-estimated-opacity`).
 */
public object DpEstimated {
    /** `--dp-est-opacity` / `--dp-data-estimated-opacity`. */
    public const val ALPHA: Float = 0.6f

    /** "≈" and a thin space. */
    public const val PREFIX: String = "≈ "

    /** `--dp-est-dash` (4 on, 4 off) at [density] pixels per dp. */
    public fun dash(density: Float): PathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * density, 4f * density))

    /** [text] marked as estimated: "≈ 0.4". */
    public fun text(text: String): String = PREFIX + text
}

/** 60 % opacity when [estimated]; pair it with [DpEstimated.text]. */
public fun Modifier.dpEstimated(estimated: Boolean = true): Modifier = if (estimated) alpha(DpEstimated.ALPHA) else this
