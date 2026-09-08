package com.jiaocai.download.model

/** 目录里的一本电子教材（用于浏览与勾选）。 */
data class Textbook(
    val id: String,
    val title: String,
    val stage: String,    // 学段（小学/初中/高中…）
    val subject: String,  // 学科（语文/数学…）
    val version: String,  // 版别（人教版/统编版…）
    val grade: String,    // 年级（一年级…）
    val volume: String,   // 册次（上册/下册…）
    val thumb: String? = null, // 封面预览图 URL
)
