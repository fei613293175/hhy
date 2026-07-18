package cc.orbexa.hhy.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val requestId: String,
    val timestamp: String,
    val data: T,
)

@Serializable
data class PlatformCapabilities(
    val registration: Boolean,
    val publishing: Boolean,
    val redPacket: Boolean,
    val withdrawal: Boolean,
)

@Serializable
data class PlatformStatus(
    val maintenance: Boolean,
    val maintenanceMessage: String,
    val capabilities: PlatformCapabilities,
    val serverTime: String,
)

@Serializable
data class VersionCheckRequest(
    val platform: String,
    val versionCode: Long,
    val versionName: String? = null,
    val channel: String,
    val environment: String,
)

@Serializable
enum class UpdateType { NONE, OPTIONAL, FORCED }

@Serializable
data class VersionPolicy(
    val platform: String,
    val latestVersionCode: Long,
    val latestVersionName: String,
    val updateType: UpdateType,
    val downloadUrl: String,
    val sha256: String,
    val releaseNotes: String,
    val minSupportedVersionCode: Long,
    val serverTime: String,
)

@Serializable
data class DownloadInfo(
    val platform: String,
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val sha256: String,
)

@Serializable
data class PublicPage(
    val code: String,
    val title: String? = null,
    val description: String? = null,
    val download: DownloadInfo? = null,
    val version: Long,
)

object HhyNetworkJson {
    val value: Json = Json {
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
    }
}

interface HhyPublicApi {
    suspend fun platformStatus(): ApiEnvelope<PlatformStatus>
    suspend fun versionCheck(request: VersionCheckRequest): ApiEnvelope<VersionPolicy>
    suspend fun latestApp(): ApiEnvelope<PublicPage>
}
