import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * 慧生活服务配置。真实签名盐值只放在本机 life798.properties，不进入 Git。
 * API 网关使用已验证可返回验证码的慧生活公开网关。
 */
val serviceConfigFile = rootProject.file("life798.properties")
val serviceConfig = Properties().apply {
    if (serviceConfigFile.exists()) {
        serviceConfigFile.inputStream().use(::load)
    }
}

fun serviceValue(key: String, defaultValue: String): String =
    serviceConfig.getProperty(key, defaultValue)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

android {
    namespace = "com.water.widget"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")

        buildConfigField(
            "String",
            "API_GATEWAY",
            "\"${serviceValue("API_GATEWAY", "https://cloud.hnkzy.com/api/v1")}\""
        )
        buildConfigField(
            "String",
            "SIGN_SALT",
            "\"${serviceValue("SIGN_SALT", "")}\""
        )
        buildConfigField(
            "String",
            "APP_SIGN_SALT",
            "\"${serviceValue("APP_SIGN_SALT", "")}\""
        )
        buildConfigField(
            "String",
            "MINI_SIGN_SALT",
            "\"${serviceValue("MINI_SIGN_SALT", "")}\""
        )
        buildConfigField(
            "String",
            "API_CID",
            "\"${serviceValue("API_CID", "")}\""
        )
        buildConfigField("String", "VERSION_NAME", "\"0.6.2\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            // 单元测试需要可计算签名，但绝不使用测试盐值生成正式包。
            buildConfigField("String", "SIGN_SALT", "\"unit-test-placeholder\"")
            buildConfigField("String", "APP_SIGN_SALT", "\"unit-test-placeholder\"")
            buildConfigField("String", "MINI_SIGN_SALT", "\"unit-test-placeholder\"")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.github.kyant0:backdrop:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    implementation("com.alipay.sdk:alipaysdk-android:15.8.16")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
