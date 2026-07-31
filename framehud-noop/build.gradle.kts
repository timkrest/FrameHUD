import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

val javaTarget = JavaVersion.toVersion(libs.versions.jvmTarget.get())

android {
    namespace = "com.timkrest.framehud.noop"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = javaTarget
        targetCompatibility = javaTarget
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(javaTarget.toString())
    }
    explicitApi()
}

dependencies {
    api(project(":framehud-api"))
    // Public: the readings are exposed as StateFlow.
    api(libs.coroutines.core)
}

val checkApiParity by tasks.registering {
    group = "verification"
    description = "Fails when this module stops mirroring the public API of :framehud."

    val implDump = rootProject.layout.projectDirectory.file("framehud/api/framehud.api")
    val noOpDump = layout.projectDirectory.file("api/framehud-noop.api")
    inputs.files(implDump, noOpDump)

    doLast {
        val absent = listOf(implDump.asFile, noOpDump.asFile).filterNot { it.exists() }
        if (absent.isNotEmpty()) {
            error("Missing API dump(s): ${absent.joinToString()}. Run apiDump first.")
        }

        // Compose adds $stable to the implementation, and the private constructors differ in arity.
        val ignored = Regex("synthetic|\\\$stable")
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
                    appendLine("framehud-noop no longer mirrors framehud.")
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

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}
