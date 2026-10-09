// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

/** Why a process ended, in the terms of `ApplicationExitInfo`. */
public enum class ExitReason {
    ANR,
    CRASH,
    CRASH_NATIVE,
    LOW_MEMORY,
    EXCESSIVE_RESOURCE_USAGE,
    FREEZER,
    INITIALIZATION_FAILURE,
    SIGNALED,
    EXIT_SELF,
    USER_REQUESTED,
    USER_STOPPED,
    PERMISSION_CHANGE,
    DEPENDENCY_DIED,
    PACKAGE_STATE_CHANGE,
    PACKAGE_UPDATED,
    OTHER,
    UNKNOWN,
}
