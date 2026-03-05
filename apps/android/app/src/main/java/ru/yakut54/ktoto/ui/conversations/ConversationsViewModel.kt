package ru.yakut54.ktoto.ui.conversations

import androidx.lifecycle.ViewModel
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.store.TokenStore

class ConversationsViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) : ViewModel()
