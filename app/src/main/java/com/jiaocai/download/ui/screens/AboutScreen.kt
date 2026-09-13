package com.jiaocai.download.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiaocai.download.BuildConfig
import com.jiaocai.download.ui.LegalText

/**
 * 「关于与免责」页：合规声明、本应用的承诺、自愿赞助入口、开源许可。
 *
 * 赞助入口只在这里出现——应用不做任何弹窗、提醒或引导去要赞助。
 * 收款码从 assets/sponsor/ 读取；未内置时显示占位提示，不留空白。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val wechat = remember { loadAssetBitmap(context, "sponsor/wechat.png") }
    val alipay = remember { loadAssetBitmap(context, "sponsor/alipay.png") }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
            Text("关于与免责", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "教材下载助手 v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "免费开源的安卓端电子课本下载工具，基于 MIT 许可的上游项目 tchMaterial-parser 二次开发。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            SectionCard("免责声明") {
                LegalText.DISCLAIMER_SECTIONS.forEachIndexed { index, (title, body) ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard("本应用的承诺") {
                listOf(
                    "不存储、不托管、不分发任何教材内容，也没有任何服务端。",
                    "不移除教材中的任何水印或权利标识。",
                    "不提供、也不提示任何绕过平台登录或鉴权的方法。",
                    "永久免费，没有付费点、没有广告、不做任何数据上报。",
                ).forEachIndexed { index, line ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    Text(
                        "· $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard("支持开发") {
                Text(
                    LegalText.SPONSOR_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(16.dp))
                if (wechat == null && alipay == null) {
                    Text(
                        LegalText.SPONSOR_PLACEHOLDER,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SponsorCode("微信", wechat, Modifier.weight(1f))
                        SponsorCode("支付宝", alipay, Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard("开源许可") {
                Text(
                    "本工程采用 MIT 许可，完整文本见仓库根目录 LICENSE。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "上游项目 tchMaterial-parser（作者：肥宅水水呀）同样使用 MIT 许可，版权与许可声明见 THIRD_PARTY_NOTICES.md。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            HorizontalDivider(
                Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            )
            content()
        }
    }
}

@Composable
private fun SponsorCode(label: String, bitmap: ImageBitmap?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "$label 收款码",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Spacer(Modifier.height(80.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

/** 从 assets 读取收款码。文件不存在时返回 null，由界面显示占位提示。 */
private fun loadAssetBitmap(context: Context, path: String): ImageBitmap? = runCatching {
    context.assets.open(path).use { input ->
        BitmapFactory.decodeStream(input)?.asImageBitmap()
    }
}.getOrNull()
