plugins {
    alias(libs.plugins.framehud.android.library)
    alias(libs.plugins.framehud.publish)
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    // framehud-noop must not drag Compose into release builds.
    api(libs.androidx.compose.runtime.annotation)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
