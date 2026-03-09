package ru.yakut54.ktoto.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import ru.yakut54.ktoto.data.store.AppTheme
import ru.yakut54.ktoto.utils.nameToAvatarColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onBlockedUsers: () -> Unit,
) {
    val vm: SettingsViewModel = koinViewModel()
    val profile by vm.profile.collectAsState()
    val uploadingAvatar by vm.uploadingAvatar.collectAsState()
    val savingProfile by vm.savingProfile.collectAsState()
    val changingPassword by vm.changingPassword.collectAsState()
    val event by vm.event.collectAsState()

    val theme by vm.prefsStore.theme.collectAsState(initial = AppTheme.SYSTEM)
    val fontScale by vm.prefsStore.fontScale.collectAsState(initial = 1.0f)
    val soundEnabled by vm.prefsStore.soundEnabled.collectAsState(initial = true)
    val vibrationEnabled by vm.prefsStore.vibrationEnabled.collectAsState(initial = true)

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    LaunchedEffect(event) {
        when (val e = event) {
            is SettingsEvent.Error -> snackbar.showSnackbar(e.message)
            is SettingsEvent.PasswordChanged -> snackbar.showSnackbar("Пароль изменён")
            is SettingsEvent.ProfileSaved -> snackbar.showSnackbar("Сохранено")
            null -> {}
        }
        vm.consumeEvent()
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { vm.uploadAvatar(context, it) }
    }

    if (showRenameDialog) {
        RenameUsernameDialog(
            current = profile.username,
            saving = savingProfile,
            onSave = { vm.saveUsername(it); showRenameDialog = false },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            changing = changingPassword,
            onChange = { cur, new -> vm.changePassword(cur, new); showPasswordDialog = false },
            onDismiss = { showPasswordDialog = false },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.logout(onLogout)
                    showLogoutDialog = false
                }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Profile card ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.size(96.dp)) {
                    val avatarColor = nameToAvatarColor(profile.username.ifBlank { "?" })
                    if (profile.avatarUrl != null) {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = "Аватар",
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(avatarColor, CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(avatarColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = profile.username.take(1).uppercase().ifBlank { "?" },
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                    // Camera badge
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.BottomEnd)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uploadingAvatar) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Сменить аватар",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = profile.username.ifBlank { "..." },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (profile.email.isNotBlank()) {
                    Text(
                        text = profile.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            // ── Account ───────────────────────────────────────────────────────
            SectionHeader("Аккаунт")
            SettingsRow(
                icon = Icons.Default.Person,
                title = "Имя пользователя",
                subtitle = profile.username.ifBlank { "..." },
                onClick = { showRenameDialog = true },
            )
            SettingsRow(
                icon = Icons.Default.Lock,
                title = "Сменить пароль",
                onClick = { showPasswordDialog = true },
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))

            // ── Appearance ────────────────────────────────────────────────────
            SectionHeader("Внешний вид")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text("Тема", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(AppTheme.SYSTEM to "Авто", AppTheme.LIGHT to "Светлая", AppTheme.DARK to "Тёмная")
                    options.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = theme == value,
                            onClick = { vm.setTheme(value) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Размер текста", style = MaterialTheme.typography.bodyLarge)
                        val label = when {
                            fontScale <= 1.0f -> "Обычный"
                            fontScale <= 1.15f -> "Крупный"
                            fontScale <= 1.3f -> "Очень крупный"
                            else -> "Максимальный"
                        }
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                var sliderValue by remember(fontScale) { mutableFloatStateOf(fontScale) }
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { vm.setFontScale(sliderValue) },
                    valueRange = 1.0f..1.5f,
                    steps = 2,
                )
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))

            // ── Notifications ─────────────────────────────────────────────────
            SectionHeader("Уведомления")
            SettingsSwitchRow(
                icon = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                title = "Звук уведомлений",
                checked = soundEnabled,
                onCheckedChange = { vm.setSoundEnabled(it) },
            )
            SettingsSwitchRow(
                icon = Icons.Default.Notifications,
                title = "Вибрация",
                checked = vibrationEnabled,
                onCheckedChange = { vm.setVibrationEnabled(it) },
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))

            // ── Privacy ───────────────────────────────────────────────────────
            SectionHeader("Конфиденциальность")
            SettingsRow(
                icon = Icons.Default.Block,
                title = "Заблокированные пользователи",
                onClick = onBlockedUsers,
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))

            // ── About ─────────────────────────────────────────────────────────
            SectionHeader("О приложении")
            SettingsRow(
                icon = Icons.Default.Info,
                title = "Ktoto",
                subtitle = "Версия 1.0.0",
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))

            // ── Logout ────────────────────────────────────────────────────────
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "Выйти",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showLogoutDialog = true },
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else titleColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RenameUsernameDialog(
    current: String,
    saving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Имя пользователя") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text("Новое имя") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank() && !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ChangePasswordDialog(
    changing: Boolean,
    onChange: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && newPass != confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена пароля") },
        text = {
            Column {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Текущий пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("Новый пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Повторите пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                    supportingText = if (mismatch) ({ Text("Пароли не совпадают") }) else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onChange(current, newPass) },
                enabled = current.isNotBlank() && newPass.length >= 8 && newPass == confirm && !changing,
            ) {
                if (changing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Изменить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
