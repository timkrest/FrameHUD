package io.github.timkrest.framehud.internal

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.runtime.remember
import io.github.timkrest.framehud.FrameHudConfig
import io.github.timkrest.framehud.OverlayMode
import io.github.timkrest.framehud.ui.FrameHudPanel
import io.github.timkrest.framehud.ui.PanelActions
import io.github.timkrest.framehud.ui.PanelState

/** Decides which kind of window the panel gets and when it is on screen. Main thread only. */
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

    /** The panel sits in the activity's window, so it dies with the activity. */
    val isAppWindow: Boolean get() = window?.mode == PanelWindowMode.APP

    /** False when an app window is needed and no activity is bound yet; the next resume brings one. */
    fun show(activity: Activity?): Boolean {
        cancelPendingHide()
        if (window != null) return true
        val mode = resolveWindowMode()
        val windowContext = when (mode) {
            PanelWindowMode.SYSTEM -> systemOverlayContext(application)
            PanelWindowMode.APP -> activity ?: return false
        }
        if (mode == PanelWindowMode.APP && canRequestOverlayPermission) logAppWindowFallbackOnce()
        window = createWindow(context = windowContext, mode = mode).apply { show() }
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

    /** Picks up an overlay permission granted while the panel was already running. */
    fun dismissIfWindowModeChanged() {
        val current = window ?: return
        if (current.mode != resolveWindowMode()) dismiss()
    }

    fun makeVisible() {
        cancelPendingHide()
        window?.setVisible(true)
    }

    /** Long enough to cover an activity swap, so the panel does not blink between screens. */
    fun hideAfterActivitySwap() {
        mainHandler.postDelayed(hideInBackground, BACKGROUND_HIDE_DELAY_MS)
    }

    private fun cancelPendingHide() {
        mainHandler.removeCallbacks(hideInBackground)
    }

    /** Asking for `SYSTEM_ALERT_WINDOW` could move the panel out of the app window. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    val canRequestOverlayPermission: Boolean
        get() = config().overlayMode == OverlayMode.PREFER_SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

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
