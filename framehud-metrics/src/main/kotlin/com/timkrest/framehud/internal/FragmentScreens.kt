// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import java.util.Collections
import java.util.WeakHashMap

internal fun fragmentScreensOrNull(onShown: (Activity) -> Unit): FragmentScreens? =
    if (linksFragments()) FragmentScreens(onShown) else null

/** By reference, not by name: R8 may have renamed the class. */
private fun linksFragments(): Boolean = try {
    FragmentActivity::class.java.name.isNotEmpty()
} catch (_: NoClassDefFoundError) {
    false
}

@MainThread
internal class FragmentScreens(private val onShown: (Activity) -> Unit) : Application.ActivityLifecycleCallbacks {

    private val watched: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap())

    fun screenOf(activity: Activity): String? {
        if (activity !is FragmentActivity) return null
        var shown = activity.supportFragmentManager.primaryNavigationFragment ?: return null
        while (true) {
            if (!shown.isAdded) return null
            shown = shown.childFragmentManager.primaryNavigationFragment ?: break
        }
        return shown.javaClass.simpleName
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is FragmentActivity || !watched.add(activity)) return
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(ShownFragments(activity), true)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    private inner class ShownFragments(private val activity: Activity) : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            onShown(activity)
        }
    }
}
