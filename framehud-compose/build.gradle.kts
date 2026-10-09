plugins {
    alias(libs.plugins.framehud.android.library)
    alias(libs.plugins.framehud.publish)
    alias(libs.plugins.compose.compiler)
}

android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.compose.foundation.minimum)
    implementation(libs.coroutines.core)

    // The app brings framehud or framehud-noop; noop is the API both of them have.
    compileOnly(project(":framehud-noop"))

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
