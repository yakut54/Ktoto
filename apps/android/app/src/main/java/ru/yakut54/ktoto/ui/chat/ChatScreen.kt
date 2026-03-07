package ru.yakut54.ktoto.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.compose.koinViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer

import ru.yakut54.ktoto.data.model.Attachment
import ru.yakut54.ktoto.data.model.Message
import ru.yakut54.ktoto.ui.navigation.SharePayload
import ru.yakut54.ktoto.utils.formatMessageTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    conversationName: String,
    currentUserId: String,
    otherUserId: String = "",
    onBack: () -> Unit,
    onNavigateToChat: (convId: String, convName: String) -> Unit = { _, _ -> },
    onStartCall: ((callType: String) -> Unit)? = null,
    sharePayload: SharePayload? = null,
    onShareConsumed: () -> Unit = {},
) {
    val vm: ChatViewModel = koinViewModel()
    val messages by vm.messages.collectAsState()
    val sending by vm.sending.collectAsState()
    val isTyping by vm.isTyping.collectAsState()
    val voiceState by vm.voiceState.collectAsState()
    val recordingSeconds by vm.recordingSeconds.collectAsState()
    val previewDuration by vm.previewDuration.collectAsState()
    val previewPlaying by vm.previewPlaying.collectAsState()
    val previewProgress by vm.previewProgress.collectAsState()
    val replyTo by vm.replyTo.collectAsState()
    val editingMessage by vm.editingMessage.collectAsState()

    val context = LocalContext.current
    val density = LocalDensity.current
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)

    val partnerOnline by vm.partnerOnline.collectAsState()
    val deletingIds by vm.deletingIds.collectAsState()

    LaunchedEffect(conversationId) {
        vm.init(conversationId, otherUserId)
        // Cancel any push notification for this conversation
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(conversationId.hashCode())
    }

    // Auto-send shared content when navigating here from share sheet
    LaunchedEffect(sharePayload?.uri, sharePayload?.text) {
        val payload = sharePayload ?: return@LaunchedEffect
        delay(300) // let vm.init settle
        onShareConsumed()
        if (payload.uri != null) {
            val type = when {
                payload.mimeType?.startsWith("image/") == true -> "image"
                payload.mimeType?.startsWith("video/") == true -> "video"
                else -> "file"
            }
            vm.sendMediaMessage(context, payload.uri, type)
        } else if (payload.text != null) {
            vm.sendMessage(payload.text)
        }
    }

    val listState = rememberLazyListState()
    val chatScope = rememberCoroutineScope()
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // Sync edit mode: populate text field with message content
    var text by remember { mutableStateOf("") }
    LaunchedEffect(editingMessage) {
        if (editingMessage != null) text = editingMessage!!.content ?: ""
    }

    val activity = context as? android.app.Activity

    val conversationsForPicker by vm.conversationsForPicker.collectAsState()

    var showAttachSheet by remember { mutableStateOf(false) }
    var pendingMedia by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var recordingCancelHint by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<ru.yakut54.ktoto.data.model.Message?>(null) }
    var showForwardPicker by remember { mutableStateOf<ru.yakut54.ktoto.data.model.Message?>(null) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    // ── Multi-select state ─────────────────────────────────────────────────────
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }
    var showMultiForwardPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    // ── Permission state ───────────────────────────────────────────────────────
    var showMicRationale by remember { mutableStateOf(false) }
    var showMicSettings by remember { mutableStateOf(false) }
    var showCameraRationale by remember { mutableStateOf(false) }
    var showCameraSettings by remember { mutableStateOf(false) }
    // pendingCamera: after permission granted — finish opening camera
    var pendingCameraOpen by remember { mutableStateOf(false) }

    // Reset cancel hint when leaving RECORDING state
    LaunchedEffect(voiceState) {
        if (voiceState != VoiceState.RECORDING) recordingCancelHint = false
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { pendingMedia = it to "image" } }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { pendingMedia = it to "image" } }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { pendingMedia = it to "file" } }

    // Mic permission launcher — called when permission not yet granted
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            val canAsk = activity?.let {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: false
            if (canAsk) showMicRationale = true else showMicSettings = true
        }
        // If granted: user will long-press mic again — natural UX, no auto-start
    }

    // Camera permission launcher
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCameraOpen = true
        } else {
            val canAsk = activity?.let {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            if (canAsk) showCameraRationale = true else showCameraSettings = true
        }
    }

    // When camera permission was just granted, open the camera
    LaunchedEffect(pendingCameraOpen) {
        if (pendingCameraOpen) {
            pendingCameraOpen = false
            val photoFile = java.io.File(context.externalCacheDir, "photo_${System.currentTimeMillis()}.jpg")
            cameraUri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", photoFile
            )
            cameraUri?.let { cameraLauncher.launch(it) }
        }
    }

    fun openCamera() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            val photoFile = java.io.File(context.externalCacheDir, "photo_${System.currentTimeMillis()}.jpg")
            cameraUri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", photoFile
            )
            cameraUri?.let { cameraLauncher.launch(it) }
        } else {
            val canAsk = activity?.let {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            if (canAsk) showCameraRationale = true else cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                val selectedMessages = messages.filter { it.id in selectedIds }
                TopAppBar(
                    title = { Text("${selectedIds.size} выбрано") },
                    navigationIcon = {
                        IconButton(onClick = { selectionMode = false; selectedIds = emptySet() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Отмена")
                        }
                    },
                    actions = {
                        val hasText = selectedMessages.any { !it.content.isNullOrBlank() }
                        if (hasText) {
                            IconButton(onClick = {
                                val txt = selectedMessages.mapNotNull { it.content }.joinToString("\n")
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("messages", txt))
                                selectionMode = false; selectedIds = emptySet()
                            }) {
                                Icon(Icons.Default.ContentCopy, "Копировать")
                            }
                        }
                        val singleOwnText = selectedIds.size == 1 &&
                            selectedMessages.firstOrNull()?.sender?.id == currentUserId &&
                            selectedMessages.firstOrNull()?.type == "text"
                        if (singleOwnText) {
                            IconButton(onClick = {
                                selectedMessages.first().let {
                                    vm.startEditing(it)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                }
                            }) { Icon(Icons.Default.Edit, "Редактировать") }
                        }
                        IconButton(onClick = {
                            showMultiForwardPicker = true
                            vm.loadConversationsForPicker()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Reply, "Переслать")
                        }
                        IconButton(onClick = { showMultiDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(conversationName, style = MaterialTheme.typography.titleMedium)
                            when {
                                isTyping -> Text(
                                    "печатает...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                partnerOnline -> Text(
                                    "онлайн",
                                    fontSize = 12.sp,
                                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                        }
                    },
                    actions = {
                        // Call buttons (only for direct chats with a known peer)
                        if (onStartCall != null && otherUserId.isNotBlank()) {
                            IconButton(onClick = { onStartCall("video") }) {
                                Icon(Icons.Default.Videocam, "Видеозвонок")
                            }
                            IconButton(onClick = { onStartCall("audio") }) {
                                Icon(Icons.Default.Call, "Позвонить")
                            }
                        }
                        Box {
                            IconButton(onClick = { showChatMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Меню")
                            }
                            DropdownMenu(
                                expanded = showChatMenu,
                                onDismissRequest = { showChatMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Удалить чат") },
                                    onClick = { showChatMenu = false; showLeaveConfirm = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Добавить в ЧС", color = MaterialTheme.colorScheme.error) },
                                    onClick = { showChatMenu = false; showBlockConfirm = true },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp, tonalElevation = 2.dp) {
                if (selectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (selectedIds.size == 1) {
                            OutlinedButton(
                                onClick = {
                                    messages.find { it.id in selectedIds }?.let { vm.setReplyTo(it) }
                                    selectionMode = false; selectedIds = emptySet()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Reply, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Ответить")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                showMultiForwardPicker = true
                                vm.loadConversationsForPicker()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Reply, null,
                                modifier = Modifier.size(18.dp).graphicsLayer { scaleX = -1f })
                            Spacer(Modifier.width(8.dp))
                            Text("Переслать")
                        }
                    }
                } else when (voiceState) {
                    // ── LOCKED: recording without holding ───────────────────────────────
                    VoiceState.LOCKED -> LockedRecordingBar(
                        seconds = recordingSeconds,
                        onCancel = { vm.cancelRecording() },
                        onPause = { vm.pauseRecording() },
                        onSend = { vm.sendVoiceFromLocked() },
                    )

                    // ── PAUSED: recorder stopped, user can listen or continue ────────────
                    VoiceState.PAUSED -> PausedRecordingBar(
                        seconds = recordingSeconds,
                        isPlaying = previewPlaying,
                        progress = previewProgress,
                        onCancel = { vm.cancelRecording() },
                        onTogglePlay = { vm.togglePreviewPlayback() },
                        onContinue = { vm.resumeRecording(context) },
                        onSend = { vm.sendVoicePaused() },
                        onSeek = { vm.seekPreviewTo(it) },
                    )

                    // ── PREVIEW: stopped, user decides to listen / send / delete ────────
                    VoiceState.PREVIEW -> VoicePreviewBar(
                        durationSeconds = previewDuration,
                        isPlaying = previewPlaying,
                        progress = previewProgress,
                        onDelete = { vm.deleteVoicePreview() },
                        onTogglePlay = { vm.togglePreviewPlayback() },
                        onSend = { vm.sendVoicePreview() },
                        onSeek = { vm.seekPreviewTo(it) },
                    )

                    // ── IDLE or RECORDING: normal input row + optional recording overlay ─
                    else -> {
                        val cancelThresholdPx = with(density) { 80.dp.toPx() }
                        val lockThresholdPx = with(density) { 80.dp.toPx() }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .imePadding(),
                        ) {
                            // ── Reply / Edit bar ───────────────────────────────────────
                            when {
                                editingMessage != null -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Редактирование",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                editingMessage!!.content ?: "",
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                        IconButton(onClick = { vm.cancelEditing(); text = "" }) {
                                            Icon(Icons.Default.Close, "Отмена")
                                        }
                                    }
                                }
                                replyTo != null -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Reply,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                replyTo!!.sender.username,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            val preview = when (replyTo!!.type) {
                                                "image" -> "📷 Фото"
                                                "voice" -> "🎤 Голосовое"
                                                "file" -> "📎 Файл"
                                                else -> replyTo!!.content ?: ""
                                            }
                                            Text(
                                                preview,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                        IconButton(onClick = { vm.clearReplyTo() }) {
                                            Icon(Icons.Default.Close, "Закрыть")
                                        }
                                    }
                                }
                            }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // ── Input row ──────────────────────────────────────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                // 📎 Attach (hidden in edit mode)
                                if (editingMessage == null) {
                                    IconButton(onClick = { showAttachSheet = true }) {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            contentDescription = "Прикрепить",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                // Text field
                                TextField(
                                    value = text,
                                    onValueChange = {
                                        text = it
                                        if (it.isNotBlank()) vm.notifyTyping()
                                    },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Сообщение...") },
                                    maxLines = 4,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences
                                    ),
                                )

                                // 📷 Camera — only when idle + text blank + not editing
                                if (text.isBlank() && !sending && voiceState == VoiceState.IDLE && editingMessage == null) {
                                    IconButton(onClick = ::openCamera) {
                                        Icon(
                                            Icons.Default.PhotoCamera,
                                            contentDescription = "Камера",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Spacer(Modifier.width(2.dp))

                                // Right action button
                                when {
                                    sending -> CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp).padding(8.dp),
                                        strokeWidth = 3.dp,
                                    )

                                    editingMessage != null && text.isNotBlank() -> {
                                        FilledIconButton(
                                            onClick = { vm.saveEdit(text); text = "" },
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(Icons.Default.Edit, "Сохранить")
                                        }
                                    }

                                    text.isNotBlank() && voiceState == VoiceState.IDLE -> {
                                        // ▶ Send text
                                        FilledIconButton(
                                            onClick = { vm.sendMessage(text); text = "" },
                                            modifier = Modifier.size(48.dp),
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
                                        }
                                    }

                                    else -> {
                                        // 🎤 Mic button
                                        // - Long press (500ms) → start recording (RECORDING state)
                                        //   - During hold, slide LEFT → cancel hint
                                        //   - During hold, slide UP  → lock mode (LOCKED state)
                                        //   - Release                → stop + show PREVIEW
                                        // Use Box (not FilledIconButton) to avoid click-event conflicts
                                        val micBg = if (voiceState == VoiceState.RECORDING)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.primary

                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(micBg, CircleShape)
                                                .pointerInput(Unit) {
                                                    awaitEachGesture {
                                                        val down = awaitFirstDown(requireUnconsumed = false)

                                                        // Wait for long press
                                                        val upBeforeLong = withTimeoutOrNull(
                                                            viewConfiguration.longPressTimeoutMillis
                                                        ) { waitForUpOrCancellation() }

                                                        if (upBeforeLong == null) {
                                                            // Long press confirmed → check mic permission first
                                                            val micGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                                                context, Manifest.permission.RECORD_AUDIO
                                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                            if (!micGranted) {
                                                                val canAsk = activity?.let {
                                                                    androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                                                                        it, Manifest.permission.RECORD_AUDIO
                                                                    )
                                                                } ?: false
                                                                if (canAsk) showMicRationale = true
                                                                else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                                return@awaitEachGesture
                                                            }
                                                            vm.startRecording(context)
                                                            recordingCancelHint = false

                                                            var cancelled = false
                                                            var lockedMode = false

                                                            while (true) {
                                                                val event = awaitPointerEvent()
                                                                val change = event.changes.firstOrNull() ?: break

                                                                val dragX = change.position.x - down.position.x
                                                                val dragY = change.position.y - down.position.y

                                                                // Slide left → cancel hint
                                                                cancelled = dragX < -cancelThresholdPx
                                                                recordingCancelHint = cancelled

                                                                // Slide up → lock mode
                                                                if (!lockedMode && dragY < -lockThresholdPx) {
                                                                    lockedMode = true
                                                                    vm.lockRecording()
                                                                    // The composable switches to LockedRecordingBar,
                                                                    // which removes this Box → coroutine cancelled.
                                                                    // That's intentional — locked UI takes over.
                                                                }

                                                                if (!change.pressed) break
                                                            }

                                                            recordingCancelHint = false

                                                            // Only handle release if NOT locked
                                                            // (lock mode: LockedRecordingBar has its own buttons)
                                                            if (!lockedMode) {
                                                                if (cancelled) vm.cancelRecording()
                                                                else vm.stopRecordingToPreview()
                                                            }
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = if (voiceState == VoiceState.RECORDING)
                                                    Icons.AutoMirrored.Filled.Send
                                                else
                                                    Icons.Default.Mic,
                                                contentDescription = if (voiceState == VoiceState.RECORDING)
                                                    "Отпусти чтобы отправить"
                                                else
                                                    "Удержи для записи",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Recording overlay (over text field, leaves mic area free) ──
                            if (voiceState == VoiceState.RECORDING) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.Center)
                                        // leave ~60dp on the right for mic button
                                        .padding(start = 8.dp, end = 60.dp, top = 6.dp, bottom = 6.dp)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // ← Cancel hint
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = null,
                                        tint = if (recordingCancelHint) MaterialTheme.colorScheme.error
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                    Text(
                                        "Отмена",
                                        fontSize = 13.sp,
                                        color = if (recordingCancelHint) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    Spacer(Modifier.weight(1f))

                                    // ● Timer
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        formatDuration(recordingSeconds),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )

                                    // ↑ Lock hint
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Зафиксировать",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(end = 8.dp).size(16.dp),
                                    )
                                }
                            }
                        } // end Box
                        } // end Column
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                val swipeOffset = remember { Animatable(0f) }
                val swipeScope = rememberCoroutineScope()
                val haptic = LocalHapticFeedback.current
                val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
                val isBulkSelected = msg.id in selectedIds
                val isHighlighted = msg.id == highlightedMessageId
                val highlightBg by animateColorAsState(
                    targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                    label = "highlight",
                )
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val pressScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.965f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label = "pressScale",
                )

                AnimatedVisibility(
                    visible = msg.id !in deletingIds,
                    exit = shrinkVertically(animationSpec = tween(260)) + fadeOut(animationSpec = tween(220)),
                    modifier = Modifier.animateItem(
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    ),
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isBulkSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else highlightBg
                        )
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (msg.id in selectedIds) selectedIds - msg.id else selectedIds + msg.id
                                    if (selectedIds.isEmpty()) selectionMode = false
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectionMode = true
                                selectedIds = selectedIds + msg.id
                            },
                        ),
                ) {
                    val swipeVal = swipeOffset.value

                    // ── Forward indicator (swipe right) ────────────────────────
                    if (swipeVal > 8f) {
                        val progress = (swipeVal / thresholdPx).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .size(36.dp)
                                .graphicsLayer { scaleX = progress; scaleY = progress; alpha = progress }
                                .background(MaterialTheme.colorScheme.secondary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Color.White,
                                modifier = Modifier.size(18.dp).graphicsLayer { scaleX = -1f })
                        }
                    }

                    // ── Reply indicator (swipe left) ────────────────────────────
                    if (swipeVal < -8f) {
                        val progress = (-swipeVal / thresholdPx).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp)
                                .size(36.dp)
                                .graphicsLayer { scaleX = progress; scaleY = progress; alpha = progress }
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // ── Sliding message content ────────────────────────────────
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(swipeVal.toInt(), 0) }
                            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                            .then(
                                if (!selectionMode) Modifier.pointerInput(msg.id) {
                                    var actionTriggered = false
                                    detectHorizontalDragGestures(
                                        onDragStart = { actionTriggered = false },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            val newOffset = (swipeOffset.value + dragAmount)
                                                .coerceIn(-thresholdPx * 1.4f, thresholdPx * 1.4f)
                                            swipeScope.launch { swipeOffset.snapTo(newOffset) }
                                            if (!actionTriggered) {
                                                when {
                                                    newOffset >= thresholdPx -> {
                                                        actionTriggered = true
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        showForwardPicker = msg
                                                        vm.loadConversationsForPicker()
                                                    }
                                                    newOffset <= -thresholdPx -> {
                                                        actionTriggered = true
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        vm.setReplyTo(msg)
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            swipeScope.launch {
                                                swipeOffset.animateTo(
                                                    0f,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium,
                                                    ),
                                                )
                                            }
                                        },
                                        onDragCancel = {
                                            swipeScope.launch { swipeOffset.animateTo(0f, spring()) }
                                        },
                                    )
                                }
                                else Modifier
                            ),
                    ) {
                        if (selectionMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TelegramCheckbox(
                                    checked = isBulkSelected,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                                MessageBubble(
                                    message = msg,
                                    isMine = msg.sender.id == currentUserId,
                                    allMessages = messages,
                                    onLongClick = {},
                                )
                            }
                        } else {
                            MessageBubble(
                                message = msg,
                                isMine = msg.sender.id == currentUserId,
                                allMessages = messages,
                                onLongClick = { selectionMode = true; selectedIds = selectedIds + msg.id },
                                onQuoteTap = { replyId ->
                                    chatScope.launch {
                                        val idx = messages.indexOfFirst { it.id == replyId }
                                        if (idx >= 0) {
                                            listState.animateScrollToItem(idx)
                                            highlightedMessageId = replyId
                                            delay(1500)
                                            highlightedMessageId = null
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                } // AnimatedVisibility
            }
        }
    }

    // Delete confirmation dialog — "для меня" / "для всех"
    if (showDeleteConfirm != null) {
        val msgToDelete = showDeleteConfirm!!
        Dialog(onDismissRequest = { showDeleteConfirm = null }) {
            Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
                Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                    Text(
                        "Удалить сообщение?",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { vm.deleteMessage(msgToDelete.id); showDeleteConfirm = null },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) { Text("Удалить у всех", color = MaterialTheme.colorScheme.error) }
                    TextButton(
                        onClick = { vm.deleteMessageForMe(msgToDelete.id); showDeleteConfirm = null },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) { Text("Удалить у меня", color = MaterialTheme.colorScheme.error) }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    TextButton(
                        onClick = { showDeleteConfirm = null },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) { Text("Отмена") }
                }
            }
        }
    }

    // Leave conversation dialog
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Удалить чат?") },
            text = { Text("Чат исчезнет из вашего списка. Собеседник не увидит, что вы покинули чат.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    vm.leaveConversation(onDone = onBack)
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Отмена") }
            },
        )
    }

    // Block user dialog
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Добавить в чёрный список?") },
            text = { Text("Пользователь не сможет писать вам. Чат будет удалён из вашего списка.") },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirm = false
                    vm.blockUser(onDone = onBack)
                }) { Text("Заблокировать", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) { Text("Отмена") }
            },
        )
    }

    // ── Media preview + caption sheet ──────────────────────────────────────────
    if (pendingMedia != null) {
        val (mediaUri, mediaType) = pendingMedia!!
        var captionText by remember { mutableStateOf("") }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val focusRequester = remember { FocusRequester() }
        val fileName = remember(mediaUri) {
            context.contentResolver.query(mediaUri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (idx >= 0) cursor.getString(idx) else null
            } ?: mediaUri.lastPathSegment ?: "Файл"
        }
        ModalBottomSheet(
            onDismissRequest = { pendingMedia = null; captionText = "" },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .imePadding(),
            ) {
                if (mediaType == "image" || mediaType == "video") {
                    AsyncImage(
                        model = mediaUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(36.dp))
                        Text(fileName, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    placeholder = { Text("Добавить подпись...") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        vm.sendMediaMessage(context, mediaUri, mediaType, captionText.trim())
                        pendingMedia = null
                        captionText = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Отправить")
                }
            }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(200)
                focusRequester.requestFocus()
            }
        }
    }

    // Attach bottom sheet — gallery + file
    if (showAttachSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                AttachOption(
                    icon = { Icon(Icons.Default.PhotoLibrary, null) },
                    label = "Галерея",
                    onClick = {
                        showAttachSheet = false
                        // PickVisualMedia is a system picker — no storage permission needed
                        imagePickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                )
                AttachOption(
                    icon = { Icon(Icons.Default.Description, null) },
                    label = "Файл",
                    onClick = {
                        showAttachSheet = false
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                )
            }
        }
    }

    // ── Forward picker ─────────────────────────────────────────────────────────
    if (showForwardPicker != null) {
        val msgToForward = showForwardPicker!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var fwdSelected by remember { mutableStateOf(emptySet<String>()) }
        ModalBottomSheet(
            onDismissRequest = { showForwardPicker = null; fwdSelected = emptySet() },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    "Переслать в...",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
                conversationsForPicker.filter { it.id != conversationId }.forEach { conv ->
                    val sel = conv.id in fwdSelected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                fwdSelected = if (sel) fwdSelected - conv.id else fwdSelected + conv.id
                            }
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(
                                MaterialTheme.colorScheme.primary, CircleShape
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (conv.name ?: "?").take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(conv.name ?: "Чат", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Checkbox(checked = sel, onCheckedChange = {
                            fwdSelected = if (sel) fwdSelected - conv.id else fwdSelected + conv.id
                        })
                    }
                }
                if (fwdSelected.isNotEmpty()) {
                    Button(
                        onClick = {
                            fwdSelected.forEach { cid -> vm.forwardMessage(msgToForward, cid) }
                            val singleId = fwdSelected.singleOrNull()
                            val singleName = conversationsForPicker.find { it.id == singleId }?.name
                            showForwardPicker = null
                            fwdSelected = emptySet()
                            if (singleId != null && singleName != null) onNavigateToChat(singleId, singleName)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Отправить (${fwdSelected.size})")
                    }
                }
            }
        }
    }

    // ── Permission dialogs ─────────────────────────────────────────────────────

    // Mic: rationale (denied once, can ask again)
    if (showMicRationale) {
        AlertDialog(
            onDismissRequest = { showMicRationale = false },
            title = { Text("Доступ к микрофону") },
            text = { Text("Для записи голосовых сообщений нужен доступ к микрофону.") },
            confirmButton = {
                TextButton(onClick = {
                    showMicRationale = false
                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Разрешить") }
            },
            dismissButton = {
                TextButton(onClick = { showMicRationale = false }) { Text("Отмена") }
            },
        )
    }

    // Mic: permanently denied — send to settings
    if (showMicSettings) {
        AlertDialog(
            onDismissRequest = { showMicSettings = false },
            title = { Text("Доступ к микрофону") },
            text = { Text("Доступ к микрофону запрещён. Разрешите его в настройках приложения.") },
            confirmButton = {
                TextButton(onClick = {
                    showMicSettings = false
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .apply { data = Uri.fromParts("package", context.packageName, null) }
                    )
                }) { Text("Настройки") }
            },
            dismissButton = {
                TextButton(onClick = { showMicSettings = false }) { Text("Отмена") }
            },
        )
    }

    // Camera: rationale
    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            title = { Text("Доступ к камере") },
            text = { Text("Для съёмки фото нужен доступ к камере.") },
            confirmButton = {
                TextButton(onClick = {
                    showCameraRationale = false
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Разрешить") }
            },
            dismissButton = {
                TextButton(onClick = { showCameraRationale = false }) { Text("Отмена") }
            },
        )
    }

    // Camera: permanently denied — send to settings
    if (showCameraSettings) {
        AlertDialog(
            onDismissRequest = { showCameraSettings = false },
            title = { Text("Доступ к камере") },
            text = { Text("Доступ к камере запрещён. Разрешите его в настройках приложения.") },
            confirmButton = {
                TextButton(onClick = {
                    showCameraSettings = false
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .apply { data = Uri.fromParts("package", context.packageName, null) }
                    )
                }) { Text("Настройки") }
            },
            dismissButton = {
                TextButton(onClick = { showCameraSettings = false }) { Text("Отмена") }
            },
        )
    }

    // ── Multi-delete confirmation ───────────────────────────────────────────────
    if (showMultiDeleteConfirm && selectedIds.isNotEmpty()) {
        Dialog(onDismissRequest = { showMultiDeleteConfirm = false }) {
            Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
                Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                    Text(
                        "Удалить ${selectedIds.size} сообщ.?",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            selectedIds.toList().forEach { vm.deleteMessage(it) }
                            showMultiDeleteConfirm = false; selectionMode = false; selectedIds = emptySet()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) { Text("Удалить у всех", color = MaterialTheme.colorScheme.error) }
                    TextButton(
                        onClick = {
                            selectedIds.toList().forEach { vm.deleteMessageForMe(it) }
                            showMultiDeleteConfirm = false; selectionMode = false; selectedIds = emptySet()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) { Text("Удалить у меня", color = MaterialTheme.colorScheme.error) }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    TextButton(
                        onClick = { showMultiDeleteConfirm = false },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) { Text("Отмена") }
                }
            }
        }
    }

    // ── Multi-forward picker ────────────────────────────────────────────────────
    if (showMultiForwardPicker && selectedIds.isNotEmpty()) {
        val msgsToForward = messages.filter { it.id in selectedIds }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var mfwdSelected by remember { mutableStateOf(emptySet<String>()) }
        ModalBottomSheet(
            onDismissRequest = { showMultiForwardPicker = false; mfwdSelected = emptySet() },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    "Переслать в...",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider()
                conversationsForPicker.filter { it.id != conversationId }.forEach { conv ->
                    val sel = conv.id in mfwdSelected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                mfwdSelected = if (sel) mfwdSelected - conv.id else mfwdSelected + conv.id
                            }
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(
                                MaterialTheme.colorScheme.primary, CircleShape
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (conv.name ?: "?").take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(conv.name ?: "Чат", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Checkbox(checked = sel, onCheckedChange = {
                            mfwdSelected = if (sel) mfwdSelected - conv.id else mfwdSelected + conv.id
                        })
                    }
                }
                if (mfwdSelected.isNotEmpty()) {
                    Button(
                        onClick = {
                            mfwdSelected.forEach { cid ->
                                msgsToForward.forEach { m -> vm.forwardMessage(m, cid) }
                            }
                            val singleId = mfwdSelected.singleOrNull()
                            val singleName = conversationsForPicker.find { it.id == singleId }?.name
                            showMultiForwardPicker = false
                            mfwdSelected = emptySet()
                            selectionMode = false; selectedIds = emptySet()
                            if (singleId != null && singleName != null) onNavigateToChat(singleId, singleName)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Отправить (${mfwdSelected.size})")
                    }
                }
            }
        }
    }
}

// ── LOCKED recording bar ───────────────────────────────────────────────────────
// Shown when user slid UP to lock. Recording continues hands-free.
// Buttons: ❌ Cancel | ▶ Send

@Composable
private fun LockedRecordingBar(
    seconds: Int,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
        Spacer(Modifier.width(6.dp))
        Text(formatDuration(seconds), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        // ✕ Cancel
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, "Отмена", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // ⏸ Pause
        IconButton(onClick = onPause) {
            Icon(Icons.Default.Pause, "Пауза", tint = MaterialTheme.colorScheme.primary)
        }
        // → Send immediately
        FilledIconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
        }
    }
}

// ── PAUSED recording bar ────────────────────────────────────────────────────────
// Recorder stopped → file is complete and playable.
// Buttons: ✕ Cancel | ▶/⏸ Listen | seek + timer | 🎤 Continue | → Send

@Composable
private fun PausedRecordingBar(
    seconds: Int,
    isPlaying: Boolean,
    progress: Float,
    onCancel: () -> Unit,
    onTogglePlay: () -> Unit,
    onContinue: () -> Unit,
    onSend: () -> Unit,
    onSeek: (Float) -> Unit = {},
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val bars = remember {
        IntArray(52) { i ->
            (kotlin.math.sin(i * 0.63) * 35 + kotlin.math.cos(i * 1.27) * 22 + 55)
                .toInt().coerceIn(12, 98)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ✕ Cancel
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Close, "Отмена", tint = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.width(4.dp))
        // Waveform + play chip
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onSeek((down.position.x / size.width).coerceIn(0f, 1f))
                        waitForUpOrCancellation()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barW = 3.dp.toPx()
                val spacing = size.width / bars.size
                val centerY = size.height / 2f
                val maxBarH = size.height * 0.85f
                val progressX = size.width * progress
                bars.forEachIndexed { i, amp ->
                    val x = i * spacing + spacing / 2f
                    val barH = maxBarH * (amp / 100f)
                    drawLine(
                        color = if (x <= progressX) primaryColor else trackColor,
                        start = Offset(x, centerY - barH / 2f),
                        end = Offset(x, centerY + barH / 2f),
                        strokeWidth = barW,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .clickable { onTogglePlay() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatDuration(seconds),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        // 🎤 Continue recording
        IconButton(onClick = onContinue, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Mic, "Продолжить", tint = MaterialTheme.colorScheme.primary)
        }
        // → Send
        FilledIconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
        }
    }
}

// ── PREVIEW bar ────────────────────────────────────────────────────────────────
// Shown after finger released. User can listen, then send or delete.
// Buttons: 🗑 Delete | ▶/⏸ Play | duration | ✓ Send

@Composable
private fun VoicePreviewBar(
    durationSeconds: Int,
    isPlaying: Boolean,
    progress: Float,
    onDelete: () -> Unit,
    onTogglePlay: () -> Unit,
    onSend: () -> Unit,
    onSeek: (Float) -> Unit = {},
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val bars = remember {
        IntArray(52) { i ->
            (kotlin.math.sin(i * 0.63) * 35 + kotlin.math.cos(i * 1.27) * 22 + 55)
                .toInt().coerceIn(12, 98)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 🗑 Delete
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.width(4.dp))

        // Waveform + play chip
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onSeek((down.position.x / size.width).coerceIn(0f, 1f))
                        waitForUpOrCancellation()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barW = 3.dp.toPx()
                val spacing = size.width / bars.size
                val centerY = size.height / 2f
                val maxBarH = size.height * 0.85f
                val progressX = size.width * progress
                bars.forEachIndexed { i, amp ->
                    val x = i * spacing + spacing / 2f
                    val barH = maxBarH * (amp / 100f)
                    drawLine(
                        color = if (x <= progressX) primaryColor else trackColor,
                        start = Offset(x, centerY - barH / 2f),
                        end = Offset(x, centerY + barH / 2f),
                        strokeWidth = barW,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // ▶ 0:06 chip overlay
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    .clickable { onTogglePlay() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatDuration(durationSeconds),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))

        // ✈ Send
        FilledIconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
        }
    }
}

// ── Attach option row ──────────────────────────────────────────────────────────

@Composable
private fun AttachOption(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ── Message bubbles ────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    allMessages: List<Message> = emptyList(),
    onLongClick: () -> Unit = {},
    onQuoteTap: ((String) -> Unit)? = null,
) {
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant

    // Use server-provided preview first; fall back to local history lookup for real-time messages
    val replyPreview = message.replyTo
    val replyFromHistory = if (replyPreview == null && message.replyToId != null)
        allMessages.find { it.id == message.replyToId } else null
    val hasReply = replyPreview != null || replyFromHistory != null
    val replyUsername = replyPreview?.sender?.username ?: replyFromHistory?.sender?.username ?: ""
    val replyType = replyPreview?.type ?: replyFromHistory?.type ?: "text"
    val replyContent = replyPreview?.content ?: replyFromHistory?.content

    val effectiveType = when {
        message.type == "image" || message.attachment?.mimeType?.startsWith("image/") == true -> "image"
        message.type == "voice" || message.attachment?.mimeType?.startsWith("audio/") == true -> "voice"
        message.type == "file"  || (message.attachment != null && message.type !in listOf("text", null)) -> "file"
        else -> "text"
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val minBubbleWidth = (screenWidthDp * 0.52f).dp
    val maxBubbleWidth = when {
        screenWidthDp <= 320 -> (screenWidthDp * 0.72f).dp
        screenWidthDp <= 360 -> (screenWidthDp * 0.74f).dp
        else -> minOf((screenWidthDp * 0.78f).dp, 300.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = if (effectiveType == "image") 0.dp else minBubbleWidth, max = maxBubbleWidth)
                .background(
                    color = if (effectiveType == "image") Color.Transparent else bubbleColor,
                    shape = RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp,
                    ),
                )
                .then(
                    if (effectiveType != "image") Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    else Modifier
                ),
        ) {
            // ── Reply quote block ──────────────────────────────────────
            if (hasReply) {
                val quotePreview = when (replyType) {
                    "image" -> "📷 Фото"
                    "voice" -> "🎤 Голосовое"
                    "file" -> "📎 Файл"
                    else -> replyContent ?: ""
                }
                val quoteTapId = replyPreview?.id ?: replyFromHistory?.id ?: message.replyToId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            textColor.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp),
                        )
                        .then(
                            if (onQuoteTap != null && quoteTapId != null)
                                Modifier.clickable { onQuoteTap(quoteTapId) }
                            else Modifier
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .padding(bottom = 4.dp),
                ) {
                    Text(
                        text = replyUsername,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = quotePreview,
                        fontSize = 11.sp,
                        maxLines = 1,
                        color = textColor.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            if (!isMine && effectiveType != "image") {
                Text(
                    text = message.sender.username,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }

            when (effectiveType) {
                "image" -> ImageBubble(message, isMine, bubbleColor, textColor, onLongClick = onLongClick)
                "voice" -> VoiceBubble(message, isMine, textColor)
                "file"  -> FileBubble(message, isMine, textColor)
                else -> {
                    Text(text = message.content ?: "", color = textColor)
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (message.editedAt != null) {
                            Text(
                                text = "ред.",
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.5f),
                            )
                        }
                        Text(
                            text = formatMessageTime(message.createdAt),
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.6f),
                        )
                        if (isMine) { DeliveryIcon(message, textColor) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBubble(message: Message, isMine: Boolean, bubbleColor: Color, textColor: Color, onLongClick: () -> Unit = {}) {
    val att = message.attachment
    var showFullscreen by remember { mutableStateOf(false) }
    val hasCaption = !message.content.isNullOrBlank()
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp,
    )

    Column(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(shape)
            .background(bubbleColor)
            .combinedClickable(
                onClick = { showFullscreen = true },
                onLongClick = onLongClick,
            ),
    ) {
        // ── Photo ──────────────────────────────────────────────────────
        Box {
            val imageUrl = att?.thumbnailUrl ?: att?.url
            if (imageUrl != null) {
                val ratio = if (att?.width != null && att.height != null && att.width > 0)
                    att.width.toFloat() / att.height.toFloat() else 1f
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Фото",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 2f)),
                )
            } else {
                Box(
                    Modifier.size(200.dp, 150.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            // Timestamp overlay — only when no caption
            if (!hasCaption) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(text = formatMessageTime(message.createdAt), fontSize = 10.sp, color = Color.White)
                    if (isMine) { DeliveryIcon(message, Color.White) }
                }
            }
        }

        // ── Caption + timestamp (when caption exists) ──────────────────
        if (hasCaption) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(text = message.content!!, color = textColor, fontSize = 14.sp)
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = formatMessageTime(message.createdAt), fontSize = 10.sp, color = textColor.copy(alpha = 0.6f))
                    if (isMine) { DeliveryIcon(message, textColor) }
                }
            }
        }
    }

    if (showFullscreen && att?.url != null) {
        FullscreenImageViewer(url = att.url, onDismiss = { showFullscreen = false })
    }
}

@Composable
private fun FullscreenImageViewer(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val up = waitForUpOrCancellation()
                        if (up != null) onDismiss()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = "Фото",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Icon(Icons.Default.Close, "Закрыть", tint = Color.White)
            }
        }
    }
}

@Composable
private fun VoiceBubble(message: Message, isMine: Boolean, textColor: Color) {
    val att = message.attachment
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(false) }
    var isPreparing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var loadError by remember { mutableStateOf(false) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    var playbackSpeed by remember { mutableStateOf(1f) }

    DisposableEffect(Unit) {
        onDispose { player.value?.release(); player.value = null }
    }

    LaunchedEffect(playbackSpeed) {
        val mp = player.value ?: return@LaunchedEffect
        if (isPlaying) {
            try { mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = player.value
            if (mp != null && mp.isPlaying && !isSeeking) {
                progress = mp.currentPosition.toFloat() / mp.duration.toFloat()
            }
            delay(200)
        }
    }

    fun startPlayback() {
        val TAG = "VoiceBubble"
        val url = att?.url ?: run {
            android.util.Log.e(TAG, "startPlayback: att.url is NULL, att=$att, message.id=${message.id}")
            loadError = true; return
        }
        android.util.Log.d(TAG, "startPlayback: url=$url  msgId=${message.id}")
        isPreparing = true
        scope.launch {
            try {
                val cacheFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val file = java.io.File(context.cacheDir, "voice_${message.id}.m4a")
                    android.util.Log.d(TAG, "cacheFile=${file.absolutePath} exists=${file.exists()} size=${file.length()}")
                    if (!file.exists() || file.length() == 0L) {
                        android.util.Log.d(TAG, "Downloading from $url")
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        val req = okhttp3.Request.Builder().url(url).build()
                        client.newCall(req).execute().use { resp ->
                            android.util.Log.d(TAG, "HTTP response: ${resp.code} ${resp.message} contentType=${resp.body?.contentType()}")
                            if (!resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                android.util.Log.e(TAG, "HTTP error ${resp.code}: $body")
                                throw Exception("HTTP ${resp.code}: $body")
                            }
                            resp.body?.byteStream()?.use { input ->
                                file.outputStream().use { out -> input.copyTo(out) }
                            }
                            android.util.Log.d(TAG, "Downloaded OK, file size=${file.length()}")
                        }
                    } else {
                        android.util.Log.d(TAG, "Using cached file, size=${file.length()}")
                    }
                    file
                }
                android.util.Log.d(TAG, "Creating MediaPlayer from ${cacheFile.absolutePath}")
                player.value = MediaPlayer().apply {
                    setDataSource(cacheFile.absolutePath)
                    setOnErrorListener { _, what, extra ->
                        android.util.Log.e(TAG, "MediaPlayer.onError what=$what extra=$extra")
                        loadError = true; isPlaying = false; isPreparing = false; true
                    }
                    setOnPreparedListener {
                        android.util.Log.d(TAG, "MediaPlayer prepared, starting playback duration=${it.duration}ms")
                        isPreparing = false
                        try { if (playbackSpeed != 1f) playbackParams = playbackParams.setSpeed(playbackSpeed) } catch (_: Exception) {}
                        start(); isPlaying = true
                    }
                    setOnCompletionListener { android.util.Log.d(TAG, "Playback completed"); isPlaying = false; progress = 0f }
                    prepareAsync()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "startPlayback FAILED", e)
                loadError = true; isPreparing = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (loadError || isPreparing) return@IconButton
                    if (isPlaying) {
                        player.value?.pause()
                        isPlaying = false
                    } else {
                        if (player.value == null) startPlayback()
                        else { player.value?.start(); isPlaying = true }
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                if (isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = textColor,
                    )
                } else {
                    Icon(
                        imageVector = when {
                            loadError -> Icons.Default.Close
                            isPlaying -> Icons.Default.Pause
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = "Воспроизвести",
                        tint = if (loadError) MaterialTheme.colorScheme.error else textColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (loadError) {
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                )
            } else {
                AudioTrack(
                    value = if (isSeeking) seekValue else progress,
                    onValueChange = { isSeeking = true; seekValue = it },
                    onValueChangeFinished = {
                        player.value?.let { mp ->
                            mp.seekTo((seekValue * mp.duration).toInt().coerceAtLeast(0))
                        }
                        progress = seekValue
                        isSeeking = false
                    },
                    enabled = !isPreparing && player.value != null,
                    trackColor = textColor,
                    modifier = Modifier.weight(1f).height(20.dp).padding(end = 6.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (loadError) "Ошибка загрузки"
                           else att?.duration?.let { formatDuration(it.toInt()) } ?: "—",
                    fontSize = 10.sp,
                    color = if (loadError) MaterialTheme.colorScheme.error else textColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp),
                )
                if (player.value != null || isPlaying || isPreparing) {
                    val speedLabel = when (playbackSpeed) { 1f -> "1×"; 1.5f -> "1.5×"; else -> "2×" }
                    val nextSpeed = when (playbackSpeed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = speedLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clickable { playbackSpeed = nextSpeed }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatMessageTime(message.createdAt),
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.6f),
                )
                if (isMine) DeliveryIcon(message, textColor)
            }
        }
    }
}

@Composable
private fun FileBubble(message: Message, isMine: Boolean, textColor: Color) {
    val att = message.attachment
    val context = LocalContext.current

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        att?.url?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(Intent.createChooser(intent, "Открыть файл"))
                        }
                    }
                }
            },
        ) {
            Icon(
                imageVector = getFileIcon(att?.mimeType),
                contentDescription = "Файл",
                tint = textColor,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = att?.fileName ?: "Файл",
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (att?.fileSize != null) {
                    Text(
                        text = formatFileSize(att.fileSize),
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.7f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = formatMessageTime(message.createdAt),
                fontSize = 10.sp,
                color = textColor.copy(alpha = 0.6f),
            )
            if (isMine) { DeliveryIcon(message, textColor) }
        }
    }
}

private fun getFileIcon(mimeType: String?) = when {
    mimeType == null -> Icons.Default.Description
    mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    mimeType.startsWith("image/") -> Icons.Default.PhotoLibrary
    else -> Icons.Default.Description
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> "%.1f МБ".format(bytes / (1024.0 * 1024.0))
}


@Composable
private fun TelegramCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (checked) Color(0xFF4CAF50) else Color.Transparent,
        animationSpec = spring(),
        label = "cbColor",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "cbAlpha",
    )
    val strokeColor = if (checked) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.85f)
    Canvas(modifier = modifier.size(24.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(r, r)
        // fill
        drawCircle(color = color, radius = r, center = center)
        // border
        drawCircle(color = strokeColor, radius = r - 1.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
        // checkmark
        if (checkAlpha > 0f) {
            val sw = 2.dp.toPx()
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - r * 0.35f, center.y)
                lineTo(center.x - r * 0.05f, center.y + r * 0.3f)
                lineTo(center.x + r * 0.4f, center.y - r * 0.3f)
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = checkAlpha),
                style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun AudioTrack(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean = true,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val trackHeightPx = with(density) { 2.5.dp.toPx() }
    val thumbRadiusPx = with(density) { 5.dp.toPx() }
    Canvas(modifier = modifier.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            onValueChange((down.position.x / size.width).coerceIn(0f, 1f))
            while (true) {
                val event = awaitPointerEvent()
                val ch = event.changes.firstOrNull() ?: break
                if (!ch.pressed) break
                ch.consume()
                onValueChange((ch.position.x / size.width).coerceIn(0f, 1f))
            }
            onValueChangeFinished()
        }
    }) {
        val cy = size.height / 2f
        val trackStart = thumbRadiusPx
        val trackEnd = size.width - thumbRadiusPx
        val thumbX = (trackStart + (trackEnd - trackStart) * value.coerceIn(0f, 1f))
        drawLine(
            color = trackColor.copy(alpha = 0.3f),
            start = Offset(trackStart, cy),
            end = Offset(trackEnd, cy),
            strokeWidth = trackHeightPx,
            cap = StrokeCap.Round,
        )
        if (value > 0f) {
            drawLine(
                color = trackColor,
                start = Offset(trackStart, cy),
                end = Offset(thumbX, cy),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color = trackColor, radius = thumbRadiusPx, center = Offset(thumbX, cy))
    }
}

/**
 * Returns the correct delivery-status icon for own messages:
 *  !isDelivered → Done (1 gray)       = sending, not on server yet
 *  isDelivered && !readByOthers → DoneAll (2 gray)  = delivered, not read
 *  isDelivered && readByOthers  → DoneAll (2 green) = read
 */
@Composable
private fun DeliveryIcon(message: ru.yakut54.ktoto.data.model.Message, textColor: Color) {
    val readColor = Color(0xFF4CAF50)
    Icon(
        imageVector = if (message.isDelivered) Icons.Default.DoneAll else Icons.Default.Done,
        contentDescription = null,
        tint = when {
            !message.isDelivered  -> textColor.copy(alpha = 0.45f)
            message.readByOthers  -> readColor
            else                  -> textColor.copy(alpha = 0.45f)
        },
        modifier = Modifier.size(14.dp),
    )
}
