package com.mazha0309.miaoassistant.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mazha0309.miaoassistant.config.ConfigRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val supportedAction = intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!supportedAction || !ConfigRepository(context).load().keepAliveEnabled) return

        try {
            KeepAliveService.start(context)
        } catch (error: RuntimeException) {
            Log.w(TAG, "System prevented keep-alive restart", error)
        }
    }

    companion object {
        private const val TAG = "MiaoBootReceiver"
    }
}
