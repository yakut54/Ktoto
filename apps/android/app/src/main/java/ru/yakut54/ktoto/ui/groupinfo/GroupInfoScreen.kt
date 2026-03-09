package ru.yakut54.ktoto.ui.groupinfo

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import ru.yakut54.ktoto.data.model.GroupMember
import ru.yakut54.ktoto.ui.components.DialogAction
import ru.yakut54.ktoto.ui.components.KtotoActionDialog
import ru.yakut54.ktoto.ui.components.KtotoDialog
import ru.yakut54.ktoto.utils.nameToAvatarColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    conversationId: String,
    conversationName: String,
    currentUserId: String,
    onBack: () -> Unit,
    onLeft: () -> Unit,           // called when current user leaves the group
    onNameChanged: (String) -> Unit, // propagate new name back to ChatScreen title
) {
    val vm: GroupInfoViewModel = koinViewModel()
    val state by vm.state.collectAsState()
    val convName by vm.convName.collectAsState()
    val saving by vm.saving.collectAsState()
    val searchUsers by vm.searchUsers.collectAsState()
    val searching by vm.searching.collectAsState()
    val avatarUrl by vm.avatarUrl.collectAsState()
    val uploadingAvatar by vm.uploadingAvatar.collectAsState()
    val context = LocalContext.current

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.uploadAvatar(context, conversationId, it) } }

    LaunchedEffect(conversationId) { vm.load(conversationId, conversationName) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var addQuery by remember { mutableStateOf("") }

    var memberMenu by remember { mutableStateOf<GroupMember?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Determine current user's role
    val myRole = (state as? GroupInfoState.Success)
        ?.members?.find { it.id == currentUserId }?.role ?: "member"
    val isAdmin = myRole == "admin"

    if (showRenameDialog) {
        RenameDialog(
            current = convName,
            saving = saving,
            onConfirm = { newName ->
                vm.rename(conversationId, newName) { n ->
                    onNameChanged(n)
                    showRenameDialog = false
                }
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showLeaveConfirm) {
        KtotoDialog(
            title = "Покинуть группу?",
            message = "Вы выйдете из группы. Вас можно снова добавить.",
            icon = Icons.Default.PersonRemove,
            confirmText = "Выйти",
            confirmColor = MaterialTheme.colorScheme.error,
            onConfirm = {
                showLeaveConfirm = false
                vm.leaveGroup(conversationId, currentUserId) { onLeft() }
            },
            onDismiss = { showLeaveConfirm = false },
        )
    }

    // Member action dialog
    memberMenu?.let { target ->
        val isSelf = target.id == currentUserId
        val actions = buildList {
            if (isAdmin && !isSelf) {
                if (target.role == "member") {
                    add(DialogAction("Сделать администратором") {
                        vm.changeRole(conversationId, target.id, "admin")
                        memberMenu = null
                    })
                } else {
                    add(DialogAction("Снять с администратора") {
                        vm.changeRole(conversationId, target.id, "member")
                        memberMenu = null
                    })
                }
                add(DialogAction("Удалить из группы", isDestructive = true) {
                    vm.removeMember(conversationId, target.id)
                    memberMenu = null
                })
            }
            if (isSelf) {
                add(DialogAction("Покинуть группу", isDestructive = true) {
                    memberMenu = null
                    showLeaveConfirm = true
                })
            }
        }
        if (actions.isNotEmpty()) {
            KtotoActionDialog(
                title = target.username,
                actions = actions,
                onDismiss = { memberMenu = null },
            )
        } else {
            memberMenu = null
        }
    }

    // Add member bottom sheet
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false; addQuery = "" },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Добавить участника",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = addQuery,
                    onValueChange = {
                        addQuery = it
                        vm.searchForAdd(it)
                    },
                    placeholder = { Text("Поиск...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (addQuery.isNotEmpty()) {
                            IconButton(onClick = { addQuery = ""; vm.searchForAdd("") }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))

                val existingIds = (state as? GroupInfoState.Success)
                    ?.members?.map { it.id }?.toSet() ?: emptySet()

                if (searching) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    val filtered = searchUsers.filter { it.id !in existingIds }
                    filtered.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.addMember(conversationId, user.id) {
                                        showAddSheet = false
                                        addQuery = ""
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(nameToAvatarColor(user.username), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    user.username.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(user.username, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (filtered.isEmpty() && addQuery.isNotBlank()) {
                        Text(
                            "Не найден",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(convName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Default.Edit, "Переименовать")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Group avatar header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .then(
                                if (isAdmin) Modifier.clickable {
                                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = convName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        // Camera overlay for admin
                        if (isAdmin) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (uploadingAvatar) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AddAPhoto,
                                        contentDescription = "Загрузить фото",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        convName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val count = (state as? GroupInfoState.Success)?.members?.size
                    if (count != null) {
                        Text(
                            "$count участников",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }

            // Add member row (admin only)
            if (isAdmin) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.searchForAdd("")
                                showAddSheet = true
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Добавить участника",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    HorizontalDivider(Modifier.padding(start = 74.dp))
                }
            }

            // Members section header
            item {
                Text(
                    "УЧАСТНИКИ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                )
            }

            when (val s = state) {
                is GroupInfoState.Loading -> item {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is GroupInfoState.Error -> item {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                is GroupInfoState.Success -> {
                    items(s.members, key = { it.id }) { member ->
                        MemberRow(
                            member = member,
                            isSelf = member.id == currentUserId,
                            canInteract = isAdmin || member.id == currentUserId,
                            onClick = { memberMenu = member },
                        )
                        HorizontalDivider(Modifier.padding(start = 74.dp))
                    }
                }
            }

            // Leave group
            item {
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = { showLeaveConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        "Покинуть группу",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMember,
    isSelf: Boolean,
    canInteract: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canInteract) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(nameToAvatarColor(member.username), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                member.username.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isSelf) "${member.username} (вы)" else member.username,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (member.role == "admin") {
                Text(
                    "администратор",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    current: String,
    saving: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Переименовать группу",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { if (value.isNotBlank() && !saving) onConfirm(value) },
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Сохранить", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
