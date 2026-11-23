import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val qnnNotices by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}

abstract class CollectQnnNotices : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archive: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun collect() {
        val names = setOf("LICENSE.pdf", "NOTICE.txt")
        val notices = archives.zipTree(archive.singleFile).matching { include(names) }
        check(notices.files.map { it.name }.toSet() == names) { "QNN runtime archive is missing its license or notices" }
        files.sync {
            from(notices) { into("third-party/qnn") }
            into(outputDirectory)
        }
    }
}

val collectQnnNotices by tasks.registering(CollectQnnNotices::class) {
    archive.from(qnnNotices)
    outputDirectory.set(layout.buildDirectory.dir("generated/qnn-notices"))
}

android {
    namespace = "com.local.parallelvrgallerypro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.local.parallelvrgallerypro"
        minSdk = 30
        targetSdk = 35
        versionCode = 11502
        versionName = "1.15.02"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // The QNN GPU provider opens the absolute path in nativeLibraryDir.
            useLegacyPackaging = true
            excludes += setOf("**/libQnnDsp*.so", "**/libQnnHtp*.so")
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += listOf("tflite", "onnx")
    }

    // Local performance harness: Release runtime flags, explicit QA entry points, never a release asset.
    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    sourceSets.getByName("benchmark") {
        java.srcDir("src/debug/java")
        manifest.srcFile("src/debug/AndroidManifest.xml")
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(collectQnnNotices, CollectQnnNotices::outputDirectory)
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
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
    implementation("com.microsoft.onnxruntime:onnxruntime-android-qnn:1.29.0")
    qnnNotices("com.qualcomm.qti:qnn-runtime:2.42.0@aar")
    implementation("com.github.awxkee:avif-coder:2.1.4")
    // Targeted addition for the explicitly selected SSS V2.2 Telea inpaint mode.
    implementation("org.opencv:opencv:4.12.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
}
