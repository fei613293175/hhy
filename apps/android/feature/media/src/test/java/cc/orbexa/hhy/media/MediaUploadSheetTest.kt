package cc.orbexa.hhy.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun oneFileLimitUsesTheRealSingleSelectionContract() {
        assertTrue(usesSingleSelectionContract(1))
        assertFalse(usesSingleSelectionContract(9))
        assertEquals(1, remainingSelectionCapacity(1, 0))
        assertEquals(0, remainingSelectionCapacity(1, 1))
        assertEquals("选择文件", selectionButtonLabel(1, 0))
        assertEquals("已选择文件", selectionButtonLabel(1, 1))
    }

    @Test
    fun multiSelectionDefaultRemainsBoundedByItsConfiguredLimit() {
        assertEquals(3, remainingSelectionCapacity(5, 2))
        assertEquals(0, remainingSelectionCapacity(5, 7))
        assertEquals("继续选择文件", selectionButtonLabel(5, 2))
    }
}
