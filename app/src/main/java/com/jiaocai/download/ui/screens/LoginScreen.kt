@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.jiaocai.download.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
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

// 官方统一身份认证登录页。登录成功后会回跳 basic.smartedu.cn（官网首页），
// 这里由 WebViewClient 拦截该回跳，并直接从 auth 域 localStorage 取走登录凭据，避免进入官网首页。
private const val LOGIN_URL = "https://auth.smartedu.cn/uias/login"
// 认证域前缀：登录成功后任何离开 auth 域的跳转都是无需展示的回跳，直接拦截。
private const val LOGIN_URL_PREFIX = "https://auth.smartedu.cn"

private const val EXTRACT_JS = """
(function () {
  try {
    const authKey = Object.keys(localStorage).find(
      key => /^ND_UC_AUTH-[^&]+&[^&]+&token$/.test(key)
    );
    if (!authKey) return "__NO_TOKEN__";
    const tokenData = JSON.parse(localStorage.getItem(authKey));
    let cred = null;
    if (tokenData && typeof tokenData.value === "string") {
      cred = JSON.parse(tokenData.value);
    } else if (tokenData && tokenData.cred) {
      cred = tokenData.cred;
    } else if (tokenData) {
      cred = tokenData;
    }
    if (!cred) return "__NO_TOKEN__";
    const access_token = cred.access_token;
    const mac_key = cred.mac_key;
    const diff = cred.diff;
    const hasAll = access_token && mac_key && diff !== undefined && diff !== null && diff !== "";
    if (hasAll) return { access_token: access_token, mac_key: mac_key, diff: diff };
    return "__NO_TOKEN__";
  } catch (e) {
    return "__NO_TOKEN__";
  }
})();
"""

/** 清空 WebView 里保留的登录态（localStorage + cookie + 缓存 + 历史）。 */
private fun clearWebViewSession(webView: WebView) {
    webView.evaluateJavascript("localStorage.clear();", null)
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    webView.clearHistory()
    webView.clearCache(true)
}

/**
 * 登录并获取下载凭据。内嵌官网登录页（自动抓取 token），并提供手动粘贴兜底。
 * 登录成功后回跳官网首页时会被拦截：先从 auth 域把 token 取走，避免跳到首页。
 */
@SuppressLint("SetJavaScriptEnabled") // 官方登录页必须启用 JS；页面限定在 https 的 auth 域内。
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
    // 是否已捕获到凭据（避免 polling 与跳转拦截重复上报）。
    var captured by remember { mutableStateOf(false) }
    // 重新加载/重新登录后重启轮询的触发器。
    var reloadKey by remember { mutableIntStateOf(0) }

    val onTokenOnce: (String?) -> Unit = { value ->
        val t = value?.trim()
        if (!captured && !t.isNullOrBlank() && t.startsWith("{") && !t.startsWith("\"")) {
            captured = true
            onToken(t)
        }
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            // 登录页只需要访问 https 站点，关闭本地文件/内容访问，缩小攻击面。
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // 不让 WebView 自己保存表单/密码。
            @Suppress("DEPRECATION")
            settings.saveFormData = false
            @Suppress("DEPRECATION")
            settings.savePassword = false
            // 也不要让系统自动填充接管登录框：否则登录后会弹「保存 auth.smartedu.cn 的
            // 账号密码？」，等于把平台凭据交给厂商/Google 的密码管理器保管。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            webViewClient = object : WebViewClient() {
                private fun intercept(view: WebView, url: String): Boolean {
                    // 登录成功后离开 auth 域的跳转（官网首页/手机版首页等）：先从当前 auth 页抓 token，再阻止跳转。
                    if (!url.startsWith(LOGIN_URL_PREFIX)) {
                        view.evaluateJavascript(EXTRACT_JS) { value -> onTokenOnce(value) }
                        return true
                    }
                    return false
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    intercept(view, request.url.toString())

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    intercept(view, url)
            }
            loadUrl(LOGIN_URL)
        }
    }
    var showManual by remember { mutableStateOf(false) }
    var manualJson by remember { mutableStateOf("") }

    LaunchedEffect(webView, reloadKey) {
        while (isActive) {
            val raw = suspendCancellableCoroutine { cont ->
                webView.evaluateJavascript(EXTRACT_JS) { value ->
                    if (cont.isActive) cont.resume(value) { }
                }
            }
            onTokenOnce(raw)
            if (captured) return@LaunchedEffect
            delay(400)
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // 顶栏
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("返回") }
            TextButton(onClick = {
                captured = false
                reloadKey++
                webView.reload()
            }) { Text("重新加载") }
        }

        // 标题区（非绿色大块，改为清爽排布）
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("登录 国家中小学智慧教育平台", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(LOGIN_URL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = error ?: hint,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    OutlinedButton(
                        onClick = {
                            // 切换账号：清空 WebView 登录态并重载，回到新鲜登录表单。
                            clearWebViewSession(webView)
                            captured = false
                            reloadKey++
                            webView.loadUrl(LOGIN_URL)
                            onLogout()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("重新登录") }
                    OutlinedButton(
                        onClick = {
                            // 退出登录：清空 WebView 登录态与 App 凭据，回到首页。
                            clearWebViewSession(webView)
                            onLogout()
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("退出登录") }
                }
                Spacer(Modifier.height(8.dp))
            }
            TextButton(onClick = {
                showManual = !showManual
            }) {
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
                        onClick = {
                            if (manualJson.isNotBlank()) {
                                captured = true
                                onToken(manualJson.trim())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("用这份凭据登录") }
                }
            }
        }
    }
}
