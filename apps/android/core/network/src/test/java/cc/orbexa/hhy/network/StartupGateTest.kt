package cc.orbexa.hhy.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupGateTest {
    private val request = StartupGateRequest(10201, "1.2.2", "official", "TEST")

    @Test
    fun maintenanceStopsBeforeVersionAndLatestRequests() = runBlocking {
        val api = FakeApi(status = platformStatus(maintenance = true))

        val result = StartupGate(api).evaluate(request)

        assertEquals(StartupGateState.Maintenance("计划维护"), result)
        assertEquals(0, api.versionCalls)
        assertEquals(0, api.latestCalls)
    }

    @Test
    fun forcedUpdateLoadsPublicDownloadContent() = runBlocking {
        val api = FakeApi(policy = versionPolicy(UpdateType.FORCED))

        val result = StartupGate(api).evaluate(request) as StartupGateState.UpdateRequired

        assertTrue(result.forced)
        assertEquals("app-latest", result.page?.code)
        assertEquals(1, api.latestCalls)
    }

    @Test
    fun optionalUpdateSurvivesLatestContentFailure() = runBlocking {
        val api = FakeApi(
            policy = versionPolicy(UpdateType.OPTIONAL),
            latestFailure = HhyApiException(503, "request_latest_failed"),
        )

        val result = StartupGate(api).evaluate(request) as StartupGateState.UpdateRequired

        assertFalse(result.forced)
        assertNull(result.page)
    }

    @Test
    fun forcedUpdateStillBlocksWhenLatestContentFails() = runBlocking {
        val api = FakeApi(
            policy = versionPolicy(UpdateType.FORCED),
            latestFailure = HhyApiException(503, "request_latest_failed"),
        )

        val result = StartupGate(api).evaluate(request) as StartupGateState.UpdateRequired

        assertTrue(result.forced)
        assertNull(result.page)
    }

    @Test
    fun currentVersionContinuesToApplication() = runBlocking {
        val api = FakeApi(policy = versionPolicy(UpdateType.NONE))

        val result = StartupGate(api).evaluate(request) as StartupGateState.Ready

        assertTrue(result.capabilities.registration)
        assertEquals(0, api.latestCalls)
    }

    @Test
    fun failedGatePreservesRequestIdForRecovery() = runBlocking {
        val api = FakeApi(statusFailure = HhyApiException(503, "request_status_failed"))

        val result = StartupGate(api).evaluate(request)

        assertEquals(StartupGateState.Unavailable("request_status_failed"), result)
    }

    @Test
    fun versionCheckFailurePreservesRequestIdForRecovery() = runBlocking {
        val api = FakeApi(versionFailure = HhyApiException(503, "request_version_failed"))

        val result = StartupGate(api).evaluate(request)

        assertEquals(StartupGateState.Unavailable("request_version_failed"), result)
    }

    private class FakeApi(
        private val status: PlatformStatus = platformStatus(),
        private val policy: VersionPolicy = versionPolicy(UpdateType.NONE),
        private val statusFailure: Exception? = null,
        private val versionFailure: Exception? = null,
        private val latestFailure: Exception? = null,
    ) : HhyPublicApi {
        var versionCalls = 0
        var latestCalls = 0

        override suspend fun platformStatus(): ApiEnvelope<PlatformStatus> {
            statusFailure?.let { throw it }
            return envelope(status)
        }

        override suspend fun versionCheck(request: VersionCheckRequest): ApiEnvelope<VersionPolicy> {
            versionCalls += 1
            versionFailure?.let { throw it }
            return envelope(policy)
        }

        override suspend fun latestApp(): ApiEnvelope<PublicPage> {
            latestCalls += 1
            latestFailure?.let { throw it }
            return envelope(
                PublicPage(
                    code = "app-latest",
                    title = "合伙云 Pro 1.2.3",
                    description = "安全更新",
                    download = DownloadInfo(
                        "ANDROID",
                        "1.2.3",
                        10202,
                        "https://downloads.example.test/hhy.apk",
                        "a".repeat(64),
                    ),
                    version = 10202,
                ),
            )
        }
    }

    private companion object {
        fun platformStatus(maintenance: Boolean = false) = PlatformStatus(
            maintenance = maintenance,
            maintenanceMessage = if (maintenance) "计划维护" else "",
            capabilities = PlatformCapabilities(true, true, false, false),
            serverTime = "2026-07-17T00:00:00Z",
        )

        fun versionPolicy(type: UpdateType) = VersionPolicy(
            platform = "ANDROID",
            latestVersionCode = 10202,
            latestVersionName = "1.2.3",
            updateType = type,
            downloadUrl = "https://downloads.example.test/hhy.apk",
            sha256 = "a".repeat(64),
            releaseNotes = "安全更新",
            minSupportedVersionCode = 10201,
            serverTime = "2026-07-17T00:00:00Z",
        )

        fun <T> envelope(data: T) = ApiEnvelope(
            success = true,
            requestId = "request_test",
            timestamp = "2026-07-17T00:00:00Z",
            data = data,
        )
    }
}
