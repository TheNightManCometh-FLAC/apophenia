import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: no Android dependencies, so the whole music core tests on the desktop JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // ONNX Runtime's Java API (ai.onnxruntime) for the speech model; the app supplies the Android build of the same API.
    compileOnly(libs.onnxruntime.jvm)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    // Desktop ONNX Runtime: runs the bundled stem models in a unit test on this PC.
    testImplementation(libs.onnxruntime.jvm)
}

tasks.test {
    useJUnit()
}
