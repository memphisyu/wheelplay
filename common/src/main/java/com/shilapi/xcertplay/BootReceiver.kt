package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts the CarPlay host after boot when the user has enabled the startup option. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AirPlayPersistence.loadAutoStartOnBoot(context)) return

        // Start the listener service directly; background activity launches are OEM restricted.
        val service = Intent(context, DiPlaySessionService::class.java)
            .setAction(DiPlaySessionService.ACTION_BACKGROUND_START)
        try {
            context.startForegroundService(service)
            RootKeepAlive.record(context, "boot requested background web service")
        } catch (error: RuntimeException) {
            RootKeepAlive.record(context, "boot service start failed ${error.javaClass.simpleName}: ${error.message?.take(160)}")
            Log.w(TAG, "Boot auto-start could not start web service", error)
        }
    }

    private companion object {
        const val TAG = "xcertplay-boot"
    }
}
