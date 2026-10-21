import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

// =============================================================================
// リリース署名の受け口
//   - 環境変数（CI / GitHub Actions 用）を優先
//   - 無ければ key.properties（ローカル用・.gitignore 済み）を読む
//   - どちらも無ければ「未署名」として扱い、ビルド自体は通る
// キーを入れる場所は RELEASE.md を参照。
// =============================================================================
val keystoreProps = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun keystoreValue(envName: String, propName: String): String? =
    System.getenv(envName) ?: keystoreProps.getProperty(propName)

val releaseStorePath: String? = keystoreValue("KEYSTORE_PATH", "storeFile")
val hasReleaseSigning: Boolean = !releaseStorePath.isNullOrBlank()

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

    }

    signingConfigs {
        create("release") {
            releaseStorePath?.takeIf { it.isNotBlank() }?.let { path ->
                storeFile = rootProject.file(path)
                storePassword = keystoreValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = keystoreValue("KEY_ALIAS", "keyAlias")
                keyPassword = keystoreValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 署名キーが揃っているときだけ署名する（未設定でもビルドは通る）
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    // === 主要 AndroidX / UI ライブラリ ===
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.activity:activity-ktx:1.11.0")
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
    implementation("androidx.activity:activity-compose:1.11.0")

    //  追加: Compose ViewModel サポート
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // === ネットワーク ===
    implementation("com.squareup.retrofit2:retrofit:2.12.0")
    implementation("com.squareup.retrofit2:converter-gson:2.12.0")

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