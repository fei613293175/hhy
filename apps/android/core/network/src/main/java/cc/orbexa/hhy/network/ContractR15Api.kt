package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject

@Serializable
data class R15NotificationResource(
    val id: String,
    val type: String,
    val title: String,
    val body: String? = null,
    val target: JsonObject? = null,
    val readAt: String? = null,
    val createdAt: String,
)

@Serializable
data class R15NotificationPage(val items: List<R15NotificationResource>, val page: R07PageMeta)

@Serializable
data class R15SupportTicketResource(
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
data class R15SupportTicketPage(val items: List<R15SupportTicketResource>, val page: R07PageMeta)

@Serializable
data class R15NotificationReadRequest(val lastReadMessageId: String)

@Serializable
data class R15CreateTicketRequest(
    val category: String,
    val subject: String,
    val content: String,
    val attachments: List<String> = emptyList(),
)

@Serializable
data class R15SupportMessageRequest(val content: String, val attachments: List<String> = emptyList())

@Serializable
data class R15ContentReportRequest(
    val reasonCode: String,
    val description: String,
    val evidenceMediaIds: List<String> = emptyList(),
    val messageIds: List<String> = emptyList(),
    val expectedVersion: Long? = null,
)

interface ContractR15Api {
    suspend fun notifications(accessToken: String, keyword: String? = null): R07CallResult<R15NotificationPage>

    suspend fun notifications(
        accessToken: String,
        keyword: String? = null,
        status: String? = null,
    ): R07CallResult<R15NotificationPage> = notifications(accessToken, keyword)
    suspend fun readNotification(accessToken: String, id: String, key: String): R07CallResult<R15NotificationResource>
    suspend fun readAllNotifications(accessToken: String, key: String): R07CallResult<CommandResultResource>
    suspend fun announcements(accessToken: String, keyword: String? = null): R07CallResult<R15NotificationPage>
    suspend fun announcement(accessToken: String, id: String): R07CallResult<R15NotificationResource>
    suspend fun helpArticles(accessToken: String, keyword: String? = null): R07CallResult<R15SupportTicketPage>
    suspend fun helpArticle(accessToken: String, id: String): R07CallResult<R15SupportTicketResource>
    suspend fun createTicket(accessToken: String, key: String, request: R15CreateTicketRequest): R07CallResult<R15SupportTicketResource>
    suspend fun tickets(accessToken: String, status: String? = null): R07CallResult<R15SupportTicketPage>
    suspend fun ticket(accessToken: String, id: String): R07CallResult<R15SupportTicketResource>
    suspend fun addMessage(accessToken: String, id: String, key: String, request: R15SupportMessageRequest): R07CallResult<R15SupportTicketResource>
    suspend fun reportContent(accessToken: String, id: String, key: String, request: R15ContentReportRequest): R07CallResult<CommandResultResource>
}

class UrlConnectionContractR15Api(baseUrl: String) : ContractR15Api {
    private val root = validateR15Root(baseUrl)

    override suspend fun notifications(accessToken: String, keyword: String?, status: String?) = call(
        "GET", listPath("/api/v1/notifications", keyword = keyword, status = status), accessToken, R15NotificationPage.serializer(),
    )

    override suspend fun readNotification(accessToken: String, id: String, key: String) = call(
        "POST", "/api/v1/notifications/${safeR15Id(id)}/read", accessToken,
        R15NotificationResource.serializer(), requireR15Key(key),
        HhyNetworkJson.value.encodeToString(R15NotificationReadRequest(id)),
    )

    override suspend fun readAllNotifications(accessToken: String, key: String) = call(
        "POST", "/api/v1/notifications/read-all", accessToken, CommandResultResource.serializer(), requireR15Key(key),
    )

    override suspend fun announcements(accessToken: String, keyword: String?) = call(
        "GET", listPath("/api/v1/announcements", keyword = keyword), accessToken, R15NotificationPage.serializer(),
    )

    override suspend fun announcement(accessToken: String, id: String) = call(
        "GET", "/api/v1/announcements/${safeR15Id(id)}", accessToken, R15NotificationResource.serializer(),
    )

    override suspend fun helpArticles(accessToken: String, keyword: String?) = call(
        "GET", listPath("/api/v1/help/articles", keyword = keyword), accessToken, R15SupportTicketPage.serializer(),
    )

    override suspend fun helpArticle(accessToken: String, id: String) = call(
        "GET", "/api/v1/help/articles/${safeR15Id(id)}", accessToken, R15SupportTicketResource.serializer(),
    )

    override suspend fun createTicket(accessToken: String, key: String, request: R15CreateTicketRequest) = call(
        "POST", "/api/v1/support/tickets", accessToken, R15SupportTicketResource.serializer(), requireR15Key(key),
        HhyNetworkJson.value.encodeToString(request),
    )

    override suspend fun tickets(accessToken: String, status: String?) = call(
        "GET", listPath("/api/v1/support/tickets", status = status), accessToken, R15SupportTicketPage.serializer(),
    )

    override suspend fun ticket(accessToken: String, id: String) = call(
        "GET", "/api/v1/support/tickets/${safeR15Id(id)}", accessToken, R15SupportTicketResource.serializer(),
    )

    override suspend fun addMessage(accessToken: String, id: String, key: String, request: R15SupportMessageRequest) = call(
        "POST", "/api/v1/support/tickets/${safeR15Id(id)}/messages", accessToken,
        R15SupportTicketResource.serializer(), requireR15Key(key), HhyNetworkJson.value.encodeToString(request),
    )

    override suspend fun reportContent(accessToken: String, id: String, key: String, request: R15ContentReportRequest) = call(
        "POST", "/api/v1/contents/${safeR15Id(id)}/report", accessToken,
        CommandResultResource.serializer(), requireR15Key(key), HhyNetworkJson.value.encodeToString(request),
    )

    private suspend fun <T> call(
        method: String,
        path: String,
        accessToken: String,
        serializer: KSerializer<T>,
        idempotencyKey: String? = null,
        body: String? = null,
    ): R07CallResult<T> = withContext(Dispatchers.IO) {
        try {
            val connection = URI.create(root + path).toURL().openConnection() as HttpURLConnection
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
                if (status !in 200..299) {
                    val error = runCatching { HhyNetworkJson.value.decodeFromString<ApiErrorEnvelope>(text) }.getOrNull()
                    return@withContext R07CallResult.Failure(
                        statusCode = status,
                        errorCode = error?.error?.code,
                        requestId = error?.requestId,
                        retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull(),
                        fieldErrors = error?.error?.fieldErrors().orEmpty(),
                        retryable = error?.error?.retryable ?: (status >= 500),
                    )
                }
                val envelope = HhyNetworkJson.value.decodeFromString(ApiEnvelope.serializer(serializer), text)
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
}

private fun listPath(route: String, keyword: String? = null, status: String? = null): String {
    val values = listOf(
        "page" to "1", "pageSize" to "20", "keyword" to keyword?.trim()?.takeIf(String::isNotEmpty),
        "status" to status?.takeIf(String::isNotEmpty), "sort" to "createdAt:desc",
    )
    val query = values.mapNotNull { (key, value) -> value?.let { "${encodeR15(key)}=${encodeR15(it)}" } }.joinToString("&")
    return "$route?$query"
}

private fun validateR15Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.fragment == null && uri.query == null)
    return uri.toString().trimEnd('/')
}

private fun safeR15Id(value: String) = value.also { require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(it)) }
private fun requireR15Key(value: String) = value.also { require(it.length in 16..128) }
private fun encodeR15(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
