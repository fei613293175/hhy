package cc.orbexa.hhy.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadUrlPolicyTest {
    @Test
    fun acceptsRegisteredHttpsDownloadHosts() {
        assertEquals(
            "https://download.orbexa.cc/apps/hhy.apk?signature=abc",
            DownloadUrlPolicy.trustedOrNull("https://download.orbexa.cc/apps/hhy.apk?signature=abc"),
        )
        assertEquals(
            "https://stg-download.orbexa.cc/releases/hhy.apk",
            DownloadUrlPolicy.trustedOrNull("https://stg-download.orbexa.cc/releases/hhy.apk"),
        )
    }

    @Test
    fun rejectsUntrustedOrUnsafeUrls() {
        listOf(
            "http://download.orbexa.cc/apps/hhy.apk",
            "https://download.orbexa.cc.attacker.example/apps/hhy.apk",
            "https://attacker.example/apps/hhy.apk",
            "https://user:password@download.orbexa.cc/apps/hhy.apk",
            "https://download.orbexa.cc:8443/apps/hhy.apk",
            "https://download.orbexa.cc/apps/hhy.apk#other",
            "not a url",
        ).forEach { value ->
            assertNull(value, DownloadUrlPolicy.trustedOrNull(value))
        }
    }
}
