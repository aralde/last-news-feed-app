package com.chronicle.newsfeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chronicle.newsfeed.ui.feed.FeedScreen
import com.chronicle.newsfeed.ui.feed.FeedViewModel
import com.chronicle.newsfeed.ui.reader.ArticleReaderScreen
import com.chronicle.newsfeed.ui.settings.SettingsScreen
import com.chronicle.newsfeed.ui.theme.ChronicleTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ChronicleTheme {
                val navController = rememberNavController()
                val feedViewModel: FeedViewModel = viewModel()
                val articles by feedViewModel.articles.collectAsState()
                val synthesizingArticleId by feedViewModel.synthesizingArticleId.collectAsState()

                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "feed"
                    ) {
                        composable("feed") {
                            FeedScreen(
                                onArticleClick = { article ->
                                    feedViewModel.toggleArticleRead(article)
                                    navController.navigate("reader/${article.id}")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                viewModel = feedViewModel
                            )
                        }

                        composable(
                            route = "reader/{articleId}",
                            arguments = listOf(navArgument("articleId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val articleId = backStackEntry.arguments?.getString("articleId") ?: ""
                            val article = articles.find { it.id == articleId }

                            if (article != null) {
                                ArticleReaderScreen(
                                    article = article,
                                    onBack = { navController.popBackStack() },
                                    onNarrate = { feedViewModel.narrateArticle(article) },
                                    onToggleFavorite = { feedViewModel.toggleArticleFavorite(article) },
                                    isSynthesizing = synthesizingArticleId == article.id
                                )
                            }
                        }

                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
