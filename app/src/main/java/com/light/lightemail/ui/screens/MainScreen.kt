package com.light.lightemail.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen {
    Home, Settings, About, ViewEmail, Compose, AddressBook
}

enum class ComposeMode {
    New, Reply, Forward
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: EmailViewModel = viewModel(), initialEmailUid: Long? = null, onEmailOpened: () -> Unit = {}) {
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
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 600
    val isSquareScreen = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp > 0.8f
    val isVerySmallScreen = configuration.screenHeightDp < 480

    // Handle deep link from notification
    LaunchedEffect(initialEmailUid, emails) {
        if (initialEmailUid != null && emails.isNotEmpty()) {
            val email = emails.find { it.uid == initialEmailUid }
            if (email != null) {
                selectedEmail = email
                viewModel.markAsRead(email)
                currentScreen = Screen.ViewEmail
                onEmailOpened()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(stringResource(R.string.folders), modifier = Modifier.padding(if (isVerySmallScreen) 8.dp else 16.dp), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 14.sp else 16.sp)
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(folders) { folder ->
                        NavigationDrawerItem(
                            label = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(folder.name, modifier = Modifier.weight(1f), fontSize = if (isVerySmallScreen) 13.sp else 14.sp)
                                    if (folder.unreadCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary, modifier = if (isVerySmallScreen) Modifier.size(16.dp) else Modifier) {
                                            Text(folder.unreadCount.toString(), color = Color.White, fontSize = if (isVerySmallScreen) 10.sp else 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(if (isVerySmallScreen) 4.dp else 8.dp))
                                    }
                                    Text("(${folder.messageCount})", fontSize = if (isVerySmallScreen) 10.sp else 12.sp, color = Color.Gray)
                                    if (folder.name.lowercase().contains("trash")) {
                                        Spacer(modifier = Modifier.width(if (isVerySmallScreen) 4.dp else 8.dp))
                                        IconButton(onClick = { viewModel.emptyTrash() }, modifier = Modifier.size(if (isVerySmallScreen) 20.dp else 24.dp)) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = "Empty Trash", modifier = Modifier.size(if (isVerySmallScreen) 14.dp else 16.dp))
                                        }
                                    }
                                }
                            },
                            selected = folder.name == currentFolder,
                            onClick = {
                                viewModel.selectFolder(folder.name)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(if (isVerySmallScreen) PaddingValues(horizontal = 8.dp, vertical = 2.dp) else NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
                HorizontalDivider()
            }
        },
        gesturesEnabled = currentScreen == Screen.Home
    ) {
        Scaffold(
            topBar = {
                if (currentScreen != Screen.Compose) {
                    val titleStyle = if (isVerySmallScreen)
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    else if (isShortScreen) 
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    else 
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.app_title), style = titleStyle) },
                        navigationIcon = {
                            val isTopLevelScreen = currentScreen in listOf(Screen.Home, Screen.AddressBook, Screen.Settings, Screen.About)
                            if (isTopLevelScreen) {
                                if (currentScreen == Screen.Home) {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu", modifier = if (isVerySmallScreen) Modifier.size(20.dp) else Modifier)
                                    }
                                }
                            } else {
                                IconButton(onClick = { currentScreen = Screen.Home }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), modifier = if (isVerySmallScreen) Modifier.size(20.dp) else Modifier)
                                }
                            }
                        },
                        actions = {
                            if (currentScreen == Screen.Home && accountEmail.isNotEmpty()) {
                                IconButton(onClick = { viewModel.refreshEmails() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), modifier = if (isVerySmallScreen) Modifier.size(20.dp) else Modifier)
                                }
                            }
                        },
                        windowInsets = TopAppBarDefaults.windowInsets,
                        modifier = if (isVerySmallScreen) Modifier.height(56.dp) else Modifier
                    )
                }
            },
            bottomBar = {
                if (currentScreen in listOf(Screen.Home, Screen.AddressBook, Screen.Settings, Screen.About)) {
                    NavigationBar(
                        containerColor = Color.Black,
                        contentColor = Color.White,
                        windowInsets = NavigationBarDefaults.windowInsets,
                        modifier = if (isVerySmallScreen) Modifier.height(48.dp) else if (isShortScreen) Modifier.height(64.dp) else Modifier
                    ) {
                        val itemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.White.copy(alpha = 0.4f),
                            unselectedTextColor = Color.White.copy(alpha = 0.4f),
                            indicatorColor = Color.Transparent
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.Home,
                            onClick = { currentScreen = Screen.Home },
                            icon = { Icon(painterResource(R.drawable.ic_envelope), contentDescription = null, modifier = if (isVerySmallScreen) Modifier.size(20.dp) else Modifier) },
                            label = { if (!isShortScreen) Text(stringResource(R.string.home)) },
                            colors = itemColors,
                            alwaysShowLabel = !isShortScreen
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.AddressBook,
                            onClick = { currentScreen = Screen.AddressBook },
                            icon = { Icon(Icons.Outlined.Person, contentDescription = null, modifier = if (isVerySmallScreen) Modifier.size(20.dp) else Modifier) },
                            label = { if (!isShortScreen) Text(stringResource(R.string.address_book)) },
                            colors = itemColors,
                            alwaysShowLabel = !isShortScreen
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.Settings,
                            onClick = { currentScreen = Screen.Settings },
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = null, modifier = if (isVerySmallScreen) Modifier.size(20.dp) else Modifier) },
                            label = { if (!isShortScreen) Text(stringResource(R.string.settings)) },
                            colors = itemColors,
                            alwaysShowLabel = !isShortScreen
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.About,
                            onClick = { currentScreen = Screen.About },
                            icon = { Icon(Icons.Outlined.Info, contentDescription = null, modifier = if (isVerySmallScreen) Modifier.size(20.dp) else Modifier) },
                            label = { if (!isShortScreen) Text(stringResource(R.string.about)) },
                            colors = itemColors,
                            alwaysShowLabel = !isShortScreen
                        )
                    }
                }
            },
            floatingActionButton = {
                if (currentScreen == Screen.Home) {
                    FloatingActionButton(
                        onClick = {
                            selectedEmail = null
                            composeMode = ComposeMode.New
                            currentScreen = Screen.Compose
                        },
                        containerColor = Color.Black,
                        contentColor = Color.White,
                        modifier = if (isVerySmallScreen) Modifier.size(48.dp) else Modifier
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.compose), modifier = if (isVerySmallScreen) Modifier.size(20.dp) else Modifier)
                    }
                }
            },
            contentWindowInsets = WindowInsets.systemBars
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when (currentScreen) {
                    Screen.Home -> EmailListScreen(emails, isLoading, textSize) { emailMsg ->
                        selectedEmail = emailMsg
                        currentScreen = Screen.ViewEmail
                        viewModel.markAsRead(emailMsg)
                    }
                    Screen.About -> AboutScreen()
                    Screen.Settings -> SettingsScreen(viewModel = viewModel)
                    Screen.AddressBook -> AddressBookScreen(viewModel = viewModel, textSize = textSize)
                    Screen.ViewEmail -> {
                        // Use derivedStateOf to avoid unnecessary recompositions while viewing
                        val emailToDisplay by remember(selectedEmail, emails) {
                            derivedStateOf {
                                emails.find { it.uid == selectedEmail?.uid } ?: selectedEmail
                            }
                        }

                        emailToDisplay?.let { email ->
                            EmailDetailScreen(
                                email = email,
                                textSize = textSize,
                                onReply = {
                                    selectedEmail = email
                                    composeMode = ComposeMode.Reply
                                    currentScreen = Screen.Compose
                                },
                                onForward = {
                                    selectedEmail = email
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
                    }
                    Screen.Compose -> {
                        val activeEmail = remember(selectedEmail, emails) {
                            emails.find { it.uid == selectedEmail?.uid } ?: selectedEmail
                        }
                        ComposeEmailScreen(
                            viewModel = viewModel,
                            mode = composeMode,
                            originalEmail = activeEmail,
                            textSize = textSize,
                            onFinished = { currentScreen = Screen.Home }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmailListScreen(emails: List<EmailMessage>, isLoading: Boolean, textSize: Float, onEmailClick: (EmailMessage) -> Unit) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isVerySmallScreen = configuration.screenHeightDp < 480
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (emails.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_emails), fontSize = textSize.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(emails, key = { it.uid }) { email ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEmailClick(email) }
                            .padding(if (isVerySmallScreen) 8.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = email.sender.uppercase(), 
                                fontSize = (textSize * 0.7f).sp, 
                                fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = email.subject, 
                                fontSize = textSize.sp, 
                                fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Bold,
                                color = Color.White,
                                maxLines = if (isVerySmallScreen) 1 else 2
                            )
                        }
                        if (email.isRead) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(if (isVerySmallScreen) 12.dp else 16.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (emails.isEmpty()) MaterialTheme.colorScheme.background else Color.Transparent),
                contentAlignment = if (emails.isEmpty()) Alignment.Center else Alignment.TopCenter
            ) {
                if (emails.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                }
            }
        }
    }
}

@Composable
fun EmailDetailScreen(email: EmailMessage, textSize: Float, onReply: () -> Unit, onForward: () -> Unit, onDelete: () -> Unit, onAddContact: (String, String) -> Unit) {
    val isDark = isSystemInDarkTheme()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 600
    val isVerySmallScreen = configuration.screenHeightDp < 480

    Column(modifier = Modifier.fillMaxSize().padding(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = email.subject, fontSize = (textSize * (if (isVerySmallScreen) 1.0f else if (isShortScreen) 1.1f else 1.2f)).sp, fontWeight = FontWeight.Bold)
            
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
            }.padding(vertical = if (isVerySmallScreen) 1.dp else if (isShortScreen) 2.dp else 4.dp)) {
                Text(text = stringResource(R.string.from_label, email.sender), fontSize = (textSize * 0.9f).sp, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", modifier = Modifier.size(if (isVerySmallScreen) 14.dp else 18.dp))
            }
            
            Text(text = stringResource(R.string.date_label, email.date), fontSize = (textSize * 0.8f).sp)
            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))
            
            if (email.htmlContent != null) {
                HtmlView(html = email.htmlContent, isDark = isDark, textSize = textSize)
            } else if (email.content.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(if (isVerySmallScreen) 4.dp else 8.dp)
                ) {
                    if (!isVerySmallScreen) {
                        Text(
                            text = stringResource(R.string.secure_text_email),
                            color = Color.Green,
                            fontSize = (textSize * 0.7f).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Text(
                        text = email.content,
                        fontSize = textSize.sp,
                        color = Color.White,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (isVerySmallScreen) 4.dp else 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isVerySmallScreen) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onForward) {
                    Icon(Icons.Default.Forward, contentDescription = stringResource(R.string.forward))
                }
                IconButton(onClick = onReply) {
                    Icon(Icons.Default.Reply, contentDescription = stringResource(R.string.reply))
                }
            } else {
                Text(
                    text = stringResource(R.string.delete).uppercase(),
                    modifier = Modifier.padding(8.dp).clickable { onDelete() },
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = (textSize * 0.8f).sp
                )
                
                Text(
                    text = stringResource(R.string.forward).uppercase(),
                    modifier = Modifier.padding(8.dp).clickable { onForward() },
                    fontWeight = FontWeight.Bold,
                    fontSize = (textSize * 0.8f).sp
                )

                Text(
                    text = stringResource(R.string.reply).uppercase(),
                    modifier = Modifier.padding(8.dp).clickable { onReply() },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (textSize * 0.8f).sp
                )
            }
        }
    }
}

@Composable
fun HtmlView(html: String, isDark: Boolean, textSize: Float) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isVerySmallScreen = configuration.screenHeightDp < 480
    
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
            padding: ${if (isVerySmallScreen) "4px" else "12px"};
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
        val lastLoadedHtml = remember { mutableStateOf("") }
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
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
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
                if (webView is WebView && lastLoadedHtml.value != styledHtml) {
                    webView.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
                    lastLoadedHtml.value = styledHtml
                }
            },
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeEmailScreen(viewModel: EmailViewModel, mode: ComposeMode, originalEmail: EmailMessage?, textSize: Float, onFinished: () -> Unit) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 600
    val isVerySmallScreen = configuration.screenHeightDp < 480
    
    val accountEmail by viewModel.accountEmail.collectAsState()
    val signature by viewModel.signature.collectAsState()
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())

    val replyPrefix = stringResource(R.string.reply_subject_prefix, originalEmail?.subject ?: "")
    val forwardPrefix = stringResource(R.string.forward_subject_prefix, originalEmail?.subject ?: "")
    val attribution = originalEmail?.let { stringResource(R.string.reply_attribution, it.date, it.sender) } ?: ""

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
    var isSending by remember { mutableStateOf(false) }

    val toFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        toFocusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        // Custom Light Phone Style Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (isVerySmallScreen) 8.dp else if (isShortScreen) 12.dp else 16.dp, 
                    start = 16.dp, 
                    end = 16.dp, 
                    bottom = if (isVerySmallScreen) 4.dp else if (isShortScreen) 6.dp else 8.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.cancel).uppercase(),
                modifier = Modifier.clickable { onFinished() },
                fontWeight = FontWeight.Bold,
                fontSize = (textSize * 0.8f).sp,
                letterSpacing = 1.sp
            )
            
            if (!isVerySmallScreen) {
                Text(
                    text = stringResource(when(mode) {
                        ComposeMode.Reply -> R.string.reply
                        ComposeMode.Forward -> R.string.forward
                        ComposeMode.New -> R.string.new_email
                    }).uppercase(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (if (isShortScreen) textSize * 0.9f else textSize).sp,
                    letterSpacing = if (isShortScreen) 1.sp else 2.sp
                )
            }

            Text(
                text = stringResource(R.string.send).uppercase(),
                modifier = Modifier
                    .alpha(if (isSending) 0.5f else 1f)
                    .clickable(enabled = !isSending) {
                        if (isSending) return@clickable
                        if (to.isEmpty()) {
                            Toast.makeText(context, "Please specify a recipient", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        val fullBody = if (mode != ComposeMode.New && originalEmail != null) {
                            val quote = originalEmail.content.lines().joinToString("\n") { "> $it" }
                            "$content\n\n--\n$signature\n$attribution\n$quote"
                        } else {
                            "$content\n\n--\n$signature"
                        }
                        isSending = true
                        viewModel.sendEmail(to, subject, fullBody) { success ->
                            if (success) {
                                Toast.makeText(context, context.getString(R.string.email_sent), Toast.LENGTH_SHORT).show()
                                onFinished()
                            } else {
                                isSending = false
                                Toast.makeText(context, context.getString(R.string.failed_to_send_email), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                fontWeight = FontWeight.Bold,
                fontSize = (textSize * 0.8f).sp,
                letterSpacing = 1.sp
            )
        }

        HorizontalDivider(thickness = if (isShortScreen) 1.dp else 2.dp, color = MaterialTheme.colorScheme.onBackground)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
            
            LightTextField(
                value = to,
                onValueChange = { to = it },
                label = stringResource(R.string.to_label),
                textSize = textSize,
                singleLine = true,
                focusRequester = toFocusRequester,
                trailingIcon = {
                    IconButton(onClick = { showContactPicker = true }, modifier = Modifier.size(if (isShortScreen) 32.dp else 48.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(if (isShortScreen) 20.dp else 24.dp))
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
            
            LightTextField(
                value = subject,
                onValueChange = { subject = it },
                label = stringResource(R.string.subject_label),
                textSize = textSize,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            
            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
            
            LightTextField(
                value = content,
                onValueChange = { content = it },
                label = stringResource(R.string.your_message_label),
                textSize = textSize,
                minLines = if (isVerySmallScreen) 3 else if (isShortScreen) 4 else 8,
                focusRequester = contentFocusRequester,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            
            if (mode != ComposeMode.New && originalEmail != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.reply_attribution, originalEmail.date, originalEmail.sender).trim(),
                    fontSize = (textSize * 0.8f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f))
                        .padding(12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (originalEmail.content.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.Gray
                        )
                    } else {
                        Text(
                            text = originalEmail.content,
                            fontSize = (textSize * 0.8f).sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.signature_preview, signature),
                fontSize = (textSize * 0.7f).sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }

    if (showContactPicker) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(enabled = false) {}
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.select_contact).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = textSize.sp,
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = { showContactPicker = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color.White, thickness = 1.dp)
                }
                
                if (contacts.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "NO CONTACTS",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = (textSize * 0.8f).sp
                            )
                        }
                    }
                } else {
                    items(contacts) { contact ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    to = contact.email
                                    showContactPicker = false
                                }
                                .padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = contact.name.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = (textSize * 0.9f).sp
                            )
                            Text(
                                text = contact.email,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = (textSize * 0.7f).sp
                            )
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}

@Composable
fun AddressBookScreen(viewModel: EmailViewModel, textSize: Float) {
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 600
    val isVerySmallScreen = configuration.screenHeightDp < 480
    
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var editingContact by remember { mutableStateOf<Contact?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp)) {
        item {
            Text(
                text = (if (editingContact == null) stringResource(R.string.add_contact) else stringResource(R.string.edit_contact)).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = (textSize * 0.9f).sp,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))
            LightTextField(
                value = name, 
                onValueChange = { name = it }, 
                label = stringResource(R.string.name_label), 
                textSize = textSize,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))
            LightTextField(
                value = email, 
                onValueChange = { email = it }, 
                label = stringResource(R.string.email_label), 
                textSize = textSize
            )
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp), horizontalArrangement = Arrangement.End) {
                if (editingContact != null) {
                    Text(
                        stringResource(R.string.cancel).uppercase(), 
                        modifier = Modifier.clickable {
                            editingContact = null
                            name = ""
                            email = ""
                        }.padding(8.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = (textSize * 0.8f).sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    (if (editingContact == null) stringResource(R.string.add) else stringResource(R.string.save)).uppercase(),
                    modifier = Modifier.clickable {
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
                    }.padding(8.dp),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (textSize * 0.8f).sp
                )
            }

            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
            HorizontalDivider(thickness = 1.dp)
            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
        }

        items(contacts) { contact ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.name.uppercase(), fontWeight = FontWeight.Bold, fontSize = (textSize * 0.9f).sp)
                    Text(contact.email, fontSize = (textSize * 0.7f).sp, color = Color.Gray)
                }
                Row {
                    IconButton(onClick = {
                        editingContact = contact
                        name = contact.name
                        email = contact.email
                    }, modifier = Modifier.size(if (isShortScreen) 32.dp else 48.dp)) { Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { viewModel.deleteContact(contact) }, modifier = Modifier.size(if (isShortScreen) 32.dp else 48.dp)) { Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp)) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        }
    }
}

@Composable
fun SettingsScreen(viewModel: EmailViewModel) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 600
    val isVerySmallScreen = configuration.screenHeightDp < 480

    val emailVal by viewModel.accountEmail.collectAsState()
    val passwordVal by viewModel.accountPassword.collectAsState()
    val imapHostVal by viewModel.imapHost.collectAsState()
    val smtpHostVal by viewModel.smtpHost.collectAsState()
    val smtpPortVal by viewModel.smtpPort.collectAsState()
    val senderNameVal by viewModel.senderName.collectAsState()
    val textSizeVal by viewModel.textSize.collectAsState()
    val signatureVal by viewModel.signature.collectAsState()

    var email by remember { mutableStateOf(emailVal) }
    var password by remember { mutableStateOf(passwordVal) }
    var imapHost by remember { mutableStateOf(imapHostVal) }
    var smtpHost by remember { mutableStateOf(smtpHostVal) }
    var smtpPort by remember { mutableStateOf(smtpPortVal) }
    var senderName by remember { mutableStateOf(senderNameVal) }
    var textSize by remember { mutableFloatStateOf(textSizeVal) }
    var signature by remember { mutableStateOf(signatureVal) }
    var passwordVisible by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openOutputStream(it)?.let { outputStream ->
                    viewModel.exportBackup(outputStream) { success ->
                        Toast.makeText(context, if (success) R.string.backup_success else R.string.backup_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.let { inputStream ->
                    viewModel.importBackup(inputStream) { success ->
                        Toast.makeText(context, if (success) R.string.restore_success else R.string.restore_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    // Auto-save settings
    LaunchedEffect(email, password, imapHost, smtpHost, smtpPort, senderName, textSize, signature) {
        if (email != emailVal || password != passwordVal || imapHost != imapHostVal ||
            smtpHost != smtpHostVal || smtpPort != smtpPortVal || senderName != senderNameVal ||
            textSize != textSizeVal || signature != signatureVal) {
            delay(1000)
            viewModel.saveSettings(email, password, imapHost, smtpHost, smtpPort, senderName, textSize, signature)
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.settings_title).uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = if (isVerySmallScreen) 14.sp else if (isShortScreen) 16.sp else 20.sp, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))

        Text(
            text = stringResource(R.string.outlook_oauth_warning),
            color = MaterialTheme.colorScheme.error,
            fontSize = if (isVerySmallScreen) 9.sp else 11.sp,
            modifier = Modifier.padding(bottom = if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp)
        )

        Text(stringResource(R.string.add_imap_account_title).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else 8.dp))
        LightTextField(value = email, onValueChange = { email = it }, label = stringResource(R.string.email_label), textSize = if (isVerySmallScreen) 14f else 16f)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))
        LightTextField(
            value = password, onValueChange = { password = it },
            label = stringResource(R.string.password_label),
            textSize = if (isVerySmallScreen) 14f else 16f,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = if (isVerySmallScreen) Modifier.size(24.dp) else Modifier) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, modifier = Modifier.size(if (isVerySmallScreen) 16.dp else 20.dp))
                }
            }
        )
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))
        LightTextField(value = imapHost, onValueChange = { imapHost = it }, label = stringResource(R.string.imap_server_label), textSize = if (isVerySmallScreen) 14f else 16f)
        
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
        Text(stringResource(R.string.add_smtp_account_title).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else 8.dp))
        LightTextField(value = smtpHost, onValueChange = { smtpHost = it }, label = stringResource(R.string.smtp_server_label), textSize = if (isVerySmallScreen) 14f else 16f)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))
        LightTextField(value = smtpPort, onValueChange = { smtpPort = it }, label = stringResource(R.string.smtp_port_label), textSize = if (isVerySmallScreen) 14f else 16f)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))
        LightTextField(
            value = senderName, 
            onValueChange = { senderName = it }, 
            label = stringResource(R.string.sender_name_label),
            textSize = if (isVerySmallScreen) 14f else 16f,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))

        Text(stringResource(R.string.text_size_label, textSize.toInt()).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else 14.sp, color = Color.Gray)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = if (isVerySmallScreen) 4.dp else if (isShortScreen) 6.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(12f, 15f, 18f, 21f, 24f).forEach { size ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${size.toInt()}", fontSize = if (isVerySmallScreen) 10.sp else 12.sp, fontWeight = if (textSize == size) FontWeight.Bold else FontWeight.Normal)
                    Spacer(modifier = Modifier.height(if (isVerySmallScreen) 2.dp else 4.dp))
                    LightRadioButton(selected = textSize == size, onClick = { textSize = size }, modifier = if (isVerySmallScreen) Modifier.size(12.dp) else Modifier)
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))
        LightTextField(
            value = signature, 
            onValueChange = { signature = it }, 
            label = stringResource(R.string.signature_label),
            textSize = if (isVerySmallScreen) 14f else 16f,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 8.dp else if (isShortScreen) 16.dp else 32.dp))

        // Background Settings Section
        Text(stringResource(R.string.background_settings_title).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))

        // Battery Optimization
        Column(modifier = Modifier.fillMaxWidth().clickable {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }
        }) {
            Text(stringResource(R.string.battery_optimization_label), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 14.sp else 16.sp)
            Text(stringResource(R.string.battery_optimization_desc), fontSize = if (isVerySmallScreen) 10.sp else 12.sp, color = Color.Gray)
            Text(stringResource(R.string.configure).uppercase(), fontSize = if (isVerySmallScreen) 10.sp else 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))

        // App Hibernation / Pause if unused
        Column(modifier = Modifier.fillMaxWidth().clickable {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }) {
            Text(stringResource(R.string.app_hibernation_label), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 14.sp else 16.sp)
            Text(stringResource(R.string.app_hibernation_desc), fontSize = if (isVerySmallScreen) 10.sp else 12.sp, color = Color.Gray)
            Text(stringResource(R.string.configure).uppercase(), fontSize = if (isVerySmallScreen) 10.sp else 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 8.dp else if (isShortScreen) 16.dp else 32.dp))

        // Backup & Restore
        Text(stringResource(R.string.backup_restore_title).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 16.dp))

        Column(modifier = Modifier.fillMaxWidth().clickable {
            backupLauncher.launch("lightemail_backup.json")
        }) {
            Text(stringResource(R.string.backup_label), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 14.sp else 16.sp)
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))

        Column(modifier = Modifier.fillMaxWidth().clickable {
            restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
        }) {
            Text(stringResource(R.string.restore_label), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 14.sp else 16.sp)
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 12.dp else if (isShortScreen) 24.dp else 48.dp))
    }
}

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 600
    val isVerySmallScreen = configuration.screenHeightDp < 480

    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        "1.0"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isVerySmallScreen) 8.dp else 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.app_title).uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = if (isVerySmallScreen) 18.sp else if (isShortScreen) 24.sp else 32.sp, letterSpacing = if (isShortScreen) 2.sp else 4.sp)
        Text(stringResource(R.string.version_label, versionName ?: "1.0").uppercase(), fontSize = 10.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 12.dp else if (isShortScreen) 24.dp else 48.dp))
        Text(
            text = stringResource(R.string.app_description),
            textAlign = TextAlign.Center,
            fontSize = if (isVerySmallScreen) 11.sp else 13.sp,
            lineHeight = if (isVerySmallScreen) 16.sp else 20.sp
        )
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 12.dp else if (isShortScreen) 24.dp else 48.dp))
        Text(stringResource(R.string.copyright).uppercase(), fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun LightRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.onBackground),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.onBackground)
            )
        }
    }
}

@Composable
fun LightSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onBackground
    val density = LocalDensity.current

    // Values from the Light OS style code provided
    val circleSize = 13.dp
    val lineWidth = 19.dp
    val lineHeight = 3.dp
    val borderSize = 3.dp

    Canvas(
        modifier = modifier
            .size(width = circleSize + lineWidth, height = circleSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        val cy = size.height / 2f
        val r = circleSize.toPx() / 2f
        val lw = lineWidth.toPx()
        val lh = lineHeight.toPx()
        val b = borderSize.toPx()
        val cs = circleSize.toPx()

        if (checked) {
            // line on the left, filled dot on the right
            drawRect(
                color = color,
                topLeft = Offset(0f, cy - lh / 2),
                size = Size(lw, lh)
            )
            drawCircle(
                color = color,
                radius = r,
                center = Offset(lw + r, cy)
            )
        } else {
            // hollow dot on the left, line on the right
            drawCircle(
                color = color,
                radius = r - b / 2,
                center = Offset(r, cy),
                style = Stroke(width = b)
            )
            drawRect(
                color = color,
                topLeft = Offset(cs, cy - lh / 2),
                size = Size(lw, lh)
            )
        }
    }
}

@Composable
fun LightTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    textSize: Float = 16f,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            fontSize = (textSize * 0.7f).sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val textFieldModifier = if (focusRequester != null) {
                Modifier.weight(1f).focusRequester(focusRequester)
            } else {
                Modifier.weight(1f)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = textFieldModifier,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = textSize.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                singleLine = singleLine,
                minLines = minLines,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation
            )
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        )
    }
}
