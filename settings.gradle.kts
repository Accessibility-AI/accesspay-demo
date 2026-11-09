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
        maven("https://jitpack.io")
    }
}

rootProject.name = "accesspay-demo"

include(":app")

// Build against SDK sources cloned next to this repo instead of the JitPack release:
//   ./gradlew assembleDebug -PlocalSdk=true
// Gradle substitutes com.github.Caculuz-cinks.accessai-android-sdk:* with the included build's modules.
if (providers.gradleProperty("localSdk").orNull == "true") {
    includeBuild("../accessai-android-sdk")
}
