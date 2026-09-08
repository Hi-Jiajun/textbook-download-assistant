package com.jiaocai.download.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartEduApiTest {

    @Test
    fun parseContentId_readsQueryParameters() {
        assertEquals(
            "content-id" to "assets_document",
            SmartEduApi.parseContentId(
                "https://basic.smartedu.cn/tchMaterial/detail?contentType=assets_document&contentId=content-id",
            ),
        )
    }

    @Test
    fun parseContentId_defaultsContentType() {
        assertEquals(
            "content-id" to "assets_document",
            SmartEduApi.parseContentId(
                "https://basic.smartedu.cn/tchMaterial/detail?contentId=content-id",
            ),
        )
    }

    @Test
    fun parseContentId_returnsNullWithoutContentId() {
        assertNull(SmartEduApi.parseContentId("https://basic.smartedu.cn/tchMaterial/detail"))
    }
}
