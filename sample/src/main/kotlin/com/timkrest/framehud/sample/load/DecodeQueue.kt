package com.timkrest.framehud.sample.load

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun DecodeQueue() {
    LaunchedEffect(Unit) {
        val depth = FrameHud.counter("decode queue")
        try {
            decodeUntilCancelled(depth)
        } finally {
            depth.set(0)
        }
    }
}

private suspend fun decodeUntilCancelled(depth: FrameHudCounter) = coroutineScope {
    val requests = Channel<Int>(capacity = QUEUE_CAPACITY)
    repeat(DECODE_WORKERS) {
        launch(Dispatchers.Default) {
            for (request in requests) {
                decode(request)
                depth.add(-1)
            }
        }
    }
    var next = 0
    while (true) {
        withFrameNanos { }
        repeat(REQUESTS_PER_FRAME) {
            if (requests.trySend(next++).isSuccess) depth.add(1)
        }
    }
}

private fun decode(request: Int): Int {
    var decoded = request
    repeat(DECODE_STEPS) { step -> decoded = decoded * 31 + step }
    return decoded
}

private const val QUEUE_CAPACITY = 64
private const val DECODE_WORKERS = 2
private const val REQUESTS_PER_FRAME = 4
private const val DECODE_STEPS = 300_000
