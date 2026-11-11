package com.driveperf.pipeline.score

import com.driveperf.testing.SyntheticDrive

/** The §8.3 scenarios, built with the shared generator. */
internal object Goldens {
    fun trace(s: SyntheticDrive.Signals): R1Trace = R1Trace(
        lonG = s.lonG,
        latG = s.latG,
        vertG = s.vertG,
        yawDps = s.yawDps,
        quality = IntArray(s.lonG.size),
        gnss = s.gnssSpeedMps.mapIndexed { i, v -> R1Fix(i.toDouble(), v) },
        fs = s.fs,
    )

    @Suppress("LongParameterList")
    fun city(j: Double, pa: Double, pb: Double, corner: Double = 0.15, cj: Double = j, rolling: Boolean = false): R1Trace =
        trace(SyntheticDrive().apply { city(j, pa, pb, corner, cj, rolling) }.build())

    val g01: R1Trace get() = city(0.6, .15, .15)
    val g02: R1Trace get() = city(1.5, .18, .20)
    val g03: R1Trace get() = city(3.0, .25, .28)
    val g04: R1Trace get() = city(6.0, .35, .45, corner = .42, cj = 6.0)
    val g06a: R1Trace get() = city(2.5, .20, .25)
    val g06b: R1Trace get() = city(2.5, .20, .25, rolling = true)

    val g05: R1Trace
        get() = trace(
            SyntheticDrive().apply {
                toSpeed(30.0, .15, .6)
                for (i in 0 until 30) {
                    hold(14.0)
                    corner(if (i % 2 == 1) .05 else -.05, .4, 2.0)
                }
                toSpeed(0.0, .12, .6)
                hold(5.0)
            }.build(),
        )

    fun g07(v0: Double): R1Trace = trace(
        SyntheticDrive(v0 = v0).apply {
            for (i in 0 until 30) {
                trap(.12, 1.5, 1.5)
                hold(4.0)
                trap(-.12, 1.5, 1.5)
                hold(4.0)
                corner(if (i % 2 == 1) .1 else -.1, 1.5, 1.0)
                hold(2.0)
            }
        }.build(),
    )

    val g09: R1Trace
        get() = trace(SyntheticDrive(v0 = 13.9).apply { hold(180.0); overwrite(1500, 6, .42, .80) }.build())

    val g10: R1Trace
        get() = trace(
            SyntheticDrive(v0 = 13.9).apply { hold(180.0); overwrite(1500, 25, -.42, 0.0) }
                .build { i, _ -> if (i <= 60) 13.9 else 9.9 },
        )
}
