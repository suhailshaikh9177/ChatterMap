// =================================================================
// build.gradle.kts - Project-level
// =================================================================

plugins {
    // ✅ FIXED: Updated to a stable Android Gradle Plugin version
    id("com.android.application") version "8.11.1" apply false
    id("com.android.library") version "8.11.1" apply false

    // ✅ FIXED: Updated Kotlin to a compatible version
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false

    // Google services (Firebase)
    id("com.google.gms.google-services") version "4.4.2" apply false
}
