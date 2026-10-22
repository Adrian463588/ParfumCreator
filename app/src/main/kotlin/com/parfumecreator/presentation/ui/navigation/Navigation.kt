package com.parfumecreator.presentation.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.parfumecreator.domain.model.*
import com.parfumecreator.presentation.ui.chat.ChatScreen
import com.parfumecreator.presentation.ui.chat.ChatViewModel
import com.parfumecreator.presentation.ui.documents.DocumentsScreen
import com.parfumecreator.presentation.ui.documents.DocumentsViewModel
import com.parfumecreator.presentation.ui.download.DownloadScreen
import com.parfumecreator.presentation.ui.download.DownloadViewModel
import com.parfumecreator.presentation.ui.formula.FormulaScreen
import com.parfumecreator.presentation.ui.formula.FormulaViewModel
import com.parfumecreator.presentation.ui.ingredients.IngredientLibraryScreen
import com.parfumecreator.presentation.ui.ingredients.IngredientViewModel
import com.parfumecreator.presentation.ui.settings.SettingsScreen
import com.parfumecreator.presentation.ui.settings.SettingsViewModel
import com.parfumecreator.presentation.ui.theme.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Formula : Screen("formula", "Formula", Icons.Default.Science)
    object Chat : Screen("chat", "AI Chat", Icons.Default.Psychology)
    object Library : Screen("library", "Library", Icons.AutoMirrored.Filled.MenuBook)
    object Documents : Screen("documents", "Documents", Icons.Default.Folder)
    object Bundle : Screen("bundle", "Bundle", Icons.Default.CloudDownload)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Formula,
    Screen.Chat,
    Screen.Library,
    Screen.Documents,
    Screen.Bundle
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val formulaViewModel: FormulaViewModel = hiltViewModel()
    val chatViewModel: ChatViewModel = hiltViewModel()
    val ingredientViewModel: IngredientViewModel = hiltViewModel()
    val documentsViewModel: DocumentsViewModel = hiltViewModel()
    val downloadViewModel: DownloadViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isImeVisible = WindowInsets.isImeVisible
    val isChatScreen = currentRoute == Screen.Chat.route
    val showBottomBar = !isChatScreen || !isImeVisible

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                NavigationBar(
                    containerColor = PerfumeSurface,
                    contentColor = PerfumeOnBackground,
                    tonalElevation = 6.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (selected) PerfumePrimary else PerfumeOutline
                                )
                            },
                            label = {
                                Text(
                                    screen.title,
                                    fontSize = 10.sp,
                                    color = if (selected) PerfumePrimary else PerfumeOutline
                                )
                            },
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    val isFromSubscreen = bottomNavItems.none { it.route == currentRoute }
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = !isFromSubscreen
                                        }
                                        launchSingleTop = true
                                        restoreState = !isFromSubscreen
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = PerfumePrimaryContainer
                            )
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = PerfumeBackground
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Formula.route,
            modifier = Modifier.padding(bottom = if (showBottomBar) paddingValues.calculateBottomPadding() else 0.dp)
        ) {
            composable(Screen.Formula.route) {
                FormulaScreen(
                    viewModel = formulaViewModel,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Chat.route) {
                ChatScreen(
                    viewModel = chatViewModel,
                    onNavigateToFormulaWithRecommendation = { rec: RecommendedFormula ->
                        formulaViewModel.importRecommendation(rec)
                        navController.navigate(Screen.Formula.route)
                    },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.Library.route) {
                IngredientLibraryScreen(
                    viewModel = ingredientViewModel,
                    onAddToFormula = { ingredient ->
                        formulaViewModel.addIngredient(ingredient)
                        navController.navigate(Screen.Formula.route)
                    },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.Documents.route) {
                DocumentsScreen(
                    viewModel = documentsViewModel,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Bundle.route) {
                DownloadScreen(
                    viewModel = downloadViewModel,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToChat = { navController.navigate(Screen.Chat.route) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
