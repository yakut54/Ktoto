package ru.yakut54.ktoto.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.Conversation
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore
import ru.yakut54.ktoto.ui.auth.AuthScreen
import ru.yakut54.ktoto.ui.chat.ChatScreen
import ru.yakut54.ktoto.ui.conversations.ConversationsScreen
import ru.yakut54.ktoto.ui.newchat.NewChatScreen

data class SharePayload(
    val uri: Uri? = null,
    val text: String? = null,
    val mimeType: String? = null,
)

object Routes {
    const val AUTH = "auth"
    const val CONVERSATIONS = "conversations"
    const val NEW_CHAT = "new_chat"
    const val CHAT = "chat/{convId}/{convName}/{userId}/{otherId}"

    fun chat(convId: String, convName: String, userId: String, otherId: String = "") =
        "chat/$convId/${convName.ifBlank { "Чат" }}/$userId/${otherId.ifBlank { "_" }}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    pendingConversationId: StateFlow<String?> = MutableStateFlow(null),
    pendingShareData: StateFlow<SharePayload?> = MutableStateFlow(null),
) {
    val tokenStore: TokenStore = koinInject()
    val socketManager: SocketManager = koinInject()
    val apiService: ApiService = koinInject()
    val token by tokenStore.accessToken.collectAsState(initial = null)
    val currentUserId by tokenStore.userId.collectAsState(initial = "")
    val pendingConvId by pendingConversationId.collectAsState()
    val shareData by pendingShareData.collectAsState()

    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()

    // Share picker state
    var showSharePicker by remember { mutableStateOf(false) }
    var sharePickerConvs by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    // Deep-link from push notification → navigate to specific conversation
    LaunchedEffect(pendingConvId) {
        val convId = pendingConvId ?: return@LaunchedEffect
        val currentToken = tokenStore.accessToken.first() ?: return@LaunchedEffect
        val userId = tokenStore.userId.first()
        (pendingConversationId as? MutableStateFlow)?.value = null
        try {
            val convs = apiService.getConversations("Bearer $currentToken")
            val conv = convs.find { it.id == convId }
            val convName = conv?.name ?: "Чат"
            token?.let { socketManager.connect(it) }
            navController.navigate(Routes.chat(convId, convName, userId)) {
                launchSingleTop = true
            }
        } catch (_: Exception) {}
    }

    // Show share picker when share data arrives and user is authenticated
    LaunchedEffect(shareData) {
        val payload = shareData ?: return@LaunchedEffect
        val currentToken = tokenStore.accessToken.first() ?: return@LaunchedEffect
        runCatching {
            sharePickerConvs = apiService.getConversations("Bearer $currentToken")
        }
        showSharePicker = true
    }

    // Share target conversation picker sheet
    if (showSharePicker) {
        ModalBottomSheet(
            onDismissRequest = {
                showSharePicker = false
                (pendingShareData as? MutableStateFlow)?.value = null
            },
            sheetState = sheetState,
        ) {
            Text(
                text = "Отправить в чат",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(sharePickerConvs, key = { it.id }) { conv ->
                    Text(
                        text = conv.name ?: "Чат",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSharePicker = false
                                token?.let { socketManager.connect(it) }
                                navController.navigate(
                                    Routes.chat(conv.id, conv.name ?: "Чат", currentUserId, conv.otherId ?: "")
                                ) { launchSingleTop = true }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
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
                    navController.navigate(Routes.chat(conv.id, conv.name ?: "Чат", userId, conv.otherId ?: ""))
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
                navArgument("otherId") { type = NavType.StringType; defaultValue = "_" },
            ),
        ) { back ->
            val convId = back.arguments?.getString("convId") ?: return@composable
            val convName = back.arguments?.getString("convName") ?: ""
            val userId = back.arguments?.getString("userId") ?: ""
            val otherId = back.arguments?.getString("otherId")?.takeIf { it != "_" } ?: ""
            ChatScreen(
                conversationId = convId,
                conversationName = convName,
                currentUserId = userId,
                otherUserId = otherId,
                onBack = { navController.popBackStack() },
                onNavigateToChat = { targetConvId, targetConvName ->
                    navController.navigate(Routes.chat(targetConvId, targetConvName, userId))
                },
                sharePayload = shareData,
                onShareConsumed = { (pendingShareData as? MutableStateFlow)?.value = null },
            )
        }
    }
}
