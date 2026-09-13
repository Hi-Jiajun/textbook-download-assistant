package com.jiaocai.download.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.jiaocai.download.ui.LegalText

/**
 * 首次启动的使用声明。必须由用户主动点「我已知悉」才能进入应用，
 * 不允许点外部或按返回键跳过，避免"声明形同虚设"。
 */
@Composable
fun UsageNoticeDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* 必须主动确认 */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("使用前请阅读", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    LegalText.USAGE_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("我已知悉", fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
