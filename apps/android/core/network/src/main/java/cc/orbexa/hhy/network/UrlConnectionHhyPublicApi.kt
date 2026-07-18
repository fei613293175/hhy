package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class UrlConnectionHhyPublicApi(baseUrl: String) : HhyPublicApi {
    private val root = validateBaseUrl(baseUrl)
    private val json = HhyNetworkJson.value

    override suspend fun platformStatus(): ApiEnvelope<PlatformStatus> =
        get("/public-api/v1/platform/status")

    override suspend fun versionCheck(request: VersionCheckRequest): ApiEnvelope<VersionPolicy> =
        post("/public-api/v1/app/version-check", json.encodeToString(request))

    override suspend fun latestApp(): ApiEnvelope<PublicPage> =
        get("/public-api/v1/app/latest")

    private suspend inline fun <reified T> get(path: String): T = request("GET", path, null)

    private suspend inline fun <reified T> post(path: String, body: String): T =
        request("POST", path, body)

    private suspend inline fun <reified T> request(method: String, path: String, body: String?): T =
        withContext(Dispatchers.IO) {
            val requestId = UUID.randomUUID().toString()
            val connection = URI.create(root + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("X-Request-Id", requestId)
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    throw HhyApiException(status, connection.getHeaderField("X-Request-Id") ?: requestId)
                }
                json.decodeFromString<T>(responseBody)
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 12_000

        fun validateBaseUrl(value: String): String {
            val uri = runCatching { URI.create(value.trim()) }
                .getOrElse { throw IllegalArgumentException("API base URL is invalid", it) }
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) {
                "API base URL must be an absolute HTTPS URL without user information"
            }
            return value.trim().trimEnd('/')
        }
    }
}

class HhyApiException(val statusCode: Int, val requestId: String) :
    RuntimeException("API request failed with HTTP $statusCode")
