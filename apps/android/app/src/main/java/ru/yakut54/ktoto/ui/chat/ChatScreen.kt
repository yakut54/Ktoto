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
import ru.yakut54.ktoto.data.model.Attachment
import ru.yakut54.ktoto.data.model.Message
import ru.yakut54.ktoto.utils.formatMessageTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val voiceState by vm.voiceState.collectAsState()
    val recordingSeconds by vm.recordingSeconds.collectAsState()
    val previewDuration by vm.previewDuration.collectAsState()
    val previewPlaying by vm.previewPlaying.collectAsState()
    val previewProgress by vm.previewProgress.collectAsState()
    val selectedMessage by vm.selectedMessage.collectAsState()
    val replyTo by vm.replyTo.collectAsState()
    val editingMessage by vm.editingMessage.collectAsState()

    val context = LocalContext.current
    val density = LocalDensity.current
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)

    LaunchedEffect(conversationId) { vm.init(conversationId) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // Sync edit mode: populate text field with message content
    var text by remember { mutableStateOf("") }
    LaunchedEffect(editingMessage) {
        if (editingMessage != null) text = editingMessage!!.content ?: ""
    }

    val activity = context as? android.app.Activity

    var showAttachSheet by remember { mutableStateOf(false) }
    var recordingCancelHint by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

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
    ) { uri: Uri? -> uri?.let { vm.sendMediaMessage(context, it, "image") } }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.let { vm.sendMediaMessage(context, it, "image") } }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { vm.sendMediaMessage(context, it, "file") } }

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
            TopAppBar(
                title = {
                    Column {
                        Text(conversationName, style = MaterialTheme.typography.titleMedium)
                        if (isTyping) {
                            Text("печатает...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
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
            Surface(shadowElevation = 4.dp, tonalElevation = 2.dp) {
                when (voiceState) {
                    // ── LOCKED: recording without holding ───────────────────────────────
                    VoiceState.LOCKED -> LockedRecordingBar(
                        seconds = recordingSeconds,
                        onCancel = { vm.cancelRecording() },
                        onSend = { vm.sendVoiceFromLocked() },
                    )

                    // ── PREVIEW: stopped, user decides to listen / send / delete ────────
                    VoiceState.PREVIEW -> VoicePreviewBar(
                        durationSeconds = previewDuration,
                        isPlaying = previewPlaying,
                        progress = previewProgress,
                        onDelete = { vm.deleteVoicePreview() },
                        onTogglePlay = { vm.togglePreviewPlayback() },
                        onSend = { vm.sendVoicePreview() },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                val isSelected = selectedMessage?.id == msg.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) Modifier.background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) else Modifier
                        )
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { vm.selectMessage(msg) },
                        ),
                ) {
                    MessageBubble(
                        message = msg,
                        isMine = msg.sender.id == currentUserId,
                        allMessages = messages,
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Удалить сообщение?") },
            text = { Text("Сообщение будет удалено у всех участников.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMessage(showDeleteConfirm!!)
                    showDeleteConfirm = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Отмена") }
            },
        )
    }

    // Message action bottom sheet
    if (selectedMessage != null) {
        val msg = selectedMessage!!
        val isMine = msg.sender.id == currentUserId
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { vm.clearSelection() },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                // Reply — always
                AttachOption(
                    icon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                    label = "Ответить",
                    onClick = {
                        vm.setReplyTo(msg)
                        vm.clearSelection()
                    },
                )
                // Copy — text only
                if (msg.type == "text" && !msg.content.isNullOrBlank()) {
                    AttachOption(
                        icon = { Icon(Icons.Default.ContentCopy, null) },
                        label = "Копировать",
                        onClick = {
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("message", msg.content)
                            )
                            vm.clearSelection()
                        },
                    )
                }
                // Edit — own text only
                if (isMine && msg.type == "text") {
                    AttachOption(
                        icon = { Icon(Icons.Default.Edit, null) },
                        label = "Редактировать",
                        onClick = {
                            vm.startEditing(msg)
                            vm.clearSelection()
                        },
                    )
                }
                // Delete — own messages only
                if (isMine) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    AttachOption(
                        icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        label = "Удалить",
                        onClick = {
                            showDeleteConfirm = msg.id
                            vm.clearSelection()
                        },
                    )
                }
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
}

// ── LOCKED recording bar ───────────────────────────────────────────────────────
// Shown when user slid UP to lock. Recording continues hands-free.
// Buttons: ❌ Cancel | ▶ Send

@Composable
private fun LockedRecordingBar(
    seconds: Int,
    onCancel: () -> Unit,
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
        // Lock icon
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        // Red dot + timer
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(Color.Red)
        )
        Spacer(Modifier.width(6.dp))
        Text(formatDuration(seconds), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        // ❌ Cancel
        IconButton(onClick = onCancel) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Отмена",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // ▶ Send immediately
        FilledIconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 🗑 Delete
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.error,
            )
        }

        // ▶/⏸ Play + progress bar + duration
        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Прослушать",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatDuration(durationSeconds),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(4.dp))

        // ✓ Send
        FilledIconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
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
private fun MessageBubble(message: Message, isMine: Boolean, allMessages: List<Message> = emptyList()) {
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant

    val replyOrigin = if (message.replyToId != null) allMessages.find { it.id == message.replyToId } else null

    val effectiveType = when {
        message.type == "image" || message.attachment?.mimeType?.startsWith("image/") == true -> "image"
        message.type == "voice" || message.attachment?.mimeType?.startsWith("audio/") == true -> "voice"
        message.type == "file"  || (message.attachment != null && message.type !in listOf("text", null)) -> "file"
        else -> "text"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
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
            if (replyOrigin != null) {
                val quotePreview = when (replyOrigin.type) {
                    "image" -> "📷 Фото"
                    "voice" -> "🎤 Голосовое"
                    "file" -> "📎 Файл"
                    else -> replyOrigin.content ?: ""
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            textColor.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .padding(bottom = 4.dp),
                ) {
                    Text(
                        text = replyOrigin.sender.username,
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
                "image" -> ImageBubble(message, isMine, bubbleColor, textColor)
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
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBubble(message: Message, isMine: Boolean, bubbleColor: Color, textColor: Color) {
    val att = message.attachment
    var showFullscreen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp,
    )

    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(shape)
            .background(bubbleColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val up = waitForUpOrCancellation()
                    if (up != null) showFullscreen = true
                }
            },
    ) {
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
        Text(
            text = formatMessageTime(message.createdAt),
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
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
    var loadError by remember { mutableStateOf(false) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose { player.value?.release(); player.value = null }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = player.value
            if (mp != null && mp.isPlaying) {
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
                        isPreparing = false; start(); isPlaying = true
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (loadError || isPreparing) return@IconButton
                if (isPlaying) {
                    player.value?.pause()
                    isPlaying = false
                } else {
                    if (player.value == null) startPlayback()
                    else { player.value?.start(); isPlaying = true }
                }
            }) {
                if (isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
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
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (loadError) MaterialTheme.colorScheme.error else textColor,
                    trackColor = textColor.copy(alpha = 0.2f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (loadError) "Ошибка загрузки"
                           else att?.duration?.let { formatDuration(it.toInt()) } ?: "—",
                    fontSize = 10.sp,
                    color = if (loadError) MaterialTheme.colorScheme.error else textColor.copy(alpha = 0.7f),
                )
            }
        }
        Text(
            text = formatMessageTime(message.createdAt),
            fontSize = 10.sp,
            color = textColor.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
        )
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
        Text(
            text = formatMessageTime(message.createdAt),
            fontSize = 10.sp,
            color = textColor.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
        )
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
