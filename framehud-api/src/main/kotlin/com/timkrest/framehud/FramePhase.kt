// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

public enum class FramePhase {
    UNKNOWN_DELAY,
    INPUT,
    ANIMATION,
    LAYOUT,
    DRAW,
    SYNC,
    COMMAND_ISSUE,
    SWAP_BUFFERS,
    GPU,

    /** The frame as a whole. The stages overlap, so it is not their sum. */
    TOTAL,
}
