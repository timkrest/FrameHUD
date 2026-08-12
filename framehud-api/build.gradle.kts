plugins {
    alias(libs.plugins.framehud.android.library)
    alias(libs.plugins.framehud.publish)
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime.annotation)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
