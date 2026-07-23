package cc.orbexa.hhy.teamleader

import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import java.net.URI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

enum class R11TeamLeaderPhase {
    LOADING,
    CONTENT,
    EMPTY,
    REFRESHING,
    APPENDING,
    PARTIAL_ERROR,
    ERROR,
    OFFLINE,
    FORBIDDEN,
}

data class R11TeamLeaderFailure(
    val phase: R11TeamLeaderPhase,
    val retryAfterSeconds: Long?,
)

fun R07CallResult.Failure.toR11TeamLeaderFailure(hasContent: Boolean = false): R11TeamLeaderFailure =
    R11TeamLeaderFailure(
        phase = if (hasContent) {
            R11TeamLeaderPhase.PARTIAL_ERROR
        } else {
            when (statusCode) {
                null -> R11TeamLeaderPhase.OFFLINE
                403 -> R11TeamLeaderPhase.FORBIDDEN
                else -> R11TeamLeaderPhase.ERROR
            }
        },
        retryAfterSeconds = retryAfterSeconds,
    )

data class R11TeamLeaderFacts(
    val teamName: String,
    val nickname: String?,
    val logoUrl: String?,
    val personalIntro: String?,
    val teamIntro: String?,
    val sizeRange: String?,
    val skills: String?,
    val cooperationTypes: String?,
    val cooperationRequirement: String?,
    val pastCases: List<String>,
    val acceptPrivateChat: Boolean?,
) {
    val introduction: String?
        get() = teamIntro ?: personalIntro

    val tags: List<String>
        get() = listOfNotNull(sizeRange, skills, cooperationTypes).distinct().take(3)

    companion object {
        fun from(resource: ContentResource): R11TeamLeaderFacts {
            val attributes = resource.attributes
            val logoMediaId = attributes.text("logoMediaId")
            val matchingLogo = logoMediaId?.let { id -> resource.media.firstOrNull { it.id == id } }
            val logoUrl = sequenceOf(matchingLogo?.thumbnailUrl, matchingLogo?.url)
                .plus(resource.media.asSequence().flatMap { sequenceOf(it.thumbnailUrl, it.url) })
                .filterNotNull()
                .firstOrNull(::secureHttps)
            return R11TeamLeaderFacts(
                teamName = attributes.text("teamName") ?: resource.title,
                nickname = attributes.text("nickname") ?: resource.publisher?.nickname,
                logoUrl = logoUrl,
                personalIntro = attributes.text("personalIntro"),
                teamIntro = attributes.text("teamIntro") ?: resource.summary?.trim()?.takeIf(String::isNotBlank),
                sizeRange = attributes.businessText("sizeRange"),
                skills = attributes.businessText("skills"),
                cooperationTypes = attributes.businessText("cooperationTypes"),
                cooperationRequirement = attributes.text("cooperationRequirement"),
                pastCases = attributes.textList("pastCases"),
                acceptPrivateChat = attributes.boolean("acceptPrivateChat"),
            )
        }
    }
}

data class R11TeamLeaderListState(
    val items: List<ContentResource> = emptyList(),
    val phase: R11TeamLeaderPhase = R11TeamLeaderPhase.LOADING,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val failure: R11TeamLeaderFailure? = null,
) {
    fun loading(reset: Boolean): R11TeamLeaderListState = copy(
        phase = if (reset && items.isNotEmpty()) R11TeamLeaderPhase.REFRESHING
        else if (!reset && items.isNotEmpty()) R11TeamLeaderPhase.APPENDING
        else R11TeamLeaderPhase.LOADING,
        failure = null,
    )

    fun success(result: List<ContentResource>, cursor: String?, canLoadMore: Boolean, reset: Boolean): R11TeamLeaderListState {
        val current = if (reset) emptyList() else items
        val known = current.mapTo(mutableSetOf()) { it.id }
        val merged = current + result.filter { it.contentType == "TEAM_LEADER" && known.add(it.id) }
        return copy(
            items = merged,
            phase = if (merged.isEmpty()) R11TeamLeaderPhase.EMPTY else R11TeamLeaderPhase.CONTENT,
            nextCursor = cursor,
            hasMore = canLoadMore,
            failure = null,
        )
    }

    fun failed(value: R07CallResult.Failure): R11TeamLeaderListState {
        val mapped = value.toR11TeamLeaderFailure(items.isNotEmpty())
        return copy(phase = mapped.phase, failure = mapped)
    }
}

private fun JsonObject?.text(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)

private fun JsonObject?.businessText(key: String): String? = text(key)?.takeIf { value ->
    value.any { it in '\u4e00'..'\u9fff' } || value.any(Char::isDigit)
}

private fun JsonObject?.boolean(key: String): Boolean? =
    (this?.get(key) as? JsonPrimitive)?.booleanOrNull

private fun JsonObject?.textList(key: String): List<String> =
    (this?.get(key) as? JsonArray).orEmpty().mapNotNull { value ->
        (value as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
    }

private fun secureHttps(value: String): Boolean = runCatching {
    val uri = URI.create(value)
    uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)
