package ru.yakut54.ktoto.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val isRecording by vm.isRecording.collectAsState()
    val recordingSeconds by vm.recordingSeconds.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(conversationId) { vm.init(conversationId) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    var text by remember { mutableStateOf("") }
    var showAttachSheet by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    // Image picker (gallery)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { vm.sendMediaMessage(context, it, "image") }
    }

    // Camera photo
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraUri?.let { vm.sendMediaMessage(context, it, "image") }
    }

    // File picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { vm.sendMediaMessage(context, it, "file") }
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
                if (isRecording) {
                    // Voice recording bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { vm.cancelRecording() }) {
                            Icon(Icons.Default.Close, "Отмена", tint = MaterialTheme.colorScheme.error)
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatDuration(recordingSeconds),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        FilledIconButton(onClick = { vm.stopRecordingAndSend(context) }) {
                            Icon(Icons.Default.Stop, "Отправить")
                        }
                    }
                } else {
                    // Normal input bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        IconButton(onClick = { showAttachSheet = true }) {
                            Icon(Icons.Default.AttachFile, "Прикрепить")
                        }
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
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        Spacer(Modifier.width(8.dp))
                        if (sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp).padding(4.dp),
                            )
                        } else if (text.isBlank()) {
                            // Mic button when text is empty
                            FilledIconButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    }
                                    vm.startRecording(context)
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Default.Mic, "Голосовое")
                            }
                        } else {
                            FilledIconButton(
                                onClick = { vm.sendMessage(text); text = "" },
                                enabled = text.isNotBlank(),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg, isMine = msg.sender.id == currentUserId)
            }
        }
    }

    // Attach bottom sheet
    if (showAttachSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Прикрепить", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
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
                    icon = { Icon(Icons.Default.PhotoCamera, null) },
                    label = "Камера",
                    onClick = {
                        showAttachSheet = false
                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                        val photoFile = java.io.File(context.externalCacheDir, "photo_${System.currentTimeMillis()}.jpg")
                        cameraUri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", photoFile
                        )
                        cameraUri?.let { cameraLauncher.launch(it) }
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
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AttachOption(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
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
                "image" -> ImageBubble(
                    message = message,
                    isMine = isMine,
                    bubbleColor = bubbleColor,
                    textColor = textColor,
                )
                "voice" -> VoiceBubble(
                    message = message,
                    isMine = isMine,
                    textColor = textColor,
                )
                "file" -> FileBubble(
                    message = message,
                    isMine = isMine,
                    textColor = textColor,
                )
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
            .pointerInput(Unit) { detectTapGestures { showFullscreen = true } },
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
            ) {
                CircularProgressIndicator()
            }
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
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = "Полноэкранное фото",
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
        onDispose {
            player.value?.release()
            player.value = null
        }
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
            IconButton(
                onClick = {
                    if (isPlaying) {
                        player.value?.pause()
                        isPlaying = false
                    } else {
                        if (player.value == null && att?.url != null) {
                            player.value = MediaPlayer().apply {
                                setDataSource(att.url)
                                prepareAsync()
                                setOnPreparedListener { start(); isPlaying = true }
                                setOnCompletionListener { isPlaying = false; progress = 0f }
                            }
                        } else {
                            player.value?.start()
                            isPlaying = true
                        }
                    }
                },
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Воспроизвести",
                    tint = textColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(2.dp))
                val durationText = att?.duration?.let { formatDuration(it.toInt()) } ?: "—"
                Text(durationText, fontSize = 10.sp, color = textColor.copy(alpha = 0.7f))
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
                detectTapGestures {
                    att?.url?.let { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(Intent.createChooser(intent, "Открыть файл"))
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
