package ru.yakut54.ktoto.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import ru.yakut54.ktoto.data.api.buildApiService
import ru.yakut54.ktoto.data.store.TokenStore
import ru.yakut54.ktoto.ui.auth.AuthViewModel
import ru.yakut54.ktoto.ui.conversations.ConversationsViewModel

// Change to your server address. For emulator: 10.0.2.2 = host machine localhost
private const val BASE_URL = "http://10.0.2.2:3000/"

val appModule = module {
    single { TokenStore(androidContext()) }
    single { buildApiService(BASE_URL) }
    viewModel { AuthViewModel(get(), get()) }
    viewModel { ConversationsViewModel(get(), get()) }
}
