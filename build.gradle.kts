plugins {
    // Android Gradle Plugin を最新安定版へ
    id("com.android.application") version "8.10.0" apply false

    // Kotlin + Compose + KSP を最近の安定版へ
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.3.0" apply false
}
