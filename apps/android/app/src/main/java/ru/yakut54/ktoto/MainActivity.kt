package ru.yakut54.ktoto

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import ru.yakut54.ktoto.service.PushService
import ru.yakut54.ktoto.ui.navigation.AppNavigation
import ru.yakut54.ktoto.ui.navigation.SharePayload
import ru.yakut54.ktoto.ui.theme.KtotoTheme

class MainActivity : ComponentActivity() {

    val pendingConversationId = MutableStateFlow<String?>(null)
    val pendingShareData = MutableStateFlow<SharePayload?>(null)
    val pendingCallAction = MutableStateFlow<String?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startPushService()
        }

    private var fullScreenIntentChecked = false
    private var overlayPermissionChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLockScreenFlags()

        intent?.getStringExtra("conversationId")?.let {
            pendingConversationId.value = it
        }
        intent?.getStringExtra("action")?.let {
            pendingCallAction.value = it
        }
        handleShareIntent(intent)

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
                AppNavigation(
                    pendingConversationId = pendingConversationId,
                    pendingShareData = pendingShareData,
                    pendingCallAction = pendingCallAction,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyLockScreenFlags()
        // Don't check during call wakeup — would redirect to Settings mid-call
        if (intent?.getStringExtra("action") !in listOf("INCOMING_CALL", "IN_CALL")) {
            checkFullScreenIntentPermission()
            checkOverlayPermission()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("conversationId")?.let {
            pendingConversationId.value = it
        }
        intent.getStringExtra("action")?.let {
            pendingCallAction.value = it
        }
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (text != null || uri != null) {
            pendingShareData.value = SharePayload(uri = uri, text = text, mimeType = intent.type)
        }
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    /** Requests SYSTEM_ALERT_WINDOW permission (needed for the incoming-call overlay
     *  when screen is on). Without it the overlay is skipped and we fall back to
     *  startActivity which restrictive OEM ROMs (HiOS, MIUI) may block. */
    private fun checkOverlayPermission() {
        if (overlayPermissionChecked) return
        overlayPermissionChecked = true
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private fun checkFullScreenIntentPermission() {
        if (fullScreenIntentChecked) return
        fullScreenIntentChecked = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

    private fun startPushService() {
        ContextCompat.startForegroundService(this, Intent(this, PushService::class.java))
    }
}
