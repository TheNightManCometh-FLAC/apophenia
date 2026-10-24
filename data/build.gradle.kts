import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Room lives in a Kotlin Multiplatform module (android + jvm) so the database is tested on the
// desktop JVM with the bundled SQLite driver, as the Room testing guide recommends over Robolectric.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    android {
        namespace = "com.betterappmafia.apophenia.data"
        compileSdk = 37
        minSdk = 29
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.room.testing)
            implementation(libs.junit)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}
