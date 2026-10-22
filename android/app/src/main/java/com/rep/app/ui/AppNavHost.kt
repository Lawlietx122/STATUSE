package com.rep.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rep.app.AppContainer
import com.rep.app.ui.chat.ChatScreen
import com.rep.app.ui.chat.ChatViewModel
import com.rep.app.ui.post.PostScreen
import com.rep.app.ui.post.PostViewModel
import com.rep.app.ui.prompts.PromptsScreen
import com.rep.app.ui.scenario.ScenarioScreen
import com.rep.app.ui.scenario.ScenarioViewModel
import com.rep.app.ui.scenarios.ScenariosScreen
import com.rep.app.ui.scenarios.ScenariosViewModel
import com.rep.app.ui.settings.ModelSettingsScreen
import com.rep.app.ui.settings.ModelSettingsViewModel
import com.rep.app.ui.tokens.TokenStatsScreen
import com.rep.app.ui.tokens.TokenStatsViewModel

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create() } })

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })

    NavHost(navController = nav, startDestination = "scenarios") {
        composable("scenarios") {
            ScenariosScreen(
                vm = appViewModel { ScenariosViewModel(container.db) },
                onOpen = { nav.navigate("scenario/$it") },
                onModels = { nav.navigate("models") },
                onPrompts = { nav.navigate("prompts") },
                onTokens = { nav.navigate("tokens") },
            )
        }
        composable("scenario/{id}", arguments = idArg) { entry ->
            val id = entry.arguments!!.getLong("id")
            ScenarioScreen(
                vm = appViewModel("scenario$id") { ScenarioViewModel(id, container.db, container.engine) },
                onBack = { nav.popBackStack() },
                onOpenPost = { nav.navigate("post/$it") },
                onOpenChat = { nav.navigate("chat/$it") },
                onTokens = { nav.navigate("tokens") },
            )
        }
        composable("post/{id}", arguments = idArg) { entry ->
            val id = entry.arguments!!.getLong("id")
            PostScreen(
                vm = appViewModel("post$id") { PostViewModel(id, container.db, container.engine) },
                onBack = { nav.popBackStack() },
            )
        }
        composable("chat/{id}", arguments = idArg) { entry ->
            val id = entry.arguments!!.getLong("id")
            ChatScreen(
                vm = appViewModel("chat$id") { ChatViewModel(id, container.db, container.engine) },
                onBack = { nav.popBackStack() },
            )
        }
        composable("models") {
            ModelSettingsScreen(
                vm = appViewModel { ModelSettingsViewModel(container.settings, container.ai) },
                onBack = { nav.popBackStack() },
            )
        }
        composable("prompts") {
            PromptsScreen(store = container.prompts, onBack = { nav.popBackStack() })
        }
        composable("tokens") {
            TokenStatsScreen(
                vm = appViewModel { TokenStatsViewModel(container.db.tokenUsageDao()) },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
