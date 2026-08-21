import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal const val BASE_NAMESPACE = "com.timkrest.framehud"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.int(alias: String): Int = version(alias).toInt()

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal val VersionCatalog.javaTarget: JavaVersion get() = JavaVersion.toVersion(version("jvmTarget"))

internal fun CommonExtension<*, *, *, *, *, *>.applyFrameHudDefaults(libs: VersionCatalog) {
    compileSdk = libs.int("androidCompileSdk")

    defaultConfig {
        minSdk = libs.int("androidMinSdk")
    }

    compileOptions {
        sourceCompatibility = libs.javaTarget
        targetCompatibility = libs.javaTarget
    }

    buildFeatures {
        buildConfig = false
        resValues = false
        shaders = false
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}

internal fun Project.checkCompilesDeviceTests() {
    tasks.named("check") { dependsOn("compileDebugAndroidTestSources") }
}

internal fun defaultNamespace(projectPath: String): String {
    val segments = projectPath
        .split(":")
        .filter { it.isNotBlank() && it != "framehud" }
        .map { it.removePrefix("framehud-").replace("-", "") }
    return (listOf(BASE_NAMESPACE) + segments).joinToString(".")
}
