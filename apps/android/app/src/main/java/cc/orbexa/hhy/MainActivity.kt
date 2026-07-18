package cc.orbexa.hhy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.network.StartupGate
import cc.orbexa.hhy.network.StartupGateRequest
import cc.orbexa.hhy.network.UrlConnectionHhyPublicApi
import cc.orbexa.hhy.shell.HhyShellScreen
import cc.orbexa.hhy.startup.StartupGateScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HhyTheme {
                val gate = remember {
                    StartupGate(UrlConnectionHhyPublicApi(BuildConfig.API_BASE_URL))
                }
                val request = remember {
                    StartupGateRequest(
                        versionCode = BuildConfig.VERSION_CODE.toLong(),
                        versionName = BuildConfig.VERSION_NAME,
                        channel = BuildConfig.APP_CHANNEL,
                        environment = BuildConfig.APP_ENVIRONMENT,
                    )
                }
                StartupGateScreen(gate = gate, request = request) {
                    HhyShellScreen(
                        versionName = BuildConfig.VERSION_NAME,
                        buildType = BuildConfig.BUILD_TYPE,
                        apiBaseUrl = BuildConfig.API_BASE_URL,
                        contractVersion = BuildConfig.CONTRACT_VERSION,
                    )
                }
            }
        }
    }
}
