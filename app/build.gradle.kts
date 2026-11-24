import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 署名鍵はリポジトリの外。keystore.properties（gitignore 済み）か環境変数（CI は Secret の 4 つ）で渡す。
val signing = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)

android {
    namespace = "app.thetahatch.tvhome"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.thetahatch.tvhome"
        minSdk = 28
        targetSdk = 35
        versionCode = 11
        versionName = "0.9.0"
        // テレビは ARM（AQUOS は 64 ビット、R4-SH は 32 ビット）。x86 は入れない（APK を小さく）
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    // シリーズ共通の鍵（Theta.Hatch.Series の secrets/README.md）。
    // CI：ANDROID_KEYSTORE_B64 を戻したファイルの場所が ANDROID_KEYSTORE_FILE、ANDROID_KEYSTORE_PASSWORD・ANDROID_KEY_ALIAS・ANDROID_KEY_PASSWORD
    signingConfigs {
        val store = signingValue("storeFile", "ANDROID_KEYSTORE_FILE")
        if (store != null) {
            create("release") {
                storeFile = file(store)
                storePassword = signingValue("storePassword", "ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "ANDROID_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // mpv の部品（.so）を圧縮して入れる（入れるときに展開。APK とダウンロードを小さく）
    packaging { jniLibs { useLegacyPackaging = true } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("roborazzi.test.record", "true")
            // Maven Central に届かない環境ではミラーを使う（例：ROBOLECTRIC_REPO=https://maven-central.storage-download.googleapis.com/maven2/）
            System.getenv("ROBOLECTRIC_REPO")?.let { url -> it.systemProperty("robolectric.defaultRepository.url", url) }
        }
    }
    lint {
        abortOnError = true
        // テレビ用の作り（タッチなし・LEANBACK）。ホームアプリなので一覧の取得は意図どおり
        disable += setOf("QueryAllPackagesPermission", "ExpiredTargetSdkVersion", "OldTargetApi", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(bom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // Google カレンダー：テレビの Google アカウントでの許可（Home.Hatch の Gmail と同じ）
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    // おんがく：再生（別プロセスのサービス）
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    // えいが：mpv（Theta Movie Hatch と同じく、mkv・HEVC・DTS・二か国語の .ts もそのまま）。ARM の 2 種類だけ入れる
    implementation("dev.jdtech.mpv:libmpv:1.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // 画面の見本（スクリーンショット）を JVM 上で描く
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.52.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.52.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
