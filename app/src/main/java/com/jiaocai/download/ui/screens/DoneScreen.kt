package com.jiaocai.download.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jiaocai.download.BuildConfig

@Composable
fun DoneScreen(
    downloadedCount: Int,
    bookmarksCount: Int,
    onAgain: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("下载完成", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text("已保存 $downloadedCount 本课本到应用下载目录")
                Text("✅ 其中 $bookmarksCount 本已自动写入章节书签", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("可随时在「我的课本库」里离线打开这些 PDF。", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(24.dp))
        SupportPanel()

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAgain, modifier = Modifier.weight(1f)) { Text("再来一个") }
            OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.weight(1f)) { Text("课本库") }
            if (BuildConfig.SPONSOR_URL.isNotBlank()) {
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SPONSOR_URL))) },
                    modifier = Modifier.weight(1f),
                ) { Text("赞助" ) }
            }
        }
    }
}

@Composable
private fun SupportPanel() {
    Column {
        Text("支持本工具（自愿，不锁定下载）", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (BuildConfig.SPONSOR_URL.isNotBlank()) {
            Text("🎁 可直接通过「赞助码 / 爱发电」支持；")
        }
        Text("⭐ 在 GitHub 上给项目点 Star，并发邮件到 ${BuildConfig.SUPPORT_EMAIL.ifBlank { "你的联系邮箱" }} 即可免费领取一份授权码。")
        Text("本 App 完全免费，打赏与否都不影响使用。", style = MaterialTheme.typography.bodySmall)
    }
}
