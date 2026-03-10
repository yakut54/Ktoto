package ru.yakut54.ktoto.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import ru.yakut54.ktoto.data.api.ApiService
import ru.yakut54.ktoto.data.model.ForgotPasswordRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    val api: ApiService = koinInject()
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Забыл пароль") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (sent) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Письмо отправлено!",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Проверьте почту $email и перейдите по ссылке для сброса пароля.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                } else {
                    Text(
                        text = "Введите email, указанный при регистрации. Мы пришлём ссылку для сброса пароля.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )

                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (email.isNotBlank() && !isLoading) {
                                scope.launch { send(api, email, { isLoading = it }, { sent = it }, { error = it }) }
                            }
                        }),
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                    )

                    Spacer(Modifier.height(24.dp))

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = {
                                scope.launch { send(api, email, { isLoading = it }, { sent = it }, { error = it }) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = email.isNotBlank(),
                        ) {
                            Text("Отправить письмо")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun send(
    api: ApiService,
    email: String,
    setLoading: (Boolean) -> Unit,
    setSent: (Boolean) -> Unit,
    setError: (String?) -> Unit,
) {
    setLoading(true)
    setError(null)
    try {
        api.forgotPassword(ForgotPasswordRequest(email.trim().lowercase()))
        setSent(true)
    } catch (_: Exception) {
        setError("Не удалось отправить письмо. Проверьте интернет и попробуйте снова.")
    } finally {
        setLoading(false)
    }
}
