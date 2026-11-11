package com.driveperf.platform.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.driveperf.platform.AndroidClock

/** Receives Activity Recognition updates while a trip is being captured and passes them to the hub. Not exported. */
class ActivityUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ActivityUpdateHub.deliver(intent, AndroidClock)
    }
}
