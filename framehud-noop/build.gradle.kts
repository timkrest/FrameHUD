plugins {
    alias(libs.plugins.framehud.android.library)
    alias(libs.plugins.framehud.publish)
}

dependencies {
    api(project(":framehud-api"))
    api(libs.coroutines.core)
    implementation(libs.androidx.annotation)
}

val checkApiParity by tasks.registering {
    group = "verification"
    description = "Fails when this module stops mirroring the public API of :framehud-metrics."

    val implDump = rootProject.layout.projectDirectory.file("framehud-metrics/api/framehud-metrics.api")
    val noOpDump = layout.projectDirectory.file("api/framehud-noop.api")
    inputs.files(implDump, noOpDump)

    doLast {
        val absent = listOf(implDump.asFile, noOpDump.asFile).filterNot { it.exists() }
        if (absent.isNotEmpty()) {
            error("Missing API dump(s): ${absent.joinToString()}. Run apiDump first.")
        }

        val ignored = Regex("synthetic")
        fun publicSurface(file: java.io.File) = file.readLines()
            .filterNot { it.contains(ignored) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val missing = publicSurface(implDump.asFile) - publicSurface(noOpDump.asFile)
        val extra = publicSurface(noOpDump.asFile) - publicSurface(implDump.asFile)
        if (missing.isNotEmpty() || extra.isNotEmpty()) {
            error(
                buildString {
                    appendLine("framehud-noop no longer mirrors framehud-metrics.")
                    if (missing.isNotEmpty()) appendLine("Missing from no-op:\n  ${missing.joinToString("\n  ")}")
                    if (extra.isNotEmpty()) appendLine("Only in no-op:\n  ${extra.joinToString("\n  ")}")
                    appendLine("Align the two, then run apiDump.")
                },
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkApiParity)
}
