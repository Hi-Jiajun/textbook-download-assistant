package com.jiaocai.download.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
fun DownloadScreen(
    done: Long,
    total: Long,
    error: String?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        if (error == null) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("正在下载并写入书签…", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
        } else {
            Text("下载失败", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(error)
        }
        Spacer(Modifier.height(24.dp))
        if (total > 0) {
            LinearProgressIndicator(progress = { (done.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("${formatBytes(done)} / ${formatBytes(total)}")
        } else {
            Text("已下载 ${formatBytes(done)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
