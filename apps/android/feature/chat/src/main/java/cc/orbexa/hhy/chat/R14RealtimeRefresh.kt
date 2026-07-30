package cc.orbexa.hhy.chat

import cc.orbexa.hhy.network.ContractR14Api
import cc.orbexa.hhy.network.R07CallResult

sealed interface R14AuthoritativeRefreshResult {
    data object Success : R14AuthoritativeRefreshResult
    data class Failure(val statusCode: Int?) : R14AuthoritativeRefreshResult
}

suspend fun refreshR14ChatAuthoritatively(
    api: ContractR14Api,
    accessToken: String,
): R14AuthoritativeRefreshResult {
    val conversationIds = linkedSetOf<String>()
    var conversationCursor: String? = null
    val seenConversationCursors = mutableSetOf<String>()
    do {
        val result = api.conversations(
            accessToken = accessToken,
            pageSize = 100,
            cursor = conversationCursor,
            sort = "updatedAt:desc",
        )
        val page = when (result) {
            is R07CallResult.Success -> result.data
            is R07CallResult.Failure -> return R14AuthoritativeRefreshResult.Failure(result.statusCode)
        }
        conversationIds += page.items.map { it.id }
        conversationCursor = page.page.nextCursor
        if (page.page.canLoadMore()) {
            if (conversationCursor.isNullOrBlank() || !seenConversationCursors.add(conversationCursor)) {
                return R14AuthoritativeRefreshResult.Failure(502)
            }
        }
    } while (page.page.canLoadMore())

    for (conversationId in conversationIds) {
        var messageCursor: String? = null
        val seenMessageCursors = mutableSetOf<String>()
        do {
            val result = api.messages(
                accessToken = accessToken,
                conversationId = conversationId,
                pageSize = 100,
                cursor = messageCursor,
            )
            val page = when (result) {
                is R07CallResult.Success -> result.data
                is R07CallResult.Failure -> return R14AuthoritativeRefreshResult.Failure(result.statusCode)
            }
            messageCursor = page.page.nextCursor
            if (page.page.canLoadMore()) {
                if (messageCursor.isNullOrBlank() || !seenMessageCursors.add(messageCursor)) {
                    return R14AuthoritativeRefreshResult.Failure(502)
                }
            }
        } while (page.page.canLoadMore())
    }
    return R14AuthoritativeRefreshResult.Success
}
