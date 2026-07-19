package cc.orbexa.hhy.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val requestId: String,
    val timestamp: String,
    val data: T,
)

@Serializable
data class ApiErrorEnvelope(
    val success: Boolean,
    val requestId: String,
    val timestamp: String,
    val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: List<ApiErrorDetail>,
    val retryable: Boolean,
    val traceId: String,
)

@Serializable
data class ApiErrorDetail(
    val field: String,
    val code: String,
    val message: String,
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

// Frozen R02 operation types. They live in core/network so Android features never own
// duplicate request DTOs or manually reconstruct contract field names.
@Serializable
data class AuthSecurityChallengeRequest(
    val scene: String,
    val clientNonce: String,
    val deviceFingerprint: String? = null,
)

@Serializable
data class AuthDevicePayload(
    val deviceFingerprint: String,
    val model: String,
    val platform: String,
    val osVersion: String,
    val appVersion: String,
)

@Serializable
data class AuthPasswordLoginRequest(
    val phone: String,
    val password: String,
    val challengeId: String,
    val challengeProof: String,
    val device: AuthDevicePayload,
)

@Serializable
data class AuthSmsSendRequest(
    val phone: String,
    val scene: String,
    val challengeId: String,
    val challengeProof: String,
)

@Serializable
data class AuthSmsLoginRequest(
    val phone: String,
    val smsCode: String,
    val device: AuthDevicePayload,
)

@Serializable
data class AuthInviteCodeValidateRequest(val inviteCode: String)

@Serializable
data class AuthRegistrationAgreementVersionResource(
    val versionId: String,
    val code: String,
    val effectiveAt: String,
)

@Serializable
data class AuthRegistrationConfigResource(
    val agreementVersions: List<AuthRegistrationAgreementVersionResource>,
)

@Serializable
data class AuthRegisterRequest(
    val phone: String,
    val password: String,
    val inviteCode: String,
    val challengeId: String,
    val challengeProof: String,
    val device: AuthDevicePayload,
)

@Serializable
data class AuthPasswordResetRequest(
    val phone: String,
    val smsCode: String,
    val newPassword: String,
)

@Serializable
data class AuthRefreshRequest(
    val refreshToken: String,
    val deviceId: String,
)

@Serializable
data class AuthSessionResource(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val userId: String,
    val sessionId: String,
    val device: kotlinx.serialization.json.JsonObject? = null,
    val capabilities: List<String> = emptyList(),
)

/** Credential-free response for the R02 device-management screen. */
@Serializable
data class UserSecuritySessionResource(
    val sessionId: String,
    val device: kotlinx.serialization.json.JsonObject? = null,
    val createdAt: String,
    val expiresAt: String,
    val status: String,
    val current: Boolean,
)

@Serializable
data class UserSecuritySessionPageMeta(
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val hasMore: Boolean,
)

@Serializable
data class UserSecuritySessionPageResource(
    val items: List<UserSecuritySessionResource>,
    val page: UserSecuritySessionPageMeta,
)

@Serializable
data class AuthPasswordChangeRequest(
    val currentPassword: String,
    val newPassword: String,
    val smsCode: String? = null,
)

@Serializable
data class UserSelfResource(
    val id: String,
    val phoneMasked: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val status: String,
    val identityStatus: String? = null,
    val membershipStatus: String? = null,
    val createdAt: String? = null,
    val version: Long,
)

// Frozen R05 identity operation types. UI modules consume these contract types and
// must not recreate page-local request or response DTOs.
@Serializable
data class IdentitySessionResource(
    val id: String,
    val userId: String? = null,
    val status: String,
    val provider: String? = null,
    val livenessUrl: String? = null,
    val failureCode: String? = null,
    val expiresAt: String? = null,
    val version: Long,
)

@Serializable
data class IdentityCreateSessionRequest(
    val realName: String,
    val idNumber: String,
    val consentVersion: String,
)

@Serializable
data class IdentityCreateLivenessTokenRequest(val returnUrl: String)

@Serializable
data class IdentityRetrySessionRequest(
    val reason: String? = null,
    val expectedVersion: Long? = null,
    val payload: JsonObject? = null,
)

@Serializable
data class SupportTicketCreateRequest(
    val category: String,
    val subject: String,
    val content: String,
    val attachments: List<String> = emptyList(),
)

@Serializable
data class SupportTicketResource(
    val id: String,
    val ticketNo: String,
    val category: String? = null,
    val subject: String? = null,
    val status: String,
    val assignee: String? = null,
    val lastMessageAt: String? = null,
    val createdAt: String? = null,
    val version: Long,
)

@Serializable
data class AccountCancellationRequest(
    val reason: String,
    val smsCode: String,
    val expectedVersion: Long,
)

// Frozen R04 media operation types. Feature modules consume these canonical
// contract models and must not recreate request/response DTOs at page level.
@Serializable
data class MediaCreateUploadSessionRequest(
    val purpose: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class MediaCompletedPart(
    val number: Int,
    val etag: String,
)

@Serializable
data class MediaCompleteUploadSessionRequest(
    val etag: String,
    val parts: List<MediaCompletedPart> = emptyList(),
)

@Serializable
data class MediaResource(
    val id: String,
    val purpose: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val uploadUrl: String? = null,
    val readUrl: String? = null,
    val status: String,
    val expiresAt: String? = null,
)

@Serializable
data class CommandResultResource(
    val resourceId: String? = null,
    val businessNo: String? = null,
    val status: String,
    val version: Long? = null,
    val acceptedAt: String,
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
