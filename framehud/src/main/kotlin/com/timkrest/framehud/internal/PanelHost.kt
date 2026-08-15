package com.timkrest.framehud.internal

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.MainThread
import androidx.compose.runtime.remember
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudPanel
import com.timkrest.framehud.OverlayMode
import com.timkrest.framehud.ui.Panel
import com.timkrest.framehud.ui.PanelActions
import com.timkrest.framehud.ui.PanelState
import kotlinx.coroutines.flow.MutableStateFlow

@MainThread
internal class PanelHost(private val application: Application) : FrameHudPanel {

    @SuppressLint("StaticFieldLeak")
    private var window: PanelWindow? = null

    private var lastPosition: PanelPosition? = null

    private var hasLoggedAppWindowFallback = false

    private val isCollapsed = MutableStateFlow(false)

    private val mainHandler = Handler(Looper.getMainLooper())

    private val hideInBackground = Runnable { window?.setVisible(false) }

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private val canRequestOverlayPermission: Boolean
        get() = FrameHud.config.overlayMode == OverlayMode.PREFER_SYSTEM &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    override fun onConfigChanged() {
        if (!FrameHud.config.enabled) {
            dismiss()
            return
        }
        dismissIfWindowModeChanged()
        show()
    }

    override fun onScreenFocused() {
        cancelPendingHide()
        if (FrameHud.config.enabled) {
            dismissIfWindowModeChanged()
            show()
        }
        window?.setVisible(true)
    }

    override fun onScreenLost() {
        if (window?.mode == PanelWindowMode.APP) {
            dismiss()
        } else {
            mainHandler.postDelayed(hideInBackground, BACKGROUND_HIDE_DELAY_MS)
        }
    }

    private fun show() {
        cancelPendingHide()
        if (window != null) return
        val mode = resolveWindowMode()
        val windowContext = when (mode) {
            PanelWindowMode.SYSTEM -> systemOverlayContext(application, FrameHud.focusedActivity)
            PanelWindowMode.APP -> FrameHud.focusedActivity ?: return
        }
        if (mode == PanelWindowMode.APP && canRequestOverlayPermission) logAppWindowFallbackOnce()
        val created = createWindow(context = windowContext, mode = mode)
        if (created.show()) window = created
    }

    private fun dismiss() {
        cancelPendingHide()
        window?.let { current ->
            lastPosition = current.position
            current.dismiss()
        }
        window = null
    }

    private fun dismissIfWindowModeChanged() {
        val current = window ?: return
        if (current.mode != resolveWindowMode()) dismiss()
    }

    private fun cancelPendingHide() {
        mainHandler.removeCallbacks(hideInBackground)
    }

    private fun createWindow(context: Context, mode: PanelWindowMode): PanelWindow {
        val canRequest = mode == PanelWindowMode.APP && canRequestOverlayPermission
        return PanelWindow(context = context, mode = mode, startPosition = lastPosition) { onDrag ->
            Panel(
                state = remember(canRequest) { panelState(canRequest) },
                actions = remember(onDrag) { panelActions(onDrag) },
            )
        }
    }

    private fun panelState(canRequestOverlayPermission: Boolean) = PanelState(
        metrics = FrameHud.metrics,
        choreographerTicksPerSecond = FrameHud.choreographerTicksPerSecond,
        memory = FrameHud.memoryStats,
        thermal = FrameHud.thermalStats,
        activeMark = FrameHud.activeMark,
        isCollapsed = isCollapsed,
        isFrozen = FrameHud.isFrozen,
        canRequestOverlayPermission = canRequestOverlayPermission,
        isEmulator = isEmulatorDevice,
    )

    private fun panelActions(onDrag: (dx: Float, dy: Float) -> Unit) = PanelActions(
        toggleCollapsed = { isCollapsed.value = !isCollapsed.value },
        toggleFrozen = FrameHud::toggleFreeze,
        reset = FrameHud::reset,
        drag = onDrag,
        requestOverlayPermission = { FrameHud.focusedActivity?.let(::openOverlayPermissionSettings) },
    )

    private fun resolveWindowMode(): PanelWindowMode =
        if (FrameHud.config.overlayMode == OverlayMode.PREFER_SYSTEM && canDrawOverlays(application)) {
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
