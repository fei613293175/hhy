package cc.orbexa.hhy.network

import kotlinx.coroutines.CancellationException

data class StartupGateRequest(
    val versionCode: Long,
    val versionName: String,
    val channel: String,
    val environment: String,
) {
    init {
        require(versionCode > 0) { "versionCode must be positive" }
        require(versionName.isNotBlank()) { "versionName is required" }
        require(channel.isNotBlank()) { "channel is required" }
        require(environment in setOf("DEV", "TEST", "STAGING", "PROD")) {
            "environment is invalid"
        }
    }

    fun versionRequest() = VersionCheckRequest(
        platform = "ANDROID",
        versionCode = versionCode,
        versionName = versionName,
        channel = channel,
        environment = environment,
    )
}

sealed interface StartupGateState {
    data object Loading : StartupGateState
    data class Maintenance(val message: String) : StartupGateState
    data class UpdateRequired(
        val policy: VersionPolicy,
        val page: PublicPage?,
        val forced: Boolean,
    ) : StartupGateState
    data class Ready(val capabilities: PlatformCapabilities) : StartupGateState
    data class Unavailable(val requestId: String?) : StartupGateState
}

class StartupGate(private val api: HhyPublicApi) {
    suspend fun evaluate(request: StartupGateRequest): StartupGateState {
        return try {
            val status = api.platformStatus().data
            if (status.maintenance) {
                return StartupGateState.Maintenance(
                    status.maintenanceMessage.ifBlank { "系统维护中" },
                )
            }

            val policy = api.versionCheck(request.versionRequest()).data
            if (policy.updateType == UpdateType.FORCED || policy.updateType == UpdateType.OPTIONAL) {
                val page = try {
                    api.latestApp().data
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    null
                }
                StartupGateState.UpdateRequired(
                    policy = policy,
                    page = page,
                    forced = policy.updateType == UpdateType.FORCED,
                )
            } else {
                StartupGateState.Ready(status.capabilities)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HhyApiException) {
            StartupGateState.Unavailable(exception.requestId)
        } catch (_: Exception) {
            StartupGateState.Unavailable(null)
        }
    }
}
