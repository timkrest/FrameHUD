package com.timkrest.framehud.internal

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.core.content.pm.PackageInfoCompat
import com.timkrest.framehud.Baseline
import com.timkrest.framehud.BaselineComparison
import com.timkrest.framehud.BaselineEnvironment
import com.timkrest.framehud.SessionExport
import com.timkrest.framehud.metrics.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal const val EXPORT_DIRECTORY = "framehud"

@WorkerThread
internal fun sessionSnapshot(
    application: Application,
    stats: MetricsEngine.ExportStats,
    isEnabled: Boolean,
    isFrozen: Boolean,
    baseline: Baseline?,
): SessionSnapshot {
    val packageInfo = packageInfo(application)
    val environment = BaselineEnvironment.current()
    return SessionSnapshot(
        takenAtEpochMs = System.currentTimeMillis(),
        takenAtNs = System.nanoTime(),
        timeZone = TimeZone.getDefault(),
        frameHudVersion = BuildConfig.FRAMEHUD_VERSION,
        packageName = application.packageName,
        appVersionName = packageInfo.versionName,
        appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
        environment = environment,
        isEnabled = isEnabled,
        isFrozen = isFrozen,
        screenName = stats.screenName,
        mark = stats.mark,
        context = stats.context,
        session = stats.session,
        screen = stats.screen,
        intervals = stats.intervals,
        baseline = baseline?.compare(environment, stats.intervals) ?: BaselineComparison.NoBaseline,
        phases = stats.metrics.phases,
        window = stats.metrics.window,
        display = stats.metrics.display,
        memory = stats.memory,
        thermal = stats.thermal,
        worstFrames = stats.worstFrames,
    )
}

@WorkerThread
internal fun SessionSnapshot.writeTo(directory: File): SessionExport {
    directory.mkdirs()
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(takenAtEpochMs))
    val name = unusedName(directory, stamp)
    val json = File(directory, "$name.json").apply { writeText(toJson()) }
    val html = File(directory, "$name.html").apply { writeText(toHtml()) }
    return SessionExport(json = json, html = html)
}

private fun unusedName(directory: File, stamp: String): String {
    var attempt = 0
    while (true) {
        val name = if (attempt == 0) "framehud-$stamp" else "framehud-$stamp-$attempt"
        if (File(directory, "$name.json").createNewFile()) return name
        attempt++
    }
}

internal fun exportDirectory(application: Application): File =
    File(application.getExternalFilesDir(null) ?: application.filesDir, EXPORT_DIRECTORY)

private fun packageInfo(application: Application): PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    application.packageManager.getPackageInfo(application.packageName, PackageManager.PackageInfoFlags.of(0L))
} else {
    @Suppress("DEPRECATION")
    application.packageManager.getPackageInfo(application.packageName, 0)
}
