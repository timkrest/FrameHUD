package com.timkrest.framehud.internal

import android.os.Build

internal fun isRunningOnEmulator(): Boolean =
    Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.contains("emulator") ||
        Build.HARDWARE == "ranchu" ||
        Build.HARDWARE == "goldfish" ||
        Build.MODEL.startsWith("sdk_gphone")
