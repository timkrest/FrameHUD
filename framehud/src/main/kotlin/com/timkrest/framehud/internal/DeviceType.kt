package com.timkrest.framehud.internal

import android.os.Build
import java.util.Locale

/** Emulators draw through the host GPU, so render-thread and GPU timings say nothing about a device. */
internal val isEmulatorDevice: Boolean by lazy {
    val hardware = Build.HARDWARE.orEmpty().lowercase(Locale.US)
    val fingerprint = Build.FINGERPRINT.orEmpty()
    hardware in EMULATOR_HARDWARE ||
        fingerprint.startsWith("generic") ||
        fingerprint.contains("sdk_gphone") ||
        Build.PRODUCT.orEmpty().startsWith("sdk_")
}

/** Android Emulator, Genymotion, Cuttlefish and Compute Engine images. */
private val EMULATOR_HARDWARE = setOf("goldfish", "ranchu", "vbox86", "cutf_cvm", "gce_x86")
