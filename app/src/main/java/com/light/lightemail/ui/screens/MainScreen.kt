package com.light.lightemail.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.light.lightemail.R
import com.light.lightemail.data.EmailMessage
import com.light.lightemail.ui.viewmodel.EmailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: EmailViewModel = viewModel()) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("home") }
    var selectedEmail by remember { mutableStateOf<EmailMessage?>(null) }
    
    val emails by viewModel.emails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val accountEmail by viewModel.accountEmail.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val signature by viewModel.signature.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_title), fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    if (currentScreen != "home") {
                        IconButton(onClick = {
                            if (currentScreen == "reply") currentScreen = "view_email"
                            else currentScreen = "home"
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (currentScreen == "home" && accountEmail.isNotEmpty()) {
                        IconButton(onClick = { viewModel.refreshEmails() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentScreen == "home") {
                BottomBar(
                    onAboutClick = { currentScreen = "about" },
                    onSettingsClick = { currentScreen = "settings" }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                "home" -> EmailListScreen(emails, isLoading, textSize) { emailMsg ->
                    selectedEmail = emailMsg
                    currentScreen = "view_email"
                }
                "about" -> AboutScreen { currentScreen = "home" }
                "settings" -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = "home" }
                )
                "view_email" -> selectedEmail?.let {
                    EmailDetailScreen(it, textSize) {
                        currentScreen = "reply"
                    }
                }
                "reply" -> selectedEmail?.let {
                    val replySentText = stringResource(R.string.reply_sent)
                    val failedReplyText = stringResource(R.string.failed_to_send_reply)
                    ReplyScreen(it, signature, onSend = { reply ->
                        viewModel.sendReply(it, reply, signature) { success ->
                            if (success) {
                                Toast.makeText(context, replySentText, Toast.LENGTH_SHORT).show()
                                currentScreen = "home"
                            } else {
                                Toast.makeText(context, failedReplyText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, onCancel = { currentScreen = "view_email" })
                }
            }
        }
    }
}

@Composable
fun BottomBar(onAboutClick: () -> Unit, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
        }
        IconButton(onClick = onAboutClick) {
            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about))
        }
    }
}

@Composable
fun EmailListScreen(emails: List<EmailMessage>, isLoading: Boolean, textSize: Float, onEmailClick: (EmailMessage) -> Unit) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
        }
    } else if (emails.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_emails), fontSize = textSize.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(emails) { email ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEmailClick(email) }
                        .padding(16.dp)
                ) {
                    Text(
                        text = email.sender,
                        fontSize = (textSize * 0.8f).sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = email.subject,
                        fontSize = textSize.sp
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.app_title), fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.app_description))
        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.copyright), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
        ) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
fun SettingsScreen(viewModel: EmailViewModel, onBack: () -> Unit) {
    val currentEmail by viewModel.accountEmail.collectAsState()
    val currentPassword by viewModel.accountPassword.collectAsState()
    val currentHost by viewModel.imapHost.collectAsState()
    val currentTextSize by viewModel.textSize.collectAsState()
    val currentSignature by viewModel.signature.collectAsState()

    var email by remember { mutableStateOf(currentEmail) }
    var password by remember { mutableStateOf(currentPassword) }
    var host by remember { mutableStateOf(currentHost) }
    var textSize by remember { mutableFloatStateOf(currentTextSize) }
    var signature by remember { mutableStateOf(currentSignature) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))

        // IMAP Settings
        Text(stringResource(R.string.add_imap_account_title), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password_label)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible)
                    Icons.Default.Visibility
                else Icons.Default.VisibilityOff

                val description = if (passwordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password)

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text(stringResource(R.string.imap_server_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Appearance Settings
        Text(stringResource(R.string.text_size_label, textSize.toInt()), fontWeight = FontWeight.Bold)
        Slider(
            value = textSize,
            onValueChange = { textSize = it },
            valueRange = 12f..24f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onBackground,
                activeTrackColor = MaterialTheme.colorScheme.onBackground
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.email_signature_title), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = signature,
            onValueChange = { signature = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.signature_label)) }
        )

        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground)
            ) {
                Text(stringResource(R.string.back))
            }
            Button(
                onClick = {
                    viewModel.saveSettings(email, password, host, textSize, signature)
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
fun EmailDetailScreen(email: EmailMessage, textSize: Float, onReply: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = email.subject, fontSize = (textSize * 1.2f).sp, fontWeight = FontWeight.Bold)
            Text(text = stringResource(R.string.from_label, email.sender), fontSize = (textSize * 0.9f).sp)
            Text(text = stringResource(R.string.date_label, email.date), fontSize = (textSize * 0.8f).sp)
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = email.content, fontSize = textSize.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onReply,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
        ) {
            Text(stringResource(R.string.reply))
        }
    }
}

@Composable
fun ReplyScreen(email: EmailMessage, signature: String, onSend: (String) -> Unit, onCancel: () -> Unit) {
    var replyText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = stringResource(R.string.replying_to, email.sender), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = replyText,
                onValueChange = { replyText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                label = { Text(stringResource(R.string.your_message_label)) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.signature_preview, signature),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = { onSend(replyText) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
            ) {
                Text(stringResource(R.string.send))
            }
        }
    }
}
