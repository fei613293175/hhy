package cc.orbexa.hhy.chat

import android.content.Context

interface R14BlockStateStore {
    fun isBlockedByMe(currentUserId: String, peerId: String): Boolean
    fun setBlockedByMe(currentUserId: String, peerId: String, blocked: Boolean): Boolean
}

class SharedPreferencesR14BlockStateStore(context: Context) : R14BlockStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun isBlockedByMe(currentUserId: String, peerId: String): Boolean =
        preferences.getBoolean(blockStateKey(currentUserId, peerId), false)

    override fun setBlockedByMe(currentUserId: String, peerId: String, blocked: Boolean): Boolean {
        val editor = preferences.edit()
        if (blocked) editor.putBoolean(blockStateKey(currentUserId, peerId), true)
        else editor.remove(blockStateKey(currentUserId, peerId))
        return editor.commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "hhy_r14_blocked_by_me"
    }
}

internal fun blockStateKey(currentUserId: String, peerId: String): String {
    require(currentUserId.isNotBlank())
    require(peerId.isNotBlank())
    return "${currentUserId.length}:$currentUserId:${peerId.length}:$peerId"
}
