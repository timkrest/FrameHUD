package com.timkrest.framehud.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.annotation.MainThread
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Follows the activity actually in use. Split-screen resumes every visible activity at once, so
 * window focus decides which one that is, not the last resume.
 */
@MainThread
internal class ActivityTracker(
    private val onFocused: (activity: Activity, previous: Activity?) -> Unit,
    private val onLost: () -> Unit,
) : Application.ActivityLifecycleCallbacks {

    private var focusedRef: WeakReference<Activity>? = null

    private val focusWatchers = WeakHashMap<Activity, ViewTreeObserver.OnWindowFocusChangeListener>()

    val focusedActivity: Activity? get() = focusedRef?.get()

    override fun onActivityResumed(activity: Activity) {
        watchWindowFocus(activity)
        if (focusedActivity?.hasWindowFocus() != true) focus(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        stopWatchingWindowFocus(activity)
        if (focusedActivity !== activity) return
        focusedRef = null
        onLost()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun focus(activity: Activity) {
        val previous = focusedActivity
        focusedRef = WeakReference(activity)
        onFocused(activity, previous)
    }

    private fun watchWindowFocus(activity: Activity) {
        if (activity in focusWatchers) return
        val observer = activity.window.decorView.viewTreeObserver
        if (!observer.isAlive) return
        val watcher = WindowFocusWatcher(activity)
        focusWatchers[activity] = watcher
        observer.addOnWindowFocusChangeListener(watcher)
    }

    private fun stopWatchingWindowFocus(activity: Activity) {
        val watcher = focusWatchers.remove(activity) ?: return
        val observer = activity.window.decorView.viewTreeObserver
        if (observer.isAlive) observer.removeOnWindowFocusChangeListener(watcher)
    }

    private inner class WindowFocusWatcher(activity: Activity) : ViewTreeObserver.OnWindowFocusChangeListener {

        private val activityRef = WeakReference(activity)

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            if (hasFocus) activityRef.get()?.let(::focus)
        }
    }
}
