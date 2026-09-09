plugins {
    alias(libs.plugins.framehud.android.library)
    alias(libs.plugins.framehud.publish)
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("androidTest") {
        kotlin.srcDir(rootProject.file("test-support/kotlin"))
        kotlin.srcDir(rootProject.file("test-support/activity"))
    }
}

dependencies {
    api(project(":framehud-metrics"))
    implementation(libs.coroutines.core)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test)
}
