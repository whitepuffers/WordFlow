package com.wordflow.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wordflow.app.ui.home.HomeRoute
import com.wordflow.app.ui.quiz.QuizRoute
import com.wordflow.app.ui.settings.SettingsRoute
import com.wordflow.app.ui.stats.StatsRoute
import com.wordflow.app.ui.study.DeckMode
import com.wordflow.app.ui.study.StudyRoute

object Routes {
    const val HOME = "home"
    const val QUIZ = "quiz"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val STUDY = "study/{mode}"

    fun study(mode: DeckMode) = "study/${mode.raw}"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier.padding(contentPadding)
    ) {
        composable(Routes.HOME) {
            HomeRoute(
                onStartStudy = { navController.navigate(Routes.study(DeckMode.NEW)) },
                onStartReview = { navController.navigate(Routes.study(DeckMode.REVIEW)) },
                onStartQuiz = { navController.navigate(Routes.QUIZ) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.QUIZ) {
            QuizRoute()
        }
        composable(Routes.STATS) {
            StatsRoute(onGoStudy = { navController.navigate(Routes.study(DeckMode.NEW)) })
        }
        composable(Routes.SETTINGS) {
            SettingsRoute()
        }
        composable(
            route = Routes.STUDY,
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { entry ->
            val mode = DeckMode.from(entry.arguments?.getString("mode"))
            StudyRoute(mode = mode, onExit = { navController.popBackStack() })
        }
    }
}
