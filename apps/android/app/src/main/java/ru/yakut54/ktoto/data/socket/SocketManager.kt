package ru.yakut54.ktoto.data.socket

import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.yakut54.ktoto.data.model.Message

class SocketManager {

    private val gson = Gson()
    private var socket: Socket? = null

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val messages = _messages.asSharedFlow()

    private val _typing = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val typing = _typing.asSharedFlow()

    private val _editedMessages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val editedMessages = _editedMessages.asSharedFlow()

    /** Pair(msgId, conversationId) */
    private val _deletedMessageIds = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val deletedMessageIds = _deletedMessageIds.asSharedFlow()

    fun connect(token: String) {
        if (socket?.connected() == true) return

        val opts = IO.Options.builder()
            .setAuth(mapOf("token" to token))
            .build()

        socket = IO.socket("http://31.128.39.216:3000", opts).apply {
            on(Socket.EVENT_CONNECT) { }
            on(Socket.EVENT_DISCONNECT) { }
            on("new_message") { args ->
                val json = args[0].toString()
                runCatching { gson.fromJson(json, Message::class.java) }
                    .onSuccess { _messages.tryEmit(it) }
                    .onFailure { android.util.Log.e("SocketManager", "new_message parse error: $json", it) }
            }
            on("typing") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val convId = obj["conversationId"] as? String ?: return@runCatching
                    val userId = obj["userId"] as? String ?: return@runCatching
                    _typing.tryEmit(convId to userId)
                }
            }
            on("message_edited") { args ->
                val json = args[0].toString()
                runCatching { gson.fromJson(json, Message::class.java) }
                    .onSuccess { _editedMessages.tryEmit(it) }
                    .onFailure { android.util.Log.e("SocketManager", "message_edited parse error: $json", it) }
            }
            on("message_deleted") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val msgId = obj["id"] as? String ?: return@runCatching
                    val convId = obj["conversationId"] as? String ?: return@runCatching
                    _deletedMessageIds.tryEmit(msgId to convId)
                }
                    .onFailure { android.util.Log.e("SocketManager", "message_deleted parse error", it) }
            }
            connect()
        }
    }

    fun sendTyping(conversationId: String) {
        socket?.emit("typing", gson.toJson(mapOf("conversationId" to conversationId)))
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
