package com.timkrest.framehud.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.MainThread
import androidx.compose.runtime.remember
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.OverlayMode
import com.timkrest.framehud.ui.FrameHudPanel
import com.timkrest.framehud.ui.PanelActions
import com.timkrest.framehud.ui.PanelState

@MainThread
internal class PanelHost(
    val application: Application,
    private val config: () -> FrameHudConfig,
    private val panelState: (canRequestOverlayPermission: Boolean) -> PanelState,
    private val panelActions: (onDrag: (dx: Float, dy: Float) -> Unit) -> PanelActions,
) {

    @SuppressLint("StaticFieldLeak")
    private var window: PanelWindow? = null
    private var lastPosition: PanelPosition? = null
    private var hasLoggedAppWindowFallback = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideInBackground = Runnable { window?.setVisible(false) }

    val isShowing: Boolean get() = window != null

    val isAppWindow: Boolean get() = window?.mode == PanelWindowMode.APP

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    val canRequestOverlayPermission: Boolean
        get() = config().overlayMode == OverlayMode.PREFER_SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun show(activity: Activity?): Boolean {
        cancelPendingHide()
        if (window != null) return true
        val mode = resolveWindowMode()
        val windowContext = when (mode) {
            PanelWindowMode.SYSTEM -> systemOverlayContext(application)
            PanelWindowMode.APP -> activity ?: return false
        }
        if (mode == PanelWindowMode.APP && canRequestOverlayPermission) logAppWindowFallbackOnce()
        val created = createWindow(context = windowContext, mode = mode)
        if (!created.show()) return false
        window = created
        return true
    }

    fun dismiss() {
        cancelPendingHide()
        window?.let { current ->
            lastPosition = current.position
            current.dismiss()
        }
        window = null
    }

    fun dismissIfWindowModeChanged() {
        val current = window ?: return
        if (current.mode != resolveWindowMode()) dismiss()
    }

    fun makeVisible() {
        cancelPendingHide()
        window?.setVisible(true)
    }

    fun hideAfterActivitySwap() {
        mainHandler.postDelayed(hideInBackground, BACKGROUND_HIDE_DELAY_MS)
    }

    private fun cancelPendingHide() {
        mainHandler.removeCallbacks(hideInBackground)
    }

    private fun createWindow(context: Context, mode: PanelWindowMode): PanelWindow {
        val canRequest = mode == PanelWindowMode.APP && canRequestOverlayPermission
        return PanelWindow(context = context, mode = mode, startPosition = lastPosition) { onDrag ->
            FrameHudPanel(
                state = remember(canRequest) { panelState(canRequest) },
                actions = remember(onDrag) { panelActions(onDrag) },
            )
        }
    }

    private fun resolveWindowMode(): PanelWindowMode =
        if (config().overlayMode == OverlayMode.PREFER_SYSTEM && canDrawOverlays(application)) {
            PanelWindowMode.SYSTEM
        } else {
            PanelWindowMode.APP
        }

    private fun logAppWindowFallbackOnce() {
        if (hasLoggedAppWindowFallback) return
        hasLoggedAppWindowFallback = true
        Log.i(LOG_TAG, "No SYSTEM_ALERT_WINDOW permission: the panel stays inside the app window")
    }

    private companion object {
        const val BACKGROUND_HIDE_DELAY_MS = 300L
    }
}
