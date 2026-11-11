package com.driveperf.app.navigation

/** What the shell can ask the app to do. */
class ShellCallbacks(
    val onTabSelected: (Tab) -> Unit,
    val onProfile: () -> Unit,
    val onStart: () -> Unit,
    val onOpenDrive: () -> Unit,
    val onFixLocation: () -> Unit,
)
