package com.jiaocai.download.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InputScreen(
    urls: String,
    onUrlsChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("第 1 步 / 共 3 步：粘贴链接", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("把电子课本的“预览页面网址”粘贴到下面，每行一个：")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = urls,
            onValueChange = onUrlsChange,
            modifier = Modifier.fillMaxWidth().height(180.dp),
            placeholder = { Text("https://basic.smartedu.cn/tchMaterial/detail?...&contentId=...") },
        )
        Spacer(Modifier.height(12.dp))
        Text("示例：basic.smartedu.cn/tchMaterial/detail?contentType=assets_document&contentId=XXXX", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("上一步") }
            Button(onClick = onNext) { Text("下一步") }
        }
    }
}
