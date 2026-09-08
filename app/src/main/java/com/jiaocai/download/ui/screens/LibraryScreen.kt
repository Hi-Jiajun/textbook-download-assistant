package com.jiaocai.download.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    var pendingDelete by remember { mutableStateOf<SavedItem?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("我的教材库", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Spacer(Modifier.height(8.dp))
        if (library.isEmpty()) {
            Text(
                "还没有下载的教材。回到首页勾选教材，跟着步骤下载后会自动收录到这里。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 用 path+addedAt 作为唯一 key，避免同一本书重复下载产生相同路径导致 key 冲突。
                items(library, key = { it.path + ":" + it.addedAt }) { item ->
                    SavedCard(item, onDelete = { pendingDelete = item })
                }
            }
        }
    }
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除教材") },
            text = { Text("将同时删除本地 PDF 文件，且无法恢复。确定删除《${item.title}》吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item.path)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SavedCard(item: SavedItem, onDelete: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.format.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(item.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${item.format.uppercase()}  ·  ${item.edition ?: "未知版别"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openPdf(context, item.path) }, modifier = Modifier.weight(1f)) { Text("打开", maxLines = 1) }
                OutlinedButton(onClick = { openFolder(context, item.path) }, modifier = Modifier.weight(1f)) { Text("打开位置", maxLines = 1) }
                OutlinedButton(onClick = { sharePdf(context, item.path) }, modifier = Modifier.weight(1f)) { Text("分享", maxLines = 1) }
            }
        }
    }
}

private fun openPdf(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在或已被移动", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开 PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开该 PDF", Toast.LENGTH_SHORT).show()
    }
}

private fun sharePdf(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在或已被移动", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "无法分享该 PDF", Toast.LENGTH_SHORT).show()
    }
}

/** 用系统文件管理器定位到文件所在目录；不支持时回退为复制目录路径。 */
private fun openFolder(context: Context, path: String) {
    val file = File(path)
    val folder = file.parentFile
    if (folder == null || !folder.exists()) {
        Toast.makeText(context, "文件夹不存在或已被移动", Toast.LENGTH_SHORT).show()
        return
    }
    val root = Environment.getExternalStorageDirectory().absolutePath
    if (!folder.absolutePath.startsWith("$root/")) {
        copyFolderPath(context, folder)
        return
    }
    val documentId = "primary:" + folder.absolutePath.removePrefix("$root/")
    val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // 优先直接打开系统「文件」，避免弹出包含网盘/办公软件的通用选择器。
    for (pkg in listOf("com.google.android.documentsui", "com.android.documentsui")) {
        try {
            context.startActivity(Intent(intent).setPackage(pkg))
            return
        } catch (_: Exception) {
            // 尝试下一个候选。
        }
    }
    try {
        context.startActivity(Intent.createChooser(intent, "用文件管理器打开"))
    } catch (e: Exception) {
        copyFolderPath(context, folder)
    }
}

private fun copyFolderPath(context: Context, folder: File) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("文件夹路径", folder.absolutePath))
    Toast.makeText(context, "无法打开文件管理器，已复制目录：${folder.absolutePath}", Toast.LENGTH_SHORT).show()
}
