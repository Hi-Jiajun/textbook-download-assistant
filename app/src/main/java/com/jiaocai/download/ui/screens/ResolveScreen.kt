package com.jiaocai.download.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jiaocai.download.model.ResourceInfo

@Composable
fun ResolveScreen(
    resources: List<ResourceInfo>,
    loading: Boolean,
    error: String?,
    onDownload: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Text("解析结果", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("正在解析资源…")
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (resources.isNotEmpty() && !loading) {
            Text("共解析到 ${resources.size} 个资源：", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        resources.forEach { r ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(r.title, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("格式：${r.format.uppercase()}   版别：${r.edition ?: "未知"}")
                    Text("章节目录：${r.chapters.size} 项${if (r.chapters.isNotEmpty()) "（将写入书签）" else "（无可写入书签）"}")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("上一步") }
            Button(onClick = onDownload, enabled = resources.isNotEmpty() && !loading) { Text("开始下载") }
        }
    }
}
