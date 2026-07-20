package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement

data class HomeSnapshot(val serverTime: String, val modules: List<HomeModuleSnapshot>)
data class HomeModuleSnapshot(val id: String, val type: String, val title: String?, val subtitle: String?, val items: List<String>)
data class AgreementSnapshot(val code: String, val title: String?, val description: String?, val content: List<String>, val version: Long)

interface ExperienceApi {
    suspend fun home(accessToken: String): Result<HomeSnapshot>
    suspend fun agreement(code: String): Result<AgreementSnapshot>
    suspend fun checkVersion(request: VersionCheckRequest): Result<VersionPolicy>
}

class UrlConnectionExperienceApi(baseUrl: String) : ExperienceApi {
    private val root = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun home(accessToken: String): Result<HomeSnapshot> = request("GET", "/api/v1/home", accessToken).map { data ->
        HomeSnapshot(
            serverTime = data.string("serverTime"),
            modules = data.array("modules").mapNotNull { module ->
                val value = module as? JsonObject ?: return@mapNotNull null
                HomeModuleSnapshot(
                    id = value.string("moduleId"), type = value.string("moduleType"),
                    title = value.optionalString("title"), subtitle = value.optionalString("subtitle"),
                    items = value.array("items").mapNotNull { item -> (item as? JsonObject)?.optionalString("title") },
                )
            },
        )
    }

    override suspend fun agreement(code: String): Result<AgreementSnapshot> = request("GET", "/public-api/v1/agreements/${safe(code)}", null).map { data ->
        AgreementSnapshot(data.string("code"), data.optionalString("title"), data.optionalString("description"), data.array("content").mapNotNull { (it as? JsonObject)?.optionalString("text") ?: it.jsonPrimitive.contentOrNull }, data.long("version"))
    }

    override suspend fun checkVersion(request: VersionCheckRequest): Result<VersionPolicy> = request("POST", "/public-api/v1/app/version-check", null, json.encodeToString(VersionCheckRequest.serializer(), request)).map { data ->
        json.decodeFromJsonElement(VersionPolicy.serializer(), data)
    }

    private suspend fun request(method: String, path: String, token: String?, body: String? = null): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URI.create(root + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method; connection.connectTimeout = 8_000; connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json"); token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) { connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json"); connection.outputStream.bufferedWriter().use { it.write(body) } }
                val status = connection.responseCode; val text = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                require(status in 200..299) { "request failed: $status" }
                val envelope = json.parseToJsonElement(text).jsonObject
                envelope["data"]?.jsonObject ?: error("response data missing")
            } finally { connection.disconnect() }
        }
    }

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.content.orEmpty()
    private fun JsonObject.optionalString(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(key: String) = this[key]?.jsonPrimitive?.longOrNull ?: 0L
    private fun JsonObject.array(key: String) = this[key]?.jsonArray.orEmpty()
    private fun safe(value: String): String = value.also { require(Regex("^[A-Za-z0-9_-]{1,128}$").matches(it)) }
}
