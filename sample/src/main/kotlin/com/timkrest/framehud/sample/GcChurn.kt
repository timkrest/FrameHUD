package com.timkrest.framehud.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos

@Composable
fun GcChurn() {
    LaunchedEffect(Unit) {
        val retained = ArrayDeque<ByteArray>()
        while (true) {
            withFrameNanos { }
            repeat(CHURN_BLOCKS) {
                retained.addLast(ByteArray(CHURN_BLOCK_BYTES))
                if (retained.size > CHURN_RETAINED_BLOCKS) retained.removeFirst()
            }
        }
    }
}

private const val CHURN_BLOCKS = 200
private const val CHURN_BLOCK_BYTES = 64 * 1024
private const val CHURN_RETAINED_BLOCKS = 32
