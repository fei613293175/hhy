package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class RewardLedgerResource(
    val id: String,
    val sourceType: String? = null,
    val amountCent: Long? = null,
    val status: String,
    val bizId: String? = null,
    val balanceAfterCent: Long? = null,
    val createdAt: String? = null,
)

@Serializable
data class RewardLedgerPage(
    val items: List<RewardLedgerResource> = emptyList(),
    val page: PageMetaResource? = null,
)

@Serializable
data class PageMetaResource(
    val page: Int = 1,
    val pageSize: Int = 20,
    val hasNext: Boolean = false,
    val nextCursor: String? = null,
)

@Serializable
data class R24PayoutAccountResource(
    val id: String,
    val userId: String,
    val accountName: String,
    val payoutAccountMasked: String,
    val status: String,
    val version: Long,
    val updatedAt: String? = null,
)

@Serializable
data class R24UpdatePayoutAccountRequest(
    val accountName: String,
    val alipayAccount: String,
    val smsCode: String,
    val expectedVersion: Long? = null,
)

@Serializable
data class R24WithdrawalQuoteRequest(
    val amountCent: Long,
    val payoutAccountId: String,
)

@Serializable
data class R24WithdrawalQuoteResource(
    val id: String,
    val status: String,
    val amountCent: Long,
    val feeCent: Long,
    val netAmountCent: Long,
    val expiresAt: String? = null,
    val version: Long,
)

@Serializable
data class R24CreateWithdrawalRequest(
    val amountCent: Long,
    val quoteId: String,
    val payoutAccountId: String,
)

@Serializable
data class R24WithdrawalResource(
    val id: String,
    val withdrawalNo: String,
    val userId: String? = null,
    val status: String,
    val amountCent: Long,
    val feeCent: Long,
    val netAmountCent: Long,
    val payoutAccountId: String? = null,
    val payoutAccountMasked: String? = null,
    val failureCode: String? = null,
    val createdAt: String? = null,
    val completedAt: String? = null,
    val updatedAt: String? = null,
    val version: Long,
)

@Serializable
data class R24WithdrawalPage(
    val items: List<R24WithdrawalResource> = emptyList(),
    val page: PageMetaResource? = null,
)

interface ContractR24Api {
    suspend fun rewardLedger(
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 20,
        status: String? = null,
        keyword: String? = null,
    ): R07CallResult<RewardLedgerPage>

    suspend fun payoutAccount(accessToken: String): R07CallResult<R24PayoutAccountResource>

    suspend fun updatePayoutAccount(
        accessToken: String,
        key: String,
        request: R24UpdatePayoutAccountRequest,
    ): R07CallResult<R24PayoutAccountResource>

    suspend fun quoteWithdrawal(
        accessToken: String,
        key: String,
        request: R24WithdrawalQuoteRequest,
    ): R07CallResult<R24WithdrawalQuoteResource>

    suspend fun createWithdrawal(
        accessToken: String,
        key: String,
        request: R24CreateWithdrawalRequest,
    ): R07CallResult<R24WithdrawalResource>

    suspend fun withdrawals(
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 20,
        status: String? = null,
    ): R07CallResult<R24WithdrawalPage>
}

class UrlConnectionContractR24Api internal constructor(
    baseUrl: String,
    private val connectionFactory: (URI) -> HttpURLConnection,
) : ContractR24Api {
    constructor(baseUrl: String) : this(
        baseUrl,
        { uri -> uri.toURL().openConnection() as HttpURLConnection },
    )

    private val root = validateR24Root(baseUrl)

    override suspend fun rewardLedger(
        accessToken: String,
        page: Int,
        pageSize: Int,
        status: String?,
        keyword: String?,
    ) = call(
        method = "GET",
        route = pageRoute("/api/v1/me/reward-ledger", page, pageSize, status, keyword),
        token = accessToken,
        serializer = RewardLedgerPage.serializer(),
    )

    override suspend fun payoutAccount(accessToken: String) = call(
        method = "GET",
        route = "/api/v1/me/payout-account",
        token = accessToken,
        serializer = R24PayoutAccountResource.serializer(),
    )

    override suspend fun updatePayoutAccount(
        accessToken: String,
        key: String,
        request: R24UpdatePayoutAccountRequest,
    ) = call(
        method = "PUT",
        route = "/api/v1/me/payout-account",
        token = accessToken,
        serializer = R24PayoutAccountResource.serializer(),
        key = requireR24Key(key),
        body = HhyNetworkJson.value.encodeToString(request),
    )

    override suspend fun quoteWithdrawal(
        accessToken: String,
        key: String,
        request: R24WithdrawalQuoteRequest,
    ) = call(
        method = "POST",
        route = "/api/v1/withdrawals/quote",
        token = accessToken,
        serializer = R24WithdrawalQuoteResource.serializer(),
        key = requireR24Key(key),
        body = HhyNetworkJson.value.encodeToString(request),
    )

    override suspend fun createWithdrawal(
        accessToken: String,
        key: String,
        request: R24CreateWithdrawalRequest,
    ) = call(
        method = "POST",
        route = "/api/v1/withdrawals",
        token = accessToken,
        serializer = R24WithdrawalResource.serializer(),
        key = requireR24Key(key),
        body = HhyNetworkJson.value.encodeToString(request),
    )

    override suspend fun withdrawals(
        accessToken: String,
        page: Int,
        pageSize: Int,
        status: String?,
    ) = call(
        method = "GET",
        route = pageRoute("/api/v1/me/withdrawals", page, pageSize, status, null),
        token = accessToken,
        serializer = R24WithdrawalPage.serializer(),
    )

    private suspend fun <T> call(
        method: String,
        route: String,
        token: String,
        serializer: KSerializer<T>,
        key: String? = null,
        body: String? = null,
    ): R07CallResult<T> = withContext(Dispatchers.IO) {
        require(token.isNotBlank())
        try {
            val connection = connectionFactory(URI.create(root + route))
            try {
                connection.requestMethod = method
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
                key?.let { connection.setRequestProperty("X-Idempotency-Key", it) }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                }
                val statusCode = connection.responseCode
                val responseBody = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (statusCode !in 200..299) return@withContext failure(connection, statusCode, responseBody)
                val envelope = HhyNetworkJson.value.decodeFromString(ApiEnvelope.serializer(serializer), responseBody)
                R07CallResult.Success(envelope.data, envelope.requestId, envelope.timestamp)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            R07CallResult.Failure(null)
        }
    }

    private fun failure(connection: HttpURLConnection, status: Int, body: String): R07CallResult.Failure {
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
}

internal fun pageRoute(
    path: String,
    page: Int,
    pageSize: Int,
    status: String?,
    keyword: String?,
): String {
    require(path == "/api/v1/me/reward-ledger" || path == "/api/v1/me/withdrawals")
    require(page >= 1)
    require(pageSize in 1..100)
    require(status == null || status.length <= 64)
    require(keyword == null || keyword.length <= 100)
    val parameters = listOfNotNull(
        "page" to page.toString(),
        "pageSize" to pageSize.toString(),
        status?.takeIf(String::isNotBlank)?.let { "status" to it },
        keyword?.takeIf(String::isNotBlank)?.let { "keyword" to it },
    )
    return "$path?" + parameters.joinToString("&") { (name, value) ->
        "$name=${URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")}"
    }
}

private fun validateR24Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.query == null && uri.fragment == null)
    return uri.toString().trimEnd('/')
}

private fun requireR24Key(value: String): String = value.also {
    require(it.length in 16..128 && Regex("^[A-Za-z0-9._:-]+$").matches(it))
}
