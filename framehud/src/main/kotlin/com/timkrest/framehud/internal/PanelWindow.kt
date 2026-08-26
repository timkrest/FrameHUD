package com.timkrest.framehud.internal

import android.annotation.SuppressLint
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
        x = start.x.clampedIntoHost(host.width, minVisiblePx)
        y = start.y.clampedIntoHost(host.height, minVisiblePx)
        title = LOG_TAG
    }

    private var fromEnd by mutableFloatStateOf(layoutParams.x.toFloat())
    private var fromTop by mutableFloatStateOf(layoutParams.y.toFloat())

    private var track by mutableStateOf<PanelDragTrack?>(null)

    private val isDragging: Boolean get() = track != null

    private var panelSize = IntSize.Zero

    private val drag = object : PanelDrag {
        override fun grab(screen: Offset) = startDragging(grabbedAt = screen)

        override fun moveTo(screen: Offset) {
            val track = track ?: return
            fromEnd = track.fromEndAt(screen.x, panelSize)
            fromTop = track.fromTopAt(screen.y, panelSize)
        }

        override fun release() = settle()
    }

    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = settle()

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }

    private val view = ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        setContent {
            Box(modifier = if (isDragging) Modifier.fillMaxSize() else Modifier) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .onSizeChanged { panelSize = it }
                        .graphicsLayer {
                            translationX = if (isDragging) -fromEnd else 0f
                            translationY = if (isDragging) fromTop else 0f
                        },
                ) {
                    content(drag)
                }
            }
        }
    }

    val position: PanelPosition
        get() = PanelPosition(x = fromEnd.roundToInt(), y = fromTop.roundToInt())

    fun show(): Boolean {
        lifecycleOwner.start()
        context.registerComponentCallbacks(configurationCallbacks)
        if (guarded("adding the panel window") { windowManager.addView(view, layoutParams) }) return true
        dismiss()
        return false
    }

    fun setVisible(visible: Boolean) {
        if (!visible && isDragging) settle()
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

    private fun startDragging(grabbedAt: Offset) {
        track = PanelDragTrack(
            host = hostSize(),
            grabbedAt = grabbedAt,
            grabbedFromEnd = fromEnd,
            grabbedFromTop = fromTop,
        )
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.x = 0
        layoutParams.y = 0
        guarded("expanding the panel window") { windowManager.updateViewLayout(view, layoutParams) }
    }

    private fun settle() {
        val host = hostSize()
        val panel = panelSize
        track = null
        fromEnd = fromEnd.insideHost(host.width, panel.width)
        fromTop = fromTop.insideHost(host.height, panel.height)
        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        layoutParams.x = fromEnd.roundToInt()
        layoutParams.y = fromTop.roundToInt()
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

        fun Int.clampedIntoHost(hostSize: Int, minVisiblePx: Int): Int =
            if (hostSize > 0) coerceIn(0, (hostSize - minVisiblePx).coerceAtLeast(0)) else this
    }
}
