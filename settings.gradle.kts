pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val offlineQnn = file(".local/toolchain/qnn-maven")
        if (offlineQnn.isDirectory) {
            maven(offlineQnn.toURI()) {
                content {
                    includeModule("com.microsoft.onnxruntime", "onnxruntime-android-qnn")
                    includeModule("com.qualcomm.qti", "qnn-runtime")
                }
            }
        }
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "ParallelVrGalleryPro"
include(":app")
