package cc.orbexa.hhy.network

import java.time.Instant

data class ApiEnvelope<T>(
    val success: Boolean,
    val requestId: String,
    val timestamp: Instant,
    val data: T,
)

data class PlatformStatus(
    val maintenance: Boolean,
    val maintenanceMessage: String,
    val capabilities: Map<String, Boolean>,
    val serverTime: Instant,
)

data class VersionCheckRequest(
    val platform: String = "ANDROID",
    val versionCode: Int,
    val versionName: String,
    val channel: String,
    val environment: String,
)

enum class UpdateType { NONE, OPTIONAL, FORCED }

data class VersionPolicy(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val updateType: UpdateType,
    val downloadUrl: String,
    val apkSha256: String,
    val releaseNotes: String,
    val minimumSupportedVersionCode: Int,
)

interface HhyPublicApi {
    suspend fun platformStatus(): ApiEnvelope<PlatformStatus>
    suspend fun versionCheck(request: VersionCheckRequest): ApiEnvelope<VersionPolicy>
}
