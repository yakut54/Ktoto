package ru.yakut54.ktoto.ui.chat

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.Message
import ru.yakut54.ktoto.data.model.SendMessageRequest
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore
import java.io.File

/** Voice recording state machine: IDLE → RECORDING → LOCKED | PREVIEW → IDLE */
enum class VoiceState { IDLE, RECORDING, LOCKED, PREVIEW }

class ChatViewModel(
    private val api: ApiService,
    private val tokenStore: TokenStore,
    val socketManager: SocketManager,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress

    // ── Voice state ────────────────────────────────────────────────────────────
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState

    /** Timer seconds — counts up during RECORDING/LOCKED, shows duration in PREVIEW */
    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds

    /** Duration saved when recording stops (used in PREVIEW bar) */
    private val _previewDuration = MutableStateFlow(0)
    val previewDuration: StateFlow<Int> = _previewDuration

    /** Preview playback state */
    private val _previewPlaying = MutableStateFlow(false)
    val previewPlaying: StateFlow<Boolean> = _previewPlaying

    private val _previewProgress = MutableStateFlow(0f)
    val previewProgress: StateFlow<Float> = _previewProgress

    // ── Internal ───────────────────────────────────────────────────────────────
    private var conversationId: String = ""
    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var recordingTimerJob: kotlinx.coroutines.Job? = null
    private var previewPlayer: MediaPlayer? = null
    private var previewProgressJob: kotlinx.coroutines.Job? = null

    // ── Init ───────────────────────────────────────────────────────────────────

    fun init(convId: String) {
        conversationId = convId
        loadHistory()
        subscribeToMessages()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                _messages.value = api.getMessages("Bearer $token", conversationId)
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
    }

    // ── Text / media messages ──────────────────────────────────────────────────

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _sending.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val msg = api.sendMessage("Bearer $token", conversationId, SendMessageRequest(content))
                if (_messages.value.none { it.id == msg.id }) _messages.value = _messages.value + msg
            }
            _sending.value = false
        }
    }

    fun sendMediaMessage(context: Context, uri: Uri, type: String) {
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
                val metaPart = Gson().toJson(mapOf("type" to type))
                    .toRequestBody("application/json".toMediaType())
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

    /** Long-press mic → starts recording */
    fun startRecording(context: Context) {
        if (_voiceState.value != VoiceState.IDLE) return
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        voiceFile = file

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

        _voiceState.value = VoiceState.RECORDING
        _recordingSeconds.value = 0
        recordingTimerJob = viewModelScope.launch {
            while (true) { delay(1000); _recordingSeconds.value++ }
        }
    }

    /** Slide UP while holding → locks recording (continues hands-free) */
    fun lockRecording() {
        if (_voiceState.value == VoiceState.RECORDING) {
            _voiceState.value = VoiceState.LOCKED
        }
    }

    /**
     * Finger released without sliding → stops recording, enters PREVIEW.
     * User can listen, then send or delete.
     */
    fun stopRecordingToPreview() {
        if (_voiceState.value !in listOf(VoiceState.RECORDING, VoiceState.LOCKED)) return
        recordingTimerJob?.cancel()
        _previewDuration.value = _recordingSeconds.value
        stopRecorder()
        _voiceState.value = VoiceState.PREVIEW
    }

    /** Slide LEFT (or tap ❌) → discards immediately */
    fun cancelRecording() {
        recordingTimerJob?.cancel()
        stopRecorder()
        releasePreviewPlayer()
        voiceFile?.delete()
        voiceFile = null
        _voiceState.value = VoiceState.IDLE
        _recordingSeconds.value = 0
        _previewDuration.value = 0
    }

    // ── Voice: locked mode actions ─────────────────────────────────────────────

    /** Tap ▶ in LOCKED bar → stops recording and sends immediately (no preview) */
    fun sendVoiceFromLocked() {
        if (_voiceState.value != VoiceState.LOCKED) return
        recordingTimerJob?.cancel()
        val duration = _recordingSeconds.value
        stopRecorder()
        val file = voiceFile ?: run { resetVoiceState(); return }
        voiceFile = null
        resetVoiceState()
        uploadVoice(file, duration)
    }

    // ── Voice: preview mode actions ────────────────────────────────────────────

    /** Tap ▶/⏸ in PREVIEW bar → plays/pauses the recorded audio */
    fun togglePreviewPlayback() {
        if (_previewPlaying.value) {
            previewPlayer?.pause()
            _previewPlaying.value = false
            previewProgressJob?.cancel()
            return
        }
        val file = voiceFile ?: return
        if (previewPlayer == null) {
            previewPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare() // synchronous — OK for local file
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

    /** Tap ✓ in PREVIEW bar → uploads and sends the recording */
    fun sendVoicePreview() {
        if (_voiceState.value != VoiceState.PREVIEW) return
        val file = voiceFile ?: run { resetVoiceState(); return }
        val duration = _previewDuration.value
        releasePreviewPlayer()
        voiceFile = null
        resetVoiceState()
        uploadVoice(file, duration)
    }

    /** Tap 🗑 in PREVIEW bar → discards the recording */
    fun deleteVoicePreview() = cancelRecording()

    // ── Typing ─────────────────────────────────────────────────────────────────

    fun notifyTyping() {
        socketManager.sendTyping(conversationId)
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        cancelRecording()
    }

    // ── Private helpers ────────────────────────────────────────────────────────

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

    private fun uploadVoice(file: File, duration: Int) {
        viewModelScope.launch {
            _sending.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val filePart = MultipartBody.Part.createFormData(
                    "file", file.name, file.asRequestBody("audio/mp4".toMediaType())
                )
                val metaPart = Gson().toJson(mapOf("type" to "voice", "duration" to duration))
                    .toRequestBody("application/json".toMediaType())
                val msg = api.uploadMessage("Bearer $token", conversationId, filePart, metaPart)
                if (_messages.value.none { it.id == msg.id }) _messages.value = _messages.value + msg
            }
            _sending.value = false
            file.delete()
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
