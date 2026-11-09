package com.driveperf.pipeline

import kotlinx.datetime.Clock

class PipelineUsesClock {
    fun now() = Clock.System.now()
}
