package com.timkrest.framehud.sample

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleStartEffect
import com.timkrest.framehud.FrameHud

@Composable
fun MeasuredScreen(name: String) {
    LifecycleStartEffect(name) {
        FrameHud.screen = name
        onStopOrDispose { }
    }
}
