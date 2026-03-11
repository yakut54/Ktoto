package ru.yakut54.ktoto

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.android.ext.android.get
import ru.yakut54.ktoto.call.CallManager
import ru.yakut54.ktoto.di.appModule

class KtotoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@KtotoApp)
            modules(appModule)
        }

        createNotificationChannels()

        // Eagerly init CallManager so socket event collectors are active
        // even when the process is woken by FCM while the app is closed.
        get<CallManager>()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Сообщения",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Уведомления о новых сообщениях" }
        )
    }

    companion object {
        const val CHANNEL_MESSAGES = "ktoto_messages"
    }
}
