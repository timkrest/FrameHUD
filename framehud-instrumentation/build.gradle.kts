plugins {
    alias(libs.plugins.framehud.android.library)
    alias(libs.plugins.framehud.publish)
}

dependencies {
    api(project(":framehud"))
    api(libs.junit4)

    testImplementation(libs.kotlin.test)
}
