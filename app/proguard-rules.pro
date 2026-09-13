# OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# PDFBox：书签写入与 PDF 解析依赖这些类，混淆后必须保留。
# （pdfbox-android 内部会按类名加载部分实现，裁掉会在运行时才炸，属于最难排查的一类问题。）
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# pdfbox 对 JPEG2000 解码器是可选依赖，本项目不带该库，忽略告警即可。
-dontwarn com.gemalto.jp2.**

# Kotlin 元数据与反射相关
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
