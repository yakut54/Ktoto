package ru.yakut54.ktoto

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

/**
 * Transparent trampoline activity for incoming call notifications.
 *
 * When the screen is ON and unlocked, Android suppresses fullScreenIntent and
 * shows a heads-up notification instead. Launching a direct startActivity() from
 * a FGS is blocked by restrictive OEM ROMs (HiOS, MIUI, etc.).
 *
 * This activity lives in its own task (taskAffinity="") with manifest-level
 * showWhenLocked + turnScreenOn attributes — these have higher privilege than
 * the programmatic Activity API on OEM ROMs. The system is more likely to allow
 * launching a singleInstance activity into a new task than bringing the main task
 * to the foreground.
 *
 * Flow: CallService → startActivity(IncomingCallActivity) → startActivity(MainActivity
 * with INCOMING_CALL) → finish() → AppNavigation navigates to CallScreen.
 */
class IncomingCallActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Programmatic lock screen flags as backup (manifest attrs are primary)
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

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("action", "INCOMING_CALL")
                putExtra("fromSleep", true)
            },
        )
        finish()
    }
}
