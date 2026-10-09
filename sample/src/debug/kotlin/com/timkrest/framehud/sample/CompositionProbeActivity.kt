// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import com.timkrest.framehud.compose.CountCompositions

class CompositionProbeActivity : ComponentActivity() {

    private val value = mutableIntStateOf(0)

    fun change() {
        value.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CountedProbe(value.intValue) }
    }
}

const val PROBE_COMPOSITIONS = "probe compositions"

@Composable
private fun CountedProbe(value: Int) {
    CountCompositions(PROBE_COMPOSITIONS)
    BasicText(text = value.toString())
}
