package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString

interface ContractR11Api {
    suspend fun teamLeaders(
        accessToken: String,
        cursor: String? = null,
        pageSize: Int = 20,
        sort: String = "createdAt:desc",
    ): R07CallResult<ContentPageResource>
}

class UrlConnectionContractR11Api(baseUrl: String) : ContractR11Api {
    private val root = validateR11Root(baseUrl)

    override suspend fun teamLeaders(
        accessToken: String,
        cursor: String?,
        pageSize: Int,
        sort: String,
    ): R07CallResult<ContentPageResource> = withContext(Dispatchers.IO) {
        require(pageSize in 1..100)
        require(sort in SORTS)
        require(cursor == null || cursor.length <= 256)
        val route = query(
            "/api/v1/contents",
            listOf(
                "contentType" to "TEAM_LEADER",
                "status" to "ONLINE",
                "cursor" to cursor,
                "pageSize" to pageSize.toString(),
                "sort" to sort,
            ),
        )
        try {
            val connection = URI.create(root + route).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
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
                        fieldErrors = error?.error?.details.orEmpty().associate { it.field to it.message },
                        retryable = error?.error?.retryable ?: (status >= 500),
                    )
                }
                val envelope = HhyNetworkJson.value.decodeFromString(
                    ApiEnvelope.serializer(ContentPageResource.serializer()),
                    text,
                )
                R07CallResult.Success(envelope.data, envelope.requestId)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            R07CallResult.Failure(null)
        }
    }

    private fun query(route: String, values: List<Pair<String, String?>>): String {
        val value = values.mapNotNull { (key, item) -> item?.let { "${encode(key)}=${encode(it)}" } }.joinToString("&")
        return if (value.isEmpty()) route else "$route?$value"
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private companion object {
        val SORTS = setOf("createdAt:desc", "updatedAt:desc", "id:desc")
    }
}

private fun validateR11Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.fragment == null && uri.query == null)
    return uri.toString().trimEnd('/')
}
