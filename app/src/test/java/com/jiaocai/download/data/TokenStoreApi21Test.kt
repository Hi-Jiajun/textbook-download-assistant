package com.jiaocai.download.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android 5.0/5.1（API 21-22）没有可用的 Keystore AES/GCM，
 * 验证降级存储的编解码可回环，且明文不直接落盘。
 * 注：Robolectric 4.16 最低只支持 API 23，这里直接测降级分支的纯逻辑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenStoreApi21Test {

    @Test
    fun `fallback encoding round trips and hides plaintext`() {
        val token = """{"access_token":"test-token","mac_key":"test-mac","diff":0}"""

        val stored = TokenStore.encodeFallback(token)

        assertTrue(stored.startsWith("plain:"))
        assertFalse(stored.contains("test-token"))
        assertEquals(token, TokenStore.decodeFallback(stored))
    }

    @Test
    fun `decode fallback ignores non fallback payload`() {
        assertNull(TokenStore.decodeFallback("3q2+7w==encrypted"))
    }
}
