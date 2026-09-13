package com.jiaocai.download.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jiaocai.download.ui.screens.AboutScreen
import com.jiaocai.download.ui.screens.UsageNoticeDialog
import com.jiaocai.download.ui.theme.TextbookTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 合规界面的渲染冒烟测试。
 *
 * 首次启动的使用声明在启动路径上——它一旦渲染失败，应用就用不了，所以必须有测试兜住。
 * 「关于与免责」页承载免责声明与赞助入口，同样需要保证能渲染、文案不缺失。
 *
 * 跑在 Robolectric 上，不需要模拟器或真机：./gradlew testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegalScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aboutScreen_rendersDisclaimerCommitmentsAndSponsor() {
        compose.setContent {
            TextbookTheme { AboutScreen(onBack = {}) }
        }

        compose.onNodeWithText("关于与免责").assertIsDisplayed()
        compose.onNodeWithText("免责声明").assertIsDisplayed()
        compose.onNodeWithText("本应用的承诺").assertExists()
        compose.onNodeWithText("支持开发").assertExists()
        compose.onNodeWithText("开源许可").assertExists()

        // 赞助文案必须明确「不解锁任何功能」，这是不能弱化的措辞
        compose.onNodeWithText(LegalText.SPONSOR_NOTICE).assertExists()
        // 收款码已内置：应显示两张码，而不是占位提示
        compose.onNodeWithContentDescription("微信 收款码").assertExists()
        compose.onNodeWithContentDescription("支付宝 收款码").assertExists()
        compose.onNodeWithText(LegalText.SPONSOR_PLACEHOLDER).assertDoesNotExist()

        // 并排时二维码偏小，点按必须能放大
        compose.onNodeWithContentDescription("微信 收款码").performScrollTo().performClick()
        compose.onNodeWithContentDescription("微信 收款码（放大）").assertExists()

        // 四段式免责声明每一段都要在界面上
        LegalText.DISCLAIMER_SECTIONS.forEach { (title, body) ->
            compose.onNodeWithText(title).assertExists()
            compose.onNodeWithText(body).assertExists()
        }
    }

    @Test
    fun usageNotice_showsFullNoticeAndAccepts() {
        var accepted = false
        compose.setContent {
            TextbookTheme { UsageNoticeDialog(onAccept = { accepted = true }) }
        }

        compose.onNodeWithText("使用前请阅读").assertIsDisplayed()
        compose.onNodeWithText(LegalText.USAGE_NOTICE).assertExists()
        compose.onNodeWithText("我已知悉").assertIsDisplayed().performClick()

        assertTrue("点击「我已知悉」应回调 onAccept", accepted)
    }
}
