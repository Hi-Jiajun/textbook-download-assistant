package com.jiaocai.download.model

/** 单个可下载资源（电子课本 PDF 或配套音频）。 */
data class ResourceInfo(
    val title: String,
    val url: String,
    val format: String,
    val chapters: List<Chapter>,
    val edition: String? = null,
)

/** PDF 章节目录节点。 */
data class Chapter(
    val title: String,
    val pageIndex: Int?,
    val children: List<Chapter> = emptyList(),
)
