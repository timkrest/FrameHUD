package io.github.timkrest.framehud.internal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

/**
 * The window the panel lives in: placement, dragging and teardown. Main thread only.
 *
 * Failures to talk to the window manager are logged, never thrown — a debug overlay must not take
 * the app down with it.
 */
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

    private val view = ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        setContent { content(::moveBy) }
    }

    val position: PanelPosition get() = PanelPosition(x = layoutParams.x, y = layoutParams.y)

    fun show() {
        lifecycleOwner.start()
        try {
            windowManager.addView(view, layoutParams)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to add the panel window", e)
        }
    }

    fun setVisible(visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
        lifecycleOwner.setVisible(visible)
    }

    fun dismiss() {
        if (view.isAttachedToWindow) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Failed to remove the panel window", e)
            }
        }
        lifecycleOwner.stop()
    }

    private fun moveBy(dx: Float, dy: Float) {
        val displayMetrics = context.resources.displayMetrics
        layoutParams.x = (layoutParams.x - dx.roundToInt())
            .coerceIn(0, (displayMetrics.widthPixels - view.width).coerceAtLeast(0))
        layoutParams.y = (layoutParams.y + dy.roundToInt())
            .coerceIn(0, (displayMetrics.heightPixels - view.height).coerceAtLeast(0))
        try {
            windowManager.updateViewLayout(view, layoutParams)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to move the panel window", e)
        }
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
    }
}
