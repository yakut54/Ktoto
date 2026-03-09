package ru.yakut54.ktoto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import ru.yakut54.ktoto.service.CallService

/**
 * Trampoline activity for the "Принять" notification action.
 *
 * The accept button in the HUD notification uses PendingIntent.getActivity() to launch
 * this activity. Because the system launches it as an Activity (not a Service), there are
 * no BAL restrictions — the call screen always opens, including on HiOS/MIUI.
 *
 * Flow: notification "Принять" → AcceptCallActivity → startService(ACTION_ACCEPT) +
 *       startActivity(MainActivity, IN_CALL) → finish().
 */
class AcceptCallActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tell the service to accept the call and switch the notification
        startService(Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_ACCEPT
        })

        // Open the call screen — allowed because we are an Activity launched from a
        // notification PendingIntent.getActivity(), so BAL restrictions don't apply
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("action", "IN_CALL")
            },
        )

        finish()
    }
}
