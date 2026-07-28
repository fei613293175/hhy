package cc.orbexa.hhy.chat

import cc.orbexa.hhy.network.ChatConversationPageResource
import cc.orbexa.hhy.network.ChatConversationResource
import cc.orbexa.hhy.network.ChatLastMessageResource
import cc.orbexa.hhy.network.PublisherSummaryResource
import cc.orbexa.hhy.network.R07CallResult
import cc.orbexa.hhy.network.R07PageMeta
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class R14ConversationListStateTest {
    @Test
    fun refreshAndPaginationPreserveServerOrderAndReplaceDuplicatesInPlace() {
        var state = R14ConversationListState().loadStarted("")
        state = state.loaded(
            state.requestGeneration,
            "",
            page(listOf(conversation("a", "旧A"), conversation("b", "B")), nextCursor = "next", hasMore = true),
        )
        state = state.loadStarted("", append = true)
        state = state.loaded(
            state.requestGeneration,
            "",
            page(listOf(conversation("b", "新B"), conversation("c", "C"))),
            append = true,
        )

        assertEquals(listOf("a", "b", "c"), state.items.map { it.id })
        assertEquals("新B", state.items[1].lastMessage?.preview)
        assertEquals(R14ConversationPhase.CONTENT, state.phase)
    }

    @Test
    fun staleSearchResponseCannotReplaceTheNewQuery() {
        var state = R14ConversationListState().loadStarted("旧关键词")
        val oldGeneration = state.requestGeneration
        state = state.loadStarted("新关键词")
        val current = state.loaded(
            state.requestGeneration,
            "新关键词",
            page(listOf(conversation("new", "新结果"))),
        )
        val stale = current.loaded(oldGeneration, "旧关键词", page(listOf(conversation("old", "旧结果"))))

        assertEquals(listOf("new"), stale.items.map { it.id })
        assertEquals("新关键词", stale.activeQuery)
    }

    @Test
    fun syncingMergesOutOfOrderDuplicatesWithoutInventingLocalSorting() {
        val initial = R14ConversationListState(items = listOf(conversation("a", "A"), conversation("b", "B")))
        val syncing = initial.syncStarted()
        val synced = syncing.synced(listOf(conversation("b", "B2"), conversation("c", "C"), conversation("b", "B3")))

        assertEquals(R14ConversationPhase.SYNCING, syncing.phase)
        assertEquals(listOf("a", "b", "c"), synced.items.map { it.id })
        assertEquals("B3", synced.items[1].lastMessage?.preview)
    }

    @Test
    fun transientFailureRetriesAtMostTwice() = runBlocking {
        var calls = 0
        val pauses = mutableListOf<Long>()
        val result = r14CallWithRetry(
            call = {
                calls += 1
                R07CallResult.Failure(statusCode = null)
            },
            pause = { pauses += it },
        )

        assertTrue(result is R07CallResult.Failure)
        assertEquals(3, calls)
        assertEquals(listOf(250L, 500L), pauses)
    }

    @Test
    fun frozen400And404ErrorsUseBusinessMessages() {
        assertEquals("关键词过长", R07CallResult.Failure(400, fieldErrors = mapOf("keyword" to "关键词过长")).r14ConversationMessage())
        assertEquals("会话列表暂不可用", R07CallResult.Failure(404).r14ConversationMessage())
    }

    @Test
    fun conversationTimeMatchesTheFrozenListHierarchy() {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.of(2026, 7, 28)
        assertEquals("10:30", conversationTimeLabel("2026-07-28T02:30:00Z", zone, today))
        assertEquals("昨天", conversationTimeLabel("2026-07-27T02:30:00Z", zone, today))
        assertEquals("07-01", conversationTimeLabel("2026-07-01T02:30:00Z", zone, today))
    }

    private fun page(
        items: List<ChatConversationResource>,
        nextCursor: String? = null,
        hasMore: Boolean = false,
    ) = ChatConversationPageResource(
        items,
        R07PageMeta(page = 1, pageSize = 20, total = items.size.toString(), nextCursor = nextCursor, hasMore = hasMore.toString()),
    )

    private fun conversation(id: String, preview: String) = ChatConversationResource(
        id = id,
        peer = PublisherSummaryResource("user_$id", "用户$id", verified = false),
        lastMessage = ChatLastMessageResource("message_$id", "TEXT", preview, createdAt = "2026-07-28T02:30:00Z"),
        unreadCount = 0,
        updatedAt = "2026-07-28T02:30:00Z",
        version = 1,
    )
}
