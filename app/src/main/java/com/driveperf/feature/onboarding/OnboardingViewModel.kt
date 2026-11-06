package com.driveperf.feature.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class OnboardingUiState(
    val currentPage: Int = 0,
    val totalPages: Int = 3,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun nextPage(): Boolean {
        val current = _uiState.value
        return if (current.currentPage < current.totalPages - 1) {
            _uiState.value = current.copy(currentPage = current.currentPage + 1)
            false
        } else {
            true // signal caller to finish onboarding
        }
    }

    fun previousPage() {
        val current = _uiState.value
        if (current.currentPage > 0) {
            _uiState.value = current.copy(currentPage = current.currentPage - 1)
        }
    }
}
