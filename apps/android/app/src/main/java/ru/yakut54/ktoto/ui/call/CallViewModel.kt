package ru.yakut54.ktoto.ui.call

import androidx.lifecycle.ViewModel
import ru.yakut54.ktoto.call.CallManager

class CallViewModel(private val callManager: CallManager) : ViewModel() {

    val callState = callManager.callState
    val callInfo = callManager.callInfo
    val isMuted = callManager.isMuted
    val isSpeakerOn = callManager.isSpeakerOn
    val durationSec = callManager.durationSec

    fun startCall(peerId: String, peerName: String, peerAvatarUrl: String?, callType: String = "audio") =
        callManager.startCall(peerId, peerName, peerAvatarUrl, callType)

    fun acceptCall() = callManager.acceptCall()
    fun rejectCall() = callManager.rejectCall()
    fun endCall() = callManager.endCall()
    fun cancelCall() = callManager.cancelCall()
    fun toggleMute() = callManager.toggleMute()
    fun toggleSpeaker() = callManager.toggleSpeaker()
}
