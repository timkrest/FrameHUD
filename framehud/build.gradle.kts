import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.compose.compiler)
}

val javaTarget = JavaVersion.toVersion(libs.versions.jvmTarget.get())

android {
    namespace = "io.github.timkrest.framehud"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = javaTarget
        targetCompatibility = javaTarget
    }

    buildFeatures {
        compose = true
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.annotation)
    // Public: the readings are exposed as StateFlow.
    api(libs.coroutines.core)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}
