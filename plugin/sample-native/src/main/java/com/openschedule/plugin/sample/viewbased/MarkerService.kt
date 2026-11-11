package com.openschedule.plugin.sample.viewbased

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Marker service. The host discovers plugins by querying for the
 * `com.openschedule.plugin.NATIVE` action and never starts this service; it only
 * needs to exist and be exported so the package shows up in `queryIntentServices`.
 */
class MarkerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
