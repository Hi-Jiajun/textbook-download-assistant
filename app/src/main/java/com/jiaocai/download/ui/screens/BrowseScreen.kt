package com.jiaocai.download.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jiaocai.download.model.Textbook
import com.jiaocai.download.ui.DownloadViewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/** 封面预热的数量上限与间隔：兼顾首滑流畅与"不做批量抓取"的合规约束。 */
private const val PREFETCH_LIMIT = 40
private const val PREFETCH_INTERVAL_MS = 120L

/** 第一步：浏览并勾选要下载的教材。 */
@Composable
fun BrowseScreen(
    viewModel: DownloadViewModel,
    state: DownloadViewModel.UiState,
    onOpenLibrary: () -> Unit,
    onOpenCredentials: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    // 级联：学科依赖已选学段，版本依赖已选学段+学科
    val subjectOptions = remember(state.textbooks, state.stageFilter) {
        val base = if (state.stageFilter.isBlank()) state.textbooks
        else state.textbooks.filter { it.stage == state.stageFilter }
        base.map { it.subject }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val versionOptions = remember(state.textbooks, state.stageFilter, state.subjectFilter) {
        state.textbooks
            .filter {
                (state.stageFilter.isBlank() || it.stage == state.stageFilter) &&
                    (state.subjectFilter.isBlank() || it.subject == state.subjectFilter)
            }
            .map { it.version }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filtered = remember(state.textbooks, state.query, state.stageFilter, state.subjectFilter, state.versionFilter) {
        state.textbooks.filter { b ->
            (state.stageFilter.isBlank() || b.stage == state.stageFilter) &&
                (state.subjectFilter.isBlank() || b.subject == state.subjectFilter) &&
                (state.versionFilter.isBlank() || b.version == state.versionFilter) &&
                (
                    state.query.isBlank() ||
                        b.title.contains(state.query, ignoreCase = true) ||
                        b.subject.contains(state.query, ignoreCase = true) ||
                        b.grade.contains(state.query, ignoreCase = true) ||
                        b.version.contains(state.query, ignoreCase = true)
                    )
            }
        }

    // 目录/筛选变化后预热列表封面，缓解首次快速滑动的卡顿。
    // 合规约束：逐张、带间隔预热，不做一次性并发上百个请求（见 README 的「本项目的红线」）。
    val context = LocalContext.current
    LaunchedEffect(filtered) {
        if (filtered.isNotEmpty()) {
            val loader = context.imageLoader
            filtered.asSequence()
                .mapNotNull { it.thumb?.takeIf(String::isNotBlank) }
                .distinct()
                .take(PREFETCH_LIMIT)
                .forEachIndexed { index, u ->
                    if (index > 0) delay(PREFETCH_INTERVAL_MS)
                    loader.enqueue(
                        ImageRequest.Builder(context)
                            .data(u)
                            .size(256)
                            .crossfade(false)
                            .build(),
                    )
                }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // 品牌头部（清爽，无大色块）
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("书", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("教材下载助手", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("选择需要的教材，一键离线下载", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenCredentials) {
                Text(if (state.loggedIn) "已登录" else "登录", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onOpenLibrary) {
                Text("教材库", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onOpenAbout) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "关于与免责",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 搜索 + 筛选
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            // 错误提示：勾选超限、解析失败等都在这里告诉用户，避免「点了没反应」。
            state.error?.let { message ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            message,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        IconButton(onClick = viewModel::dismissError) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭提示",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索书名 / 学科 / 年级") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                // 目录有 12 小时缓存，之前只有「列表为空」时才能刷新，这里补一个常驻入口。
                trailingIcon = {
                    if (state.catalogLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::refreshCatalog) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新教材目录")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterDropdown("学段", state.filterStages, state.stageFilter, viewModel::setStageFilter, Modifier.weight(1f))
                FilterDropdown("学科", subjectOptions, state.subjectFilter, viewModel::setSubjectFilter, Modifier.weight(1f))
                FilterDropdown("版本", versionOptions, state.versionFilter, viewModel::setVersionFilter, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }

        // 列表
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            when {
                state.catalogLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.textbooks.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.catalogError ?: "没有找到教材", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::refreshCatalog) { Text("重试") }
                }
                filtered.isEmpty() -> Text("没有符合筛选条件的教材", Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { b ->
                        TextbookCard(
                            book = b,
                            selected = b.id in state.selectedIds,
                            onToggle = { viewModel.toggleSelect(b.id) },
                        )
                    }
                }
            }
        }

        // 底部去下载
        val n = state.selectedIds.size
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Button(
                onClick = viewModel::startDownload,
                enabled = n > 0 && !state.catalogLoading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    if (n > 0) "去下载（$n）" else "请先勾选教材",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TextbookCard(book: Textbook, selected: Boolean, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 58.dp, height = 80.dp).clip(RoundedCornerShape(12.dp)).background(coverColor(book.subject)),
                contentAlignment = Alignment.Center,
            ) {
                Text(book.subject.take(1).ifEmpty { "书" }, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (book.thumb != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(book.thumb)
                            .size(256)
                            .crossfade(true)
                            .build(),
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(book.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val meta = listOf(book.stage, book.subject, book.version, book.grade, book.volume)
                    .filter { it.isNotBlank() }.distinct().joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = if (selected.isBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (selected.isBlank()) label else selected,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("全部") }, onClick = { onSelect(""); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

/** 按学科名生成一个稳定的封面色。 */
private fun coverColor(subject: String): Color {
    val palette = listOf(
        Color(0xFF6750A4), Color(0xFF00696D), Color(0xFF7D5260), Color(0xFF8B5000),
        Color(0xFF386A20), Color(0xFF4A5F82), Color(0xFF006875), Color(0xFF7C4DFF),
    )
    // floorMod 而不是 absoluteValue：Int.MIN_VALUE 取绝对值仍是负数，会越界崩溃。
    return palette[Math.floorMod(subject.hashCode(), palette.size)]
}
