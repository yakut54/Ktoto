package ru.yakut54.ktoto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import ru.yakut54.ktoto.service.PushService
import ru.yakut54.ktoto.ui.navigation.AppNavigation
import ru.yakut54.ktoto.ui.theme.KtotoTheme

class MainActivity : ComponentActivity() {

    val pendingConversationId = MutableStateFlow<String?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startPushService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        intent?.getStringExtra("conversationId")?.let {
            pendingConversationId.value = it
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startPushService()
            } else {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startPushService()
        }

        setContent {
            KtotoTheme {
                AppNavigation(pendingConversationId = pendingConversationId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("conversationId")?.let {
            pendingConversationId.value = it
        }
    }

    private fun startPushService() {
        ContextCompat.startForegroundService(this, Intent(this, PushService::class.java))
    }
}
