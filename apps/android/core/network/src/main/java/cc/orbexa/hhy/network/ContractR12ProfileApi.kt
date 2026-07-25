package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class R12ProfilePatchRequest(
    val nickname: String? = null,
    val avatarMediaId: String? = null,
    val bio: String? = null,
    val expectedVersion: Long,
)

internal data class R12ProfilePatchCall(
    val route: String,
    val idempotencyKey: String,
    val request: R12ProfilePatchRequest,
    val body: String,
)

sealed interface R12ProfilePatchResult {
    data class User(val resource: UserSelfResource) : R12ProfilePatchResult
    data class Command(val command: CommandResultResource) : R12ProfilePatchResult
}

interface ContractR12ProfileApi {
    suspend fun profile(accessToken: String): R07CallResult<UserSelfResource>

    suspend fun patchProfile(
        accessToken: String,
        idempotencyKey: String,
        request: R12ProfilePatchRequest,
    ): R07CallResult<R12ProfilePatchResult>
}

class UrlConnectionContractR12ProfileApi(baseUrl: String) : ContractR12ProfileApi {
    private val root = validateR12ProfileRoot(baseUrl)

    override suspend fun profile(accessToken: String): R07CallResult<UserSelfResource> = call(
        method = "GET",
        route = "/api/v1/me",
        accessToken = accessToken,
        decode = { text ->
            val envelope = HhyNetworkJson.value.decodeFromString(
                ApiEnvelope.serializer(UserSelfResource.serializer()),
                text,
            )
            R07CallResult.Success(envelope.data, envelope.requestId)
        },
    )

    override suspend fun patchProfile(
        accessToken: String,
        idempotencyKey: String,
        request: R12ProfilePatchRequest,
    ): R07CallResult<R12ProfilePatchResult> {
        val call = buildR12ProfilePatchCall(idempotencyKey, request)
        return call(
            method = "PATCH",
            route = call.route,
            accessToken = accessToken,
            idempotencyKey = call.idempotencyKey,
            body = call.body,
            decode = { text ->
                val envelope = HhyNetworkJson.value.decodeFromString(
                    ApiEnvelope.serializer(JsonElement.serializer()),
                    text,
                )
                R07CallResult.Success(decodeR12ProfileData(envelope.data), envelope.requestId)
            },
        )
    }

    private suspend fun <T> call(
        method: String,
        route: String,
        accessToken: String,
        idempotencyKey: String? = null,
        body: String? = null,
        decode: (String) -> R07CallResult<T>,
    ): R07CallResult<T> = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank())
        try {
            val connection = URI.create(root + route).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
                idempotencyKey?.let { connection.setRequestProperty("X-Idempotency-Key", it) }
                body?.let {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(it) }
                }
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status in 200..299) decode(text) else profileFailure(connection, status, text)
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

internal fun buildR12ProfilePatchCall(
    idempotencyKey: String,
    request: R12ProfilePatchRequest,
): R12ProfilePatchCall {
    require(request.expectedVersion >= 0)
    require(request.nickname != null || request.avatarMediaId != null || request.bio != null)
    val normalized = request.copy(
        nickname = request.nickname?.let(::stripR12ProfileText),
        avatarMediaId = request.avatarMediaId?.let(::stripR12ProfileText),
        bio = request.bio?.let(::stripR12ProfileText),
    )
    normalized.nickname?.let {
        require(it.isNotEmpty() && r12ProfileCodePointCount(it) <= PROFILE_TEXT_LIMIT)
    }
    normalized.bio?.let { require(r12ProfileCodePointCount(it) <= PROFILE_TEXT_LIMIT) }
    normalized.avatarMediaId?.takeIf(String::isNotEmpty)?.let {
        require(it.length <= MEDIA_ID_LIMIT && it.toLongOrNull()?.let { value -> value > 0 } == true)
    }
    val key = idempotencyKey.also {
        require(it.length in IDEMPOTENCY_KEY_MIN..IDEMPOTENCY_KEY_MAX)
        require(Regex("^[A-Za-z0-9._:-]+$").matches(it))
    }
    return R12ProfilePatchCall(
        route = "/api/v1/me/profile",
        idempotencyKey = key,
        request = normalized,
        body = HhyNetworkJson.value.encodeToString(normalized),
    )
}

internal fun decodeR12ProfileData(data: JsonElement): R12ProfilePatchResult {
    runCatching {
        HhyNetworkJson.value.decodeFromJsonElement(UserSelfResource.serializer(), data)
    }.getOrNull()?.let { return R12ProfilePatchResult.User(it) }
    return R12ProfilePatchResult.Command(
        HhyNetworkJson.value.decodeFromJsonElement(CommandResultResource.serializer(), data),
    )
}

internal fun stripR12ProfileText(value: String): String = value.trim()

internal fun r12ProfileCodePointCount(value: String): Int =
    Character.codePointCount(value, 0, value.length)

private fun profileFailure(
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
        fieldErrors = error?.error?.details.orEmpty().associate { it.field to it.message },
        retryable = error?.error?.retryable ?: (status >= 500),
    )
}

private fun validateR12ProfileRoot(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.query == null && uri.fragment == null)
    return uri.toString().trimEnd('/')
}

private const val PROFILE_TEXT_LIMIT = 255
private const val MEDIA_ID_LIMIT = 64
private const val IDEMPOTENCY_KEY_MIN = 16
private const val IDEMPOTENCY_KEY_MAX = 128
