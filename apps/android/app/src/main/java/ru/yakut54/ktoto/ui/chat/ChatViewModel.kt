package ru.yakut54.ktoto.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
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

    // Voice recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds

    private var conversationId: String = ""
    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var recordingTimerJob: kotlinx.coroutines.Job? = null

    fun init(convId: String) {
        conversationId = convId
        loadHistory()
        subscribeToMessages()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val history = api.getMessages("Bearer $token", conversationId)
                _messages.value = history
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
                    kotlinx.coroutines.delay(3000)
                    _isTyping.value = false
                }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _sending.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch
                val msg = api.sendMessage("Bearer $token", conversationId, SendMessageRequest(content))
                if (_messages.value.none { it.id == msg.id }) {
                    _messages.value = _messages.value + msg
                }
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
                val requestBody = bytes.toRequestBody(mimeType.toMediaType())
                val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)

                val metaJson = Gson().toJson(mapOf("type" to type))
                val metaPart = metaJson.toRequestBody("application/json".toMediaType())

                _uploadProgress.value = 0.5f
                val msg = api.uploadMessage("Bearer $token", conversationId, filePart, metaPart)
                _uploadProgress.value = 1f

                if (_messages.value.none { it.id == msg.id }) {
                    _messages.value = _messages.value + msg
                }
            }
            _sending.value = false
            _uploadProgress.value = null
        }
    }

    fun startRecording(context: Context) {
        if (_isRecording.value) return
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

        _isRecording.value = true
        _recordingSeconds.value = 0
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _recordingSeconds.value++
            }
        }
    }

    fun stopRecordingAndSend(context: Context) {
        if (!_isRecording.value) return
        recordingTimerJob?.cancel()
        val duration = _recordingSeconds.value
        recorder?.apply { stop(); release() }
        recorder = null
        _isRecording.value = false
        _recordingSeconds.value = 0

        val file = voiceFile ?: return
        voiceFile = null

        viewModelScope.launch {
            _sending.value = true
            runCatching {
                val token = tokenStore.accessToken.first() ?: return@launch

                val mimeType = "audio/mp4"
                val requestBody = file.asRequestBody(mimeType.toMediaType())
                val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)

                val metaJson = Gson().toJson(mapOf("type" to "voice", "duration" to duration))
                val metaPart = metaJson.toRequestBody("application/json".toMediaType())

                val msg = api.uploadMessage("Bearer $token", conversationId, filePart, metaPart)
                if (_messages.value.none { it.id == msg.id }) {
                    _messages.value = _messages.value + msg
                }
            }
            _sending.value = false
            file.delete()
        }
    }

    fun cancelRecording() {
        if (!_isRecording.value) return
        recordingTimerJob?.cancel()
        recorder?.apply { stop(); release() }
        recorder = null
        _isRecording.value = false
        _recordingSeconds.value = 0
        voiceFile?.delete()
        voiceFile = null
    }

    fun notifyTyping() {
        socketManager.sendTyping(conversationId)
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (nameIndex >= 0) it.getString(nameIndex) else null
        }
    }
}
