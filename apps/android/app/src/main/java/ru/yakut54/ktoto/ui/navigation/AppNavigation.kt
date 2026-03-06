package ru.yakut54.ktoto.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore
import ru.yakut54.ktoto.ui.auth.AuthScreen
import ru.yakut54.ktoto.ui.chat.ChatScreen
import ru.yakut54.ktoto.ui.conversations.ConversationsScreen
import ru.yakut54.ktoto.ui.newchat.NewChatScreen

object Routes {
    const val AUTH = "auth"
    const val CONVERSATIONS = "conversations"
    const val NEW_CHAT = "new_chat"
    const val CHAT = "chat/{convId}/{convName}/{userId}"

    fun chat(convId: String, convName: String, userId: String) =
        "chat/$convId/${convName.ifBlank { "Чат" }}/$userId"
}

@Composable
fun AppNavigation() {
    val tokenStore: TokenStore = koinInject()
    val socketManager: SocketManager = koinInject()
    val token by tokenStore.accessToken.collectAsState(initial = null)
    val currentUserId by tokenStore.userId.collectAsState(initial = "")

    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()

    // On app start: read saved token from DataStore and skip login if valid
    LaunchedEffect(Unit) {
        val savedToken = tokenStore.accessToken.first()
        if (!savedToken.isNullOrEmpty()) {
            navController.navigate(Routes.CONVERSATIONS) {
                popUpTo(Routes.AUTH) { inclusive = true }
            }
        }
    }

    // Auto-redirect to login when token is cleared at runtime (refresh token expired / logout)
    LaunchedEffect(token) {
        val route = currentRoute?.destination?.route ?: return@LaunchedEffect
        if (token == null && route != Routes.AUTH) {
            socketManager.disconnect()
            navController.navigate(Routes.AUTH) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.AUTH,
        enterTransition = {
            slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(220))
        },
        exitTransition = {
            slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(220))
        },
        popExitTransition = {
            slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(200))
        },
    ) {
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
                    navController.navigate(Routes.chat(conv.id, conv.name ?: "Чат", userId))
                },
                onNewChat = { navController.navigate(Routes.NEW_CHAT) },
                onLogout = {
                    socketManager.disconnect()
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.CONVERSATIONS) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.NEW_CHAT) {
            NewChatScreen(
                onBack = { navController.popBackStack() },
                onCreated = { convId, convName ->
                    token?.let { socketManager.connect(it) }
                    navController.navigate(Routes.chat(convId, convName, currentUserId)) {
                        popUpTo(Routes.NEW_CHAT) { inclusive = true }
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
                onNavigateToChat = { targetConvId, targetConvName ->
                    navController.navigate(Routes.chat(targetConvId, targetConvName, userId))
                },
            )
        }
    }
}
