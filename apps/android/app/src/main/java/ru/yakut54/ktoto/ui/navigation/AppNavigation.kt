package ru.yakut54.ktoto.ui.navigation


import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.androidx.compose.get
import ru.yakut54.ktoto.data.store.TokenStore
import ru.yakut54.ktoto.ui.auth.AuthScreen

object Routes {
    const val AUTH = "auth"
    const val CONVERSATIONS = "conversations"
}

@Composable
fun AppNavigation() {
    val tokenStore: TokenStore = get()
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
            // Placeholder — will be replaced in M.3
            androidx.compose.material3.Text("Chats coming soon...")
        }
    }
}
