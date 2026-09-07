package com.jiaocai.download.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    onOpenLibrary: () -> Unit,
    libraryCount: Int,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("教材下载助手", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "跟着步骤走，就能下载国家中小学智慧教育平台的电子课本 PDF。",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text("使用说明", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("① 粘贴电子课本预览页链接")
                Text("② 在 App 内登录平台账号（自动读取登录凭据）")
                Text("③ 点击下载，文件按课本名命名并写入书签")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "仅供个人学习与教学参考，资源版权归原平台所有。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("开始使用")
        }
        if (libraryCount > 0) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) {
                Text("我的课本库（$libraryCount）")
            }
        }
    }
}
