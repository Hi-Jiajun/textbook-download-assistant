package com.jiaocai.download.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlin.math.absoluteValue

/** 第一步：浏览并勾选要下载的教材。 */
@Composable
fun BrowseScreen(
    viewModel: DownloadViewModel,
    state: DownloadViewModel.UiState,
    onOpenLibrary: () -> Unit,
    onOpenCredentials: () -> Unit,
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
                        b.grade.contains(state.query, ignoreCase = true)
                    )
        }
    }

    // 目录/筛选变化后，一次性预热当前列表的封面缩略图，避免首次滑动时逐个下载导致卡顿。
    val context = LocalContext.current
    LaunchedEffect(filtered) {
        if (filtered.isNotEmpty()) {
            val loader = context.imageLoader
            filtered.take(120).forEach { b ->
                b.thumb?.let { u ->
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
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // 品牌顶栏
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("教材下载助手", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("选择需要的教材，一键离线下载", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onOpenCredentials) {
                        Text(if (state.loggedIn) "已登录" else "登录", fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onOpenLibrary) { Text("课本库", fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        // 搜索 + 筛选
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索书名 / 学科 / 年级") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterDropdown("学段", state.filterStages, state.stageFilter, viewModel::setStageFilter, Modifier.weight(1f))
                FilterDropdown("学科", subjectOptions, state.subjectFilter, viewModel::setSubjectFilter, Modifier.weight(1f))
                FilterDropdown("版本", versionOptions, state.versionFilter, viewModel::setVersionFilter, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }

        // 列表
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
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
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

        // 底部
        val n = state.selectedIds.size
        Surface(color = MaterialTheme.colorScheme.surface) {
            Button(
                onClick = viewModel::startDownload,
                enabled = n > 0 && !state.catalogLoading,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (n > 0) "去下载（$n）" else "请先勾选教材", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TextbookCard(book: Textbook, selected: Boolean, onToggle: () -> Unit) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 54.dp, height = 74.dp).clip(RoundedCornerShape(8.dp)).background(coverColor(book.subject)),
                contentAlignment = Alignment.Center,
            ) {
                Text(book.subject.take(1).ifEmpty { "书" }, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (book.thumb != null) {
                    AsyncImage(
                        // 下采样到小尺寸再显示，避免为 46dp 图标加载约 1MB 的原始封面图
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
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(book.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(if (selected.isBlank()) label else selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    return palette[subject.hashCode().absoluteValue % palette.size]
}
