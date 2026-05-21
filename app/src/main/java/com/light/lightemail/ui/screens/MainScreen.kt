package com.light.lightemail.ui.screens

import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.light.lightemail.R
import com.light.lightemail.data.Contact
import com.light.lightemail.data.EmailMessage
import com.light.lightemail.ui.viewmodel.EmailViewModel
import kotlinx.coroutines.launch

enum class Screen {
    Home, Settings, About, ViewEmail, Compose, AddressBook
}

enum class ComposeMode {
    New, Reply, Forward
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: EmailViewModel = viewModel()) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var selectedEmail by remember { mutableStateOf<EmailMessage?>(null) }
    var composeMode by remember { mutableStateOf(ComposeMode.New) }
    
    val emails by viewModel.emails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val accountEmail by viewModel.accountEmail.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val signature by viewModel.signature.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(stringResource(R.string.folders), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                HorizontalDivider()
                LazyColumn {
                    items(folders) { folder ->
                        NavigationDrawerItem(
                            label = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(folder.name, modifier = Modifier.weight(1f))
                                    if (folder.unreadCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text(folder.unreadCount.toString(), color = Color.White)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text("(${folder.messageCount})", fontSize = 12.sp, color = Color.Gray)
                                    if (folder.name.lowercase().contains("trash")) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = { viewModel.emptyTrash() }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = "Empty Trash", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            },
                            selected = folder.name == currentFolder,
                            onClick = {
                                viewModel.selectFolder(folder.name)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        },
        gesturesEnabled = currentScreen == Screen.Home
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.app_title), fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
                    navigationIcon = {
                        if (currentScreen != Screen.Home) {
                            IconButton(onClick = { currentScreen = Screen.Home }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    },
                    actions = {
                        if (currentScreen == Screen.Home && accountEmail.isNotEmpty()) {
                            IconButton(onClick = { viewModel.refreshEmails() }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (currentScreen == Screen.Home) {
                    FloatingActionButton(onClick = {
                        selectedEmail = null
                        composeMode = ComposeMode.New
                        currentScreen = Screen.Compose
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.compose))
                    }
                }
            },
            bottomBar = {
                if (currentScreen == Screen.Home) {
                    BottomBar(
                        onAboutClick = { currentScreen = Screen.About },
                        onSettingsClick = { currentScreen = Screen.Settings },
                        onAddressBookClick = { currentScreen = Screen.AddressBook }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    Screen.Home -> EmailListScreen(emails, isLoading, textSize) { emailMsg ->
                        selectedEmail = emailMsg
                        viewModel.markAsRead(emailMsg)
                        currentScreen = Screen.ViewEmail
                    }
                    Screen.About -> AboutScreen { currentScreen = Screen.Home }
                    Screen.Settings -> SettingsScreen(viewModel = viewModel, onBack = { currentScreen = Screen.Home })
                    Screen.AddressBook -> AddressBookScreen(viewModel = viewModel, textSize = textSize)
                    Screen.ViewEmail -> selectedEmail?.let { email ->
                        EmailDetailScreen(
                            email = email,
                            textSize = textSize,
                            onReply = {
                                composeMode = ComposeMode.Reply
                                currentScreen = Screen.Compose
                            },
                            onForward = {
                                composeMode = ComposeMode.Forward
                                currentScreen = Screen.Compose
                            },
                            onDelete = {
                                viewModel.deleteEmail(email)
                                currentScreen = Screen.Home
                            },
                            onAddContact = { name, emailAddr ->
                                viewModel.addContact(name, emailAddr)
                                Toast.makeText(context, context.getString(R.string.contact_added), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    Screen.Compose -> ComposeEmailScreen(
                        viewModel = viewModel,
                        mode = composeMode,
                        originalEmail = selectedEmail,
                        textSize = textSize,
                        onFinished = { currentScreen = Screen.Home }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomBar(onAboutClick: () -> Unit, onSettingsClick: () -> Unit, onAddressBookClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
        }
        IconButton(onClick = onAddressBookClick) {
            Icon(Icons.Default.Contacts, contentDescription = stringResource(R.string.address_book))
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
            CircularProgressIndicator()
        }
    } else if (emails.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_emails), fontSize = textSize.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(emails) { email ->
                val opacity = if (email.isRead) 0.5f else 1.0f
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEmailClick(email) }
                        .padding(16.dp)
                        .graphicsLayer(alpha = opacity)
                ) {
                    Text(text = email.sender, fontSize = (textSize * 0.8f).sp, fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Bold)
                    Text(text = email.subject, fontSize = textSize.sp, fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Bold)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
fun EmailDetailScreen(email: EmailMessage, textSize: Float, onReply: () -> Unit, onForward: () -> Unit, onDelete: () -> Unit, onAddContact: (String, String) -> Unit) {
    val isDark = isSystemInDarkTheme()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = email.subject, fontSize = (textSize * 1.2f).sp, fontWeight = FontWeight.Bold)
            
            // Clickable sender to add to contact
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                val name: String
                val emailAddr: String
                if (email.sender.contains("<")) {
                    name = email.sender.substringBefore("<").trim().removeSurrounding("\"")
                    emailAddr = email.sender.substringAfter("<").substringBefore(">").trim()
                } else {
                    name = email.sender.substringBefore("@").trim()
                    emailAddr = email.sender.trim()
                }
                onAddContact(if (name.isNotEmpty()) name else emailAddr, emailAddr)
            }) {
                Text(text = stringResource(R.string.from_label, email.sender), fontSize = (textSize * 0.9f).sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", modifier = Modifier.size(18.dp))
            }
            
            Text(text = stringResource(R.string.date_label, email.date), fontSize = (textSize * 0.8f).sp)
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
            if (email.htmlContent != null) {
                HtmlView(html = email.htmlContent, isDark = isDark, textSize = textSize)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.secure_text_email),
                        color = Color.Green,
                        fontSize = (textSize * 0.7f).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = email.content,
                        fontSize = textSize.sp,
                        color = Color.White,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onReply) { Text(stringResource(R.string.reply)) }
            Button(onClick = onForward) { Text(stringResource(R.string.forward)) }
            Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { 
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.onError) 
            }
        }
    }
}

@Composable
fun HtmlView(html: String, isDark: Boolean, textSize: Float) {
    val context = LocalContext.current
    // Force black background for HTML emails as requested by user
    val backgroundColor = "#000000"
    val textColor = "#FFFFFF"
    val styledHtml = """
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
        * { 
            background-color: transparent !important; 
            background: transparent !important;
            color: inherit !important;
            font-size: inherit !important;
        }
        html, body { 
            background-color: $backgroundColor !important; 
            color: $textColor !important; 
            font-family: sans-serif !important;
            font-size: ${textSize}px !important;
            line-height: 1.5 !important;
            margin: 0;
            padding: 12px;
        }
        /* Ensure images are visible and responsive */
        img { 
            max-width: 100% !important; 
            height: auto !important; 
            display: block !important;
            margin: 10px 0 !important;
        }
        /* Keep links visible */
        a {
            color: #58a6ff !important;
            text-decoration: underline !important;
        }
        </style>
        </head>
        <body>$html</body>
        </html>
    """.trimIndent()

    var webViewError by remember { mutableStateOf(false) }

    if (webViewError) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.secure_text_email),
                color = Color.Green,
                fontSize = (textSize * 0.7f).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = html,
                color = Color.White,
                fontSize = textSize.sp
            )
        }
    } else {
        AndroidView(
            factory = { context ->
                try {
                    WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                if (url != null) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    context.startActivity(intent)
                                    return true
                                }
                                return false
                            }
                        }
                        settings.javaScriptEnabled = false
                        settings.loadWithOverviewMode = false
                        settings.useWideViewPort = false
                        settings.textZoom = 100
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                } catch (e: Exception) {
                    webViewError = true
                    View(context)
                }
            },
            update = { webView ->
                if (webView is WebView) {
                    webView.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
    }
}

@Composable
fun ComposeEmailScreen(viewModel: EmailViewModel, mode: ComposeMode, originalEmail: EmailMessage?, textSize: Float, onFinished: () -> Unit) {
    val context = LocalContext.current
    val accountEmail by viewModel.accountEmail.collectAsState()
    val signature by viewModel.signature.collectAsState()
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())

    val replyPrefix = stringResource(R.string.reply_subject_prefix, originalEmail?.subject ?: "")
    val forwardPrefix = stringResource(R.string.forward_subject_prefix, originalEmail?.subject ?: "")

    var to by remember { mutableStateOf(
        when(mode) {
            ComposeMode.New, ComposeMode.Forward -> ""
            ComposeMode.Reply -> originalEmail?.sender ?: ""
        }
    ) }
    var subject by remember { mutableStateOf(
        when(mode) {
            ComposeMode.Reply -> replyPrefix
            ComposeMode.Forward -> forwardPrefix
            ComposeMode.New -> ""
        }
    ) }
    var content by remember { mutableStateOf("") }
    var showContactPicker by remember { mutableStateOf(false) }

    if (showContactPicker) {
        AlertDialog(
            onDismissRequest = { showContactPicker = false },
            title = { Text(stringResource(R.string.select_contact)) },
            text = {
                LazyColumn {
                    items(contacts) { contact ->
                        Text(
                            text = "${contact.name} <${contact.email}>",
                            modifier = Modifier.fillMaxWidth().clickable {
                                to = contact.email
                                showContactPicker = false
                            }.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactPicker = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(text = stringResource(R.string.from_label, accountEmail), fontSize = (textSize * 0.8f).sp, modifier = Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = to, 
                onValueChange = { to = it }, 
                label = { Text(stringResource(R.string.to_label)) }, 
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(fontSize = textSize.sp)
            )
            IconButton(onClick = { showContactPicker = true }) { Icon(Icons.Default.Person, contentDescription = null) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = subject, 
            onValueChange = { subject = it }, 
            label = { Text(stringResource(R.string.subject_label)) }, 
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = textSize.sp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = content, 
            onValueChange = { content = it }, 
            label = { Text(stringResource(R.string.your_message_label)) }, 
            modifier = Modifier.fillMaxWidth(), 
            minLines = 10,
            textStyle = LocalTextStyle.current.copy(fontSize = textSize.sp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.signature_preview, signature), fontSize = (textSize * 0.7f).sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onFinished) { Text(stringResource(R.string.cancel)) }
            Button(onClick = {
                val fullBody = if (mode != ComposeMode.New && originalEmail != null) {
                    "$content\n\n--\n$signature\n\n--- Original Message ---\n${originalEmail.content}"
                } else {
                    "$content\n\n--\n$signature"
                }
                viewModel.sendEmail(to, subject, fullBody) { success ->
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.email_sent), Toast.LENGTH_SHORT).show()
                        onFinished()
                    } else {
                        Toast.makeText(context, context.getString(R.string.failed_to_send_email), Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text(stringResource(R.string.send)) }
        }
    }
}

@Composable
fun AddressBookScreen(viewModel: EmailViewModel, textSize: Float) {
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var editingContact by remember { mutableStateOf<Contact?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = if (editingContact == null) stringResource(R.string.add_contact) else stringResource(R.string.edit_contact),
            fontWeight = FontWeight.Bold,
            fontSize = textSize.sp
        )
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name_label)) }, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontSize = textSize.sp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email_label)) }, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontSize = textSize.sp))
        
        Row(modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
            if (editingContact != null) {
                TextButton(onClick = {
                    editingContact = null
                    name = ""
                    email = ""
                }) {
                    Text(stringResource(R.string.cancel), fontSize = textSize.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Button(onClick = {
                if (name.isNotEmpty() && email.isNotEmpty()) {
                    if (editingContact == null) {
                        viewModel.addContact(name, email)
                    } else {
                        viewModel.updateContact(editingContact!!.copy(name = name, email = email))
                        editingContact = null
                    }
                    name = ""
                    email = ""
                }
            }) { Text(if (editingContact == null) stringResource(R.string.add) else stringResource(R.string.save), fontSize = textSize.sp) }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(contacts) { contact ->
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(contact.name, fontWeight = FontWeight.Bold, fontSize = textSize.sp)
                        Text(contact.email, fontSize = (textSize * 0.8f).sp)
                    }
                    Row {
                        IconButton(onClick = {
                            editingContact = contact
                            name = contact.name
                            email = contact.email
                        }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { viewModel.deleteContact(contact) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: EmailViewModel, onBack: () -> Unit) {
    val emailVal by viewModel.accountEmail.collectAsState()
    val passwordVal by viewModel.accountPassword.collectAsState()
    val imapHostVal by viewModel.imapHost.collectAsState()
    val smtpHostVal by viewModel.smtpHost.collectAsState()
    val smtpPortVal by viewModel.smtpPort.collectAsState()
    val senderNameVal by viewModel.senderName.collectAsState()
    val syncIntervalVal by viewModel.syncInterval.collectAsState()
    val textSizeVal by viewModel.textSize.collectAsState()
    val signatureVal by viewModel.signature.collectAsState()

    var email by remember { mutableStateOf(emailVal) }
    var password by remember { mutableStateOf(passwordVal) }
    var imapHost by remember { mutableStateOf(imapHostVal) }
    var smtpHost by remember { mutableStateOf(smtpHostVal) }
    var smtpPort by remember { mutableStateOf(smtpPortVal) }
    var senderName by remember { mutableStateOf(senderNameVal) }
    var syncInterval by remember { mutableIntStateOf(syncIntervalVal) }
    var textSize by remember { mutableFloatStateOf(textSizeVal) }
    var signature by remember { mutableStateOf(signatureVal) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.add_imap_account_title), fontWeight = FontWeight.Bold)
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(stringResource(R.string.password_label)) },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                }
            }
        )
        OutlinedTextField(value = imapHost, onValueChange = { imapHost = it }, label = { Text(stringResource(R.string.imap_server_label)) }, modifier = Modifier.fillMaxWidth())
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.add_smtp_account_title), fontWeight = FontWeight.Bold)
        OutlinedTextField(value = smtpHost, onValueChange = { smtpHost = it }, label = { Text(stringResource(R.string.smtp_server_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = smtpPort, onValueChange = { smtpPort = it }, label = { Text(stringResource(R.string.smtp_port_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = senderName, onValueChange = { senderName = it }, label = { Text(stringResource(R.string.sender_name_label)) }, modifier = Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.sync_interval_label), fontWeight = FontWeight.Bold)
        Row {
            listOf(1, 3, 5, 15).forEach { min ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { syncInterval = min }.padding(8.dp)) {
                    RadioButton(selected = syncInterval == min, onClick = { syncInterval = min })
                    Text("$min min")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.text_size_label, textSize.toInt()), fontWeight = FontWeight.Bold)
        Slider(value = textSize, onValueChange = { textSize = it }, valueRange = 12f..24f, steps = 5)

        Text(stringResource(R.string.email_signature_title), fontWeight = FontWeight.Bold)
        OutlinedTextField(value = signature, onValueChange = { signature = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.signature_label)) })

        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text(stringResource(R.string.back)) }
            Button(onClick = {
                viewModel.saveSettings(email, password, imapHost, smtpHost, smtpPort, senderName, syncInterval, textSize, signature)
                onBack()
            }) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        "1.0"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.app_title), fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(stringResource(R.string.version_label, versionName ?: "1.0"), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.app_description),
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.copyright), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
    }
}
