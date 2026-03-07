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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
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

    // Navigate back when call is truly idle
    LaunchedEffect(state) {
        if (state == CallState.IDLE) onCallEnded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Spacer(Modifier.height(80.dp))

            // Avatar
            AvatarPulse(
                avatarUrl = info?.peerAvatarUrl,
                peerName = info?.peerName ?: "",
                isRinging = state == CallState.INCOMING_RINGING || state == CallState.OUTGOING_RINGING,
            )

            Spacer(Modifier.height(24.dp))

            // Peer name
            Text(
                text = info?.peerName ?: "",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))

            // Status line
            AnimatedContent(targetState = state) { s ->
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

            Spacer(Modifier.weight(1f))

            // Controls
            when (state) {
                CallState.INCOMING_RINGING -> {
                    IncomingCallControls(
                        onAccept = { vm.acceptCall() },
                        onDecline = { vm.rejectCall() },
                    )
                }
                CallState.OUTGOING_RINGING, CallState.NEGOTIATING -> {
                    // Cancel button only
                    Row(horizontalArrangement = Arrangement.Center) {
                        CallButton(
                            icon = Icons.Default.CallEnd,
                            tint = Color.White,
                            background = Color(0xFFE53935),
                            onClick = { if (state == CallState.OUTGOING_RINGING) vm.cancelCall() else vm.endCall() },
                        )
                    }
                }
                CallState.IN_CALL, CallState.RECONNECTING -> {
                    InCallControls(
                        isMuted = isMuted,
                        isSpeakerOn = isSpeakerOn,
                        onMute = { vm.toggleMute() },
                        onSpeaker = { vm.toggleSpeaker() },
                        onEnd = { vm.endCall() },
                    )
                }
                else -> {}
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

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
        // Pulse ring
        if (isRinging) {
            Box(
                Modifier
                    .size(130.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            )
        }
        // Avatar
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = peerName,
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape),
            )
        } else {
            // Fallback: initials circle
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

@Composable
private fun IncomingCallControls(onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decline
        CallButton(
            icon = Icons.Default.CallEnd,
            tint = Color.White,
            background = Color(0xFFE53935),
            onClick = onDecline,
        )
        // Accept
        CallButton(
            icon = Icons.Default.Call,
            tint = Color.White,
            background = Color(0xFF43A047),
            onClick = onAccept,
        )
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mute
        CallButton(
            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            tint = if (isMuted) Color.White else Color(0xFF1A1A2E),
            background = if (isMuted) Color(0xFF555580) else Color.White.copy(alpha = 0.9f),
            size = 56.dp,
            onClick = onMute,
        )
        // End call
        CallButton(
            icon = Icons.Default.CallEnd,
            tint = Color.White,
            background = Color(0xFFE53935),
            size = 68.dp,
            onClick = onEnd,
        )
        // Speaker
        CallButton(
            icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
            tint = if (isSpeakerOn) Color.White else Color(0xFF1A1A2E),
            background = if (isSpeakerOn) Color(0xFF555580) else Color.White.copy(alpha = 0.9f),
            size = 56.dp,
            onClick = onSpeaker,
        )
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
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

private fun formatDuration(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}
