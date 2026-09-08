package com.jiaocai.download.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jiaocai.download.model.Textbook
import com.jiaocai.download.ui.DownloadViewModel

/** 第一步：浏览并勾选要下载的教材。 */
@Composable
fun BrowseScreen(
    viewModel: DownloadViewModel,
    state: DownloadViewModel.UiState,
    onOpenLibrary: () -> Unit,
) {
    val stages = remember(state.textbooks) {
        state.textbooks.map { it.stage }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val subjects = remember(state.textbooks) {
        state.textbooks.map { it.subject }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val versions = remember(state.textbooks) {
        state.textbooks.map { it.version }.filter { it.isNotBlank() }.distinct().sorted()
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("选择要下载的教材", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onOpenLibrary) { Text("我的课本") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索书名 / 学科 / 年级") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdown("学段", stages, state.stageFilter, viewModel::setStageFilter, Modifier.weight(1f))
            FilterDropdown("学科", subjects, state.subjectFilter, viewModel::setSubjectFilter, Modifier.weight(1f))
            FilterDropdown("版本", versions, state.versionFilter, viewModel::setVersionFilter, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.catalogLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.textbooks.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.catalogError ?: "没有找到教材",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = viewModel::refreshCatalog) { Text("重试") }
                }
                filtered.isEmpty() -> Text("没有符合筛选条件的教材", Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { b ->
                        TextbookRow(
                            book = b,
                            selected = b.id in state.selectedIds,
                            onToggle = { viewModel.toggleSelect(b.id) },
                        )
                    }
                }
            }
        }

        val n = state.selectedIds.size
        Button(
            onClick = viewModel::startDownload,
            enabled = n > 0 && !state.catalogLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (n > 0) "去下载（$n）" else "请先勾选教材")
        }
    }
}

@Composable
private fun TextbookRow(book: Textbook, selected: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(book.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOf(book.stage, book.subject, book.version, book.grade, book.volume)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider()
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
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
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
