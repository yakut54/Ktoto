package ru.yakut54.ktoto.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject
import ru.yakut54.ktoto.data.store.TokenStore
import ru.yakut54.ktoto.ui.auth.AuthScreen
import ru.yakut54.ktoto.ui.conversations.ConversationsScreen

object Routes {
    const val AUTH = "auth"
    const val CONVERSATIONS = "conversations"
}

@Composable
fun AppNavigation() {
    val tokenStore: TokenStore = koinInject()
    val token by tokenStore.accessToken.collectAsState(initial = null)

    val navController = rememberNavController()
    val startDestination = if (token != null) Routes.CONVERSATIONS else Routes.AUTH

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.AUTH) {
            AuthScreen(onSuccess = {
                navController.navigate(Routes.CONVERSATIONS) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            })
        }
        composable(Routes.CONVERSATIONS) {
            ConversationsScreen(
                onConversationClick = { /* M.4 — chat screen */ },
                onLogout = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.CONVERSATIONS) { inclusive = true }
                    }
                },
            )
        }
    }
}
