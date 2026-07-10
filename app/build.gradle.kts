plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.local.parallelvrgallerypro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.local.parallelvrgallerypro"
        minSdk = 30
        targetSdk = 35
        versionCode = 10003
        versionName = "1.0.03"

        // GitHub releases are installed on physical Android devices.  Building with
        // -PgithubRelease=true omits emulator/legacy ABIs without changing the
        // regular universal debug APK used during development.
        if (providers.gradleProperty("githubRelease").orNull == "true") {
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += "tflite"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("com.github.awxkee:avif-coder:2.1.4")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
}
