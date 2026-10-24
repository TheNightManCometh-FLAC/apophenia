plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.betterappmafia.apophenia"
    compileSdk = 37
    // Same NDK as :audio, so packaging can strip the native libraries.
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.betterappmafia.apophenia"
        minSdk = 29
        targetSdk = 36
        versionCode = 64
        versionName = "0.7.3"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":audio"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.litert)
    implementation(libs.onnxruntime.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
