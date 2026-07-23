package cc.orbexa.hhy.teamleader

import cc.orbexa.hhy.network.ContentResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R08ContactInput
import java.net.URI
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

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
    NOT_FOUND,
    CONFLICT,
    SUBMITTING,
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
                404 -> R11TeamLeaderPhase.NOT_FOUND
                409 -> R11TeamLeaderPhase.CONFLICT
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

class R11IntentKeys {
    private val keys = mutableMapOf<String, Pair<String, String>>()

    fun forBody(intent: String, fingerprint: String): String = keys[intent]
        ?.takeIf { it.first == fingerprint }
        ?.second
        ?: UUID.randomUUID().toString().also { keys[intent] = fingerprint to it }

    fun consume(intent: String, fingerprint: String) {
        if (keys[intent]?.first == fingerprint) keys.remove(intent)
    }
}

data class R11TeamLeaderForm(
    val nickname: String = "",
    val personalIntro: String = "",
    val teamName: String = "",
    val teamIntro: String = "",
    val sizeRange: String = "",
    val skills: String = "",
    val cooperationTypes: String = "",
    val cooperationRequirement: String = "",
    val pastCases: String = "",
    val categoryCode: String = "",
    val regionCode: String = "",
    val contactChannel: String = "WECHAT",
    val contactValue: String = "",
    val existingContactAvailable: Boolean = false,
    val mediaIds: List<String> = emptyList(),
    val acceptPrivateChat: Boolean = true,
    val expectedVersion: Long? = null,
) {
    fun validate(): Map<String, String> = buildMap {
        required("nickname", nickname, "请输入团队长昵称", 20)
        required("personalIntro", personalIntro, "请输入个人介绍", 300)
        required("teamName", teamName, "请输入团队名称", 2000)
        required("teamIntro", teamIntro, "请输入团队介绍", 2000)
        if (sizeRange !in SIZE_RANGES) put("sizeRange", "请选择团队人数")
        required("categoryCode", categoryCode, "请输入擅长领域", 2000)
        if (regionCode.length > 2000) put("regionCode", "所在地区不能超过2000字")
        if (skills.length > 255) put("skills", "核心能力不能超过255字")
        if (cooperationTypes.length > 255) put("cooperationTypes", "合作类型不能超过255字")
        if (cooperationRequirement.length > 2000) put("cooperationRequirement", "合作要求不能超过2000字")
        if (pastCases.length > 2000) put("pastCases", "过往案例不能超过2000字")
        if (contactChannel !in CONTACT_CHANNELS) put("contactChannel", "联系方式渠道不受支持")
        if (!existingContactAvailable || contactValue.isNotBlank()) required("contactValue", contactValue, "请输入联系方式", 2000)
        if (mediaIds.size > 100 || mediaIds.distinct().size != mediaIds.size || mediaIds.any { !ID.matches(it) }) {
            put("mediaIds", "团队图片数量或内容不符合要求")
        }
        if (expectedVersion != null && expectedVersion < 0) put("expectedVersion", "数据版本无效")
    }

    fun attributes(): JsonObject = buildJsonObject {
        put("nickname", nickname.trim())
        put("personalIntro", personalIntro.trim())
        put("teamName", teamName.trim())
        put("teamIntro", teamIntro.trim())
        put("sizeRange", sizeRange)
        skills.trim().takeIf(String::isNotBlank)?.let { put("skills", it) }
        cooperationTypes.trim().takeIf(String::isNotBlank)?.let { put("cooperationTypes", it) }
        cooperationRequirement.trim().takeIf(String::isNotBlank)?.let { put("cooperationRequirement", it) }
        val cases = pastCases.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        if (cases.isNotEmpty()) put("pastCases", buildJsonArray { cases.forEach { add(JsonPrimitive(it)) } })
        put("acceptPrivateChat", acceptPrivateChat)
        mediaIds.firstOrNull()?.let { put("logoMediaId", it) }
    }

    fun contacts(): List<R08ContactInput>? = contactValue.trim().takeIf(String::isNotBlank)
        ?.let { listOf(R08ContactInput(contactChannel, it)) }
    fun fingerprint() = listOf(
        nickname, personalIntro, teamName, teamIntro, sizeRange, skills, cooperationTypes,
        cooperationRequirement, pastCases, categoryCode, regionCode, contactChannel,
        contactValue, mediaIds.joinToString(","), acceptPrivateChat.toString(), expectedVersion.toString(),
    ).joinToString("|")

    companion object {
        val SIZE_RANGES = listOf("10人以下", "10-50人", "50-200人", "200人以上")
        val CONTACT_CHANNELS = setOf("WECHAT", "PHONE", "QQ", "EMAIL")
        private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")

        fun from(resource: ContentResource): R11TeamLeaderForm {
            val facts = R11TeamLeaderFacts.from(resource)
            val masked = resource.contactsMasked.firstOrNull { it.available }
            return R11TeamLeaderForm(
                nickname = facts.nickname.orEmpty(),
                personalIntro = facts.personalIntro.orEmpty(),
                teamName = facts.teamName,
                teamIntro = facts.teamIntro.orEmpty(),
                sizeRange = facts.sizeRange.orEmpty(),
                skills = facts.skills.orEmpty(),
                cooperationTypes = facts.cooperationTypes.orEmpty(),
                cooperationRequirement = facts.cooperationRequirement.orEmpty(),
                pastCases = facts.pastCases.joinToString("\n"),
                categoryCode = resource.categoryCode.orEmpty(),
                regionCode = resource.regionCode.orEmpty(),
                contactChannel = masked?.channel?.takeIf { it in CONTACT_CHANNELS } ?: "WECHAT",
                existingContactAvailable = masked != null,
                mediaIds = resource.media.map { it.id },
                acceptPrivateChat = facts.acceptPrivateChat != false,
                expectedVersion = resource.version,
            )
        }

        private fun MutableMap<String, String>.required(key: String, value: String, message: String, max: Int) {
            if (value.isBlank()) put(key, message) else if (value.length > max) put(key, "内容不能超过${max}字")
        }
    }
}

fun R11TeamLeaderFacts.detailSections(resource: ContentResource): List<Pair<String, List<String>>> = buildList {
    introduction?.let { add("团队介绍" to listOf(it)) }
    cooperationRequirement?.let { add("合作要求" to listOf(it)) }
    if (pastCases.isNotEmpty()) add("过往案例" to pastCases)
    val capabilities = listOfNotNull(skills, cooperationTypes)
    if (capabilities.isNotEmpty()) add("能力与合作" to capabilities)
    resource.description?.trim()?.takeIf(String::isNotBlank)?.let { description ->
        if (description != introduction) add("详细说明" to listOf(description))
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
