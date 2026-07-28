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
    fun meChildReturnsToMeAndTopLevelNavigationDoesNotDuplicateDestinations() {
        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            NavHost(
                navController = navController,
                startDestination = AuthenticatedRoute.Home,
                enterTransition = { HhyMotion.forwardEnter() },
                exitTransition = { HhyMotion.forwardExit() },
                popEnterTransition = { HhyMotion.backwardEnter() },
                popExitTransition = { HhyMotion.backwardExit() },
            ) {
                composable<AuthenticatedRoute.Home> { Text("首页") }
                composable<AuthenticatedRoute.Messages> { Text("消息") }
                composable<AuthenticatedRoute.Me> { Text("我的") }
                composable<AuthenticatedRoute.Profile> { Text("个人资料") }
            }
        }

        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.runOnUiThread {
            navController.navigate(AuthenticatedRoute.Me) {
                popUpTo<AuthenticatedRoute.Home> { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            navController.navigate(AuthenticatedRoute.Profile)
        }
        composeRule.onNodeWithText("个人资料").assertIsDisplayed()
        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.onNodeWithText("我的").assertIsDisplayed()
        composeRule.runOnUiThread {
            navController.navigate(AuthenticatedRoute.Home) {
                popUpTo<AuthenticatedRoute.Home> { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            navController.navigate(AuthenticatedRoute.Me) {
                popUpTo<AuthenticatedRoute.Home> { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            navController.navigate(AuthenticatedRoute.Messages) {
                popUpTo<AuthenticatedRoute.Home> { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        composeRule.onNodeWithText("消息").assertIsDisplayed()
    }

    @Test
    fun chatDetailReturnsToItsRealBusinessSource() {
        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            NavHost(
                navController = navController,
                startDestination = AuthenticatedRoute.Home,
                enterTransition = { HhyMotion.forwardEnter() },
                exitTransition = { HhyMotion.forwardExit() },
                popEnterTransition = { HhyMotion.backwardEnter() },
                popExitTransition = { HhyMotion.backwardExit() },
            ) {
                composable<AuthenticatedRoute.Home> { Text("首页") }
                composable<AuthenticatedRoute.Projects> { Text("项目列表") }
                composable<AuthenticatedRoute.ChatDetail> { Text("私聊") }
            }
        }

        composeRule.runOnUiThread {
            navController.navigate(AuthenticatedRoute.Projects)
            navController.navigate(AuthenticatedRoute.ChatDetail("conversation_42"))
        }
        composeRule.onNodeWithText("私聊").assertIsDisplayed()
        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.onNodeWithText("项目列表").assertIsDisplayed()
    }

    @Test
    fun chatDetailOpenedFromMessagesReturnsToMessagesForEveryBackSource() {
        lateinit var navController: TestNavHostController
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            NavHost(navController = navController, startDestination = AuthenticatedRoute.Home) {
                composable<AuthenticatedRoute.Home> { Text("首页") }
                composable<AuthenticatedRoute.Messages> { Text("会话列表") }
                composable<AuthenticatedRoute.ChatDetail> { Text("私聊") }
            }
        }

        composeRule.runOnUiThread {
            navController.navigate(AuthenticatedRoute.Messages)
            navController.navigate(AuthenticatedRoute.ChatDetail("conversation_42"))
        }
        composeRule.onNodeWithText("私聊").assertIsDisplayed()
        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.onNodeWithText("会话列表").assertIsDisplayed()
    }
}
