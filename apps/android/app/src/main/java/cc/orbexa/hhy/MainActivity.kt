package cc.orbexa.hhy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cc.orbexa.hhy.designsystem.HhyTheme
import cc.orbexa.hhy.shell.HhyShellScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HhyTheme {
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
