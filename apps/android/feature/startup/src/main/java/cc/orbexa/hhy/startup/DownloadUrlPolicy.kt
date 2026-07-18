package cc.orbexa.hhy.startup

import java.net.URI
import java.util.Locale

/** Allows update downloads only from the two APK origins registered in DOMAIN_PLAN.yaml. */
internal object DownloadUrlPolicy {
    private val trustedHosts = setOf(
        "download.orbexa.cc",
        "stg-download.orbexa.cc",
    )

    fun trustedOrNull(value: String): String? = runCatching {
        val candidate = value.trim()
        val uri = URI(candidate)
        val host = uri.host?.lowercase(Locale.ROOT)
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                host in trustedHosts &&
                uri.userInfo == null &&
                uri.fragment == null &&
                (uri.port == -1 || uri.port == 443),
        ) { "Update download URL is not trusted" }
        candidate
    }.getOrNull()
}
