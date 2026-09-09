package com.timkrest.framehud.internal

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.timkrest.framehud.ui.GrabbedPanel
import com.timkrest.framehud.ui.PanelDrag
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
    content: @Composable (drag: PanelDrag) -> Unit,
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
        val density = context.resources.displayMetrics.density
        val minVisiblePx = (MIN_VISIBLE_DP * density).roundToInt()
        val host = hostSize()
        val start = startPosition ?: defaultPosition(density)
        gravity = Gravity.TOP or Gravity.END
        x = start.x.insideHost(host.width, minVisiblePx)
        y = start.y.insideHost(host.height, minVisiblePx)
        title = LOG_TAG
    }

    private val drag = PanelDrag {
        val grabbedFrom = position
        val grabbedPointer = view.pointerOnScreen
        GrabbedPanel {
            val travelled = view.pointerOnScreen - grabbedPointer
            placeAt(x = grabbedFrom.x - travelled.x, y = grabbedFrom.y + travelled.y)
        }
    }

    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = keepInsideHost()

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }

    private val view: PointerTrackingLayout = PointerTrackingLayout(context).apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        setContent { content(drag) }
    }

    val position: PanelPosition
        get() = PanelPosition(x = layoutParams.x, y = layoutParams.y)

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

    private fun keepInsideHost() = placeAt(x = layoutParams.x.toFloat(), y = layoutParams.y.toFloat())

    private fun placeAt(x: Float, y: Float) {
        val host = hostSize()
        layoutParams.x = x.roundToInt().insideHost(host.width, view.width)
        layoutParams.y = y.roundToInt().insideHost(host.height, view.height)
        guarded("moving the panel window") { windowManager.updateViewLayout(view, layoutParams) }
    }

    private fun hostSize(): IntSize {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            return IntSize(
                width = metrics.bounds.width() - bars.left - bars.right,
                height = metrics.bounds.height() - bars.top - bars.bottom,
            )
        }
        val displayMetrics = context.resources.displayMetrics
        return IntSize(width = displayMetrics.widthPixels, height = displayMetrics.heightPixels)
    }

    private companion object {
        const val DEFAULT_END_MARGIN_DP = 8
        const val DEFAULT_TOP_MARGIN_DP = 48
        const val MIN_VISIBLE_DP = 48

        fun defaultPosition(density: Float): PanelPosition = PanelPosition(
            x = (DEFAULT_END_MARGIN_DP * density).roundToInt(),
            y = (DEFAULT_TOP_MARGIN_DP * density).roundToInt(),
        )
    }
}

internal fun Int.insideHost(hostSize: Int, panelSize: Int): Int =
    coerceIn(0, (hostSize - panelSize).coerceAtLeast(0))

private class PointerTrackingLayout(context: Context) : FrameLayout(context) {

    private val composeView = ComposeView(context).also(::addView)

    var pointerOnScreen: Offset = Offset.Zero
        private set

    fun setContent(content: @Composable () -> Unit) = composeView.setContent(content)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        pointerOnScreen = Offset(x = event.rawX, y = event.rawY)
        return super.dispatchTouchEvent(event)
    }
}
