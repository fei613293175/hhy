package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString

/** Transport bound one-to-one to the frozen R05 identity operationIds. */
interface ContractIdentityApi {
    suspend fun overview(accessToken: String): IdentityOverviewCallResult

    suspend fun consent(accessToken: String): IdentityConsentCallResult

    suspend fun createSession(
        accessToken: String,
        idempotencyKey: String,
        request: IdentityCreateSessionRequest,
    ): IdentityCallResult

    suspend fun createLivenessToken(
        accessToken: String,
        sessionId: String,
        idempotencyKey: String,
        request: IdentityCreateLivenessTokenRequest,
    ): IdentityCallResult

    suspend fun session(accessToken: String, sessionId: String): IdentityCallResult

    suspend fun retry(
        accessToken: String,
        sessionId: String,
        idempotencyKey: String,
        request: IdentityRetrySessionRequest,
    ): IdentityCallResult
}

sealed interface IdentityCallResult {
    data class Success(val session: IdentitySessionResource) : IdentityCallResult
    data class Failure(
        val statusCode: Int?,
        val errorCode: String? = null,
        val retryAfterSeconds: Long? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
        val message: String? = null,
    ) : IdentityCallResult
}

sealed interface IdentityOverviewCallResult {
    data class Success(val overview: IdentityOverviewResource) : IdentityOverviewCallResult
    data class Failure(val failure: IdentityCallResult.Failure) : IdentityOverviewCallResult
}

sealed interface IdentityConsentCallResult {
    data class Success(val consent: IdentityConsentResource) : IdentityConsentCallResult
    data class Failure(
        val statusCode: Int?,
        val errorCode: String? = null,
        val retryAfterSeconds: Long? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : IdentityConsentCallResult
}

class UrlConnectionContractIdentityApi(baseUrl: String) : ContractIdentityApi {
    private val root = validateRoot(baseUrl)

    override suspend fun overview(accessToken: String): IdentityOverviewCallResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = URI.create(root + "/api/v1/identity/overview")
                    .toURL().openConnection() as HttpURLConnection
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
                    if (status !in 200..299) {
                        return@withContext IdentityOverviewCallResult.Failure(failure(connection, status, text))
                    }
                    val envelope = HhyNetworkJson.value
                        .decodeFromString<ApiEnvelope<IdentityOverviewResource>>(text)
                    IdentityOverviewCallResult.Success(envelope.data)
                } finally {
                    connection.disconnect()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                IdentityOverviewCallResult.Failure(IdentityCallResult.Failure(null))
            }
        }

    override suspend fun consent(accessToken: String): IdentityConsentCallResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = URI.create(root + "/api/v1/identity/consent")
                    .toURL().openConnection() as HttpURLConnection
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
                    if (status !in 200..299) {
                        val error = runCatching {
                            HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(text)
                        }.getOrNull()
                        return@withContext IdentityConsentCallResult.Failure(
                            statusCode = status,
                            errorCode = error?.error?.code,
                            retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull(),
                            fieldErrors = error?.error?.details.orEmpty()
                                .associate { it.field to it.message },
                        )
                    }
                    val envelope = HhyNetworkJson.value
                        .decodeFromString<ApiEnvelope<IdentityConsentResource>>(text)
                    IdentityConsentCallResult.Success(envelope.data)
                } finally {
                    connection.disconnect()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                IdentityConsentCallResult.Failure(null)
            }
        }

    override suspend fun createSession(
        accessToken: String,
        idempotencyKey: String,
        request: IdentityCreateSessionRequest,
    ) = call(
        method = "POST",
        path = "/api/v1/identity/sessions",
        accessToken = accessToken,
        idempotencyKey = idempotencyKey,
        body = request,
        bodySerializer = IdentityCreateSessionRequest.serializer(),
    )

    override suspend fun createLivenessToken(
        accessToken: String,
        sessionId: String,
        idempotencyKey: String,
        request: IdentityCreateLivenessTokenRequest,
    ) = call(
        method = "POST",
        path = "/api/v1/identity/sessions/${safeId(sessionId)}/liveness-token",
        accessToken = accessToken,
        idempotencyKey = idempotencyKey,
        body = request,
        bodySerializer = IdentityCreateLivenessTokenRequest.serializer(),
    )

    override suspend fun session(accessToken: String, sessionId: String) = call<Unit>(
        method = "GET",
        path = "/api/v1/identity/sessions/${safeId(sessionId)}",
        accessToken = accessToken,
    )

    override suspend fun retry(
        accessToken: String,
        sessionId: String,
        idempotencyKey: String,
        request: IdentityRetrySessionRequest,
    ) = call(
        method = "POST",
        path = "/api/v1/identity/sessions/${safeId(sessionId)}/retry",
        accessToken = accessToken,
        idempotencyKey = idempotencyKey,
        body = request,
        bodySerializer = IdentityRetrySessionRequest.serializer(),
    )

    private suspend fun <T> call(
        method: String,
        path: String,
        accessToken: String,
        idempotencyKey: String? = null,
        body: T? = null,
        bodySerializer: KSerializer<T>? = null,
    ): IdentityCallResult = withContext(Dispatchers.IO) {
        try {
            val connection = URI.create(root + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
                idempotencyKey?.let {
                    connection.setRequestProperty("X-Idempotency-Key", requireKey(it))
                }
                if (body != null && bodySerializer != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(HhyNetworkJson.value.encodeToString(bodySerializer, body))
                    }
                }
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) return@withContext failure(connection, status, text)
                val envelope = HhyNetworkJson.value.decodeFromString<ApiEnvelope<IdentitySessionResource>>(text)
                IdentityCallResult.Success(envelope.data)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            IdentityCallResult.Failure(null)
        }
    }

    private fun failure(
        connection: HttpURLConnection,
        status: Int,
        body: String,
    ): IdentityCallResult.Failure {
        val error = runCatching {
            HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(body)
        }.getOrNull()
        return IdentityCallResult.Failure(
            statusCode = status,
            errorCode = error?.error?.code,
            message = error?.error?.message,
            retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull(),
            fieldErrors = error?.error?.details.orEmpty().associate { it.field to it.message },
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 15_000
        private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

        fun validateRoot(value: String): String {
            val uri = URI.create(value.trim())
            require(
                uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null,
            )
            return value.trim().trimEnd('/')
        }

        fun safeId(value: String): String = value.also { require(SAFE_ID.matches(it)) }
        fun requireKey(value: String): String = value.also { require(it.length in 16..128) }
    }
}
