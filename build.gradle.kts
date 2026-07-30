plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.ktlint)
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
}

apiValidation {
    ignoredProjects += "sample"
}
