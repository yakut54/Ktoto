package ru.yakut54.ktoto.ui.chat

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.EditMessageRequest
import ru.yakut54.ktoto.data.model.Message
import ru.yakut54.ktoto.data.model.SendMessageRequest
import ru.yakut54.ktoto.data.model.Sender
import java.time.Instant
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore
import java.io.File
import java.nio.ByteBuffer

/**
 * Voice recording state machine:
 *
 *   IDLE → (long-press mic) → RECORDING
 *   RECORDING → (slide up)  → LOCKED   (hands-free)
 *   RECORDING → (release)   → PREVIEW  (single-segment preview)
 *   LOCKED   → (⏸)         → PAUSED   (recorder stopped, file ready to play)
 *   LOCKED   → (release)    → PREVIEW
 *   PAUSED   → (🎤)         → LOCKED   (new segment starts, continues timer)
 *   PAUSED   → (→ send)     → IDLE     (segments merged + uploaded)
 *   PAUSED / LOCKED → (✕)  → IDLE     (all files discarded)
 *   PREVIEW  → (→ send)     → IDLE
 *   PREVIEW  → (🗑)         → IDLE
 */
enum class VoiceState { IDLE, RECORDING, LOCKED, PAUSED, PREVIEW }

class ChatViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    val socketManager: SocketManager,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _selectedMessage = MutableStateFlow<Message?>(null)
    val selectedMessage: StateFlow<Message?> = _selectedMessage

    private val _replyTo = MutableStateFlow<Message?>(null)
    val replyTo: StateFlow<Message?> = _replyTo

    private val _editingMessage = MutableStateFlow<Message?>(null)
    val editingMessage: StateFlow<Message?> = _editingMessage

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress

    private val _conversationsForPicker = MutableStateFlow<List<ru.yakut54.ktoto.data.model.Conversation>>(emptyList())
    val conversationsForPicker: StateFlow<List<ru.yakut54.ktoto.data.model.Conversation>> = _conversationsForPicker

    private val _otherUserId = MutableStateFlow("")
    /** true when the chat partner is currently online */
    val partnerOnline: StateFlow<Boolean> = combine(socketManager.onlineUsers, _otherUserId) { online, otherId ->
        otherId.isNotBlank() && otherId in online
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Voice state ────────────────────────────────────────────────────────────
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState

    /** Counts up while recording (across all segments — never resets between pause/resume) */
    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds

    /** Total duration saved when recording finishes (shown in PREVIEW bar) */
    private val _previewDuration = MutableStateFlow(0)
    val previewDuration: StateFlow<Int> = _previewDuration

    /** Preview playback state (PREVIEW + PAUSED bars) */
    private val _previewPlaying = MutableStateFlow(false)
    val previewPlaying: StateFlow<Boolean> = _previewPlaying

    private val _previewProgress = MutableStateFlow(0f)
    val previewProgress: StateFlow<Float> = _previewProgress

    // ── Internal ───────────────────────────────────────────────────────────────
    private var conversationId: String = ""
    private var currentUserId: String = ""
    private var currentUsername: String = ""
    private var recorder: MediaRecorder? = null

    /**
     * The currently active recording file (null when in PAUSED state —
     * the file was moved to [voiceSegments]).
     */
    private var voiceFile: File? = null

    /** Completed segments from previous pause/resume cycles. */
    private val voiceSegments = mutableListOf<File>()

    private var recordingTimerJob: kotlinx.coroutines.Job? = null
    private var previewPlayer: MediaPlayer? = null
    private var previewProgressJob: kotlinx.coroutines.Job? = null

    /** File to play in preview / paused bars: last completed segment or current voiceFile. */
    private val currentPreviewFile: File?
        get() = voiceFile ?: voiceSegments.lastOrNull()

    // ── Init ───────────────────────────────────────────────────────────────────

    fun init(convId: String, otherUserId: String = "") {
        conversationId = convId
        if (otherUserId.isNotBlank()) _otherUserId.value = otherUserId
        viewModelScope.launch {
            currentUserId = tokenStore.userId.first()
            currentUsername = tokenStore.username.first() ?: ""
        }
        loadHistory()
        subscribeToMessages()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val msgs = api.getMessages("Bearer $token", conversationId)
                _messages.value = msgs
                // Discover partner's ID from history if not yet known
                if (_otherUserId.value.isBlank()) {
                    val myId = tokenStore.userId.first()
                    msgs.firstOrNull { it.sender.id != myId }?.sender?.id?.let { _otherUserId.value = it }
                }
            }
        }
    }

    private fun subscribeToMessages() {
        viewModelScope.launch {
            socketManager.messages
                .filter { it.conversationId == conversationId }
                .collect { msg ->
                    if (_messages.value.none { it.id == msg.id }) {
                        _messages.value = _messages.value + msg
                        markConversationRead()
                    }
                }
        }
        viewModelScope.launch {
            socketManager.typing
                .filter { it.first == conversationId }
                .collect {
                    _isTyping.value = true
                    delay(3000)
                    _isTyping.value = false
                }
        }
        viewModelScope.launch {
            socketManager.editedMessages
                .filter { it.conversationId == conversationId }
                .collect { edited ->
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == edited.id) msg.copy(content = edited.content, editedAt = edited.editedAt)
                        else msg
                    }
                }
        }
        viewModelScope.launch {
            socketManager.deletedMessageIds
                .filter { it.second == conversationId }
                .collect { (msgId, _) ->
                    _messages.value = _messages.value.filter { it.id != msgId }
                }
        }
        viewModelScope.launch {
            socketManager.messagesRead
                .filter { it.conversationId == conversationId && it.readerId != currentUserId }
                .collect { event ->
                    _messages.value = _messages.value.map { msg ->
                        if (msg.sender.id == currentUserId && !msg.readByOthers && msg.createdAt <= event.readAt)
                            msg.copy(readByOthers = true)
                        else msg
                    }
                }
        }
    }

    // ── Text / media messages ──────────────────────────────────────────────────

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        val replyId = _replyTo.value?.id
        _replyTo.value = null

        // Optimistic: show message immediately with isDelivered=false (1 gray check)
        val tempId = "temp_${System.currentTimeMillis()}"
        val optimistic = Message(
            id = tempId,
            content = content,
            type = "text",
            createdAt = Instant.now().toString(),
            editedAt = null,
            replyToId = replyId,
            sender = Sender(id = currentUserId, username = currentUsername, avatarUrl = null),
            conversationId = conversationId,
            isDelivered = false,
        )
        _messages.value = _messages.value + optimistic

        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: run {
                    _messages.value = _messages.value.filter { it.id != tempId }
                    return@launch
                }
                val msg = api.sendMessage(
                    "Bearer $token", conversationId,
                    SendMessageRequest(content, reply_to_id = replyId),
                )
                // Replace optimistic with confirmed message (isDelivered=true by default)
                _messages.value = _messages.value
                    .filter { it.id != tempId }
                    .let { list -> if (list.none { it.id == msg.id }) list + msg else list }
            }.onFailure {
                // Remove optimistic on failure
                _messages.value = _messages.value.filter { it.id != tempId }
                android.util.Log.e("ChatViewModel", "sendMessage failed", it)
            }
        }
    }

    fun selectMessage(msg: Message) { _selectedMessage.value = msg }
    fun clearSelection() { _selectedMessage.value = null }

    fun setReplyTo(msg: Message) { _replyTo.value = msg }
    fun clearReplyTo() { _replyTo.value = null }

    fun startEditing(msg: Message) { _editingMessage.value = msg }
    fun cancelEditing() { _editingMessage.value = null }

    fun deleteMessage(msgId: String) {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.deleteMessage("Bearer $token", conversationId, msgId)
                _messages.value = _messages.value.filter { it.id != msgId }
            }.onFailure { android.util.Log.e("ChatViewModel", "deleteMessage failed", it) }
        }
        clearSelection()
    }

    fun deleteMessageForMe(msgId: String) {
        _messages.value = _messages.value.filter { it.id != msgId }
        clearSelection()
    }

    fun loadConversationsForPicker() {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                _conversationsForPicker.value = api.getConversations("Bearer $token")
            }
        }
    }

    fun forwardMessage(message: Message, targetConversationId: String) {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val msg = api.sendMessage(
                    "Bearer $token", targetConversationId,
                    SendMessageRequest(forward_message_id = message.id),
                )
                if (targetConversationId == conversationId) {
                    if (_messages.value.none { it.id == msg.id }) _messages.value = _messages.value + msg
                }
            }.onFailure { android.util.Log.e("ChatViewModel", "forwardMessage failed", it) }
        }
    }

    fun saveEdit(newContent: String) {
        val msg = _editingMessage.value ?: return
        if (newContent.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val updated = api.editMessage("Bearer $token", conversationId, msg.id, EditMessageRequest(newContent))
                _messages.value = _messages.value.map {
                    if (it.id == updated.id) it.copy(content = updated.content, editedAt = updated.editedAt) else it
                }
            }.onFailure { android.util.Log.e("ChatViewModel", "saveEdit failed", it) }
        }
        _editingMessage.value = null
    }

    fun sendMediaMessage(context: Context, uri: Uri, type: String) {
        val replyId = _replyTo.value?.id
        _replyTo.value = null
        viewModelScope.launch {
            _sending.value = true
            _uploadProgress.value = 0f
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.readBytes()
                inputStream.close()
                val fileName = getFileName(context, uri) ?: "file"
                val filePart = MultipartBody.Part.createFormData(
                    "file", fileName, bytes.toRequestBody(mimeType.toMediaType())
                )
                val metaMap = mutableMapOf<String, Any>("type" to type)
                if (replyId != null) metaMap["reply_to_id"] = replyId
                val metaPart = Gson().toJson(metaMap)
                    .toRequestBody("text/plain".toMediaType())
                _uploadProgress.value = 0.5f
                val msg = api.uploadMessage("Bearer $token", conversationId, filePart, metaPart)
                _uploadProgress.value = 1f
                if (_messages.value.none { it.id == msg.id }) _messages.value = _messages.value + msg
            }
            _sending.value = false
            _uploadProgress.value = null
        }
    }

    // ── Voice: recording lifecycle ─────────────────────────────────────────────

    /** Long-press mic → starts recording (first segment) */
    fun startRecording(context: Context) {
        if (_voiceState.value != VoiceState.IDLE) return
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        voiceFile = file
        startMediaRecorder(context, file)
        _voiceState.value = VoiceState.RECORDING
        _recordingSeconds.value = 0
        startTimer()
    }

    /** Slide UP while holding → locks recording (continues hands-free) */
    fun lockRecording() {
        if (_voiceState.value == VoiceState.RECORDING) {
            _voiceState.value = VoiceState.LOCKED
        }
    }

    /**
     * Finger released without sliding → stops recording, enters PREVIEW.
     * If there are prior segments, they are merged synchronously before preview.
     */
    fun stopRecordingToPreview() {
        if (_voiceState.value !in listOf(VoiceState.RECORDING, VoiceState.LOCKED)) return
        recordingTimerJob?.cancel()
        _previewDuration.value = _recordingSeconds.value
        stopRecorder()

        if (voiceSegments.isNotEmpty()) {
            // Merge all segments + current file into one for preview
            val currentFile = voiceFile
            voiceFile = null
            viewModelScope.launch {
                val allSegments = voiceSegments.toList() + listOfNotNull(currentFile)
                val merged = withContext(Dispatchers.IO) {
                    val out = File(allSegments[0].parent, "voice_merged_${System.currentTimeMillis()}.m4a")
                    concatenateM4aFiles(allSegments, out)
                    out
                }
                voiceSegments.forEach { it.delete() }
                voiceSegments.clear()
                currentFile?.delete()
                voiceFile = merged
            }
        }
        _voiceState.value = VoiceState.PREVIEW
    }

    /** Slide LEFT (or tap ✕ in any recording bar) → discards everything */
    fun cancelRecording() {
        recordingTimerJob?.cancel()
        stopRecorder()
        releasePreviewPlayer()
        voiceFile?.delete()
        voiceFile = null
        voiceSegments.forEach { it.delete() }
        voiceSegments.clear()
        _voiceState.value = VoiceState.IDLE
        _recordingSeconds.value = 0
        _previewDuration.value = 0
    }

    // ── Voice: LOCKED bar actions ──────────────────────────────────────────────

    /**
     * Tap ⏸ in LOCKED bar:
     *   - Stops the recorder (file is finalized and playable)
     *   - Moves file to [voiceSegments]
     *   - Enters PAUSED state where user can listen or continue recording
     */
    fun pauseRecording() {
        if (_voiceState.value != VoiceState.LOCKED) return
        recordingTimerJob?.cancel()
        stopRecorder()
        voiceFile?.let { voiceSegments.add(it) }
        voiceFile = null
        _previewDuration.value = _recordingSeconds.value
        // Reset preview player so next togglePreviewPlayback() loads fresh file
        releasePreviewPlayer()
        _voiceState.value = VoiceState.PAUSED
    }

    /**
     * Tap 🎤 in PAUSED bar:
     *   - Starts a new recording segment
     *   - Timer continues from where it stopped
     */
    fun resumeRecording(context: Context) {
        if (_voiceState.value != VoiceState.PAUSED) return
        releasePreviewPlayer()
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        voiceFile = file
        startMediaRecorder(context, file)
        _voiceState.value = VoiceState.LOCKED
        startTimer()
    }

    /** Tap → in LOCKED bar → stops + sends immediately (no preview) */
    fun sendVoiceFromLocked() {
        if (_voiceState.value != VoiceState.LOCKED) return
        recordingTimerJob?.cancel()
        val duration = _recordingSeconds.value
        val replyId = _replyTo.value?.id
        _replyTo.value = null
        stopRecorder()
        val currentFile = voiceFile
        voiceFile = null
        val allSegments = voiceSegments.toList() + listOfNotNull(currentFile)
        voiceSegments.clear()
        resetVoiceState()
        uploadVoiceSegments(allSegments, duration, replyId)
    }

    /** Tap → in PAUSED bar → sends all recorded segments */
    fun sendVoicePaused() {
        if (_voiceState.value != VoiceState.PAUSED) return
        val duration = _recordingSeconds.value
        val replyId = _replyTo.value?.id
        _replyTo.value = null
        releasePreviewPlayer()
        val allSegments = voiceSegments.toList()
        voiceSegments.clear()
        resetVoiceState()
        uploadVoiceSegments(allSegments, duration, replyId)
    }

    // ── Voice: preview bar actions (PREVIEW state) ────────────────────────────

    /** Tap ▶/⏸ in PREVIEW or PAUSED bar → plays/pauses the recorded audio */
    fun togglePreviewPlayback() {
        if (_previewPlaying.value) {
            previewPlayer?.pause()
            _previewPlaying.value = false
            previewProgressJob?.cancel()
            return
        }
        val file = currentPreviewFile ?: return
        if (previewPlayer == null) {
            previewPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _previewPlaying.value = false
                    _previewProgress.value = 0f
                    previewProgressJob?.cancel()
                }
            }
        }
        previewPlayer?.start()
        _previewPlaying.value = true
        previewProgressJob = viewModelScope.launch {
            while (_previewPlaying.value) {
                val mp = previewPlayer
                if (mp != null && mp.isPlaying) {
                    _previewProgress.value = mp.currentPosition.toFloat() / mp.duration.toFloat()
                }
                delay(200)
            }
        }
    }

    /** Drag progress bar in PREVIEW/PAUSED → seek to position [0..1] */
    fun seekPreviewTo(position: Float) {
        val mp = previewPlayer ?: return
        mp.seekTo((position * mp.duration.toFloat()).toInt().coerceAtLeast(0))
        _previewProgress.value = position
    }

    /** Tap ✓ in PREVIEW bar → uploads and sends the recording */
    fun sendVoicePreview() {
        if (_voiceState.value != VoiceState.PREVIEW) return
        val file = voiceFile ?: run { resetVoiceState(); return }
        val duration = _previewDuration.value
        val replyId = _replyTo.value?.id
        _replyTo.value = null
        releasePreviewPlayer()
        voiceFile = null
        resetVoiceState()
        uploadVoiceSegments(listOf(file), duration, replyId)
    }

    /** Tap 🗑 in PREVIEW bar → discards the recording */
    fun deleteVoicePreview() = cancelRecording()

    // ── Typing ─────────────────────────────────────────────────────────────────

    fun leaveConversation(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.deleteConversation("Bearer $token", conversationId)
            }
            onDone()
        }
    }

    fun blockUser(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.blockConversationPartner("Bearer $token", conversationId)
            }
            onDone()
        }
    }

    fun markConversationRead() {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                api.markConversationRead("Bearer $token", conversationId)
            }
        }
    }

    fun notifyTyping() {
        socketManager.sendTyping(conversationId)
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        cancelRecording()
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun startMediaRecorder(context: Context, file: File) {
        @Suppress("DEPRECATION")
        recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    private fun startTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) { delay(1000); _recordingSeconds.value++ }
        }
    }

    private fun stopRecorder() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
    }

    private fun releasePreviewPlayer() {
        previewProgressJob?.cancel()
        previewPlayer?.release()
        previewPlayer = null
        _previewPlaying.value = false
        _previewProgress.value = 0f
    }

    private fun resetVoiceState() {
        _voiceState.value = VoiceState.IDLE
        _recordingSeconds.value = 0
        _previewDuration.value = 0
    }

    /**
     * Concatenates multiple M4A/AAC files into a single output file using [MediaMuxer].
     * Timestamps are adjusted so segments play back-to-back without gaps.
     * Must be called on a background thread.
     */
    private fun concatenateM4aFiles(inputs: List<File>, output: File) {
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var outputTrackIndex = -1
        var offsetUs = 0L

        for (file in inputs) {
            if (!file.exists() || file.length() == 0L) continue
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val trackIdx = (0 until extractor.trackCount).firstOrNull { idx ->
                    extractor.getTrackFormat(idx)
                        .getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: continue

                extractor.selectTrack(trackIdx)
                val format = extractor.getTrackFormat(trackIdx)

                if (outputTrackIndex < 0) {
                    outputTrackIndex = muxer.addTrack(format)
                    muxer.start()
                }

                val buf = ByteBuffer.allocate(512 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    info.offset = 0
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) break
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime + offsetUs
                    @Suppress("WrongConstant")
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(outputTrackIndex, buf, info)
                    extractor.advance()
                }

                val durationUs = runCatching {
                    format.getLong(MediaFormat.KEY_DURATION)
                }.getOrDefault(0L)
                offsetUs += durationUs
            } finally {
                extractor.release()
            }
        }

        if (outputTrackIndex >= 0) {
            muxer.stop()
        }
        muxer.release()
    }

    /**
     * Uploads voice message. If there are multiple segments they are merged first.
     * All source files are deleted after upload.
     */
    private fun uploadVoiceSegments(segments: List<File>, duration: Int, replyId: String? = null) {
        viewModelScope.launch {
            _sending.value = true
            var mergedFile: File? = null
            try {
                val token = tokenStore.accessToken.first()
                    ?: throw IllegalStateException("No access token")

                val validSegments = segments.filter { it.exists() && it.length() > 0 }
                if (validSegments.isEmpty()) return@launch

                val fileToSend = if (validSegments.size == 1) {
                    validSegments[0]
                } else {
                    val out = File(validSegments[0].parent, "voice_merged_${System.currentTimeMillis()}.m4a")
                    mergedFile = out
                    withContext(Dispatchers.IO) { concatenateM4aFiles(validSegments, out) }
                    out
                }

                val filePart = MultipartBody.Part.createFormData(
                    "file", fileToSend.name, fileToSend.asRequestBody("audio/mp4".toMediaType())
                )
                val metaMap = mutableMapOf<String, Any>("type" to "voice", "duration" to duration)
                if (replyId != null) metaMap["reply_to_id"] = replyId
                val metaPart = Gson().toJson(metaMap)
                    .toRequestBody("text/plain".toMediaType())
                val msg = api.uploadMessage("Bearer $token", conversationId, filePart, metaPart)
                _messages.value = if (_messages.value.none { it.id == msg.id }) {
                    _messages.value + msg
                } else {
                    _messages.value.map { if (it.id == msg.id) msg else it }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Voice upload failed, reloading messages", e)
                runCatching {
                    val token = tokenStore.accessToken.first() ?: return@runCatching
                    val latest = api.getMessages("Bearer $token", conversationId)
                    val existingIds = _messages.value.map { it.id }.toSet()
                    val newMsgs = latest.filter { it.id !in existingIds }
                    if (newMsgs.isNotEmpty()) _messages.value = _messages.value + newMsgs
                }
            } finally {
                _sending.value = false
                segments.forEach { it.delete() }
                mergedFile?.delete()
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (idx >= 0) it.getString(idx) else null
        }
    }
}
