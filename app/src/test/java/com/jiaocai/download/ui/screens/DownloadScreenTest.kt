package com.jiaocai.download.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jiaocai.download.ui.theme.TextbookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 下载页状态测试。
 *
 * 专门盯住一个真实出现过的故障：下载失败时页面没有任何按钮，而返回键又被禁用，
 * 用户除了杀进程没有别的出路。这里保证失败态永远有「重试」和「返回首页」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun errorState_offersRetryAndBackHome() {
        var retried = false
        var wentHome = false
        compose.setContent {
            TextbookTheme {
                DownloadScreen(
                    done = 1024,
                    total = 4096,
                    error = "第 2/3 本下载失败：连接超时",
                    index = 1,
                    count = 3,
                    onRetry = { retried = true },
                    onBackHome = { wentHome = true },
                )
            }
        }

        compose.onNodeWithText("下载失败").assertIsDisplayed()
        compose.onNodeWithText("重试").assertIsDisplayed().performClick()
        compose.onNodeWithText("返回首页").assertIsDisplayed().performClick()
        assertTrue("「重试」应回调 onRetry", retried)
        assertTrue("「返回首页」应回调 onBackHome", wentHome)
    }

    @Test
    fun loadingState_showsBatchProgress() {
        compose.setContent {
            TextbookTheme {
                DownloadScreen(
                    done = 2L * 1024 * 1024,
                    total = 8L * 1024 * 1024,
                    error = null,
                    index = 1,
                    count = 3,
                    onRetry = {},
                    onBackHome = {},
                )
            }
        }

        compose.onNodeWithText("正在下载并写入书签…").assertIsDisplayed()
        compose.onNodeWithText("第 2 / 3 本").assertIsDisplayed()
    }
}
