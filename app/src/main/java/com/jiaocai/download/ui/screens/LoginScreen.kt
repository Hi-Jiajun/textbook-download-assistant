@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.jiaocai.download.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val EXTRACT_JS = """
(function () {
  try {
    const authKey = Object.keys(localStorage).find(
      key => /^ND_UC_AUTH-[^&]+&[^&]+&token$/.test(key)
    );
    if (!authKey) return "__NO_TOKEN__";
    const tokenData = JSON.parse(localStorage.getItem(authKey));
    const cred = JSON.parse(tokenData.value);
    return { access_token: cred.access_token, mac_key: cred.mac_key, diff: cred.diff };
  } catch (e) {
    return "__NO_TOKEN__";
  }
})();
"""

@Composable
fun LoginScreen(
    hint: String,
    error: String?,
    onToken: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            webViewClient = WebViewClient()
            loadUrl("https://basic.smartedu.cn")
        }
    }
    var showManual by remember { mutableStateOf(false) }
    var manualJson by remember { mutableStateOf("") }

    // 轮询 localStorage，登录成功即读取凭据并进入下一步
    LaunchedEffect(webView) {
        while (isActive) {
            val raw = suspendCancellableCoroutine { cont ->
                webView.evaluateJavascript(EXTRACT_JS) { value ->
                    if (cont.isActive) cont.resume(value) { }
                }
            }
            val trimmed = raw?.trim()
            if (!trimmed.isNullOrBlank() && trimmed.startsWith("{") && !trimmed.startsWith("\"")) {
                onToken(trimmed)
                return@LaunchedEffect
            }
            delay(1500)
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("上一步") }
            TextButton(onClick = { webView.reload() }) { Text("重新加载") }
        }
        Text("第 2 步 / 共 3 步：登录并获取凭据", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall)
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))

        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showManual = !showManual }) {
            Text(if (showManual) "收起手动粘贴" else "网页打不开？手动粘贴凭据")
        }
        if (showManual) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "在电脑浏览器打开平台并登录，按 F12 → 控制台，粘贴取凭据脚本后复制整段 JSON 粘到下面。（脚本见 README）",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = manualJson,
                    onValueChange = { manualJson = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("粘贴 { access_token, mac_key, diff } JSON") },
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { if (manualJson.isNotBlank()) onToken(manualJson) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("用这份凭据继续")
                }
            }
        }
    }
}
