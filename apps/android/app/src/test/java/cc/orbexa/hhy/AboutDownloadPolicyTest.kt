package cc.orbexa.hhy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutDownloadPolicyTest {
    @Test fun acceptsAbsoluteHttpsDownload() = assertTrue(isSafeDownloadUrl("https://download.orbexa.cc/app.apk"))
    @Test fun rejectsUnsafeDownloadTargets() {
        assertFalse(isSafeDownloadUrl("http://download.orbexa.cc/app.apk"))
        assertFalse(isSafeDownloadUrl("https://user:pass@download.orbexa.cc/app.apk"))
        assertFalse(isSafeDownloadUrl("https://download.orbexa.cc/app.apk#fragment"))
        assertFalse(isSafeDownloadUrl(null))
    }
}
