package ru.yakut54.ktoto.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
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
    val voiceState by vm.voiceState.collectAsState()
    val recordingSeconds by vm.recordingSeconds.collectAsState()
    val previewDuration by vm.previewDuration.collectAsState()
    val previewPlaying by vm.previewPlaying.collectAsState()
    val previewProgress by vm.previewProgress.collectAsState()

    val context = LocalContext.current
    val density = LocalDensity.current

    LaunchedEffect(conversationId) { vm.init(conversationId) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    var text by remember { mutableStateOf("") }
    var showAttachSheet by remember { mutableStateOf(false) }
    var recordingCancelHint by remember { mutableStateOf(false) }

    // Reset cancel hint when leaving RECORDING state
    LaunchedEffect(voiceState) {
        if (voiceState != VoiceState.RECORDING) recordingCancelHint = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

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

    fun openCamera() {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        val photoFile = java.io.File(context.externalCacheDir, "photo_${System.currentTimeMillis()}.jpg")
        cameraUri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", photoFile
        )
        cameraUri?.let { cameraLauncher.launch(it) }
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

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .imePadding(),
                        ) {
                            // ── Input row ──────────────────────────────────────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                // 📎 Attach
                                IconButton(onClick = { showAttachSheet = true }) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = "Прикрепить",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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

                                // 📷 Camera — only when idle + text blank
                                if (text.isBlank() && !sending && voiceState == VoiceState.IDLE) {
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
                                                            // Long press confirmed → start recording
                                                            permissionLauncher.launch(
                                                                arrayOf(Manifest.permission.RECORD_AUDIO)
                                                            )
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
                        }
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
                        if (Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
                        } else {
                            permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                        }
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
                .widthIn(max = 300.dp)
                .background(
                    color = if (message.type == "image") Color.Transparent else bubbleColor,
                    shape = RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp,
                    ),
                )
                .then(
                    if (message.type != "image") Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    else Modifier
                ),
        ) {
            if (!isMine && message.type != "image") {
                Text(
                    text = message.sender.username,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }

            when (message.type) {
                "image" -> ImageBubble(message, isMine, bubbleColor, textColor)
                "voice" -> VoiceBubble(message, isMine, textColor)
                "file"  -> FileBubble(message, isMine, textColor)
                else -> {
                    Text(text = message.content ?: "", color = textColor)
                    Text(
                        text = formatMessageTime(message.createdAt),
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    )
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
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
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

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (isPlaying) {
                    player.value?.pause(); isPlaying = false
                } else {
                    if (player.value == null && att?.url != null) {
                        player.value = MediaPlayer().apply {
                            setDataSource(att.url)
                            prepareAsync()
                            setOnPreparedListener { start(); isPlaying = true }
                            setOnCompletionListener { isPlaying = false; progress = 0f }
                        }
                    } else {
                        player.value?.start(); isPlaying = true
                    }
                }
            }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Воспроизвести",
                    tint = textColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(2.dp))
                Text(
                    att?.duration?.let { formatDuration(it.toInt()) } ?: "—",
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.7f),
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
