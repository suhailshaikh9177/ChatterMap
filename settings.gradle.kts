// =================================================================
// settings.gradle.kts - Project-level
// =================================================================

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") } // ✅ FIXED: Explicit JitPack
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // ✅ FIXED: Explicit JitPack
    }
}

rootProject.name = "locatorchat"
include(":app")
