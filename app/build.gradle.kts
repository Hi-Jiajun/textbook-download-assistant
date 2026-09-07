plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jiaocai.download"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jiaocai.download"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // 从 .env 读取可选配置（BuildConfig 常量）。缺失时用空串兜底。
        val licenseApiBaseUrl = providers.gradleProperty("LICENSE_API_BASE_URL").orNull ?: ""
        val sponsorUrl = providers.gradleProperty("SPONSOR_URL").orNull ?: ""
        val supportEmail = providers.gradleProperty("SUPPORT_EMAIL").orNull ?: ""
        buildConfigField("String", "LICENSE_API_BASE_URL", "\"$licenseApiBaseUrl\"")
        buildConfigField("String", "SPONSOR_URL", "\"$sponsorUrl\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"$supportEmail\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // 网络与 JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 本地 Token 持久化
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // PDF 书签写入（上游 pypdf 的 Android 等价实现）
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
