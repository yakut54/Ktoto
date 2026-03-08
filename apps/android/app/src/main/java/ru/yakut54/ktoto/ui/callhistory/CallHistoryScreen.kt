package ru.yakut54.ktoto.ui.callhistory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import ru.yakut54.ktoto.data.model.CallRecord
import ru.yakut54.ktoto.utils.nameToAvatarColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(onBack: () -> Unit) {
    val vm: CallHistoryViewModel = koinViewModel()
    val records by vm.records.collectAsState()
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История звонков", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Звонков ещё не было", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(records, key = { it.id }) { record ->
                CallHistoryItem(record)
                HorizontalDivider(Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun CallHistoryItem(record: CallRecord) {
    val status = callStatus(record)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        val avatarUrl = record.peer.avatarUrl
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val color = nameToAvatarColor(record.peer.username)
                androidx.compose.foundation.Canvas(modifier = Modifier.size(44.dp)) {
                    drawCircle(color = color)
                }
                Text(
                    text = record.peer.username.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Name + details
        Column(modifier = Modifier.weight(1f)) {
            Text(record.peer.username, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = status.icon,
                    contentDescription = null,
                    tint = status.color,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = status.label + (record.durationSec?.let { " · ${formatDuration(it)}" } ?: ""),
                    color = status.color,
                    fontSize = 13.sp,
                )
            }
        }

        // Type icon + time
        Column(horizontalAlignment = Alignment.End) {
            Icon(
                imageVector = if (record.callType == "video") Icons.Default.Videocam else Icons.Default.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = formatCallTime(record.startedAt),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class CallStatusUi(
    val label: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private fun callStatus(record: CallRecord): CallStatusUi {
    val answered = record.answeredAt != null
    val reason = record.endReason ?: "unknown"
    return when {
        answered -> if (record.isOutgoing)
            CallStatusUi("Исходящий", Color(0xFF2196F3), Icons.Default.Phone)
        else
            CallStatusUi("Входящий", Color(0xFF4CAF50), Icons.AutoMirrored.Filled.PhoneCallback)

        record.isOutgoing && reason == "declined" ->
            CallStatusUi("Отклонён", Color(0xFF9E9E9E), Icons.Default.Phone)

        record.isOutgoing ->
            CallStatusUi("Отменён", Color(0xFF9E9E9E), Icons.Default.Phone)

        reason == "cancelled" || reason.startsWith("timeout") ->
            CallStatusUi("Пропущен", Color(0xFFF44336), Icons.AutoMirrored.Filled.PhoneMissed)

        reason == "declined" ->
            CallStatusUi("Отклонён", Color(0xFF9E9E9E), Icons.AutoMirrored.Filled.PhoneCallback)

        else ->
            CallStatusUi("Пропущен", Color(0xFFF44336), Icons.AutoMirrored.Filled.PhoneMissed)
    }
}

private fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "${m}м ${s}с" else "${s}с"
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFmt = SimpleDateFormat("d MMM", Locale.forLanguageTag("ru"))

private fun formatCallTime(isoStr: String): String {
    return try {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            .also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(isoStr) ?: return isoStr
        val now = Date()
        val diff = now.time - date.time
        if (diff < 86_400_000) timeFmt.format(date) else dateFmt.format(date)
    } catch (_: Exception) {
        isoStr
    }
}
