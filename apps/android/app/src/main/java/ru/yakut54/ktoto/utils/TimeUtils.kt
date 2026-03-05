package ru.yakut54.ktoto.utils

import androidx.compose.ui.graphics.Color
import ru.yakut54.ktoto.ui.theme.AvatarPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fmtTime = DateTimeFormatter.ofPattern("HH:mm")
private val fmtDate = DateTimeFormatter.ofPattern("dd.MM.yy")
private val fmtDay  = DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("ru"))

fun formatMessageTime(iso: String?): String = runCatching {
    Instant.parse(iso!!).atZone(ZoneId.systemDefault()).format(fmtTime)
}.getOrDefault("")

fun formatConversationTime(iso: String?): String = runCatching {
    val zdt   = Instant.parse(iso!!).atZone(ZoneId.systemDefault())
    val today = LocalDate.now(ZoneId.systemDefault())
    when {
        zdt.toLocalDate() == today                         -> zdt.format(fmtTime)
        zdt.toLocalDate().isAfter(today.minusDays(6))     -> zdt.format(fmtDay)
        else                                              -> zdt.format(fmtDate)
    }
}.getOrDefault("")

fun nameToAvatarColor(name: String): Color =
    AvatarPalette[name.hashCode().and(0x7FFFFFFF) % AvatarPalette.size]
