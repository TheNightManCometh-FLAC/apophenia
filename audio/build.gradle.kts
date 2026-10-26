plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.betterappmafia.apophenia.audio"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 29
        ndk {
            abiFilters += "arm64-v8a"
            // `-Pemulator` adds x86_64 for the Android emulator on the PC; builds for the phone stay arm64-only.
            if (project.hasProperty("emulator")) abiFilters += "x86_64"
        }
        externalNativeBuild {
            cmake {
                // Oboe's prefab libraries are built against the shared C++ runtime.
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    buildFeatures {
        prefab = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.oboe)
    testImplementation(libs.junit)
}
