// 根构建文件：只声明插件版本，具体配置在 app/build.gradle.kts
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
