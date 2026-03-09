package ru.yakut54.ktoto.data.socket

import android.util.Log
import com.google.gson.Gson
import ru.yakut54.ktoto.utils.CallLogger
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import ru.yakut54.ktoto.data.model.CallEndEvent
import ru.yakut54.ktoto.data.model.CallIceCandidate
import ru.yakut54.ktoto.data.model.CallMuteEvent
import ru.yakut54.ktoto.data.model.CallSdp
import ru.yakut54.ktoto.data.model.IceCandidatePayload
import ru.yakut54.ktoto.data.model.IncomingCallEvent
import ru.yakut54.ktoto.data.model.Message
import ru.yakut54.ktoto.data.model.MessagesReadEvent
import ru.yakut54.ktoto.data.model.SdpPayload

private const val TAG = "SocketManager"

class SocketManager {

    private val gson = Gson()
    private var socket: Socket? = null

    // ─── Messaging flows ─────────────────────────────────────────────────────

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val messages = _messages.asSharedFlow()

    private val _typing = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val typing = _typing.asSharedFlow()

    private val _editedMessages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val editedMessages = _editedMessages.asSharedFlow()

    /** Pair(msgId, conversationId) */
    private val _deletedMessageIds = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val deletedMessageIds = _deletedMessageIds.asSharedFlow()

    private val _messagesRead = MutableSharedFlow<MessagesReadEvent>(extraBufferCapacity = 64)
    val messagesRead = _messagesRead.asSharedFlow()

    /** Fired when a new conversation is created (e.g. someone adds you to a group) */
    private val _newConversation = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val newConversation: SharedFlow<String> = _newConversation.asSharedFlow()

    /** Fired when group is renamed — Pair(convId, newName) */
    private val _groupUpdated = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)
    val groupUpdated: SharedFlow<Pair<String, String>> = _groupUpdated.asSharedFlow()

    /** Set of user IDs currently online */
    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers: StateFlow<Set<String>> = _onlineUsers.asStateFlow()

    // ─── Call flows ───────────────────────────────────────────────────────────

    private val _callIncoming = MutableSharedFlow<IncomingCallEvent>(extraBufferCapacity = 4)
    val callIncoming: SharedFlow<IncomingCallEvent> = _callIncoming.asSharedFlow()

    /** callId confirmed by server after call_initiate */
    private val _callInitiated = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val callInitiated: SharedFlow<String> = _callInitiated.asSharedFlow()

    private val _callRinging = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val callRinging: SharedFlow<String> = _callRinging.asSharedFlow()

    private val _callOffer = MutableSharedFlow<CallSdp>(extraBufferCapacity = 4)
    val callOffer: SharedFlow<CallSdp> = _callOffer.asSharedFlow()

    private val _callAnswer = MutableSharedFlow<CallSdp>(extraBufferCapacity = 4)
    val callAnswer: SharedFlow<CallSdp> = _callAnswer.asSharedFlow()

    private val _callIceCandidate = MutableSharedFlow<CallIceCandidate>(extraBufferCapacity = 64)
    val callIceCandidate: SharedFlow<CallIceCandidate> = _callIceCandidate.asSharedFlow()

    private val _callRejected = MutableSharedFlow<CallEndEvent>(extraBufferCapacity = 4)
    val callRejected: SharedFlow<CallEndEvent> = _callRejected.asSharedFlow()

    private val _callCancelled = MutableSharedFlow<CallEndEvent>(extraBufferCapacity = 4)
    val callCancelled: SharedFlow<CallEndEvent> = _callCancelled.asSharedFlow()

    private val _callEnded = MutableSharedFlow<CallEndEvent>(extraBufferCapacity = 4)
    val callEnded: SharedFlow<CallEndEvent> = _callEnded.asSharedFlow()

    private val _callForceEnd = MutableSharedFlow<CallEndEvent>(extraBufferCapacity = 4)
    val callForceEnd: SharedFlow<CallEndEvent> = _callForceEnd.asSharedFlow()

    private val _callMute = MutableSharedFlow<CallMuteEvent>(extraBufferCapacity = 4)
    val callMute: SharedFlow<CallMuteEvent> = _callMute.asSharedFlow()

    // ─────────────────────────────────────────────────────────────────────────

    /** Seed initial online status from REST (called after loading conversations) */
    fun initOnlineUsers(userIds: Set<String>) {
        _onlineUsers.value = userIds
    }

    fun connect(token: String) {
        if (socket?.connected() == true) return

        val opts = IO.Options.builder()
            .setAuth(mapOf("token" to token))
            .build()

        socket = IO.socket("http://31.128.39.216:3000", opts).apply {
            on(Socket.EVENT_CONNECT) {
                CallLogger.i(TAG, "Socket CONNECTED")
            }
            on(Socket.EVENT_CONNECT_ERROR) { args ->
                CallLogger.e(TAG, "Socket CONNECT_ERROR", "error" to args.firstOrNull())
            }
            on(Socket.EVENT_DISCONNECT) { args ->
                CallLogger.w(TAG, "Socket DISCONNECTED", "reason" to args.firstOrNull())
            }

            // ── Messaging ──────────────────────────────────────────────────
            on("new_message") { args ->
                val json = args[0].toString()
                runCatching { gson.fromJson(json, Message::class.java) }
                    .onSuccess { _messages.tryEmit(it) }
                    .onFailure { Log.e(TAG, "new_message parse error: $json", it) }
            }
            on("typing") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val convId = obj["conversationId"] as? String ?: return@runCatching
                    val userId = obj["userId"] as? String ?: return@runCatching
                    _typing.tryEmit(convId to userId)
                }
            }
            on("user_status") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val uid = obj["userId"] as? String ?: return@runCatching
                    val status = obj["status"] as? String ?: return@runCatching
                    _onlineUsers.value = if (status == "online") {
                        _onlineUsers.value + uid
                    } else {
                        _onlineUsers.value - uid
                    }
                }
            }
            on("message_edited") { args ->
                val json = args[0].toString()
                runCatching { gson.fromJson(json, Message::class.java) }
                    .onSuccess { _editedMessages.tryEmit(it) }
                    .onFailure { Log.e(TAG, "message_edited parse error: $json", it) }
            }
            on("message_deleted") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val msgId = obj["id"] as? String ?: return@runCatching
                    val convId = obj["conversationId"] as? String ?: return@runCatching
                    _deletedMessageIds.tryEmit(msgId to convId)
                }
                    .onFailure { Log.e(TAG, "message_deleted parse error", it) }
            }
            on("messages_read") { args ->
                val json = args[0].toString()
                runCatching { gson.fromJson(json, MessagesReadEvent::class.java) }
                    .onSuccess { _messagesRead.tryEmit(it) }
                    .onFailure { Log.e(TAG, "messages_read parse error: $json", it) }
            }
            on("new_conversation") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val convId = obj["conversationId"] as? String ?: return@runCatching
                    _newConversation.tryEmit(convId)
                }
            }
            on("group_updated") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val convId = obj["conversationId"] as? String ?: return@runCatching
                    val name = obj["name"] as? String ?: return@runCatching
                    _groupUpdated.tryEmit(convId to name)
                }
            }

            // ── Call signaling ─────────────────────────────────────────────
            on("call_incoming") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val callId = obj["callId"] as? String ?: return@runCatching
                    val from = obj["fromUsername"] as? String ?: "?"
                    val type = obj["callType"] as? String ?: "audio"
                    CallLogger.i(TAG, "SOCKET call_incoming", "callId" to callId, "from" to from, "type" to type)
                    _callIncoming.tryEmit(
                        IncomingCallEvent(
                            callId = callId,
                            fromUserId = obj["fromUserId"] as? String ?: return@runCatching,
                            fromUsername = from,
                            fromAvatarUrl = obj["fromAvatarUrl"] as? String,
                            callType = type,
                        )
                    )
                }.onFailure { CallLogger.e(TAG, "call_incoming parse error: ${it.message}") }
            }
            on("call_initiated") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val callId = obj["callId"] as? String ?: return@runCatching
                    CallLogger.i(TAG, "SOCKET call_initiated", "callId" to callId)
                    _callInitiated.tryEmit(callId)
                }
            }
            on("call_ringing") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val callId = obj["callId"] as? String ?: return@runCatching
                    CallLogger.i(TAG, "SOCKET call_ringing", "callId" to callId)
                    _callRinging.tryEmit(callId)
                }
            }
            on("call_offer") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val callId = obj["callId"] as? String ?: return@runCatching
                    @Suppress("UNCHECKED_CAST")
                    val sdpMap = obj["sdp"] as? Map<String, Any> ?: return@runCatching
                    CallLogger.i(TAG, "SOCKET call_offer received", "callId" to callId)
                    _callOffer.tryEmit(
                        CallSdp(callId, SdpPayload(sdpMap["type"] as? String ?: "", sdpMap["sdp"] as? String ?: ""))
                    )
                }.onFailure { CallLogger.e(TAG, "call_offer parse error: ${it.message}") }
            }
            on("call_answer") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val callId = obj["callId"] as? String ?: return@runCatching
                    @Suppress("UNCHECKED_CAST")
                    val sdpMap = obj["sdp"] as? Map<String, Any> ?: return@runCatching
                    CallLogger.i(TAG, "SOCKET call_answer received", "callId" to callId)
                    _callAnswer.tryEmit(
                        CallSdp(callId, SdpPayload(sdpMap["type"] as? String ?: "", sdpMap["sdp"] as? String ?: ""))
                    )
                }.onFailure { CallLogger.e(TAG, "call_answer parse error: ${it.message}") }
            }
            on("call_ice_candidate") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    val callId = obj["callId"] as? String ?: return@runCatching
                    @Suppress("UNCHECKED_CAST")
                    val cMap = obj["candidate"] as? Map<String, Any> ?: return@runCatching
                    _callIceCandidate.tryEmit(
                        CallIceCandidate(
                            callId,
                            IceCandidatePayload(
                                candidate = cMap["candidate"] as? String ?: "",
                                sdpMid = cMap["sdpMid"] as? String,
                                sdpMLineIndex = (cMap["sdpMLineIndex"] as? Double)?.toInt(),
                            )
                        )
                    )
                }
            }
            on("call_rejected") { args -> parseCallEnd(args)?.let { e -> CallLogger.w(TAG, "SOCKET call_rejected", "callId" to e.callId, "reason" to e.reason); _callRejected.tryEmit(e) } }
            on("call_cancelled") { args -> parseCallEnd(args)?.let { e -> CallLogger.i(TAG, "SOCKET call_cancelled", "callId" to e.callId, "reason" to e.reason); _callCancelled.tryEmit(e) } }
            on("call_ended") { args -> parseCallEnd(args)?.let { e -> CallLogger.i(TAG, "SOCKET call_ended", "callId" to e.callId, "duration" to e.duration); _callEnded.tryEmit(e) } }
            on("call_force_end") { args ->
                parseCallEnd(args)?.let { e -> CallLogger.w(TAG, "SOCKET call_force_end", "callId" to e.callId, "reason" to e.reason); _callForceEnd.tryEmit(e) }
            }
            on("call_error") { args ->
                CallLogger.e(TAG, "SOCKET call_error", "data" to args.firstOrNull())
            }
            on("call_busy") { args ->
                CallLogger.w(TAG, "SOCKET call_busy", "data" to args.firstOrNull())
            }
            on("call_mute") { args ->
                runCatching {
                    val obj = gson.fromJson(args[0].toString(), Map::class.java)
                    _callMute.tryEmit(
                        CallMuteEvent(
                            callId = obj["callId"] as? String ?: return@runCatching,
                            muted = obj["muted"] as? Boolean ?: return@runCatching,
                            fromUserId = obj["fromUserId"] as? String ?: "",
                        )
                    )
                }
            }

            connect()
        }
    }

    private fun parseCallEnd(args: Array<Any>): CallEndEvent? = runCatching {
        val obj = gson.fromJson(args[0].toString(), Map::class.java)
        CallEndEvent(
            callId = obj["callId"] as? String ?: return@runCatching null,
            reason = obj["reason"] as? String,
            duration = (obj["duration"] as? Double)?.toInt(),
        )
    }.getOrNull()

    // ─── Emit helpers (use JSONObject so server receives a JS object) ──────────

    fun sendTyping(conversationId: String) {
        emit("typing", JSONObject().put("conversationId", conversationId))
    }

    fun emitCallInitiate(toUserId: String, callType: String) {
        CallLogger.i(TAG, "EMIT call_initiate", "toUserId" to toUserId, "callType" to callType, "connected" to socket?.connected())
        emit("call_initiate", JSONObject().put("toUserId", toUserId).put("callType", callType))
    }

    fun emitCallRinging(callId: String) {
        emit("call_ringing", JSONObject().put("callId", callId))
    }

    fun emitCallOffer(callId: String, type: String, sdp: String) {
        CallLogger.i(TAG, "EMIT call_offer", "callId" to callId, "sdpType" to type)
        emit("call_offer", JSONObject()
            .put("callId", callId)
            .put("sdp", JSONObject().put("type", type).put("sdp", sdp))
        )
    }

    fun emitCallAnswer(callId: String, type: String, sdp: String) {
        CallLogger.i(TAG, "EMIT call_answer", "callId" to callId, "sdpType" to type)
        emit("call_answer", JSONObject()
            .put("callId", callId)
            .put("sdp", JSONObject().put("type", type).put("sdp", sdp))
        )
    }

    fun emitCallIceCandidate(callId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        emit("call_ice_candidate", JSONObject()
            .put("callId", callId)
            .put("candidate", JSONObject()
                .put("candidate", candidate)
                .put("sdpMid", sdpMid)
                .put("sdpMLineIndex", sdpMLineIndex)
            )
        )
    }

    fun emitCallReject(callId: String) {
        CallLogger.i(TAG, "EMIT call_reject", "callId" to callId)
        emit("call_reject", JSONObject().put("callId", callId).put("reason", "declined"))
    }

    fun emitCallCancel(callId: String) {
        CallLogger.i(TAG, "EMIT call_cancel", "callId" to callId)
        emit("call_cancel", JSONObject().put("callId", callId).put("reason", "cancelled"))
    }

    fun emitCallEnd(callId: String, durationSec: Long) {
        emit("call_end", JSONObject()
            .put("callId", callId)
            .put("duration", durationSec)
            .put("reason", "normal")
        )
    }

    fun emitCallMute(callId: String, muted: Boolean) {
        emit("call_mute", JSONObject().put("callId", callId).put("muted", muted))
    }

    fun emitCallHeartbeat(callId: String) {
        emit("call_heartbeat", JSONObject().put("callId", callId))
    }

    fun emitCallStateSync(callId: String?) {
        emit("call_state_sync", JSONObject().put("callId", callId))
    }

    private fun emit(event: String, data: JSONObject) {
        val connected = socket?.connected() == true
        if (!connected) Log.w(TAG, "emit '$event' skipped — socket not connected")
        socket?.emit(event, data)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        _onlineUsers.value = emptySet()
    }
}
