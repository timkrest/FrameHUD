package com.timkrest.framehud.internal

import android.os.Build
import com.timkrest.framehud.InternalFrameHudApi
import java.util.Locale

@InternalFrameHudApi
public val isEmulatorDevice: Boolean by lazy {
    val fingerprint = Build.FINGERPRINT.orEmpty()
    Build.HARDWARE.orEmpty().lowercase(Locale.US) in EMULATOR_HARDWARE ||
        fingerprint.startsWith("generic") ||
        fingerprint.contains("emulator") ||
        fingerprint.contains("sdk_gphone") ||
        Build.MODEL.orEmpty().startsWith("sdk_gphone") ||
        Build.PRODUCT.orEmpty().startsWith("sdk_")
}

private val EMULATOR_HARDWARE = setOf("goldfish", "ranchu", "vbox86", "cutf_cvm", "gce_x86")
