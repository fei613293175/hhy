package cc.orbexa.hhy.network

private val validRequestId = Regex("[A-Za-z0-9_-]{8,64}")

object RequestIdPolicy {
    fun isValid(value: String?): Boolean = value != null && validRequestId.matches(value)
}
