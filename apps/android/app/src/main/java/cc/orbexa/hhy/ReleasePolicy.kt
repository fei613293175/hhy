package cc.orbexa.hhy

import java.net.URI

object ReleasePolicy {
    const val VERSION_CODE: Int = 10201
    const val VERSION_NAME: String = "1.2.1"
    const val CONTRACT_VERSION: String = "1.2.1"

    fun isPublishableApiBaseUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" &&
            !uri.host.isNullOrBlank() &&
            !uri.host.endsWith(".invalid") &&
            uri.userInfo == null &&
            uri.fragment == null
    }.getOrDefault(false)
}
