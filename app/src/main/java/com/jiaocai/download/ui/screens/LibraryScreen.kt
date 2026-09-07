package com.jiaocai.download.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.jiaocai.download.model.SavedItem
import java.io.File

@Composable
fun LibraryScreen(
    library: List<SavedItem>,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("我的课本库", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Spacer(Modifier.height(8.dp))
        if (library.isEmpty()) {
            Text("还没有下载的课本。回到首页粘贴链接，跟着步骤下载即可自动收录到这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(library, key = { it.path }) { item ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(item.title, fontWeight = FontWeight.Bold)
                            Text("${item.format.uppercase()}  ·  ${item.edition ?: "未知版别"}  ·  ${item.fileName}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Row {
                                OutlinedButton(onClick = { openPdf(context, item.path) }) { Text("打开") }
                                TextButton(onClick = { onDelete(item.path) }) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openPdf(context: android.content.Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "打开 PDF"))
}
