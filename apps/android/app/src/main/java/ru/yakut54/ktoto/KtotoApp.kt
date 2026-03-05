package ru.yakut54.ktoto

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import ru.yakut54.ktoto.di.appModule

class KtotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KtotoApp)
            modules(appModule)
        }
    }
}
