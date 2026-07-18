package cc.orbexa.hhy.network

import android.content.Context
import android.os.Build
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.KSerializer

/** Thin transport for frozen authentication operations and their core-network contract types. */
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
    suspend fun registrationConfig(): AuthCallResult
    suspend fun register(
        phone: String,
        smsCode: String,
        password: String,
        inviteCode: String,
        agreementVersions: List<String>,
    ): AuthCallResult
    suspend fun resetPassword(phone: String, smsCode: String, newPassword: String): AuthCallResult
    suspend fun refresh(refreshToken: String, deviceId: String): AuthCallResult
    suspend fun sessions(accessToken: String, page: Int = 1, pageSize: Int = 20): AuthCallResult
    suspend fun revokeSession(accessToken: String, sessionId: String): AuthCallResult
    suspend fun changePassword(accessToken: String, currentPassword: String, newPassword: String, smsCode: String? = null): AuthCallResult
    suspend fun self(accessToken: String): AuthCallResult
    suspend fun createSupportTicket(accessToken: String, category: String, subject: String, content: String): AuthCallResult
    suspend fun requestCancellation(accessToken: String, reason: String, smsCode: String, expectedVersion: Long): AuthCallResult
}

sealed interface AuthCallResult {
    data class Success(val data: JsonObject, val requestId: String) : AuthCallResult
    data class Failure(
        val statusCode: Int?,
        val requestId: String?,
        val errorCode: String? = null,
        val retryAfterSeconds: Long? = null,
    ) : AuthCallResult
}

fun AuthCallResult.Success.sessionOrNull(): AuthSessionResource? = runCatching {
    HhyNetworkJson.value.decodeFromJsonElement(AuthSessionResource.serializer(), data)
}.getOrNull()

fun AuthCallResult.Success.registrationConfigOrNull(): AuthRegistrationConfigResource? = runCatching {
    HhyNetworkJson.value.decodeFromJsonElement(AuthRegistrationConfigResource.serializer(), data)
}.getOrNull()

fun AuthCallResult.Success.securitySessionsOrNull(): UserSecuritySessionPageResource? = runCatching {
    HhyNetworkJson.value.decodeFromJsonElement(UserSecuritySessionPageResource.serializer(), data)
}.getOrNull()

fun AuthCallResult.Success.userSelfOrNull(): UserSelfResource? = runCatching {
    HhyNetworkJson.value.decodeFromJsonElement(UserSelfResource.serializer(), data)
}.getOrNull()

fun AuthCallResult.Success.supportTicketOrNull(): SupportTicketResource? = runCatching {
    HhyNetworkJson.value.decodeFromJsonElement(SupportTicketResource.serializer(), data)
}.getOrNull()

class UrlConnectionContractAuthApi(
    baseUrl: String,
    context: Context,
) : ContractAuthApi {
    private val root = validateRoot(baseUrl)
    private val device = AndroidAuthDevicePayload.create(context.applicationContext)

    override suspend fun securityChallenge(scene: String): AuthCallResult = post(
        "/api/v1/auth/security-challenges",
        AuthSecurityChallengeRequest(scene, UUID.randomUUID().toString(), device.deviceFingerprint),
        AuthSecurityChallengeRequest.serializer(),
    )

    override suspend fun passwordLogin(phone: String, password: String, challengeId: String, challengeProof: String) = post(
        "/api/v1/auth/password/login",
        AuthPasswordLoginRequest(phone, password, challengeId, challengeProof, device),
        AuthPasswordLoginRequest.serializer(),
    )

    override suspend fun sendSms(phone: String, scene: String, challengeId: String, challengeProof: String) = post(
        "/api/v1/auth/sms/send",
        AuthSmsSendRequest(phone, scene, challengeId, challengeProof),
        AuthSmsSendRequest.serializer(),
    )

    override suspend fun smsLogin(phone: String, smsCode: String) = post(
        "/api/v1/auth/sms/login", AuthSmsLoginRequest(phone, smsCode, device), AuthSmsLoginRequest.serializer(),
    )

    override suspend fun validateInviteCode(inviteCode: String) = post(
        "/api/v1/auth/invite-codes/validate", AuthInviteCodeValidateRequest(inviteCode), AuthInviteCodeValidateRequest.serializer(),
    )

    override suspend fun registrationConfig() = get("/api/v1/auth/registration-config")

    override suspend fun register(phone: String, smsCode: String, password: String, inviteCode: String, agreementVersions: List<String>) = post(
        "/api/v1/auth/register",
        AuthRegisterRequest(phone, smsCode, password, inviteCode, agreementVersions, device),
        AuthRegisterRequest.serializer(),
    )

    override suspend fun resetPassword(phone: String, smsCode: String, newPassword: String) = post(
        "/api/v1/auth/password/reset", AuthPasswordResetRequest(phone, smsCode, newPassword), AuthPasswordResetRequest.serializer(),
    )

    override suspend fun refresh(refreshToken: String, deviceId: String) = post(
        "/api/v1/auth/refresh",
        AuthRefreshRequest(refreshToken, deviceId),
        AuthRefreshRequest.serializer(),
        headers = mapOf("X-Refresh-Token" to refreshToken),
    )

    override suspend fun sessions(accessToken: String, page: Int, pageSize: Int) = get(
        "/api/v1/auth/sessions?page=$page&pageSize=$pageSize",
        headers = bearer(accessToken),
    )

    override suspend fun revokeSession(accessToken: String, sessionId: String) = delete(
        "/api/v1/auth/sessions/$sessionId", headers = bearer(accessToken),
    )

    override suspend fun changePassword(accessToken: String, currentPassword: String, newPassword: String, smsCode: String?) = post(
        "/api/v1/me/security/password/change", AuthPasswordChangeRequest(currentPassword, newPassword, smsCode),
        AuthPasswordChangeRequest.serializer(), headers = bearer(accessToken),
    )

    override suspend fun self(accessToken: String) = get("/api/v1/me", headers = bearer(accessToken))

    override suspend fun createSupportTicket(
        accessToken: String,
        category: String,
        subject: String,
        content: String,
    ) = post(
        "/api/v1/support/tickets",
        SupportTicketCreateRequest(category, subject, content),
        SupportTicketCreateRequest.serializer(),
        headers = bearer(accessToken),
    )

    override suspend fun requestCancellation(
        accessToken: String,
        reason: String,
        smsCode: String,
        expectedVersion: Long,
    ) = post(
        "/api/v1/me/cancellation",
        AccountCancellationRequest(reason, smsCode, expectedVersion),
        AccountCancellationRequest.serializer(),
        headers = bearer(accessToken),
    )

    private suspend fun <T> post(
        path: String,
        body: T,
        serializer: KSerializer<T>,
        headers: Map<String, String> = emptyMap(),
    ): AuthCallResult = withContext(Dispatchers.IO) {
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
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(HhyNetworkJson.value.encodeToString(serializer, body))
                }
                val status = connection.responseCode
                val requestId = connection.getHeaderField("X-Request-Id") ?: localRequestId
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val error = runCatching { HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(response) }.getOrNull()
                    return@withContext AuthCallResult.Failure(
                        statusCode = status,
                        requestId = error?.requestId ?: requestId,
                        errorCode = error?.error?.code,
                        retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull(),
                    )
                }
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

    private suspend fun get(path: String, headers: Map<String, String> = emptyMap()): AuthCallResult = withContext(Dispatchers.IO) {
        val localRequestId = UUID.randomUUID().toString()
        try {
            val connection = URI.create(root + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("X-Request-Id", localRequestId)
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                val status = connection.responseCode
                val requestId = connection.getHeaderField("X-Request-Id") ?: localRequestId
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val error = runCatching { HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(response) }.getOrNull()
                    return@withContext AuthCallResult.Failure(
                        statusCode = status,
                        requestId = error?.requestId ?: requestId,
                        errorCode = error?.error?.code,
                        retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull(),
                    )
                }
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

    private suspend fun delete(path: String, headers: Map<String, String>): AuthCallResult = withContext(Dispatchers.IO) {
        val localRequestId = UUID.randomUUID().toString()
        try {
            val connection = URI.create(root + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "DELETE"
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("X-Request-Id", localRequestId)
                connection.setRequestProperty("X-Idempotency-Key", localRequestId)
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                val status = connection.responseCode
                val requestId = connection.getHeaderField("X-Request-Id") ?: localRequestId
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val error = runCatching { HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(response) }.getOrNull()
                    return@withContext AuthCallResult.Failure(status, error?.requestId ?: requestId,
                        error?.error?.code, connection.getHeaderField("Retry-After")?.toLongOrNull())
                }
                val envelope = HhyNetworkJson.value.parseToJsonElement(response).jsonObject
                val data = envelope["data"]?.jsonObject
                    ?: return@withContext AuthCallResult.Failure(status, requestId)
                AuthCallResult.Success(data, envelope["requestId"]?.let { (it as? JsonPrimitive)?.content } ?: requestId)
            } finally { connection.disconnect() }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            AuthCallResult.Failure(null, null)
        }
    }

    private fun bearer(accessToken: String): Map<String, String> = mapOf("Authorization" to "Bearer $accessToken")

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

private object AndroidAuthDevicePayload {
    fun create(context: Context): AuthDevicePayload {
        val packageName = context.packageName
        val installationFingerprint = context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(INSTALLATION_FINGERPRINT_KEY, null)
            ?: sha256(UUID.randomUUID().toString()).also { fingerprint ->
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(INSTALLATION_FINGERPRINT_KEY, fingerprint)
                    .apply()
            }
        return AuthDevicePayload(
            // A per-installation random identifier avoids collecting hardware IDs. It is
            // hashed again before leaving the device and the server applies its own HMAC.
            deviceFingerprint = sha256("$packageName:$installationFingerprint"),
            model = Build.MODEL.take(64),
            platform = "ANDROID",
            osVersion = Build.VERSION.RELEASE.take(64),
            appVersion = context.packageManager.getPackageInfo(packageName, 0).versionName.orEmpty().take(32),
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private const val PREFERENCES_NAME = "hhy_auth_device"
    private const val INSTALLATION_FINGERPRINT_KEY = "installation_fingerprint"
}
