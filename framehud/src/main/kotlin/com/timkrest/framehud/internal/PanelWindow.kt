package com.timkrest.framehud.internal

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

internal data class PanelPosition(val x: Int, val y: Int)

internal enum class PanelWindowMode(val windowType: Int) {
    @SuppressLint("InlinedApi")
    SYSTEM(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY),
    APP(WindowManager.LayoutParams.TYPE_APPLICATION),
}

@MainThread
internal class PanelWindow(
    private val context: Context,
    val mode: PanelWindowMode,
    startPosition: PanelPosition?,
    content: @Composable (onDrag: (dx: Float, dy: Float) -> Unit) -> Unit,
) {

    private val windowManager = requireNotNull(context.getSystemService(WindowManager::class.java))
    private val lifecycleOwner = PanelLifecycleOwner()

    private val layoutParams = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        mode.windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        val displayMetrics = context.resources.displayMetrics
        val minVisiblePx = (MIN_VISIBLE_DP * displayMetrics.density).roundToInt()
        val start = startPosition ?: defaultPosition(displayMetrics.density)
        gravity = Gravity.TOP or Gravity.END
        x = clampToHost(start.x, displayMetrics.widthPixels, minVisiblePx)
        y = clampToHost(start.y, displayMetrics.heightPixels, minVisiblePx)
        title = LOG_TAG
    }

    private var preciseX = layoutParams.x.toFloat()
    private var preciseY = layoutParams.y.toFloat()

    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = clampIntoDisplay()

        override fun onLowMemory() = Unit
    }

    private val view = ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        setContent { content(::moveBy) }
    }

    val position: PanelPosition get() = PanelPosition(x = layoutParams.x, y = layoutParams.y)

    fun show(): Boolean {
        lifecycleOwner.start()
        context.registerComponentCallbacks(configurationCallbacks)
        if (guarded("adding the panel window") { windowManager.addView(view, layoutParams) }) return true
        dismiss()
        return false
    }

    fun setVisible(visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
        lifecycleOwner.setVisible(visible)
    }

    fun dismiss() {
        context.unregisterComponentCallbacks(configurationCallbacks)
        if (view.isAttachedToWindow) {
            guarded("removing the panel window") { windowManager.removeViewImmediate(view) }
        }
        lifecycleOwner.stop()
    }

    private fun moveBy(dx: Float, dy: Float) = moveTo(preciseX - dx, preciseY + dy)

    private fun clampIntoDisplay() = moveTo(preciseX, preciseY)

    private fun moveTo(x: Float, y: Float) {
        val displayMetrics = context.resources.displayMetrics
        preciseX = x.coerceIn(0f, travelRange(displayMetrics.widthPixels, view.width))
        preciseY = y.coerceIn(0f, travelRange(displayMetrics.heightPixels, view.height))
        layoutParams.x = preciseX.roundToInt()
        layoutParams.y = preciseY.roundToInt()
        guarded("moving the panel window") { windowManager.updateViewLayout(view, layoutParams) }
    }

    private companion object {
        const val DEFAULT_END_MARGIN_DP = 8
        const val DEFAULT_TOP_MARGIN_DP = 48
        const val MIN_VISIBLE_DP = 48

        fun defaultPosition(density: Float): PanelPosition = PanelPosition(
            x = (DEFAULT_END_MARGIN_DP * density).roundToInt(),
            y = (DEFAULT_TOP_MARGIN_DP * density).roundToInt(),
        )

        fun clampToHost(value: Int, hostSize: Int, minVisiblePx: Int): Int =
            if (hostSize > 0) value.coerceIn(0, (hostSize - minVisiblePx).coerceAtLeast(0)) else value

        fun travelRange(hostSize: Int, panelSize: Int): Float = (hostSize - panelSize).coerceAtLeast(0).toFloat()
    }
}
