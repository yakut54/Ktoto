package ru.yakut54.ktoto.ui.newchat

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import ru.yakut54.ktoto.utils.nameToAvatarColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onCreated: (convId: String, convName: String) -> Unit,
) {
    val vm: CreateGroupViewModel = koinViewModel()
    val query by vm.query.collectAsState()
    val users by vm.users.collectAsState()
    val loading by vm.loading.collectAsState()
    val selected by vm.selected.collectAsState()
    val selectedUsers by vm.selectedUsers.collectAsState()
    val creating by vm.creating.collectAsState()

    var step by remember { mutableIntStateOf(1) }
    var groupName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (step == 1) {
                        TextField(
                            value = query,
                            onValueChange = { vm.query.value = it },
                            placeholder = { Text("Добавить участников...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Column {
                            Text("Новая группа", fontWeight = FontWeight.Bold)
                            Text(
                                text = "${selectedUsers.size} участн.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (step == 2) step = 1 else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (step == 1 && selected.isNotEmpty()) {
                        IconButton(onClick = { step = 2 }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Далее")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (step == 2) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (groupName.isNotBlank() && !creating) {
                            vm.createGroup(groupName) { convId, convName ->
                                onCreated(convId, convName)
                            }
                        }
                    },
                    icon = {
                        if (creating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Group, null)
                        }
                    },
                    text = { Text("Создать") },
                )
            }
        },
    ) { padding ->
        if (step == 1) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (selectedUsers.isNotEmpty()) {
                    Text(
                        text = selectedUsers.joinToString(", ") { it.username },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                    )
                    HorizontalDivider()
                }

                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(users, key = { it.id }) { user ->
                            val isSelected = user.id in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.toggleUser(user) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.size(46.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(nameToAvatarColor(user.username), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = user.username.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color.White,
                                        )
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .align(Alignment.BottomEnd)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp),
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = user.username,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            HorizontalDivider(Modifier.padding(start = 74.dp))
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(Modifier.height(28.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Название группы") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "${selectedUsers.size} участников: ${selectedUsers.joinToString(", ") { it.username }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
