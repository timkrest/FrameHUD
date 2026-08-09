package com.timkrest.framehud.internal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.WindowManager

internal fun canDrawOverlays(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(context)

/** API 30+ rejects an application context for overlay windows. */
internal fun systemOverlayContext(context: Context): Context {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return context
    val display = context.getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
        ?: return context
    return context.createDisplayContext(display)
        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
}

internal fun openOverlayPermissionSettings(activity: Activity) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.fromParts("package", activity.packageName, null),
    )
    guarded("opening the overlay permission screen") { activity.startActivity(intent) }
}
