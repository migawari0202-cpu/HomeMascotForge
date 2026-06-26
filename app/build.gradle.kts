import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {

    namespace = "com.example.mascotforge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mascotforge"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // APIキー読み込み
        val localProperties = rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use {
            Properties().apply { load(it) }
        }
        val apiKey = localProperties?.getProperty("WEATHER_API_KEY") ?: ""
        buildConfigField("String", "WEATHER_API_KEY", "\"$apiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

// キャラクターリソース同期タスク
tasks.register<Copy>("syncCharacters") {
    description = "キャラクターのリソース(画像・テキスト)をassetsに同期"
    group = "mascotforge"

    from("src/main/java/com/example/mascotforge/characters") {
        include("**/*.png")
        include("**/*.webp")
        include("**/*.jpg")
        include("**/*.jpeg")
        include("**/*.txt")
        include("**/*.json")
        exclude("**/*.kt")
        exclude("**/*.java")
    }

    into("src/main/assets/characters")
    includeEmptyDirs = false

    doFirst {
        println("キャラクターリソースを同期中...")
    }

    doLast {
        println("キャラクターリソースの同期完了!")
    }
}

tasks.named("preBuild") {
    dependsOn("syncCharacters")
}

dependencies {
    // === 最新版（2025年1月時点） ===
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // === Compose（BOM使用） ===
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")

    //  追加: Compose ViewModel サポート
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.compose.material:material-icons-core")

    // === ネットワーク ===
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // === WorkManager ===
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // === Room ===
    val roomVersion = "2.7.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // === Coroutines ===
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // === テスト ===
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}