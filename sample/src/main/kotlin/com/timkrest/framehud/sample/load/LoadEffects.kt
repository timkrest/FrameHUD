package com.timkrest.framehud.sample.load

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.timkrest.framehud.FrameHud

@Composable
fun LoadEffects(active: ActiveLoads) {
    if (Load.GcChurn in active) GcChurn()
    if (Load.BackgroundDecode in active) DecodeQueue()
    LaunchedEffect(active) { FrameHud.context = active.asContext() }
}
