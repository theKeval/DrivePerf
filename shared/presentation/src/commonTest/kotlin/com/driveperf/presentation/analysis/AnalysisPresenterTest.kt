package com.driveperf.presentation.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class) // setMain
class AnalysisPresenterTest {
    @Test
    fun `the stub holds its trip and Back closes the screen`() = runTest {
        val p = AnalysisPresenter("trip-1", backgroundScope)
        assertEquals(AnalysisState("trip-1", loading = true), p.state.value)
        p.dispatch(AnalysisIntent.Back)
        assertEquals(AnalysisEffect.Close, p.effects.first())
        p.close()
    }

    @Test
    fun `onMain owns its scope`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val p = AnalysisPresenter.onMain("trip-2")
            assertEquals("trip-2", p.state.value.tripId)
            p.close()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
