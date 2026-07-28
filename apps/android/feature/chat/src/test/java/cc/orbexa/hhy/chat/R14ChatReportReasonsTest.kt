package cc.orbexa.hhy.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class R14ChatReportReasonsTest {
    @Test
    fun generatedCatalogExposesOnlyEnabledReasonsInFrozenOrder() {
        assertEquals(
            listOf(
                R14ReportReasonOption("HARASSMENT", "骚扰"),
                R14ReportReasonOption("DUPLICATE_BULK_MESSAGE", "相同文案批量发送"),
                R14ReportReasonOption("DANGEROUS_LINK", "危险链接"),
            ),
            R14_CHAT_REPORT_REASONS,
        )
    }
}
