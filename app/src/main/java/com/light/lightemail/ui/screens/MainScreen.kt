package com.light.lightemail.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.light.lightemail.data.EmailMessage
import com.light.lightemail.ui.viewmodel.EmailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: EmailViewModel = viewModel()) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("home") }
    var textSize by remember { mutableFloatStateOf(16f) }
    var signature by remember { mutableStateOf("Sent from Light Email") }
    var selectedEmail by remember { mutableStateOf<EmailMessage?>(null) }
    
    val emails by viewModel.emails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val accountEmail by viewModel.accountEmail.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LIGHT EMAIL", fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (currentScreen == "home" && accountEmail.isNotEmpty()) {
                        IconButton(onClick = { viewModel.refreshEmails() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentScreen == "home") {
                BottomBar(
                    onAboutClick = { currentScreen = "about" },
                    onSettingsClick = { currentScreen = "settings" },
                    onAddAccountClick = { currentScreen = "add_account" }
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
                    textSize = textSize,
                    onTextSizeChange = { textSize = it },
                    signature = signature,
                    onSignatureChange = { signature = it },
                    onBack = { currentScreen = "home" }
                )
                "add_account" -> AddAccountScreen(
                    onAccountAdded = { email, password, host ->
                        viewModel.setAccount(email, password, host)
                        currentScreen = "home"
                    },
                    onCancel = { currentScreen = "home" }
                )
                "view_email" -> selectedEmail?.let {
                    EmailDetailScreen(it, textSize) {
                        currentScreen = "reply"
                    }
                }
                "reply" -> selectedEmail?.let {
                    ReplyScreen(it, signature, onSend = { reply ->
                        viewModel.sendReply(it, reply, signature) { success ->
                            if (success) {
                                Toast.makeText(context, "Reply sent", Toast.LENGTH_SHORT).show()
                                currentScreen = "home"
                            } else {
                                Toast.makeText(context, "Failed to send reply", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, onCancel = { currentScreen = "view_email" })
                }
            }
        }
    }
}

@Composable
fun BottomBar(onAboutClick: () -> Unit, onSettingsClick: () -> Unit, onAddAccountClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        IconButton(onClick = onAddAccountClick) {
            Icon(Icons.Default.Add, contentDescription = "Add Account")
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
        IconButton(onClick = onAboutClick) {
            Icon(Icons.Default.Info, contentDescription = "About")
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
            Text("No emails. Add an account or refresh.", fontSize = textSize.sp)
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("LIGHT EMAIL", fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("A minimalist mail application.")
        Spacer(modifier = Modifier.height(32.dp))
        Text("Copyright 2026 By Rudi Timmermans", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
        ) {
            Text("Back")
        }
    }
}

@Composable
fun SettingsScreen(textSize: Float, onTextSizeChange: (Float) -> Unit, signature: String, onSignatureChange: (String) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("SETTINGS", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Text Size: ${textSize.toInt()}sp")
        Slider(
            value = textSize,
            onValueChange = onTextSizeChange,
            valueRange = 12f..24f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onBackground,
                activeTrackColor = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Email Signature", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = signature,
            onValueChange = onSignatureChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Signature") }
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
        ) {
            Text("Back")
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
        Text(text = email.subject, fontSize = (textSize * 1.2f).sp, fontWeight = FontWeight.Bold)
        Text(text = "From: ${email.sender}", fontSize = (textSize * 0.9f).sp)
        Text(text = "Date: ${email.date}", fontSize = (textSize * 0.8f).sp)
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = email.content, fontSize = textSize.sp, modifier = Modifier.weight(1f))

        Button(
            onClick = onReply,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
        ) {
            Text("Reply")
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
        Text(text = "Replying to: ${email.sender}", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = replyText,
            onValueChange = { replyText = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("Your Message") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Signature: $signature", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
            Button(
                onClick = { onSend(replyText) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun AddAccountScreen(onAccountAdded: (String, String, String) -> Unit, onCancel: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("imap.gmail.com") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("ADD IMAP ACCOUNT", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("IMAP Server") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onAccountAdded(email, password, server) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)
            ) {
                Text("Add")
            }
        }
    }
}
