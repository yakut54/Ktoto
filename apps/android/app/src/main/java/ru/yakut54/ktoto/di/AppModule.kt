package ru.yakut54.ktoto.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.yakut54.ktoto.call.CallManager
import ru.yakut54.ktoto.data.api.buildApiService
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore
import ru.yakut54.ktoto.ui.auth.AuthViewModel
import ru.yakut54.ktoto.ui.call.CallViewModel
import ru.yakut54.ktoto.ui.chat.ChatViewModel
import ru.yakut54.ktoto.ui.conversations.ConversationsViewModel
import ru.yakut54.ktoto.ui.callhistory.CallHistoryViewModel
import ru.yakut54.ktoto.ui.groupinfo.GroupInfoViewModel
import ru.yakut54.ktoto.ui.newchat.CreateGroupViewModel
import ru.yakut54.ktoto.ui.newchat.NewChatViewModel

private const val BASE_URL = "http://31.128.39.216:3000/"

val appModule = module {
    single { TokenStore(androidContext()) }
    single { buildApiService(BASE_URL, get()) }
    single { SocketManager() }
    single {
        CallManager(androidContext(), get(), get(), get()).also { it.init() }
    }
    viewModel { AuthViewModel(get(), get()) }
    viewModel { ConversationsViewModel(get(), get(), get()) }
    viewModel { ChatViewModel(get(), get(), get()) }
    viewModel { NewChatViewModel(get(), get()) }
    viewModel { CreateGroupViewModel(get(), get()) }
    viewModel { GroupInfoViewModel(get(), get()) }
    viewModel { CallViewModel(get()) }
    viewModel { CallHistoryViewModel(get(), get()) }
}
