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
    testOptions {
        unitTests.isIncludeAndroidResources = true
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

    // 封面缩略图加载
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // 本地单元测试（Robolectric 用于 DataStore/JSON/PDFBox 等 Android 环境）
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("org.robolectric:robolectric:4.16.1")

    // Compose 界面冒烟测试（跑在 Robolectric 上，不需要模拟器或真机）
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // createComposeRule() 需要一个宿主 Activity：ui-test-manifest 把它声明进清单。
    // 必须用 debugImplementation 才会参与清单合并（testImplementation 只进测试类路径），
    // 因此它对 release 构建没有任何影响。
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
