import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

val javaTarget = JavaVersion.toVersion(libs.versions.jvmTarget.get())

android {
    namespace = "io.github.timkrest.framehud.api"
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
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}
