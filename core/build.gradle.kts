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
    // ONNX Runtime unpacks its native library into a new temp folder every run and never deletes it on Windows
    // (101 folders, 1.7 GB, by 27 Oct 2026): keep them in the build folder, where a clean removes them.
    val tmp = layout.buildDirectory.dir("tmp/test-jvm").get().asFile
    doFirst { tmp.deleteRecursively(); tmp.mkdirs() }
    systemProperty("java.io.tmpdir", tmp.absolutePath)
}

// DemucsModelTest: the user-supplied HT-Demucs file and its reference run, only when given (-PdemucsModel, -PdemucsRef).
tasks.test {
    providers.gradleProperty("demucsModel").orNull?.let { systemProperty("demucs.model", it) }
    providers.gradleProperty("demucsRef").orNull?.let { systemProperty("demucs.ref", it) }
    testLogging { showStandardStreams = providers.gradleProperty("demucsModel").isPresent }
}
