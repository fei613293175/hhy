package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString

/** Read-only transport for the three frozen SCR-ME-001 aggregate resources. */
interface ContractR12MeApi {
    suspend fun user(accessToken: String): R07CallResult<UserSelfResource>
    suspend fun membership(accessToken: String): R07CallResult<MembershipResource>
    suspend fun rewardAccount(accessToken: String): R07CallResult<RewardAccountResource>
}

internal enum class R12MeResource(val route: String) {
    USER("/api/v1/me"),
    MEMBERSHIP("/api/v1/me/membership"),
    REWARD_ACCOUNT("/api/v1/me/reward-account"),
}

class UrlConnectionContractR12MeApi(baseUrl: String) : ContractR12MeApi {
    private val root = validateR12MeRoot(baseUrl)

    override suspend fun user(accessToken: String): R07CallResult<UserSelfResource> = get(
        route = R12MeResource.USER.route,
        accessToken = accessToken,
        serializer = UserSelfResource.serializer(),
    )

    override suspend fun membership(accessToken: String): R07CallResult<MembershipResource> = get(
        route = R12MeResource.MEMBERSHIP.route,
        accessToken = accessToken,
        serializer = MembershipResource.serializer(),
    )

    override suspend fun rewardAccount(accessToken: String): R07CallResult<RewardAccountResource> = get(
        route = R12MeResource.REWARD_ACCOUNT.route,
        accessToken = accessToken,
        serializer = RewardAccountResource.serializer(),
    )

    private suspend fun <T> get(
        route: String,
        accessToken: String,
        serializer: KSerializer<T>,
    ): R07CallResult<T> = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank())
        try {
            val connection = URI.create(root + route).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status in 200..299) {
                    val envelope = HhyNetworkJson.value.decodeFromString(
                        ApiEnvelope.serializer(serializer),
                        text,
                    )
                    R07CallResult.Success(envelope.data, envelope.requestId, envelope.timestamp)
                } else {
                    meFailure(connection, status, text)
                }
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            R07CallResult.Failure(statusCode = null)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 15_000
    }
}

private fun meFailure(
    connection: HttpURLConnection,
    status: Int,
    body: String,
): R07CallResult.Failure {
    val error = runCatching { HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(body) }.getOrNull()
    return R07CallResult.Failure(
        statusCode = status,
        errorCode = error?.error?.code,
        requestId = error?.requestId,
        retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull(),
        fieldErrors = error?.error?.fieldErrors().orEmpty(),
        retryable = error?.error?.retryable ?: (status >= 500),
    )
}

private fun validateR12MeRoot(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.query == null && uri.fragment == null)
    return uri.toString().trimEnd('/')
}
