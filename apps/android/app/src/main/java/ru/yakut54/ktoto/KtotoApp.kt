package ru.yakut54.ktoto

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.webrtc.PeerConnectionFactory
import ru.yakut54.ktoto.di.appModule

class KtotoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize WebRTC globally — must happen before any PeerConnection is created
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        startKoin {
            androidContext(this@KtotoApp)
            modules(appModule)
        }
    }
}
