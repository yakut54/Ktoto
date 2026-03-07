package ru.yakut54.ktoto.call

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.IceCandidatePayload
import ru.yakut54.ktoto.data.model.IncomingCallEvent
import ru.yakut54.ktoto.data.socket.SocketManager
import ru.yakut54.ktoto.data.store.TokenStore
import ru.yakut54.ktoto.service.CallService

private const val TAG = "CallManager"

enum class CallState {
    IDLE,
    OUTGOING_RINGING,   // We called, waiting for answer
    INCOMING_RINGING,   // Someone called us
    NEGOTIATING,        // SDP exchange in progress
    IN_CALL,            // Media flowing
    RECONNECTING,       // Temporary disconnection
    ENDED,
}

data class CallInfo(
    val callId: String,
    val peerId: String,
    val peerName: String,
    val peerAvatarUrl: String?,
    val callType: String,
    val isOutgoing: Boolean,
    val startedAt: Long = System.currentTimeMillis(),
)

class CallManager(
    private val context: Context,
    private val socketManager: SocketManager,
    private val apiService: ApiService,
    private val tokenStore: TokenStore,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── State ────────────────────────────────────────────────────────────────

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callInfo = MutableStateFlow<CallInfo?>(null)
    val callInfo: StateFlow<CallInfo?> = _callInfo.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    /** Elapsed seconds in IN_CALL state */
    private val _durationSec = MutableStateFlow(0L)
    val durationSec: StateFlow<Long> = _durationSec.asStateFlow()

    // ─── WebRTC ───────────────────────────────────────────────────────────────

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null

    // ─── Session ──────────────────────────────────────────────────────────────

    private var callId: String? = null
    private var pendingOffer: SessionDescription? = null   // stored while in INCOMING_RINGING
    private var pendingCandidates = mutableListOf<IceCandidatePayload>()  // buffered before setRemoteDesc

    private var iceRestartAttempts = 0
    private var reconnectJob: Job? = null
    private var durationJob: Job? = null
    private var heartbeatJob: Job? = null
    private var ringTimeoutJob: Job? = null

    // ─── Audio ───────────────────────────────────────────────────────────────

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    // ─────────────────────────────────────────────────────────────────────────

    fun init() {
        scope.launch { socketManager.callIncoming.collect { onCallIncoming(it) } }
        scope.launch { socketManager.callInitiated.collect { onCallInitiated(it) } }
        scope.launch { socketManager.callRinging.collect { onCallRinging() } }
        scope.launch { socketManager.callOffer.collect { onCallOffer(it.callId, it.sdp.type, it.sdp.sdp) } }
        scope.launch { socketManager.callAnswer.collect { onCallAnswer(it.callId, it.sdp.type, it.sdp.sdp) } }
        scope.launch { socketManager.callIceCandidate.collect { onIceCandidate(it.callId, it.candidate) } }
        scope.launch { socketManager.callRejected.collect { onCallTerminated("rejected") } }
        scope.launch { socketManager.callCancelled.collect { onCallTerminated("cancelled") } }
        scope.launch { socketManager.callEnded.collect { onCallTerminated("ended") } }
        scope.launch { socketManager.callForceEnd.collect { onCallTerminated("force_end") } }
    }

    // ─── Outgoing call ────────────────────────────────────────────────────────

    fun startCall(peerId: String, peerName: String, peerAvatarUrl: String?, callType: String) {
        if (_callState.value != CallState.IDLE) return

        _callInfo.value = CallInfo(
            callId = "", peerId = peerId, peerName = peerName,
            peerAvatarUrl = peerAvatarUrl, callType = callType, isOutgoing = true,
        )
        _callState.value = CallState.OUTGOING_RINGING

        startCallService()
        socketManager.emitCallInitiate(peerId, callType)

        // Ring timeout: 30s
        ringTimeoutJob = scope.launch {
            delay(30_000)
            if (_callState.value == CallState.OUTGOING_RINGING) {
                callId?.let { socketManager.emitCallCancel(it) }
                cleanupAndSetIdle("no_answer")
            }
        }
    }

    private fun onCallInitiated(servCallId: String) {
        if (_callState.value != CallState.OUTGOING_RINGING) return
        callId = servCallId
        _callInfo.value = _callInfo.value?.copy(callId = servCallId)

        // Create offer immediately so callee gets it when they accept
        scope.launch {
            val token = tokenStore.getAccessToken() ?: return@launch
            val iceServers = runCatching { apiService.getTurnCredentials("Bearer $token").iceServers }.getOrNull()
            createPeerConnection(iceServers?.map { toRtcIceServer(it) } ?: defaultIceServers())
            setupLocalAudio()
            createAndSendOffer()
        }
    }

    private fun onCallRinging() {
        // Callee confirmed ring — UI update only (state stays OUTGOING_RINGING)
        Log.d(TAG, "Remote is ringing")
    }

    fun cancelCall() {
        callId?.let { socketManager.emitCallCancel(it) }
        cleanupAndSetIdle("cancelled")
    }

    // ─── Incoming call ────────────────────────────────────────────────────────

    private fun onCallIncoming(event: IncomingCallEvent) {
        if (_callState.value != CallState.IDLE) {
            // We're busy — reject automatically
            socketManager.emitCallReject(event.callId)
            return
        }

        callId = event.callId
        _callInfo.value = CallInfo(
            callId = event.callId, peerId = event.fromUserId,
            peerName = event.fromUsername, peerAvatarUrl = event.fromAvatarUrl,
            callType = event.callType, isOutgoing = false,
        )
        _callState.value = CallState.INCOMING_RINGING

        socketManager.emitCallRinging(event.callId)
        startCallService()

        // Ring timeout: 30s auto-miss
        ringTimeoutJob = scope.launch {
            delay(30_000)
            if (_callState.value == CallState.INCOMING_RINGING) {
                cleanupAndSetIdle("missed")
            }
        }
    }

    fun acceptCall() {
        val cid = callId ?: return
        if (_callState.value != CallState.INCOMING_RINGING) return
        _callState.value = CallState.NEGOTIATING
        ringTimeoutJob?.cancel()

        scope.launch {
            val token = tokenStore.getAccessToken() ?: return@launch
            val iceServers = runCatching { apiService.getTurnCredentials("Bearer $token").iceServers }.getOrNull()
            createPeerConnection(iceServers?.map { toRtcIceServer(it) } ?: defaultIceServers())
            setupLocalAudio()

            val offer = pendingOffer
            if (offer != null) {
                // We already have the offer — process it immediately
                applyOfferAndAnswer(cid, offer)
            }
            // else: wait for offer to arrive in onCallOffer()
        }
    }

    fun rejectCall() {
        callId?.let { socketManager.emitCallReject(it) }
        cleanupAndSetIdle("rejected")
    }

    private fun onCallOffer(incomingCallId: String, type: String, sdpStr: String) {
        val cid = callId ?: return
        if (incomingCallId != cid) return

        val sdp = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdpStr)

        when (_callState.value) {
            CallState.INCOMING_RINGING -> {
                // User hasn't accepted yet — store offer
                pendingOffer = sdp
            }
            CallState.NEGOTIATING -> {
                // Already accepted — process immediately
                scope.launch { applyOfferAndAnswer(cid, sdp) }
            }
            else -> {}
        }
    }

    private suspend fun applyOfferAndAnswer(cid: String, offer: SessionDescription) {
        val pc = peerConnection ?: return
        pc.setRemoteDescription(NoOpSdpObserver("setRemote(offer)"), offer)

        // Apply any buffered ICE candidates
        drainPendingCandidates()

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(answer: SessionDescription) {
                pc.setLocalDescription(NoOpSdpObserver("setLocal(answer)"), answer)
                socketManager.emitCallAnswer(cid, answer.type.canonicalForm(), answer.description)
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createAnswer failed: $error")
                cleanupAndSetIdle("answer_failed")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun onCallAnswer(incomingCallId: String, type: String, sdpStr: String) {
        val cid = callId ?: return
        if (incomingCallId != cid) return
        val pc = peerConnection ?: return

        val sdp = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdpStr)
        pc.setRemoteDescription(NoOpSdpObserver("setRemote(answer)"), sdp)
        drainPendingCandidates()
        _callState.value = CallState.NEGOTIATING
    }

    // ─── ICE candidates ───────────────────────────────────────────────────────

    private fun onIceCandidate(incomingCallId: String, payload: ru.yakut54.ktoto.data.model.IceCandidatePayload) {
        val cid = callId ?: return
        if (incomingCallId != cid) return

        val candidate = IceCandidate(payload.sdpMid ?: "", payload.sdpMLineIndex ?: 0, payload.candidate)
        val pc = peerConnection

        if (pc?.remoteDescription == null) {
            pendingCandidates.add(payload)
        } else {
            pc.addIceCandidate(candidate)
        }
    }

    private fun drainPendingCandidates() {
        val pc = peerConnection ?: return
        pendingCandidates.forEach { payload ->
            pc.addIceCandidate(IceCandidate(payload.sdpMid ?: "", payload.sdpMLineIndex ?: 0, payload.candidate))
        }
        pendingCandidates.clear()
    }

    // ─── End call ─────────────────────────────────────────────────────────────

    fun endCall() {
        val cid = callId ?: run { cleanupAndSetIdle("ended"); return }
        socketManager.emitCallEnd(cid, _durationSec.value)
        cleanupAndSetIdle("ended")
    }

    private fun onCallTerminated(reason: String) {
        if (_callState.value == CallState.IDLE) return
        cleanupAndSetIdle(reason)
    }

    // ─── Controls ─────────────────────────────────────────────────────────────

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        localAudioTrack?.setEnabled(!newMuted)
        callId?.let { socketManager.emitCallMute(it, newMuted) }
    }

    fun toggleSpeaker() {
        val newSpeaker = !_isSpeakerOn.value
        _isSpeakerOn.value = newSpeaker
        audioManager.isSpeakerphoneOn = newSpeaker
    }

    // ─── WebRTC setup ─────────────────────────────────────────────────────────

    private fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory!!.createPeerConnection(config, object : PeerConnection.Observer {

            override fun onIceCandidate(candidate: IceCandidate) {
                val cid = callId ?: return
                socketManager.emitCallIceCandidate(
                    cid, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex
                )
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE connection state: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        iceRestartAttempts = 0
                        reconnectJob?.cancel()
                        if (_callState.value == CallState.NEGOTIATING ||
                            _callState.value == CallState.RECONNECTING
                        ) {
                            _callState.value = CallState.IN_CALL
                            startDurationTimer()
                            startHeartbeat()
                        }
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        if (_callState.value == CallState.IN_CALL) {
                            _callState.value = CallState.RECONNECTING
                            scheduleReconnect(delayMs = 3000)
                        }
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        if (_callState.value == CallState.IN_CALL ||
                            _callState.value == CallState.RECONNECTING
                        ) {
                            if (iceRestartAttempts < 2) {
                                iceRestartAttempts++
                                restartIce()
                            } else {
                                cleanupAndSetIdle("ice_failed")
                            }
                        }
                    }
                    else -> {}
                }
            }

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                Log.d(TAG, "Peer connection state: $state")
                if (state == PeerConnection.PeerConnectionState.FAILED) {
                    scope.launch { cleanupAndSetIdle("connection_failed") }
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })
    }

    private fun setupLocalAudio() {
        val factory = peerConnectionFactory ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = factory.createAudioSource(constraints)
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack, listOf("stream0"))

        requestAudioFocus()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    private fun createAndSendOffer() {
        val pc = peerConnection ?: return
        val cid = callId ?: return

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(offer: SessionDescription) {
                pc.setLocalDescription(NoOpSdpObserver("setLocal(offer)"), offer)
                socketManager.emitCallOffer(cid, offer.type.canonicalForm(), offer.description)
                _callState.value = CallState.NEGOTIATING
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failed: $error")
                cleanupAndSetIdle("offer_failed")
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun restartIce() {
        val pc = peerConnection ?: return
        val cid = callId ?: return
        val info = _callInfo.value ?: return

        if (info.isOutgoing) {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            }
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(offer: SessionDescription) {
                    pc.setLocalDescription(NoOpSdpObserver("setLocal(restart)"), offer)
                    socketManager.emitCallOffer(cid, offer.type.canonicalForm(), offer.description)
                }
                override fun onCreateFailure(error: String?) {}
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
        // Callee waits for new offer from caller
    }

    private fun scheduleReconnect(delayMs: Long) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (_callState.value == CallState.RECONNECTING) {
                restartIce()
            }
        }
        // Hard timeout
        scope.launch {
            delay(20_000)
            if (_callState.value == CallState.RECONNECTING) {
                cleanupAndSetIdle("reconnect_timeout")
            }
        }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    @Synchronized
    private fun cleanupAndSetIdle(reason: String) {
        Log.d(TAG, "Call cleanup, reason=$reason")
        ringTimeoutJob?.cancel()
        reconnectJob?.cancel()
        durationJob?.cancel()
        heartbeatJob?.cancel()

        peerConnection?.dispose()
        peerConnection = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        pendingOffer = null
        pendingCandidates.clear()
        iceRestartAttempts = 0

        releaseAudioFocus()
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false

        callId = null
        _callInfo.value = null
        _isMuted.value = false
        _isSpeakerOn.value = false
        _durationSec.value = 0L
        _callState.value = CallState.ENDED

        stopCallService()

        // Reset to IDLE after brief pause (allows UI to show ended state)
        scope.launch {
            delay(1500)
            _callState.value = CallState.IDLE
        }
    }

    // ─── Duration timer ───────────────────────────────────────────────────────

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = scope.launch {
            while (true) {
                delay(1000)
                _durationSec.value++
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(15_000)
                callId?.let { socketManager.emitCallHeartbeat(it) }
            }
        }
    }

    // ─── Audio focus ──────────────────────────────────────────────────────────

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioFocusRequest = null
    }

    // ─── Service ──────────────────────────────────────────────────────────────

    private fun startCallService() {
        val intent = Intent(context, CallService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopCallService() {
        context.stopService(Intent(context, CallService::class.java))
    }

    // ─── ICE servers ─────────────────────────────────────────────────────────

    private fun toRtcIceServer(cfg: ru.yakut54.ktoto.data.model.IceServerConfig): PeerConnection.IceServer {
        return if (cfg.username != null && cfg.credential != null) {
            PeerConnection.IceServer.builder(cfg.urls)
                .setUsername(cfg.username)
                .setPassword(cfg.credential)
                .createIceServer()
        } else {
            PeerConnection.IceServer.builder(cfg.urls).createIceServer()
        }
    }

    private fun defaultIceServers(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:31.128.39.216:3478").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    )
}

// ─── Helper ───────────────────────────────────────────────────────────────────

private class NoOpSdpObserver(private val label: String) : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() { Log.d("SDP", "$label success") }
    override fun onCreateFailure(error: String?) { Log.e("SDP", "$label create error: $error") }
    override fun onSetFailure(error: String?) { Log.e("SDP", "$label set error: $error") }
}
