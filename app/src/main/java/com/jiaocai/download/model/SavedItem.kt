package com.jiaocai.download.model

/** 本地课本库中的一条记录。 */
data class SavedItem(
    val title: String,
    val path: String,
    val format: String,
    val edition: String?,
    val addedAt: Long,
) {
    /** 文件名（不含目录）。 */
    val fileName: String get() = path.substringAfterLast('/').substringAfterLast('\\')
}
