package ru.yakut54.ktoto.ui.call

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import ru.yakut54.ktoto.call.CallState

@Composable
fun CallScreen(
    onCallEnded: () -> Unit,
    vm: CallViewModel = koinViewModel(),
) {
    val state by vm.callState.collectAsState()
    val info by vm.callInfo.collectAsState()
    val isMuted by vm.isMuted.collectAsState()
    val isSpeakerOn by vm.isSpeakerOn.collectAsState()
    val duration by vm.durationSec.collectAsState()
    val isVideoCall by vm.isVideoCall.collectAsState()
    val isVideoEnabled by vm.isVideoEnabled.collectAsState()
    val isCameraFront by vm.isCameraFront.collectAsState()
    val localTrack by vm.localVideoTrack.collectAsState()
    val remoteTrack by vm.remoteVideoTrack.collectAsState()
    val eglContext = vm.eglBaseContext

    LaunchedEffect(state) {
        if (state == CallState.IDLE) onCallEnded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
    ) {
        // ── Remote video (full screen) ──
        if (isVideoCall && remoteTrack != null && eglContext != null) {
            SurfaceVideoView(
                track = remoteTrack!!,
                eglContext = eglContext,
                mirror = false,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Avatar + name + status (audio call or no remote video yet) ──
        if (!isVideoCall || remoteTrack == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 32.dp)
                    .padding(top = 80.dp),
            ) {
                AvatarPulse(
                    avatarUrl = info?.peerAvatarUrl,
                    peerName = info?.peerName ?: "",
                    isRinging = state == CallState.INCOMING_RINGING || state == CallState.OUTGOING_RINGING,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = info?.peerName ?: "",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            // Overlay peer name on video
            Text(
                text = info?.peerName ?: "",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 56.dp),
            )
        }

        // ── Status text ──
        AnimatedContent(
            targetState = state,
            label = "callStatus",
            modifier = Modifier
                .align(if (isVideoCall && remoteTrack != null) Alignment.TopStart else Alignment.Center)
                .padding(
                    start = if (isVideoCall && remoteTrack != null) 20.dp else 0.dp,
                    top = if (isVideoCall && remoteTrack != null) 88.dp else 260.dp,
                ),
        ) { s ->
            Text(
                text = when (s) {
                    CallState.OUTGOING_RINGING -> "Вызов..."
                    CallState.INCOMING_RINGING -> "Входящий звонок"
                    CallState.NEGOTIATING -> "Соединение..."
                    CallState.RECONNECTING -> "Восстановление..."
                    CallState.IN_CALL -> formatDuration(duration)
                    CallState.ENDED -> "Звонок завершён"
                    else -> ""
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
            )
        }

        // ── Local camera PiP (video calls) ──
        if (isVideoCall && localTrack != null && isVideoEnabled && eglContext != null) {
            SurfaceVideoView(
                track = localTrack!!,
                eglContext = eglContext,
                mirror = isCameraFront,
                isOnTop = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 12.dp)
                    .size(width = 96.dp, height = 144.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        // ── Controls ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
        ) {
            when (state) {
                CallState.INCOMING_RINGING -> {
                    IncomingCallControls(
                        onAccept = { vm.acceptCall() },
                        onDecline = { vm.rejectCall() },
                    )
                }
                CallState.OUTGOING_RINGING, CallState.NEGOTIATING -> {
                    Row(horizontalArrangement = Arrangement.Center) {
                        CallButton(
                            icon = Icons.Default.CallEnd,
                            tint = Color.White,
                            background = Color(0xFFE53935),
                            onClick = {
                                if (state == CallState.OUTGOING_RINGING) vm.cancelCall() else vm.endCall()
                            },
                        )
                    }
                }
                CallState.IN_CALL, CallState.RECONNECTING -> {
                    if (isVideoCall) {
                        VideoInCallControls(
                            isMuted = isMuted,
                            isVideoEnabled = isVideoEnabled,
                            isSpeakerOn = isSpeakerOn,
                            onMute = { vm.toggleMute() },
                            onVideo = { vm.toggleVideo() },
                            onSwitchCamera = { vm.switchCamera() },
                            onSpeaker = { vm.toggleSpeaker() },
                            onEnd = { vm.endCall() },
                        )
                    } else {
                        InCallControls(
                            isMuted = isMuted,
                            isSpeakerOn = isSpeakerOn,
                            onMute = { vm.toggleMute() },
                            onSpeaker = { vm.toggleSpeaker() },
                            onEnd = { vm.endCall() },
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

// ── Video rendering ───────────────────────────────────────────────────────────

@Composable
private fun SurfaceVideoView(
    track: VideoTrack,
    eglContext: EglBase.Context,
    mirror: Boolean,
    isOnTop: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).also { renderer ->
                try {
                    renderer.init(eglContext, null)
                    renderer.setEnableHardwareScaler(true)
                    renderer.setMirror(mirror)
                    if (isOnTop) renderer.setZOrderMediaOverlay(true)
                    track.addSink(renderer)
                } catch (e: Exception) {
                    android.util.Log.e("SurfaceVideoView", "init failed: ${e.message}", e)
                }
            }
        },
        modifier = modifier,
        onRelease = { renderer ->
            try { track.removeSink(renderer) } catch (_: Exception) {}
            try { renderer.release() } catch (_: Exception) {}
        },
    )
}

// ── Avatar ────────────────────────────────────────────────────────────────────

@Composable
private fun AvatarPulse(avatarUrl: String?, peerName: String, isRinging: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRinging) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale",
    )

    Box(contentAlignment = Alignment.Center) {
        if (isRinging) {
            Box(
                Modifier
                    .size(130.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            )
        }
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = peerName,
                modifier = Modifier.size(110.dp).clip(CircleShape),
            )
        } else {
            Box(
                Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = peerName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── Controls ──────────────────────────────────────────────────────────────────

@Composable
private fun IncomingCallControls(onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(60.dp), verticalAlignment = Alignment.CenterVertically) {
        CallButton(icon = Icons.Default.CallEnd, tint = Color.White, background = Color(0xFFE53935), onClick = onDecline)
        CallButton(icon = Icons.Default.Call, tint = Color.White, background = Color(0xFF43A047), onClick = onAccept)
    }
}

@Composable
private fun InCallControls(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onEnd: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        CallButton(
            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            tint = if (isMuted) Color.White else Color(0xFF1A1A2E),
            background = if (isMuted) Color(0xFF555580) else Color.White.copy(alpha = 0.9f),
            size = 56.dp, onClick = onMute,
        )
        CallButton(icon = Icons.Default.CallEnd, tint = Color.White, background = Color(0xFFE53935), size = 68.dp, onClick = onEnd)
        CallButton(
            icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            tint = if (isSpeakerOn) Color.White else Color(0xFF1A1A2E),
            background = if (isSpeakerOn) Color(0xFF555580) else Color.White.copy(alpha = 0.9f),
            size = 56.dp, onClick = onSpeaker,
        )
    }
}

@Composable
private fun VideoInCallControls(
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    isSpeakerOn: Boolean,
    onMute: () -> Unit,
    onVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSpeaker: () -> Unit,
    onEnd: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Top row: Mic, Camera, Speaker, SwitchCam
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            CallButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                tint = if (isMuted) Color.White else Color(0xFF1A1A2E),
                background = if (isMuted) Color(0xFF555580) else Color.White.copy(alpha = 0.9f),
                size = 52.dp, onClick = onMute,
            )
            CallButton(
                icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                tint = if (!isVideoEnabled) Color.White else Color(0xFF1A1A2E),
                background = if (!isVideoEnabled) Color(0xFF555580) else Color.White.copy(alpha = 0.9f),
                size = 52.dp, onClick = onVideo,
            )
            CallButton(
                icon = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                tint = if (isSpeakerOn) Color.White else Color(0xFF1A1A2E),
                background = if (isSpeakerOn) Color(0xFF555580) else Color.White.copy(alpha = 0.9f),
                size = 52.dp, onClick = onSpeaker,
            )
            CallButton(
                icon = Icons.Default.Cameraswitch,
                tint = Color(0xFF1A1A2E),
                background = Color.White.copy(alpha = 0.9f),
                size = 52.dp, onClick = onSwitchCamera,
            )
        }
        // End call
        CallButton(icon = Icons.Default.CallEnd, tint = Color.White, background = Color(0xFFE53935), size = 68.dp, onClick = onEnd)
    }
}

@Composable
private fun CallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size).clip(CircleShape).background(background),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

private fun formatDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}
