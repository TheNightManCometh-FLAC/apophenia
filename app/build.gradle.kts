import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from keystore.properties at the repo root (gitignored, never committed): storeFile,
// storePassword, keyAlias, keyPassword. Without it the release build is unsigned (F-Droid signs its own).
val keystoreProps = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
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
        versionCode = 75
        versionName = "0.8.4"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        keystoreProps?.let { p ->
            create("release") {
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // [Claude] No R8 for now: the JNI bridge, Room and the ONNX/LiteRT runtimes would each need keep rules
            // checked on the phone, and the code is a small part of the size (the models are most of it).
            isMinifyEnabled = false
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
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
    // LiteRT 2.3.0 pulls in Google Play's AI-pack delivery (play-services-basement, asset-delivery, WorkManager): only its
    // AiPackModelProvider uses them, which the app never calls (it loads the bundled model with Interpreter). Proprietary and
    // networked, so left out: it's what added a 2019 Fragment and the boot and network permissions.
    implementation(libs.litert) {
        exclude(group = "com.google.android.play")
        exclude(group = "com.google.android.gms")
    }
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

// The privacy promise, checked on every build: the merged manifest (what the phone sees, libraries included) must
// not ask for the network. A library update that adds INTERNET fails the build instead of shipping.
tasks.configureEach {
    if (name.matches(Regex("process(Debug|Release)(Main)?Manifest(ForPackage)?"))) doLast {
        val forbidden = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.RECEIVE_BOOT_COMPLETED")
        outputs.files.asFileTree.matching { include("**/AndroidManifest.xml") }.forEach { mf ->
            val text = mf.readText()
            val found = forbidden.filter { "\"$it\"" in text }
            if (found.isNotEmpty()) throw GradleException("${mf.path} asks for $found: Apophenia must have no network permission (plan §1).")
        }
    }
}
