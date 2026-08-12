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
    api(project(":framehud-metrics"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.annotation)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
