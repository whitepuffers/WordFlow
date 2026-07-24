package com.wordflow.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wordflow.app.di.AppContainer
import com.wordflow.app.tts.SoundPlayer
import com.wordflow.app.ui.navigation.AppNavHost
import com.wordflow.app.ui.navigation.Routes

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

/** 全局开关：震动反馈 / 音效 */
val LocalHapticsEnabled = compositionLocalOf { true }
val LocalSoundEnabled = compositionLocalOf { true }

/** 统一的震动反馈入口：尊重用户设置 */
@Composable
fun rememberAppHaptics(): (HapticFeedbackType) -> Unit {
    val enabled = LocalHapticsEnabled.current
    val haptics = LocalHapticFeedback.current
    return remember(enabled, haptics) {
        { type -> if (enabled) haptics.performHapticFeedback(type) }
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabItem(Routes.HOME, "首页", Icons.Default.Home),
    TabItem(Routes.QUIZ, "练习", Icons.Default.School),
    TabItem(Routes.STATS, "统计", Icons.Default.BarChart),
    TabItem(Routes.SETTINGS, "设置", Icons.Default.Settings)
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val container = LocalAppContainer.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TABS.map { it.route }

    // 退出应用时释放提示音资源
    DisposableEffect(Unit) {
        onDispose { SoundPlayer.release() }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier,
            contentPadding = innerPadding
        )
    }
}
