package com.driveperf.feature.permissions

import android.Manifest
import android.os.Build
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PermissionItem(
    val permission: String,
    val title: String,
    val rationale: String,
    val isGranted: Boolean = false,
)

data class PermissionsUiState(
    val permissions: List<PermissionItem> = buildDefaultPermissions(),
    val allGranted: Boolean = false,
)

private fun buildDefaultPermissions(): List<PermissionItem> = buildList {
    add(
        PermissionItem(
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            title = "Precise location",
            rationale = "Required to track your route and detect speeding. DrivePerf records location only during active trips.",
        )
    )
    add(
        PermissionItem(
            permission = Manifest.permission.ACTIVITY_RECOGNITION,
            title = "Physical activity",
            rationale = "Lets DrivePerf detect when you enter a vehicle so trips start automatically without draining your battery.",
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(
            PermissionItem(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                title = "Notifications",
                rationale = "DrivePerf shows a persistent notification while recording so you always know when tracking is active.",
            )
        )
    }
}

@HiltViewModel
class PermissionsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    fun onPermissionResult(permission: String, granted: Boolean) {
        val updated = _uiState.value.permissions.map { item ->
            if (item.permission == permission) item.copy(isGranted = granted) else item
        }
        val allGranted = updated.all { it.isGranted }
        _uiState.value = _uiState.value.copy(permissions = updated, allGranted = allGranted)
    }
}
