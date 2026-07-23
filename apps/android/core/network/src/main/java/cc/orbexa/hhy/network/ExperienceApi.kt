package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement

data class HomeExperimentAssignmentSnapshot(val experimentKey: String, val variant: String)
data class HomeTrackingContextSnapshot(
    val pageCode: String,
    val source: String,
    val campaignId: String?,
    val contentId: String?,
    val requestId: String?,
    val experimentAssignments: List<HomeExperimentAssignmentSnapshot>,
)
data class HomeNavigationTargetSnapshot(
    val targetType: String,
    val route: String?,
    val url: String?,
    val requiresLogin: Boolean,
)
data class HomeModuleItemSnapshot(
    val id: String,
    val itemType: String,
    val title: String,
    val subtitle: String?,
    val coverUrl: String?,
    val badges: List<String>,
    val target: HomeNavigationTargetSnapshot,
    val trackingContext: HomeTrackingContextSnapshot?,
)
data class HomeModuleSnapshot(
    val id: String,
    val type: String,
    val title: String?,
    val subtitle: String?,
    val layoutType: String,
    val items: List<HomeModuleItemSnapshot>,
    val moreTarget: HomeNavigationTargetSnapshot?,
    val trackingContext: HomeTrackingContextSnapshot?,
    val startAt: String?,
    val endAt: String?,
)
data class HomeFeatureFlagSnapshot(
    val key: String,
    val enabled: Boolean,
    val variant: String?,
    val reason: String?,
)
data class HomeSnapshot(
    val serverTime: String,
    val modules: List<HomeModuleSnapshot>,
    val featureFlags: List<HomeFeatureFlagSnapshot> = emptyList(),
    val trackingContext: HomeTrackingContextSnapshot? = null,
)
data class AgreementSnapshot(val code: String, val title: String?, val description: String?, val content: List<String>, val version: Long)

interface ExperienceApi {
    suspend fun home(accessToken: String): Result<HomeSnapshot>
    suspend fun agreement(code: String): Result<AgreementSnapshot>
    suspend fun checkVersion(request: VersionCheckRequest): Result<VersionPolicy>
}

class UrlConnectionExperienceApi(baseUrl: String) : ExperienceApi {
    private val root = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun home(accessToken: String): Result<HomeSnapshot> =
        request("GET", "/api/v1/home", accessToken).map(::parseHomeSnapshot)

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

    private fun safe(value: String): String = value.also { require(Regex("^[A-Za-z0-9_-]{1,128}$").matches(it)) }
}

internal fun parseHomeSnapshot(data: JsonObject): HomeSnapshot = HomeSnapshot(
    serverTime = data.string("serverTime"),
    modules = data.array("modules").mapNotNull { element ->
        val module = element as? JsonObject ?: return@mapNotNull null
        HomeModuleSnapshot(
            id = module.string("moduleId"),
            type = module.string("moduleType").uppercase(),
            title = module.optionalString("title"),
            subtitle = module.optionalString("subtitle"),
            layoutType = module.string("layoutType"),
            items = module.array("items").mapNotNull { itemElement ->
                val item = itemElement as? JsonObject ?: return@mapNotNull null
                HomeModuleItemSnapshot(
                    id = item.string("id"),
                    itemType = item.string("itemType").uppercase(),
                    title = item.string("title"),
                    subtitle = item.optionalString("subtitle"),
                    coverUrl = item.optionalString("coverUrl"),
                    badges = item.array("badges").mapNotNull { it.jsonPrimitive.contentOrNull },
                    target = parseNavigationTarget(item.objectValue("target")),
                    trackingContext = parseTrackingContext(item.objectValue("trackingContext")),
                )
            },
            moreTarget = module.objectValue("moreTarget")?.let(::parseNavigationTarget),
            trackingContext = parseTrackingContext(module.objectValue("trackingContext")),
            startAt = module.optionalString("startAt"),
            endAt = module.optionalString("endAt"),
        )
    },
    featureFlags = data.array("featureFlags").mapNotNull { element ->
        val flag = element as? JsonObject ?: return@mapNotNull null
        HomeFeatureFlagSnapshot(
            key = flag.string("key"),
            enabled = flag.boolean("enabled"),
            variant = flag.optionalString("variant"),
            reason = flag.optionalString("reason"),
        )
    },
    trackingContext = parseTrackingContext(data.objectValue("trackingContext")),
)

private fun parseNavigationTarget(value: JsonObject?): HomeNavigationTargetSnapshot =
    HomeNavigationTargetSnapshot(
        targetType = value?.string("targetType")?.uppercase().orEmpty().ifBlank { "NONE" },
        route = value?.optionalString("route"),
        url = value?.optionalString("url"),
        requiresLogin = value?.boolean("requiresLogin", default = true) ?: true,
    )

private fun parseTrackingContext(value: JsonObject?): HomeTrackingContextSnapshot? {
    value ?: return null
    return HomeTrackingContextSnapshot(
        pageCode = value.string("pageCode"),
        source = value.string("source"),
        campaignId = value.optionalString("campaignId"),
        contentId = value.optionalString("contentId"),
        requestId = value.optionalString("requestId"),
        experimentAssignments = value.array("experimentAssignments").mapNotNull { element ->
            val assignment = element as? JsonObject ?: return@mapNotNull null
            HomeExperimentAssignmentSnapshot(
                experimentKey = assignment.string("experimentKey"),
                variant = assignment.string("variant"),
            )
        },
    )
}

private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.optionalString(key: String) = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.long(key: String) = this[key]?.jsonPrimitive?.longOrNull ?: 0L
private fun JsonObject.boolean(key: String, default: Boolean = false) =
    this[key]?.jsonPrimitive?.booleanOrNull ?: default
private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
private fun JsonObject.objectValue(key: String): JsonObject? = this[key] as? JsonObject
