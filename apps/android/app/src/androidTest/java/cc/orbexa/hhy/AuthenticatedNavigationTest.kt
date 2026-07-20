package cc.orbexa.hhy

import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import cc.orbexa.hhy.designsystem.HhyMotion
import org.junit.Rule
import org.junit.Test

class AuthenticatedNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun poppingChildReturnsToItsRealSource() {
        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            NavHost(
                navController = navController,
                startDestination = "profile",
                enterTransition = { HhyMotion.forwardEnter() },
                exitTransition = { HhyMotion.forwardExit() },
                popEnterTransition = { HhyMotion.backwardEnter() },
                popExitTransition = { HhyMotion.backwardExit() },
            ) {
                composable("profile") { Text("我的") }
                composable("identity") { Text("实名认证") }
            }
        }

        composeRule.onNodeWithText("我的").assertIsDisplayed()
        composeRule.runOnUiThread { navController.navigate("identity") }
        composeRule.onNodeWithText("实名认证").assertIsDisplayed()
        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.onNodeWithText("我的").assertIsDisplayed()
    }
}
