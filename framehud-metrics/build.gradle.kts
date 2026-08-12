plugins {
    alias(libs.plugins.framehud.android.library)
    alias(libs.plugins.framehud.publish)
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(project(":framehud-api"))
    api(libs.coroutines.core)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test)
}
