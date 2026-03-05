package ru.yakut54.ktoto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import ru.yakut54.ktoto.service.PushService
import ru.yakut54.ktoto.ui.navigation.AppNavigation
import ru.yakut54.ktoto.ui.theme.KtotoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.startForegroundService(this, Intent(this, PushService::class.java))
        setContent {
            KtotoTheme {
                AppNavigation()
            }
        }
    }
}
