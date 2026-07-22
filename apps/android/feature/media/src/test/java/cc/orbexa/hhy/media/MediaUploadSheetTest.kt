package cc.orbexa.hhy.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaUploadSheetTest {
    @Test
    fun acceptedTypeSummaryUsesActualMediaTypes() {
        assertEquals("支持图片文件", acceptedTypeSummary(arrayOf("image/*")))
        assertEquals(
            "支持图片、视频文件",
            acceptedTypeSummary(arrayOf("image/jpeg", "video/mp4")),
        )
    }

    @Test
    fun acceptedTypeSummaryDoesNotInventUnknownTypeNames() {
        assertEquals(
            "支持当前业务允许的文件类型",
            acceptedTypeSummary(arrayOf("application/pdf")),
        )
        assertEquals(
            "支持当前业务允许的文件类型",
            acceptedTypeSummary(arrayOf("*/*")),
        )
    }
}
