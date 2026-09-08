package com.jiaocai.download.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthSignerTest {

    @Test
    fun signatureString_matchesPlatformFormat() {
        val actual = AuthSigner.signatureString(
            url = "https://example.com/a%20b?x=1",
            method = "get",
            nonce = "nonce",
        )

        assertEquals("nonce\nGET\n/a b?x=1\nexample.com\n", actual)
    }

    @Test
    fun signMac_isDeterministic() {
        assertEquals(
            "kwezuRXvtRcf8U2MtV+8x5jGwO8UVtZt7RpqpyOli3s=",
            AuthSigner.signMac("hello", "key"),
        )
    }

    @Test
    fun parseTokenInput_readsAllFields() {
        val credentials = AuthSigner.parseTokenInput(
            """{"access_token":"token","mac_key":"mac","diff":1234}""",
        )

        assertEquals("token", credentials.accessToken)
        assertEquals("mac", credentials.macKey)
        assertEquals(1234L, credentials.diff)
    }

    @Test
    fun parseTokenInput_rejectsMissingFields() {
        val error = assertThrows(AuthSigner.TokenInputError::class.java) {
            AuthSigner.parseTokenInput("""{"access_token":"token"}""")
        }

        assertTrue(error.message.orEmpty().contains("mac_key"))
    }

    @Test
    fun buildNdAuth_usesProvidedNonce() {
        val credentials = AuthSigner.Credentials("token", "key", 0)
        val header = AuthSigner.buildNdAuth(
            url = "https://example.com/file.pdf",
            method = "GET",
            credentials = credentials,
            nonce = "fixed-nonce",
        )

        assertTrue(header.startsWith("MAC id=\"token\",nonce=\"fixed-nonce\",mac=\""))
        assertTrue(header.endsWith("\""))
    }
}
