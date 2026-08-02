plugins {
    alias(libs.plugins.framehud.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    defaultConfig {
        applicationId = "com.timkrest.framehud.sample"
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Only so the no-op variant can be installed without a keystore; the sample ships nowhere.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        // A developer harness that ships nowhere: no launcher icon, and nothing worth backing up.
        disable += setOf("MissingApplicationIcon", "DataExtractionRules")
    }
}

dependencies {
    debugImplementation(project(":framehud"))
    releaseImplementation(project(":framehud-noop"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)

    androidTestImplementation(project(":framehud-instrumentation"))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test)
}
