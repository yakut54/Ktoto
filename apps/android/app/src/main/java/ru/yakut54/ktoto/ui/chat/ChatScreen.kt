package ru.yakut54.ktoto.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import ru.yakut54.ktoto.data.model.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    conversationName: String,
    currentUserId: String,
    onBack: () -> Unit,
) {
    val vm: ChatViewModel = koinViewModel()
    val messages by vm.messages.collectAsState()
    val sending by vm.sending.collectAsState()
    val isTyping by vm.isTyping.collectAsState()

    LaunchedEffect(conversationId) { vm.init(conversationId) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    var text by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(conversationName, style = MaterialTheme.typography.titleMedium)
                        if (isTyping) Text("печатает...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        if (it.isNotBlank()) vm.notifyTyping()
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение...") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                if (sending) {
                    CircularProgressIndicator(Modifier.size(40.dp))
                } else {
                    IconButton(
                        onClick = { vm.sendMessage(text); text = "" },
                        enabled = text.isNotBlank(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить",
                            tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg, isMine = msg.sender.id == currentUserId)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean) {
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp,
                ))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (!isMine) {
                Text(
                    text = message.sender.username,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(text = message.content ?: "", color = textColor)
        }
    }
}
