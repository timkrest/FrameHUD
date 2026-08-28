package com.timkrest.framehud.internal

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.core.content.pm.PackageInfoCompat

internal class AppVersion(val name: String?, val code: Long)

@WorkerThread
internal fun appVersion(application: Application): AppVersion {
    val packageInfo = packageInfo(application)
    return AppVersion(name = packageInfo.versionName, code = PackageInfoCompat.getLongVersionCode(packageInfo))
}

private fun packageInfo(application: Application): PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    application.packageManager.getPackageInfo(application.packageName, PackageManager.PackageInfoFlags.of(0L))
} else {
    @Suppress("DEPRECATION")
    application.packageManager.getPackageInfo(application.packageName, 0)
}
