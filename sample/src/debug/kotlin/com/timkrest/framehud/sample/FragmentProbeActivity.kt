// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView

class FragmentProbeActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FragmentContainerView(this).apply { id = R.id.probe_fragments })
        if (savedInstanceState == null) show(CartProbeFragment())
    }

    fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.probe_fragments, fragment)
            .setPrimaryNavigationFragment(fragment)
            .commitNow()
    }
}

class CartProbeFragment : ProbeFragment()

class CheckoutProbeFragment : ProbeFragment()

class HostProbeFragment : ProbeFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val destination = CheckoutProbeFragment()
        childFragmentManager.beginTransaction()
            .add(destination, null)
            .setPrimaryNavigationFragment(destination)
            .commitNow()
    }
}

open class ProbeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        View(inflater.context).apply { setBackgroundColor(Color.DKGRAY) }
}
