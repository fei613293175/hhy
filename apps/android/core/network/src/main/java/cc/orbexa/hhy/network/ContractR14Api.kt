package cc.orbexa.hhy.network

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface ChatMessagePayload

data class ChatLastMessageResource(
    val messageId: String,
    val messageType: String,
    val preview: String,
    val senderId: String? = null,
    val createdAt: String,
)

data class ChatConversationResource(
    val id: String,
    val peer: PublisherSummaryResource? = null,
    val lastMessage: ChatLastMessageResource? = null,
    val unreadCount: Long,
    val lastReadMessageId: String? = null,
    val updatedAt: String? = null,
    val version: Long,
)

data class ChatConversationPageResource(
    val items: List<ChatConversationResource>,
    val page: R07PageMeta,
)

@Serializable
data class ChatTextPayload(val text: String) : ChatMessagePayload {
    init { require(text.isNotBlank() && text.length <= 5_000) }
}

@Serializable
data class ChatImagePayload(
    val mediaId: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
) : ChatMessagePayload {
    init {
        requireFrozenR14Id(mediaId)
        require(thumbnailUrl == null || isSafeR14MediaUrl(thumbnailUrl))
        require(width == null || width in 1..10_000)
        require(height == null || height in 1..10_000)
    }
}

@Serializable
data class ChatContentCardPayload(
    val contentId: String,
    val contentType: String,
    val title: String,
    val coverUrl: String? = null,
) : ChatMessagePayload {
    init {
        requireFrozenR14Id(contentId)
        require(contentType in CHAT_CONTENT_TYPES)
        require(title.isNotBlank() && title.length <= 255)
        require(coverUrl == null || isSafeR14MediaUrl(coverUrl))
    }
}

@Serializable
data class ChatContactField(
    val type: String,
    val label: String? = null,
    val value: String,
) {
    init {
        require(type in CHAT_CONTACT_TYPES)
        require(label == null || label.length <= 32)
        require(value.isNotBlank() && value.length <= 256)
    }
}

@Serializable
data class ChatContactCardPayload(
    val fields: List<ChatContactField>,
    val note: String? = null,
) : ChatMessagePayload {
    init {
        require(fields.size in 1..5)
        require(note == null || note.length <= 200)
    }
}

data class ChatMessageResource(
    val id: String,
    val conversationId: String,
    val sender: PublisherSummaryResource,
    val clientMessageId: String,
    val messageType: String,
    val payload: ChatMessagePayload,
    val status: String,
    val serverSequence: Long? = null,
    val createdAt: String,
    val readAt: String? = null,
)

data class ChatMessagePageResource(
    val items: List<ChatMessageResource>,
    val page: R07PageMeta,
)

sealed interface ChatSendMessageRequest {
    val clientMessageId: String
    val messageType: String
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ChatTextMessageRequest(
    override val clientMessageId: String,
    @EncodeDefault
    override val messageType: String = "TEXT",
    val payload: ChatTextPayload,
) : ChatSendMessageRequest {
    init { requireMessageRequest(clientMessageId, messageType, "TEXT") }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ChatImageMessageRequest(
    override val clientMessageId: String,
    @EncodeDefault
    override val messageType: String = "IMAGE",
    val payload: ChatImagePayload,
) : ChatSendMessageRequest {
    init { requireMessageRequest(clientMessageId, messageType, "IMAGE") }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ChatContentCardMessageRequest(
    override val clientMessageId: String,
    @EncodeDefault
    override val messageType: String = "CONTENT_CARD",
    val payload: ChatContentCardPayload,
) : ChatSendMessageRequest {
    init { requireMessageRequest(clientMessageId, messageType, "CONTENT_CARD") }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ChatContactCardMessageRequest(
    override val clientMessageId: String,
    @EncodeDefault
    override val messageType: String = "CONTACT_CARD",
    val payload: ChatContactCardPayload,
) : ChatSendMessageRequest {
    init { requireMessageRequest(clientMessageId, messageType, "CONTACT_CARD") }
}

@Serializable
data class ChatPostConversationsByIdReadRequest(val lastReadMessageId: String) {
    init { requireFrozenR14Id(lastReadMessageId) }
}

@Serializable
data class ChatBlockRequest(val reason: String? = null) {
    init { require(reason == null || reason.length <= 2_000) }
}

@Serializable
data class ChatReportRequest(
    val reasonCode: String,
    val description: String,
    val evidenceMediaIds: List<String> = emptyList(),
    val messageIds: List<String> = emptyList(),
    val expectedVersion: Long? = null,
) {
    init {
        require(reasonCode.isNotBlank() && reasonCode.length <= 2_000)
        require(description.length <= 2_000)
        require(evidenceMediaIds.size <= 100 && messageIds.size <= 100)
        evidenceMediaIds.forEach(::requireFrozenR14Id)
        messageIds.forEach(::requireFrozenR14Id)
        require(expectedVersion == null || expectedVersion >= 0)
    }
}

interface ContractR14Api {
    suspend fun conversations(
        accessToken: String,
        page: Int = 1,
        pageSize: Int = 20,
        cursor: String? = null,
        status: String? = null,
        keyword: String? = null,
        sort: String? = null,
    ): R07CallResult<ChatConversationPageResource>

    suspend fun messages(
        accessToken: String,
        conversationId: String,
        page: Int = 1,
        pageSize: Int = 20,
        cursor: String? = null,
        status: String? = null,
        keyword: String? = null,
        sort: String? = null,
    ): R07CallResult<ChatMessagePageResource>

    suspend fun send(
        accessToken: String,
        conversationId: String,
        idempotencyKey: String,
        request: ChatSendMessageRequest,
    ): R07CallResult<ChatMessageResource>

    suspend fun read(
        accessToken: String,
        conversationId: String,
        idempotencyKey: String,
        request: ChatPostConversationsByIdReadRequest,
    ): R07CallResult<CommandResultResource>

    suspend fun report(accessToken: String, conversationId: String, idempotencyKey: String, request: ChatReportRequest): R07CallResult<CommandResultResource> = unsupportedR14Action()
    suspend fun block(accessToken: String, userId: String, idempotencyKey: String, request: ChatBlockRequest): R07CallResult<CommandResultResource> = unsupportedR14Action()
    suspend fun unblock(accessToken: String, userId: String, idempotencyKey: String): R07CallResult<CommandResultResource> = unsupportedR14Action()
    suspend fun deleteConversation(accessToken: String, conversationId: String, idempotencyKey: String): R07CallResult<CommandResultResource> = unsupportedR14Action()
}

private fun unsupportedR14Action(): R07CallResult<CommandResultResource> =
    R07CallResult.Failure(statusCode = 501, retryable = false)

class UrlConnectionContractR14Api(baseUrl: String) : ContractR14Api {
    private val root = validateR14Root(baseUrl)

    override suspend fun conversations(
        accessToken: String,
        page: Int,
        pageSize: Int,
        cursor: String?,
        status: String?,
        keyword: String?,
        sort: String?,
    ): R07CallResult<ChatConversationPageResource> {
        val route = r14ConversationRoute(page, pageSize, cursor, status, keyword, sort)
        return callJson("GET", route, accessToken).decodeR14(::decodeChatConversationPage)
    }

    override suspend fun messages(
        accessToken: String,
        conversationId: String,
        page: Int,
        pageSize: Int,
        cursor: String?,
        status: String?,
        keyword: String?,
        sort: String?,
    ): R07CallResult<ChatMessagePageResource> {
        require(page >= 1 && pageSize in 1..100)
        require(cursor == null || cursor.length <= 256)
        require(status == null || status.length <= 64)
        require(keyword == null || keyword.length <= 100)
        require(sort == null || sort.length <= 64)
        val route = queryR14(
            "/api/v1/conversations/${requireFrozenR14Id(conversationId)}/messages",
            listOf(
                "page" to page.toString(),
                "pageSize" to pageSize.toString(),
                "cursor" to cursor?.takeIf(String::isNotBlank),
                "status" to status?.takeIf(String::isNotBlank),
                "keyword" to keyword?.takeIf(String::isNotBlank),
                "sort" to sort?.takeIf(String::isNotBlank),
            ),
        )
        return callJson("GET", route, accessToken).decodeR14(::decodeChatMessagePage)
    }

    override suspend fun send(
        accessToken: String,
        conversationId: String,
        idempotencyKey: String,
        request: ChatSendMessageRequest,
    ): R07CallResult<ChatMessageResource> = callJson(
        method = "POST",
        route = "/api/v1/conversations/${requireFrozenR14Id(conversationId)}/messages",
        accessToken = accessToken,
        idempotencyKey = requireR14Key(idempotencyKey),
        body = encodeChatSendRequest(request),
    ).decodeR14(::decodeChatMessageResource)

    override suspend fun read(
        accessToken: String,
        conversationId: String,
        idempotencyKey: String,
        request: ChatPostConversationsByIdReadRequest,
    ): R07CallResult<CommandResultResource> = callJson(
        method = "POST",
        route = "/api/v1/conversations/${requireFrozenR14Id(conversationId)}/read",
        accessToken = accessToken,
        idempotencyKey = requireR14Key(idempotencyKey),
        body = HhyNetworkJson.value.encodeToString(request),
    ).decodeR14 { data -> HhyNetworkJson.value.decodeFromJsonElement(CommandResultResource.serializer(), data) }

    override suspend fun report(accessToken: String, conversationId: String, idempotencyKey: String, request: ChatReportRequest) =
        command("POST", "/api/v1/conversations/${requireFrozenR14Id(conversationId)}/report", accessToken, idempotencyKey, HhyNetworkJson.value.encodeToString(request))

    override suspend fun block(accessToken: String, userId: String, idempotencyKey: String, request: ChatBlockRequest) =
        command("POST", "/api/v1/users/${requireFrozenR14Id(userId)}/block", accessToken, idempotencyKey, HhyNetworkJson.value.encodeToString(request))

    override suspend fun unblock(accessToken: String, userId: String, idempotencyKey: String) =
        command("DELETE", "/api/v1/users/${requireFrozenR14Id(userId)}/block", accessToken, idempotencyKey)

    override suspend fun deleteConversation(accessToken: String, conversationId: String, idempotencyKey: String) =
        command("DELETE", "/api/v1/conversations/${requireFrozenR14Id(conversationId)}", accessToken, idempotencyKey)

    private suspend fun command(method: String, route: String, accessToken: String, key: String, body: String? = null) =
        callJson(method, route, accessToken, requireR14Key(key), body)
            .decodeR14 { data -> HhyNetworkJson.value.decodeFromJsonElement(CommandResultResource.serializer(), data) }

    private suspend fun callJson(
        method: String,
        route: String,
        accessToken: String,
        idempotencyKey: String? = null,
        body: String? = null,
    ): R07CallResult<JsonObject> = withContext(Dispatchers.IO) {
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
                val statusCode = connection.responseCode
                val text = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (statusCode !in 200..299) return@withContext r14Failure(connection, statusCode, text)
                val envelope = HhyNetworkJson.value.decodeFromString(
                    ApiEnvelope.serializer(JsonObject.serializer()),
                    text,
                )
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

internal fun encodeChatSendRequest(request: ChatSendMessageRequest): String = when (request) {
    is ChatTextMessageRequest -> HhyNetworkJson.value.encodeToString(request)
    is ChatImageMessageRequest -> HhyNetworkJson.value.encodeToString(request)
    is ChatContentCardMessageRequest -> HhyNetworkJson.value.encodeToString(request)
    is ChatContactCardMessageRequest -> HhyNetworkJson.value.encodeToString(request)
}

internal fun decodeChatMessagePage(data: JsonObject): ChatMessagePageResource {
    requireOnlyR14Keys(data, setOf("items", "page"))
    val items = data.getValue("items").jsonArray.map { decodeChatMessageResource(it.jsonObject) }
    val page = HhyNetworkJson.value.decodeFromJsonElement(R07PageMeta.serializer(), data.getValue("page"))
    return ChatMessagePageResource(items, page)
}

internal fun decodeChatConversationPage(data: JsonObject): ChatConversationPageResource {
    requireOnlyR14Keys(data, setOf("items", "page"))
    val items = data.getValue("items").jsonArray.map { decodeChatConversationResource(it.jsonObject) }
    val page = HhyNetworkJson.value.decodeFromJsonElement(R07PageMeta.serializer(), data.getValue("page"))
    return ChatConversationPageResource(items, page)
}

internal fun decodeChatConversationResource(data: JsonObject): ChatConversationResource {
    requireOnlyR14Keys(data, setOf("id", "peer", "lastMessage", "unreadCount", "lastReadMessageId", "updatedAt", "version"))
    val peer = data["peer"]?.takeUnless { it is JsonNull }?.let {
        HhyNetworkJson.value.decodeFromJsonElement(PublisherSummaryResource.serializer(), it)
    }
    val lastMessage = data["lastMessage"]?.takeUnless { it is JsonNull }?.jsonObject?.let(::decodeChatLastMessage)
    return ChatConversationResource(
        id = requireFrozenR14Id(data.requiredR14String("id")),
        peer = peer,
        lastMessage = lastMessage,
        unreadCount = data.getValue("unreadCount").jsonPrimitive.longOrNull?.also { require(it >= 0) }
            ?: throw IllegalArgumentException("Invalid unread count"),
        lastReadMessageId = data["lastReadMessageId"]?.jsonPrimitive?.contentOrNull?.also(::requireFrozenR14Id),
        updatedAt = data["updatedAt"]?.jsonPrimitive?.contentOrNull,
        version = data.getValue("version").jsonPrimitive.longOrNull?.also { require(it >= 0) }
            ?: throw IllegalArgumentException("Invalid conversation version"),
    )
}

private fun decodeChatLastMessage(data: JsonObject): ChatLastMessageResource {
    requireOnlyR14Keys(data, setOf("messageId", "messageType", "preview", "senderId", "createdAt"))
    val type = data.requiredR14String("messageType")
    require(type.length <= 32)
    return ChatLastMessageResource(
        messageId = requireFrozenR14Id(data.requiredR14String("messageId")),
        messageType = type,
        preview = data.requiredR14String("preview").also { require(it.length <= 300) },
        senderId = data["senderId"]?.jsonPrimitive?.contentOrNull?.also(::requireFrozenR14Id),
        createdAt = data.requiredR14String("createdAt"),
    )
}

internal fun decodeChatMessageResource(data: JsonObject): ChatMessageResource {
    requireOnlyR14Keys(
        data,
        setOf("id", "conversationId", "sender", "clientMessageId", "messageType", "payload", "status", "serverSequence", "createdAt", "readAt"),
    )
    val messageType = data.requiredR14String("messageType")
    val payloadObject = data.getValue("payload").jsonObject
    val payload = when (messageType) {
        "TEXT" -> HhyNetworkJson.value.decodeFromJsonElement(ChatTextPayload.serializer(), payloadObject)
        "IMAGE" -> HhyNetworkJson.value.decodeFromJsonElement(ChatImagePayload.serializer(), payloadObject)
        "CONTENT_CARD" -> HhyNetworkJson.value.decodeFromJsonElement(ChatContentCardPayload.serializer(), payloadObject)
        "CONTACT_CARD" -> HhyNetworkJson.value.decodeFromJsonElement(ChatContactCardPayload.serializer(), payloadObject)
        else -> throw IllegalArgumentException("Unsupported frozen chat message type")
    }
    val status = data.requiredR14String("status")
    require(status in CHAT_DELIVERY_STATES)
    return ChatMessageResource(
        id = requireFrozenR14Id(data.requiredR14String("id")),
        conversationId = requireFrozenR14Id(data.requiredR14String("conversationId")),
        sender = HhyNetworkJson.value.decodeFromJsonElement(PublisherSummaryResource.serializer(), data.getValue("sender")),
        clientMessageId = data.requiredR14String("clientMessageId").also { require(it.length <= 64) },
        messageType = messageType,
        payload = payload,
        status = status,
        serverSequence = data["serverSequence"]?.jsonPrimitive?.longOrNull?.also { require(it >= 1) },
        createdAt = data.requiredR14String("createdAt"),
        readAt = data["readAt"]?.jsonPrimitive?.content,
    )
}

private fun <T> R07CallResult<JsonObject>.decodeR14(decoder: (JsonObject) -> T): R07CallResult<T> = when (this) {
    is R07CallResult.Failure -> this
    is R07CallResult.Success -> runCatching { decoder(data) }.fold(
        onSuccess = { R07CallResult.Success(it, requestId, timestamp) },
        onFailure = { R07CallResult.Failure(500, errorCode = "COMMON-500-CONTRACT", retryable = false) },
    )
}

private fun r14Failure(connection: HttpURLConnection, status: Int, body: String): R07CallResult.Failure {
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

private fun validateR14Root(value: String): String {
    val uri = URI.create(value.trimEnd('/'))
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && !uri.host.endsWith(".invalid"))
    require(uri.userInfo == null && uri.fragment == null && uri.query == null)
    return uri.toString().trimEnd('/')
}

internal fun requireFrozenR14Id(value: String): String = value.also {
    require(Regex("^[A-Za-z0-9_-]{1,64}$").matches(it))
}

private fun requireR14Key(value: String): String = value.also {
    require(it.length in 16..128 && Regex("^[A-Za-z0-9._:-]+$").matches(it))
}

private fun requireMessageRequest(clientMessageId: String, actualType: String, requiredType: String) {
    require(clientMessageId.isNotBlank() && clientMessageId.length <= 64)
    require(actualType == requiredType)
}

private fun queryR14(route: String, values: List<Pair<String, String?>>): String {
    val query = values.mapNotNull { (key, value) ->
        value?.let { "${encodeR14(key)}=${encodeR14(it)}" }
    }.joinToString("&")
    return if (query.isEmpty()) route else "$route?$query"
}

internal fun r14ConversationRoute(
    page: Int = 1,
    pageSize: Int = 20,
    cursor: String? = null,
    status: String? = null,
    keyword: String? = null,
    sort: String? = null,
): String {
    require(page >= 1 && pageSize in 1..100)
    require(cursor == null || cursor.length <= 256)
    require(status == null || status.length <= 64)
    require(keyword == null || keyword.length <= 100)
    require(sort == null || sort.length <= 64)
    return queryR14(
        "/api/v1/conversations",
        listOf(
            "page" to page.toString(),
            "pageSize" to pageSize.toString(),
            "cursor" to cursor?.takeIf(String::isNotBlank),
            "status" to status?.takeIf(String::isNotBlank),
            "keyword" to keyword?.takeIf(String::isNotBlank),
            "sort" to sort?.takeIf(String::isNotBlank),
        ),
    )
}

private fun encodeR14(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

private fun isSafeR14MediaUrl(value: String): Boolean = runCatching {
    val uri = URI.create(value)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)

private fun requireOnlyR14Keys(value: JsonObject, allowed: Set<String>) {
    require(value.keys.all(allowed::contains))
}

private fun JsonObject.requiredR14String(name: String): String = getValue(name).jsonPrimitive.content

private val CHAT_CONTENT_TYPES = setOf("PROJECT", "APP", "GROUP_CHAT", "TEAM_LEADER")
private val CHAT_CONTACT_TYPES = setOf("PHONE", "WECHAT", "QQ", "EMAIL", "OTHER")
private val CHAT_DELIVERY_STATES = setOf("SENT", "DELIVERED", "READ")
