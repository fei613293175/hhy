package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable data class RewardLedgerResource(val id: String, val sourceType: String? = null, val amountCent: Long? = null, val status: String, val bizId: String? = null, val balanceAfterCent: Long? = null, val createdAt: String? = null)
@Serializable data class RewardLedgerPage(val items: List<RewardLedgerResource> = emptyList(), val page: PageMetaResource? = null)
@Serializable data class PageMetaResource(val page: Int = 1, val pageSize: Int = 20, val hasNext: Boolean = false, val nextCursor: String? = null)

interface ContractR24Api { suspend fun rewardLedger(accessToken: String, cursor: String? = null, pageSize: Int = 20): R07CallResult<RewardLedgerPage> }

class UrlConnectionContractR24Api(baseUrl: String) : ContractR24Api {
    private val root = baseUrl.trimEnd('/')
    override suspend fun rewardLedger(accessToken: String, cursor: String?, pageSize: Int): R07CallResult<RewardLedgerPage> = get('/api/v1/me/reward-ledger', accessToken, cursor, pageSize, RewardLedgerPage.serializer())
    private suspend fun <T> get(route: String, accessToken: String, cursor: String?, pageSize: Int, serializer: KSerializer<T>): R07CallResult<T> = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()); require(pageSize in 1..100)
        try {
            val query = buildString { append("?pageSize=").append(pageSize); cursor?.let { append("&cursor=").append(URLEncoder.encode(it, StandardCharsets.UTF_8)) } }
            val connection = URI.create(root + route + query).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"; connection.connectTimeout = 8000; connection.readTimeout = 15000; connection.setRequestProperty("Accept", "application/json"); connection.setRequestProperty("Authorization", "Bearer $accessToken")
                val status = connection.responseCode; val text = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status in 200..299) { val envelope = HhyNetworkJson.value.decodeFromString(ApiEnvelope.serializer(serializer), text); R07CallResult.Success(envelope.data, envelope.requestId, envelope.timestamp) } else R07CallResult.Failure(status)
            } finally { connection.disconnect() }
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { R07CallResult.Failure(null) }
    }
}
