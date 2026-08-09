package com.timkrest.framehud.internal

import android.os.Build
import java.util.Locale

internal val isEmulatorDevice: Boolean by lazy {
    val hardware = Build.HARDWARE.orEmpty().lowercase(Locale.US)
    val fingerprint = Build.FINGERPRINT.orEmpty()
    hardware in KNOWN_EMULATOR_HARDWARE ||
        fingerprint.startsWith("generic") ||
        fingerprint.contains("sdk_gphone") ||
        Build.PRODUCT.orEmpty().startsWith("sdk_")
}

private val KNOWN_EMULATOR_HARDWARE = setOf("goldfish", "ranchu", "vbox86", "cutf_cvm", "gce_x86")
