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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine

private const val LOGIN_URL = "https://basic.smartedu.cn"

private const val EXTRACT_JS = """
(function () {
  try {
    const authKey = Object.keys(localStorage).find(
      key => /^ND_UC_AUTH-[^&]+&[^&]+&token$/.test(key)
    );
    if (!authKey) return "__NO_TOKEN__";
    const tokenData = JSON.parse(localStorage.getItem(authKey));
    const cred = tokenData.cred || tokenData;
    return { access_token: cred.access_token, mac_key: cred.mac_key, diff: cred.diff };
  } catch (e) {
    return "__NO_TOKEN__";
  }
})();
"""

/**
 * 登录并获取下载凭据。内嵌官网登录页（自动抓取 token），并提供手动粘贴兜底，
 * 以及退出登录/重新登录入口。
 */
@Composable
fun LoginScreen(
    hint: String,
    error: String?,
    loggedIn: Boolean,
    onToken: (String) -> Unit,
    onLogout: () -> Unit,
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
            loadUrl(LOGIN_URL)
        }
    }
    var showManual by remember { mutableStateOf(false) }
    var manualJson by remember { mutableStateOf("") }

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

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // 顶栏
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("返回") }
            TextButton(onClick = { webView.reload() }) { Text("重新加载") }
        }

        // 标题区（显式展示登录地址）
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("登录 国家中小学智慧教育平台", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("$LOGIN_URL", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(8.dp))

        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        // 底部操作
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (loggedIn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { webView.reload() }, modifier = Modifier.weight(1f)) { Text("重新登录") }
                    OutlinedButton(onClick = onLogout, modifier = Modifier.weight(1f)) { Text("退出登录") }
                }
                Spacer(Modifier.height(8.dp))
            }
            TextButton(onClick = { showManual = !showManual }) {
                Text(if (showManual) "收起手动粘贴凭据" else "网页打不开？手动粘贴凭据")
            }
            if (showManual) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "在浏览器打开 $LOGIN_URL 并登录，按 F12 → 控制台，用取凭据脚本复制出整段 JSON 粘到下面；格式为 { access_token, mac_key, diff }。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = manualJson,
                        onValueChange = { manualJson = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("{ \"access_token\": ..., \"mac_key\": ..., \"diff\": 0 }") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { if (manualJson.isNotBlank()) onToken(manualJson.trim()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("用这份凭据登录") }
                }
            }
        }
    }
}
