import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// release 签名从 keystore.properties 读取（该文件 + .jks 都在 .gitignore 里，不入库）。
// 文件缺失时（如 CI 或新克隆）不报错，只是产出未签名 APK。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystorePropsFile.exists() &&
        !keystoreProps.getProperty("storeFile").isNullOrBlank()
val pangguaiPropsFile = rootProject.file("pangguai.properties")
val pangguaiProps = Properties().apply {
    if (pangguaiPropsFile.exists()) pangguaiPropsFile.inputStream().use { load(it) }
}
val pangguaiAppSecret = pangguaiProps.getProperty("APP_SECRET", "")

android {
    namespace = "com.fktm.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rea.campuslite"
        minSdk = 26
        targetSdk = 36
        versionCode = 28
        versionName = "0.6.3"
        buildConfigField(
            "String",
            "PANGGUAI_APP_SECRET",
            "\"${pangguaiAppSecret.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
        buildConfigField("String", "PANGGUAI_API_VERSION", "\"1.60.3\"")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // The first build is a functional prototype. Keep symbols intact so
            // BLE/Compose/reflection failures remain diagnosable on-device.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 自更新要求新旧 APK 同签名，否则覆盖安装会被系统拒绝
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // Shizuku UserService 靠 AIDL 跨进程调用（在 shell 身份的独立进程里跑 logcat）
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(project(":life798"))

    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("io.github.kyant0:backdrop:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.alipay.sdk:alipaysdk-android:15.8.16")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Shizuku：借 shell 身份读 logcat（普通 App 从 Android 4.1 起读不到别的进程日志）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // 扫饮水机二维码：CameraX 出帧，ZXing 纯 Java 解码。
    // 刻意不用 ML Kit —— 那套要 Google Play 服务，国内 ROM 上不一定有。
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // JVM 单测：黄金向量回归
    testImplementation("junit:junit:4.13.2")
    // 单测里跑不了 android.jar 的 org.json 桩实现（会抛 "not mocked"），
    // 换成官方 JVM 实现，清单解析才能真正被断言。
    testImplementation("org.json:json:20240303")
}
