package ru.yakut54.ktoto.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.koin.compose.koinInject
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore
import ru.yakut54.ktoto.ui.auth.AuthScreen
import ru.yakut54.ktoto.ui.chat.ChatScreen
import ru.yakut54.ktoto.ui.conversations.ConversationsScreen

object Routes {
    const val AUTH = "auth"
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{convId}/{convName}/{userId}"

    fun chat(convId: String, convName: String, userId: String) =
        "chat/$convId/${convName.ifBlank { "Чат" }}/$userId"
}

@Composable
fun AppNavigation() {
    val tokenStore: TokenStore = koinInject()
    val socketManager: SocketManager = koinInject()
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
                onConversationClick = { conv, userId ->
                    token?.let { socketManager.connect(it) }
                    navController.navigate(
                        Routes.chat(conv.id, conv.name ?: "Чат", userId)
                    )
                },
                onLogout = {
                    socketManager.disconnect()
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.CONVERSATIONS) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("convId") { type = NavType.StringType },
                navArgument("convName") { type = NavType.StringType },
                navArgument("userId") { type = NavType.StringType },
            ),
        ) { back ->
            val convId = back.arguments?.getString("convId") ?: return@composable
            val convName = back.arguments?.getString("convName") ?: ""
            val userId = back.arguments?.getString("userId") ?: ""
            ChatScreen(
                conversationId = convId,
                conversationName = convName,
                currentUserId = userId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
