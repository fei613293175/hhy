package cc.orbexa.hhy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionMetadataTest {
    @Test
    fun releaseAndContractVersionsRemainAligned() {
        assertEquals("R05 test APK versionCode must remain monotonic", 10213, ReleasePolicy.VERSION_CODE)
        assertEquals(BuildConfig.VERSION_CODE, ReleasePolicy.VERSION_CODE)
        assertTrue(BuildConfig.VERSION_NAME.startsWith(ReleasePolicy.VERSION_NAME))
        assertEquals(BuildConfig.CONTRACT_VERSION, ReleasePolicy.CONTRACT_VERSION)
        assertEquals("R05 owner test APK must use the published channel", "official", BuildConfig.APP_CHANNEL)
        assertEquals("R05 owner test APK must use Staging policy", "STAGING", BuildConfig.APP_ENVIRONMENT)
    }

    @Test
    fun publishPolicyRejectsPlaceholdersAndUnsafeUrls() {
        assertFalse(ReleasePolicy.isPublishableApiBaseUrl("https://api.example.invalid"))
        assertFalse(ReleasePolicy.isPublishableApiBaseUrl("http://api.orbexa.cc"))
        assertFalse(ReleasePolicy.isPublishableApiBaseUrl("https://user:pass@api.orbexa.cc"))
        assertTrue(ReleasePolicy.isPublishableApiBaseUrl("https://api.orbexa.cc"))
    }
}
