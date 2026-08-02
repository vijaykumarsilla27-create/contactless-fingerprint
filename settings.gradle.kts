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
        google()
        mavenCentral()
    }
}

rootProject.name = "ContactlessFingerprintCapture"
include(":app")
// include(":opencv")  // uncomment once the OpenCV-android-sdk module is imported (File > New > Import Module)
