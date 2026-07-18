package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Thin transport for frozen authentication operations. Request bodies deliberately remain
 * contract JSON instead of duplicating OpenAPI DTOs in an Android feature module.
 */
interface ContractAuthApi {
    suspend fun securityChallenge(scene: String): AuthCallResult
    suspend fun passwordLogin(
        phone: String,
        password: String,
        challengeId: String,
        challengeProof: String,
    ): AuthCallResult
    suspend fun sendSms(
        phone: String,
        scene: String,
        challengeId: String,
        challengeProof: String,
    ): AuthCallResult
    suspend fun smsLogin(phone: String, smsCode: String): AuthCallResult
    suspend fun validateInviteCode(inviteCode: String): AuthCallResult
    suspend fun register(
        phone: String,
        smsCode: String,
        password: String,
        inviteCode: String,
        agreementVersions: List<String>,
    ): AuthCallResult
    suspend fun resetPassword(phone: String, smsCode: String, newPassword: String): AuthCallResult
}

sealed interface AuthCallResult {
    data class Success(val data: JsonObject, val requestId: String) : AuthCallResult
    data class Failure(val statusCode: Int?, val requestId: String?) : AuthCallResult
}

class UrlConnectionContractAuthApi(baseUrl: String) : ContractAuthApi {
    private val root = validateRoot(baseUrl)

    override suspend fun securityChallenge(scene: String): AuthCallResult = post(
        "/api/v1/auth/security-challenges",
        buildJsonObject {
            put("scene", scene)
            put("clientNonce", UUID.randomUUID().toString())
        },
    )

    override suspend fun passwordLogin(phone: String, password: String, challengeId: String, challengeProof: String) = post(
        "/api/v1/auth/password/login",
        buildJsonObject {
            put("phone", phone); put("password", password)
            put("challengeId", challengeId); put("challengeProof", challengeProof)
        },
    )

    override suspend fun sendSms(phone: String, scene: String, challengeId: String, challengeProof: String) = post(
        "/api/v1/auth/sms/send",
        buildJsonObject {
            put("phone", phone); put("scene", scene)
            put("challengeId", challengeId); put("challengeProof", challengeProof)
        },
    )

    override suspend fun smsLogin(phone: String, smsCode: String) = post(
        "/api/v1/auth/sms/login", buildJsonObject { put("phone", phone); put("smsCode", smsCode) },
    )

    override suspend fun validateInviteCode(inviteCode: String) = post(
        "/api/v1/auth/invite-codes/validate", buildJsonObject { put("inviteCode", inviteCode) },
    )

    override suspend fun register(phone: String, smsCode: String, password: String, inviteCode: String, agreementVersions: List<String>) = post(
        "/api/v1/auth/register",
        buildJsonObject {
            put("phone", phone); put("smsCode", smsCode); put("password", password); put("inviteCode", inviteCode)
            put("agreementVersions", kotlinx.serialization.json.JsonArray(agreementVersions.map(::JsonPrimitive)))
        },
    )

    override suspend fun resetPassword(phone: String, smsCode: String, newPassword: String) = post(
        "/api/v1/auth/password/reset", buildJsonObject {
            put("phone", phone); put("smsCode", smsCode); put("newPassword", newPassword)
        },
    )

    private suspend fun post(path: String, body: JsonObject): AuthCallResult = withContext(Dispatchers.IO) {
        val localRequestId = UUID.randomUUID().toString()
        try {
            val connection = URI.create(root + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.doOutput = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-Request-Id", localRequestId)
                connection.setRequestProperty("X-Idempotency-Key", localRequestId)
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(HhyNetworkJson.value.encodeToString(JsonObject.serializer(), body))
                }
                val status = connection.responseCode
                val requestId = connection.getHeaderField("X-Request-Id") ?: localRequestId
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) return@withContext AuthCallResult.Failure(status, requestId)
                val envelope = HhyNetworkJson.value.parseToJsonElement(response).jsonObject
                val data = envelope["data"]?.jsonObject
                    ?: return@withContext AuthCallResult.Failure(status, requestId)
                AuthCallResult.Success(data, envelope["requestId"]?.let { (it as? JsonPrimitive)?.content } ?: requestId)
            } finally {
                connection.disconnect()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            AuthCallResult.Failure(null, null)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 12_000

        fun validateRoot(value: String): String {
            val uri = URI.create(value.trim())
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) {
                "API base URL must be an absolute HTTPS URL without user information"
            }
            return value.trim().trimEnd('/')
        }
    }
}
