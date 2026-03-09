package ru.yakut54.ktoto.ui.conversations

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import ru.yakut54.ktoto.ui.components.KtotoDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.koin.androidx.compose.koinViewModel
import ru.yakut54.ktoto.data.model.Conversation
import ru.yakut54.ktoto.utils.formatConversationTime
import ru.yakut54.ktoto.utils.nameToAvatarColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (Conversation, userId: String) -> Unit,
    onNewChat: () -> Unit,
    onCallHistory: () -> Unit,
    onLogout: () -> Unit,
) {
    val vm: ConversationsViewModel = koinViewModel()
    val state by vm.state.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val userId by vm.userId.collectAsState()
    val username by vm.username.collectAsState()
    val onlineUsers by vm.onlineUsers.collectAsState()
    val typingConvIds by vm.typingConvIds.collectAsState()

    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* результат сохранён ОС */ }

    // Запрашиваем пермишны сразу после входа
    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.load()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ktoto", fontWeight = FontWeight.Bold)
                        if (!username.isNullOrBlank()) {
                            Text(
                                text = username.orEmpty(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onCallHistory) {
                        Icon(Icons.Default.Phone, contentDescription = "История звонков")
                    }
                    IconButton(onClick = { vm.logout(onLogout) }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Выйти")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat) {
                Icon(Icons.Default.Add, contentDescription = "Новый чат")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                is ConversationsState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is ConversationsState.Error -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }

                is ConversationsState.Success -> {
                    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

                    if (pendingDeleteId != null) {
                        KtotoDialog(
                            title = "Удалить чат?",
                            message = "Вся переписка будет удалена без возможности восстановления.",
                            icon = Icons.Default.Delete,
                            confirmText = "Удалить",
                            confirmColor = MaterialTheme.colorScheme.error,
                            onConfirm = {
                                vm.deleteConversation(pendingDeleteId!!)
                                pendingDeleteId = null
                            },
                            onDismiss = { pendingDeleteId = null },
                        )
                    }

                    if (s.items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Нет чатов",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Нажмите + чтобы начать диалог",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(s.items, key = { it.id }) { conv ->
                                val dismissState = rememberSwipeToDismissBoxState()
                                LaunchedEffect(dismissState.currentValue) {
                                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                        pendingDeleteId = conv.id
                                        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                    }
                                }
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFFE53935)),
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Удалить",
                                                tint = Color.White,
                                                modifier = Modifier.padding(end = 24.dp),
                                            )
                                        }
                                    },
                                ) {
                                    ConversationItem(
                                        conversation = conv,
                                        currentUserId = userId,
                                        isOnline = conv.otherId != null && conv.otherId in onlineUsers,
                                        isTyping = conv.id in typingConvIds,
                                        onClick = { onConversationClick(conv, userId) },
                                    )
                                }
                                HorizontalDivider(Modifier.padding(start = 80.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    currentUserId: String,
    isOnline: Boolean,
    isTyping: Boolean,
    onClick: () -> Unit,
) {
    val name        = conversation.name ?: "Чат"
    val avatarColor = nameToAvatarColor(name)
    val isGroup     = conversation.type == "group"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar with optional online dot / group icon
        Box(modifier = Modifier.size(52.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(avatarColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isGroup) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Text(
                        text = name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                    )
                }
            }
            if (isOnline && !isGroup) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .padding(2.dp)
                        .background(Color(0xFF4CAF50), CircleShape),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            // Name + timestamp row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                val timeStr = formatConversationTime(
                    conversation.lastMessage?.sentAt ?: conversation.updatedAt
                )
                if (timeStr.isNotEmpty()) {
                    Text(
                        text = timeStr,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // Last message preview / typing indicator
            if (isTyping) {
                Text(
                    text = "Печатает...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            } else {
                val lm = conversation.lastMessage
                val mediaPrefix = when (lm?.type) {
                    "image" -> "📷 Фото"
                    "voice" -> "🎤 Голосовое"
                    "video" -> "🎬 Видео"
                    "file"  -> "📎 Файл"
                    else    -> null
                }
                val callPreview: String? = if (lm?.type == "call") {
                    val json = runCatching { org.json.JSONObject(lm.content ?: "{}") }.getOrNull()
                    val callType = json?.optString("callType", "audio") ?: "audio"
                    val outcome  = json?.optString("outcome", "missed") ?: "missed"
                    val duration = json?.optInt("duration", 0)?.takeIf { it > 0 }
                    when (outcome) {
                        "completed" -> (if (callType == "video") "📹 Видео звонок" else "📞 Аудио звонок") +
                            (duration?.let { " · ${it / 60}м ${it % 60}с" } ?: "")
                        "declined"  -> "📵 Отклонённый звонок"
                        "cancelled" -> "📵 Отменённый звонок"
                        else        -> "📵 Пропущенный звонок"
                    }
                } else null
                val preview = when {
                    lm == null -> "Нет сообщений"
                    callPreview != null -> callPreview
                    mediaPrefix != null && lm.userId == currentUserId -> "Вы: $mediaPrefix"
                    mediaPrefix != null -> mediaPrefix
                    lm.userId == currentUserId -> "Вы: ${lm.content.orEmpty()}"
                    else -> lm.content.orEmpty()
                }
                Text(
                    text = preview,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (conversation.unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            Badge {
                Text(conversation.unreadCount.toString())
            }
        }
    }
}
