package cc.orbexa.hhy.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean,
    val requestId: String,
    val timestamp: String? = null,
    val data: T,
)

@Serializable
data class ApiErrorEnvelope(
    val success: Boolean,
    val requestId: String,
    val timestamp: String? = null,
    val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: List<ApiErrorDetail> = emptyList(),
    val retryable: Boolean? = null,
    val traceId: String? = null,
)

@Serializable
data class ApiErrorDetail(
    val field: String? = null,
    val code: String,
    val message: String,
)

internal fun ApiErrorBody.fieldErrors(): Map<String, String> = details.mapNotNull { detail ->
    detail.field?.takeIf(String::isNotBlank)?.let { field -> field to detail.message }
}.toMap()

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

@Serializable
data class MembershipBenefitResource(
    val benefitCode: String,
    val name: String,
    val value: JsonElement,
    val unit: String? = null,
)

@Serializable
data class MembershipResource(
    val id: String? = null,
    val skuId: String? = null,
    val name: String? = null,
    val status: String,
    val startsAt: String? = null,
    val expiresAt: String? = null,
    val benefits: List<MembershipBenefitResource> = emptyList(),
    val paidValueCent: Long? = null,
    val remainingValueCent: Long? = null,
    val version: Long,
)

@Serializable
data class RewardAccountResource(
    val userId: String,
    val pendingCent: Long,
    val availableCent: Long,
    val frozenCent: Long,
    val withdrawnCent: Long? = null,
    val version: Long,
    val updatedAt: String? = null,
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
data class IdentityOverviewResource(
    val status: String,
    val activeSession: IdentitySessionResource? = null,
)

@Serializable
data class IdentityConsentResource(
    val consentVersion: String,
    val title: String,
    val content: String,
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

// Frozen R07 search, publisher and contact-access types. The contact value is
// deliberately modelled only in the transport layer and must never be persisted.
@Serializable
data class R07PageMeta(
    val page: Long? = null,
    val pageSize: Long,
    val total: String? = null,
    val nextCursor: String? = null,
    val hasMore: String,
) {
    fun canLoadMore(): Boolean = hasMore.equals("true", ignoreCase = true)
}

@Serializable
data class PublisherSummaryResource(
    val userId: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val verified: Boolean,
    val memberBadge: String? = null,
    val followed: Boolean? = null,
)

@Serializable
data class SearchResultResource(
    val id: String,
    val contentType: String,
    val title: String,
    val summary: String? = null,
    val coverUrl: String? = null,
    val publisher: PublisherSummaryResource? = null,
    val score: Double? = null,
    val badges: List<String> = emptyList(),
)

@Serializable
data class SearchTermResource(
    val id: String,
    val keyword: String,
    val createdAt: String? = null,
)

@Serializable
data class SearchResultPageResource(
    val items: List<SearchResultResource>,
    val page: R07PageMeta,
)

@Serializable
data class SearchTermPageResource(
    val items: List<SearchTermResource>,
    val page: R07PageMeta,
)

@Serializable
data class MediaItemResource(
    val id: String,
    val mediaType: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Long? = null,
    val height: Long? = null,
    val durationMs: Long? = null,
    val altText: String? = null,
    val sortOrder: Long,
    val accessMode: String? = null,
)

@Serializable
data class ContactChannelSummaryResource(
    val channel: String,
    val maskedValue: String? = null,
    val available: Boolean,
    val accessPolicy: String,
    val accessed: Boolean,
)

@Serializable
data class ContentStatisticsResource(
    val viewCount: Long,
    val favoriteCount: Long,
    val shareCount: Long,
    val contactAccessCount: Long,
    val conversationCount: Long? = null,
)

@Serializable
data class ContentResource(
    val id: String,
    val contentType: String,
    val title: String,
    val summary: String? = null,
    val description: String? = null,
    val categoryCode: String? = null,
    val regionCode: String? = null,
    val media: List<MediaItemResource> = emptyList(),
    val publisher: PublisherSummaryResource? = null,
    val contactsMasked: List<ContactChannelSummaryResource> = emptyList(),
    val status: String,
    val reviewStatus: String? = null,
    val statistics: ContentStatisticsResource? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val version: Long,
    val attributes: JsonObject? = null,
)

@Serializable
data class ContentPageResource(
    val items: List<ContentResource>,
    val page: R07PageMeta,
)

@Serializable
data class ContactAccessRequest(val clientContext: JsonObject? = null)

@Serializable
data class ContactAccessResource(
    val channel: String,
    val value: String,
    val accessedAt: String,
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
