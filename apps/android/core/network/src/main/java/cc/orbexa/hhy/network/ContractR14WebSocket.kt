package cc.orbexa.hhy.network

import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

const val R14_WEBSOCKET_SUBPROTOCOL = "hhy.v1"
private const val R14_ACCESS_SUBPROTOCOL_PREFIX = "hhy.access."
private const val R14_MAX_PAYLOAD_BYTES = 10 * 1024 * 1024
private val R14_COMPACT_JWT = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")

enum class R14RealtimeScope { CHAT, NOTIFICATIONS }

sealed interface R14RealtimeEvent {
    data class ChatChanged(
        val conversationId: String,
        val eventType: String,
    ) : R14RealtimeEvent

    data class GapFillRequired(
        val resumeFromServerSequence: Long,
        val affectedScopes: Set<R14RealtimeScope>,
    ) : R14RealtimeEvent

    data object Connected : R14RealtimeEvent
    data object Disconnected : R14RealtimeEvent
    data object SessionInvalidated : R14RealtimeEvent
}

interface R14RealtimeClient : AutoCloseable {
    val events: SharedFlow<R14RealtimeEvent>
    fun connect(accessToken: String)
    fun disconnect()
    fun completeGapFill(
        resumeFromServerSequence: Long,
        refreshedScopes: Set<R14RealtimeScope>,
    ): Boolean
}

internal data class R14RealtimeEnvelope(
    val eventId: String,
    val eventType: String,
    val occurredAt: String,
    val serverSequence: Long?,
    val conversationId: String?,
    val ackRequired: Boolean,
    val payload: JsonObject,
)

internal sealed interface R14SequenceDecision {
    data class Deliver(
        val event: R14RealtimeEvent?,
        val acknowledgement: String?,
    ) : R14SequenceDecision

    data class Duplicate(val acknowledgement: String?) : R14SequenceDecision
    data class Gap(val expected: Long, val received: Long) : R14SequenceDecision
    data class RestGap(val gap: R14RealtimeEvent.GapFillRequired) : R14SequenceDecision
    data object ReplayComplete : R14SequenceDecision
    data object SessionInvalidated : R14SequenceDecision
}

internal class R14SequenceTracker {
    var highestContiguousSequence: Long = 0
        private set

    private var pendingGap: R14RealtimeEvent.GapFillRequired? = null

    fun reset() {
        highestContiguousSequence = 0
        pendingGap = null
    }

    fun accept(envelope: R14RealtimeEnvelope): R14SequenceDecision {
        if (envelope.eventType == "system.resume") return acceptResume(envelope)
        if (envelope.eventType == "system.kickout") return R14SequenceDecision.SessionInvalidated

        val sequence = envelope.serverSequence
        if (sequence != null) {
            val acknowledgement = envelope.takeIf { it.ackRequired }?.let(::deliveryAcknowledgement)
            if (sequence <= highestContiguousSequence) {
                return R14SequenceDecision.Duplicate(acknowledgement)
            }
            val expected = highestContiguousSequence + 1
            if (sequence != expected) return R14SequenceDecision.Gap(expected, sequence)
            highestContiguousSequence = sequence
            return R14SequenceDecision.Deliver(envelope.toPublicEvent(), acknowledgement)
        }
        return R14SequenceDecision.Deliver(envelope.toPublicEvent(), null)
    }

    fun completeGap(
        resumeFromServerSequence: Long,
        refreshedScopes: Set<R14RealtimeScope>,
    ): Boolean {
        val gap = pendingGap ?: return false
        if (gap.resumeFromServerSequence != resumeFromServerSequence) return false
        if (!refreshedScopes.containsAll(gap.affectedScopes)) return false
        if (R14RealtimeScope.NOTIFICATIONS in gap.affectedScopes) return false
        highestContiguousSequence = resumeFromServerSequence
        pendingGap = null
        return true
    }

    private fun acceptResume(envelope: R14RealtimeEnvelope): R14SequenceDecision {
        val mode = envelope.payload.requiredString("mode")
        val serverHighWatermark = envelope.payload.requiredNonNegativeLong("serverHighWatermark")
        val resumeFrom = envelope.payload.requiredNonNegativeLong("resumeFromServerSequence")
        val requested = envelope.payload.requiredNonNegativeLong("requestedLastServerSequence")
        require(requested <= serverHighWatermark)
        return when (mode) {
            "REPLAY_COMPLETE" -> {
                require(envelope.payload.requiredScopes().isEmpty())
                if (serverHighWatermark != highestContiguousSequence) {
                    R14SequenceDecision.Gap(highestContiguousSequence + 1, serverHighWatermark)
                } else {
                    require(resumeFrom == serverHighWatermark)
                    R14SequenceDecision.ReplayComplete
                }
            }
            "REST_GAP_FILL" -> {
                val scopes = envelope.payload.requiredScopes()
                require(scopes.isNotEmpty())
                val gap = R14RealtimeEvent.GapFillRequired(resumeFrom, scopes)
                pendingGap = gap
                R14SequenceDecision.RestGap(gap)
            }
            else -> error("Unsupported resume mode")
        }
    }
}

class OkHttpR14RealtimeClient(
    baseUrl: String,
    private val randomLong: (Long) -> Long = { upperExclusive ->
        if (upperExclusive <= 1) 0 else Random.nextLong(upperExclusive)
    },
) : R14RealtimeClient {
    private val endpoint = validateR14WebSocketBaseUrl(baseUrl) + "/ws"
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableEvents = MutableSharedFlow<R14RealtimeEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<R14RealtimeEvent> = mutableEvents.asSharedFlow()
    private val tracker = R14SequenceTracker()
    private val lock = Any()

    private var accessToken: String? = null
    private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var reconnect: Job? = null
    private var manuallyDisconnected = true
    private var closed = false
    private var generation = 0L
    private var reconnectAttempt = 0
    private var connectedAtNanos = 0L
    private var waitingForGapFill = false

    override fun connect(accessToken: String) {
        requireR14CompactJwt(accessToken)
        synchronized(lock) {
            check(!closed) { "Realtime client is closed" }
            if (this.accessToken != accessToken) {
                tracker.reset()
                reconnectAttempt = 0
            }
            this.accessToken = accessToken
            manuallyDisconnected = false
            waitingForGapFill = false
            openLocked()
        }
    }

    override fun disconnect() {
        synchronized(lock) {
            manuallyDisconnected = true
            generation += 1
            reconnect?.cancel()
            reconnect = null
            heartbeat?.cancel()
            heartbeat = null
            socket?.close(1000, "client disconnect")
            socket = null
        }
    }

    override fun completeGapFill(
        resumeFromServerSequence: Long,
        refreshedScopes: Set<R14RealtimeScope>,
    ): Boolean = synchronized(lock) {
        if (!tracker.completeGap(resumeFromServerSequence, refreshedScopes)) return@synchronized false
        waitingForGapFill = false
        reconnectAttempt = 0
        if (!manuallyDisconnected && !closed) openLocked()
        true
    }

    override fun close() {
        disconnect()
        synchronized(lock) { closed = true }
        scope.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun openLocked() {
        if (closed || manuallyDisconnected || waitingForGapFill || socket != null) return
        val token = accessToken ?: return
        val request = r14WebSocketRequest(endpoint, token, tracker.highestContiguousSequence)
        val listenerGeneration = ++generation
        socket = client.newWebSocket(request, Listener(listenerGeneration))
    }

    private fun scheduleReconnect(listenerGeneration: Long) {
        synchronized(lock) {
            if (listenerGeneration != generation || manuallyDisconnected || closed || waitingForGapFill) return
            socket = null
            heartbeat?.cancel()
            heartbeat = null
            reconnect?.cancel()
            val stableSeconds = if (connectedAtNanos == 0L) 0L
            else (System.nanoTime() - connectedAtNanos) / 1_000_000_000L
            if (stableSeconds >= 60) reconnectAttempt = 0
            val delayMs = r14ReconnectDelayMillis(reconnectAttempt++, randomLong)
            reconnect = scope.launch {
                delay(delayMs)
                synchronized(lock) {
                    reconnect = null
                    if (listenerGeneration == generation) openLocked()
                }
            }
        }
    }

    private fun startHeartbeat(webSocket: WebSocket, listenerGeneration: Long) {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (isActive) {
                delay(25_000)
                synchronized(lock) {
                    if (listenerGeneration != generation || socket !== webSocket) return@launch
                }
                if (!webSocket.send(systemPing())) {
                    webSocket.cancel()
                    return@launch
                }
            }
        }
    }

    private inner class Listener(
        private val listenerGeneration: Long,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isR14SelectedSubprotocol(response.header("Sec-WebSocket-Protocol"))) {
                webSocket.close(4400, "invalid subprotocol")
                return
            }
            synchronized(lock) {
                if (listenerGeneration != generation || manuallyDisconnected || closed) {
                    webSocket.close(1000, "stale connection")
                    return
                }
                socket = webSocket
                connectedAtNanos = System.nanoTime()
                startHeartbeat(webSocket, listenerGeneration)
            }
            mutableEvents.tryEmit(R14RealtimeEvent.Connected)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.toByteArray(Charsets.UTF_8).size > R14_MAX_PAYLOAD_BYTES) {
                webSocket.close(4400, "invalid event")
                return
            }
            val decision = runCatching {
                synchronized(lock) { tracker.accept(decodeR14RealtimeEnvelope(text)) }
            }.getOrElse {
                webSocket.close(4400, "invalid event")
                return
            }
            when (decision) {
                is R14SequenceDecision.Deliver -> {
                    decision.acknowledgement?.let(webSocket::send)
                    decision.event?.let(mutableEvents::tryEmit)
                }
                is R14SequenceDecision.Duplicate -> decision.acknowledgement?.let(webSocket::send)
                is R14SequenceDecision.Gap -> {
                    webSocket.close(4409, "sequence gap")
                }
                is R14SequenceDecision.RestGap -> {
                    synchronized(lock) {
                        waitingForGapFill = true
                        heartbeat?.cancel()
                        heartbeat = null
                        socket = null
                    }
                    mutableEvents.tryEmit(decision.gap)
                    webSocket.close(1000, "rest gap fill")
                }
                R14SequenceDecision.ReplayComplete -> reconnectAttempt = 0
                R14SequenceDecision.SessionInvalidated -> {
                    synchronized(lock) { manuallyDisconnected = true }
                    mutableEvents.tryEmit(R14RealtimeEvent.SessionInvalidated)
                    webSocket.close(4401, "session invalid")
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            mutableEvents.tryEmit(R14RealtimeEvent.Disconnected)
            scheduleReconnect(listenerGeneration)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            mutableEvents.tryEmit(R14RealtimeEvent.Disconnected)
            scheduleReconnect(listenerGeneration)
        }
    }
}

fun validateR14WebSocketBaseUrl(value: String): String {
    val uri = runCatching { URI(value) }.getOrNull()
    require(
        uri?.scheme == "wss" &&
            !uri.host.isNullOrBlank() &&
            !uri.host.endsWith(".invalid") &&
            uri.userInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            (uri.path.isNullOrBlank() || uri.path == "/")
    ) { "Unsafe WebSocket endpoint" }
    return value.trimEnd('/')
}

internal fun r14WebSocketRequest(
    endpoint: String,
    accessToken: String,
    lastServerSequence: Long,
): Request {
    require(lastServerSequence >= 0)
    requireR14CompactJwt(accessToken)
    val url = "$endpoint?lastServerSequence=$lastServerSequence"
    return Request.Builder()
        .url(url)
        .header(
            "Sec-WebSocket-Protocol",
            "$R14_WEBSOCKET_SUBPROTOCOL, $R14_ACCESS_SUBPROTOCOL_PREFIX$accessToken",
        )
        .build()
}

internal fun r14ReconnectDelayMillis(
    attempt: Int,
    randomLong: (Long) -> Long,
): Long {
    require(attempt >= 0)
    val exponent = min(attempt, 16)
    val cap = min(30_000L, 500L * (1L shl exponent))
    return randomLong(cap + 1).coerceIn(0, cap)
}

internal fun decodeR14RealtimeEnvelope(text: String): R14RealtimeEnvelope {
    val root = HhyNetworkJson.value.parseToJsonElement(text).jsonObject
    val eventId = root.requiredString("eventId")
    UUID.fromString(eventId)
    val occurredAt = root.requiredString("occurredAt")
    Instant.parse(occurredAt)
    val eventType = root.requiredString("eventType")
    require(eventType in R14_SERVER_EVENT_TYPES)
    val serverSequence = root["serverSequence"]?.jsonPrimitive?.longOrNull
    require(serverSequence == null || serverSequence >= 1)
    val ackRequired = root["ackRequired"]?.jsonPrimitive?.booleanOrNull ?: false
    val conversationId = root["conversationId"]?.jsonPrimitive?.contentOrNull
    val payload = root["payload"]?.jsonObject ?: error("Missing payload")
    return R14RealtimeEnvelope(
        eventId = eventId,
        eventType = eventType,
        occurredAt = occurredAt,
        serverSequence = serverSequence,
        conversationId = conversationId,
        ackRequired = ackRequired,
        payload = payload,
    )
}

private fun R14RealtimeEnvelope.toPublicEvent(): R14RealtimeEvent? = when (eventType) {
    "chat.message.ack", "chat.message.new", "chat.read.updated" -> {
        val id = conversationId ?: payload.requiredString("conversationId")
        R14RealtimeEvent.ChatChanged(id, eventType)
    }
    else -> null
}

internal fun isR14SelectedSubprotocol(value: String?): Boolean =
    value == R14_WEBSOCKET_SUBPROTOCOL

private fun deliveryAcknowledgement(envelope: R14RealtimeEnvelope): String {
    val sequence = requireNotNull(envelope.serverSequence)
    return HhyNetworkJson.value.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("eventId", UUID.randomUUID().toString())
            put("eventType", "system.delivery.ack")
            put("occurredAt", Instant.now().toString())
            putJsonObject("payload") {
                put("eventId", envelope.eventId)
                put("serverSequence", sequence)
            }
        },
    )
}

private fun systemPing(): String = HhyNetworkJson.value.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put("eventId", UUID.randomUUID().toString())
        put("eventType", "system.ping")
        put("occurredAt", Instant.now().toString())
        putJsonObject("payload") { put("clientTime", Instant.now().toString()) }
    },
)

private fun requireR14CompactJwt(value: String) {
    require(R14_COMPACT_JWT.matches(value)) { "Invalid access credential" }
    require('=' !in value && value.none(Char::isWhitespace)) { "Invalid access credential" }
}

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: error("Missing $name")

private fun JsonObject.requiredNonNegativeLong(name: String): Long =
    (this[name]?.jsonPrimitive?.longOrNull ?: error("Missing $name")).also { require(it >= 0) }

private fun JsonObject.requiredScopes(): Set<R14RealtimeScope> =
    (this["affectedScopes"]?.jsonArray ?: error("Missing affectedScopes"))
        .map { R14RealtimeScope.valueOf(it.jsonPrimitive.content) }
        .also { require(it.distinct().size == it.size && it.size <= 2) }
        .toSet()

private val R14_SERVER_EVENT_TYPES = setOf(
    "chat.message.ack",
    "chat.message.new",
    "chat.read.updated",
    "chat.typing",
    "notification.new",
    "system.kickout",
    "system.pong",
    "system.resume",
)
