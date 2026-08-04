package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class R18MembershipOrderRequest(val skuId: String, val paymentChannel: String)

@Serializable
data class R18MembershipUpgradeQuoteRequest(val targetSkuId: String)

@Serializable
data class R18MembershipUpgradeOrderRequest(val quoteId: String, val paymentChannel: String)

@Serializable
data class R18MembershipPage(val items: List<MembershipResource> = emptyList(), val page: R07PageMeta)

/** Transport for the five frozen client-side R18 membership operations. */
interface ContractR18MembershipApi {
    suspend fun skus(accessToken: String): R07CallResult<R18MembershipPage>
    suspend fun current(accessToken: String): R07CallResult<MembershipResource>
    suspend fun purchase(
        accessToken: String, request: R18MembershipOrderRequest, idempotencyKey: String,
    ): R07CallResult<CommandResultResource>
    suspend fun quote(
        accessToken: String, request: R18MembershipUpgradeQuoteRequest, idempotencyKey: String,
    ): R07CallResult<MembershipResource>
    suspend fun upgrade(
        accessToken: String, request: R18MembershipUpgradeOrderRequest, idempotencyKey: String,
    ): R07CallResult<CommandResultResource>
}

class UrlConnectionContractR18MembershipApi(baseUrl: String) : ContractR18MembershipApi {
    private val root = URI.create(baseUrl.trimEnd('/')).also {
        require(it.scheme == "https" && !it.host.isNullOrBlank() && !it.host.endsWith(".invalid"))
        require(it.userInfo == null && it.query == null && it.fragment == null)
    }.toString().trimEnd('/')

    override suspend fun skus(accessToken: String) = call(
        "GET", "/api/v1/membership/skus?page=1&pageSize=100&sort=priceCent%3Aasc",
        accessToken, R18MembershipPage.serializer(), null, null,
    )

    override suspend fun current(accessToken: String) = call(
        "GET", "/api/v1/me/membership", accessToken, MembershipResource.serializer(), null, null,
    )

    override suspend fun purchase(
        accessToken: String, request: R18MembershipOrderRequest, idempotencyKey: String,
    ) = call("POST", "/api/v1/membership/orders", accessToken,
        CommandResultResource.serializer(), idempotencyKey, HhyNetworkJson.value.encodeToString(request))

    override suspend fun quote(
        accessToken: String, request: R18MembershipUpgradeQuoteRequest, idempotencyKey: String,
    ) = call("POST", "/api/v1/membership/upgrade-quotes", accessToken,
        MembershipResource.serializer(), idempotencyKey, HhyNetworkJson.value.encodeToString(request))

    override suspend fun upgrade(
        accessToken: String, request: R18MembershipUpgradeOrderRequest, idempotencyKey: String,
    ) = call("POST", "/api/v1/membership/upgrade-orders", accessToken,
        CommandResultResource.serializer(), idempotencyKey, HhyNetworkJson.value.encodeToString(request))

    private suspend fun <T> call(
        method: String, route: String, accessToken: String, serializer: KSerializer<T>,
        idempotencyKey: String?, body: String?,
    ): R07CallResult<T> = withContext(Dispatchers.IO) {
        try {
            val connection = URI.create(root + route).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
                idempotencyKey?.let { connection.setRequestProperty("X-Idempotency-Key", it) }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                }
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) return@withContext failure(connection, status, text)
                val envelope = HhyNetworkJson.value.decodeFromString(ApiEnvelope.serializer(serializer), text)
                R07CallResult.Success(envelope.data, envelope.requestId, envelope.timestamp)
            } finally { connection.disconnect() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { R07CallResult.Failure(null) }
    }

    private fun failure(connection: HttpURLConnection, status: Int, body: String): R07CallResult.Failure {
        val error = runCatching { HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(body) }.getOrNull()
        return R07CallResult.Failure(
            statusCode = status, errorCode = error?.error?.code, requestId = error?.requestId,
            retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull(),
            fieldErrors = error?.error?.fieldErrors().orEmpty(),
            retryable = error?.error?.retryable ?: (status >= 500),
        )
    }
}
