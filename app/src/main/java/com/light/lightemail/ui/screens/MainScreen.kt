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
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.light.lightemail.R
import com.light.lightemail.data.Contact
import com.light.lightemail.data.EmailMessage
import com.light.lightemail.data.ComposeData
import com.light.lightemail.data.Attachment
import com.light.lightemail.ui.viewmodel.EmailViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen {
    Home, Settings, About, ViewEmail, Compose, AddressBook
}

enum class ComposeMode {
    New, Reply, ReplyAll, ReplyToSender, Forward
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: EmailViewModel = viewModel(), 
    initialEmailUid: Long? = null, 
    initialComposeData: ComposeData? = null,
    onEmailOpened: () -> Unit = {},
    onComposeStarted: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var selectedEmail by remember { mutableStateOf<EmailMessage?>(null) }
    var composeMode by remember { mutableStateOf(ComposeMode.New) }
    
    var initialTo by remember { mutableStateOf("") }
    var initialSubject by remember { mutableStateOf("") }
    var initialBody by remember { mutableStateOf("") }
    var initialCc by remember { mutableStateOf("") }
    var initialBcc by remember { mutableStateOf("") }
    var initialAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    
    val pagingEmails = viewModel.pagingEmails.collectAsLazyPagingItems()
    val emails by viewModel.emails.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val accountEmail by viewModel.accountEmail.collectAsState()
    val textSize by viewModel.textSize.collectAsState()
    val headerTextSize by viewModel.headerTextSize.collectAsState()
    val signature by viewModel.signature.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val updateAvailable by viewModel.updateAvailable.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 600
    val isSquareScreen = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp > 0.8f
    val isVerySmallScreen = configuration.screenHeightDp < 480
    val isLP3 = isSquareScreen && isShortScreen && !isVerySmallScreen
    val isStandardPhone = !isLP3 && !isVerySmallScreen && configuration.screenHeightDp >= 600

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

    // Handle initial compose data from other apps
    LaunchedEffect(initialComposeData) {
        if (initialComposeData != null) {
            initialTo = initialComposeData.to
            initialSubject = initialComposeData.subject
            initialBody = initialComposeData.body
            initialCc = initialComposeData.cc
            initialBcc = initialComposeData.bcc
            initialAttachments = initialComposeData.attachments
            
            selectedEmail = null
            composeMode = ComposeMode.New
            currentScreen = Screen.Compose
            onComposeStarted()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isVerySmallScreen) 4.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.folders),
                        modifier = Modifier.padding(if (isVerySmallScreen) 8.dp else 12.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isVerySmallScreen) 18.sp else 20.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { drawerState.close() } }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close Drawer",
                            modifier = Modifier.size(if (isVerySmallScreen) 24.dp else 28.dp)
                        )
                    }
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(folders) { folder ->
                        NavigationDrawerItem(
                            label = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(folder.name, modifier = Modifier.weight(1f), fontSize = if (isVerySmallScreen) 16.sp else 18.sp)
                                    if (folder.unreadCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            modifier = if (isVerySmallScreen) Modifier.size(20.dp) else Modifier.size(24.dp)
                                        ) {
                                            Text(folder.unreadCount.toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = if (isVerySmallScreen) 12.sp else 14.sp)
                                        }
                                        Spacer(modifier = Modifier.width(if (isVerySmallScreen) 6.dp else 10.dp))
                                    }
                                    Text("(${folder.messageCount})", fontSize = if (isVerySmallScreen) 14.sp else 16.sp, color = Color.Gray)
                                    if (folder.name.lowercase().contains("trash")) {
                                        Spacer(modifier = Modifier.width(if (isVerySmallScreen) 6.dp else 10.dp))
                                        IconButton(
                                            onClick = { viewModel.emptyTrash() },
                                            modifier = Modifier.size(if (isVerySmallScreen) 28.dp else 32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteSweep,
                                                contentDescription = "Empty Trash",
                                                modifier = Modifier.size(if (isVerySmallScreen) 20.dp else 24.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            selected = folder.name == currentFolder,
                            onClick = {
                                viewModel.selectFolder(folder.name)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(
                                if (isVerySmallScreen) 
                                    PaddingValues(horizontal = 8.dp, vertical = 6.dp) 
                                else 
                                    PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            )
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
                            AnimatedContent(
                                targetState = isTopLevelScreen,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                                            fadeOut(animationSpec = tween(90))
                                }, label = "NavIcon"
                            ) { targetIsTopLevel ->
                                if (targetIsTopLevel) {
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
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        windowInsets = NavigationBarDefaults.windowInsets,
                        modifier = if (isVerySmallScreen) Modifier.height(48.dp) else if (isLP3) Modifier.height(80.dp) else if (isStandardPhone) Modifier.height(72.dp) else if (isShortScreen) Modifier.height(64.dp) else Modifier
                    ) {
                        val itemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            indicatorColor = Color.Transparent
                        )
                        val iconSize = if (isVerySmallScreen) 20.dp else if (isLP3) 32.dp else if (isStandardPhone) 26.dp else 24.dp
                        val footerTextSize = if (isLP3) 12.sp else if (isStandardPhone) (textSize * 0.7f).sp else 11.sp
                        
                        NavigationBarItem(
                            selected = currentScreen == Screen.Home,
                            onClick = { currentScreen = Screen.Home },
                            icon = { Icon(painterResource(R.drawable.ic_envelope), contentDescription = stringResource(R.string.home), modifier = Modifier.size(iconSize)) },
                            label = { if (isLP3) Text(stringResource(R.string.home), fontSize = footerTextSize) },
                            colors = itemColors,
                            alwaysShowLabel = isLP3
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.AddressBook,
                            onClick = { currentScreen = Screen.AddressBook },
                            icon = { Icon(Icons.Outlined.Person, contentDescription = stringResource(R.string.address_book), modifier = Modifier.size(iconSize)) },
                            label = { if (isLP3) Text(stringResource(R.string.address_book), fontSize = footerTextSize) },
                            colors = itemColors,
                            alwaysShowLabel = isLP3
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.Settings,
                            onClick = { currentScreen = Screen.Settings },
                            icon = { 
                                BadgedBox(
                                    badge = {
                                        if (updateAvailable != null) {
                                            Badge(containerColor = MaterialTheme.colorScheme.primary, modifier = if (isLP3) Modifier.offset(x = 4.dp, y = (-4).dp) else Modifier)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings), modifier = Modifier.size(iconSize))
                                }
                            },
                            label = { if (isLP3) Text(stringResource(R.string.settings), fontSize = footerTextSize) },
                            colors = itemColors,
                            alwaysShowLabel = isLP3
                        )
                        NavigationBarItem(
                            selected = currentScreen == Screen.About,
                            onClick = { currentScreen = Screen.About },
                            icon = { 
                                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about), modifier = Modifier.size(iconSize))
                            },
                            label = { if (isLP3) Text(stringResource(R.string.about), fontSize = footerTextSize) },
                            colors = itemColors,
                            alwaysShowLabel = isLP3
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = if (isVerySmallScreen) Modifier.size(48.dp) else Modifier
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.compose), modifier = if (isVerySmallScreen) Modifier.size(20.dp) else Modifier)
                    }
                }
            },
            contentWindowInsets = WindowInsets.systemBars
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        if (targetState == Screen.ViewEmail || targetState == Screen.Compose) {
                            (slideInHorizontally { it } + fadeIn()).togetherWith(
                                slideOutHorizontally { -it / 3 } + fadeOut())
                        } else if (initialState == Screen.ViewEmail || initialState == Screen.Compose) {
                            (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(
                                slideOutHorizontally { it } + fadeOut())
                        } else {
                            fadeIn().togetherWith(fadeOut())
                        }
                    },
                    label = "ScreenTransition"
                ) { targetScreen ->
                    when (targetScreen) {
                        Screen.Home -> EmailListScreen(pagingEmails, isLoading, textSize, headerTextSize) { emailMsg ->
                            selectedEmail = emailMsg
                            currentScreen = Screen.ViewEmail
                            viewModel.markAsRead(emailMsg)
                        }
                        Screen.About -> AboutScreen(viewModel = viewModel)
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
                                key(email.uid) {
                                    EmailDetailScreen(
                                        viewModel = viewModel,
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
                                        onReplyAll = {
                                            selectedEmail = email
                                            composeMode = ComposeMode.ReplyAll
                                            currentScreen = Screen.Compose
                                        },
                                        onReplyToSender = {
                                            selectedEmail = email
                                            composeMode = ComposeMode.ReplyToSender
                                            currentScreen = Screen.Compose
                                        },
                                        onAddContact = { name, emailAddr ->
                                            viewModel.addContact(name, emailAddr)
                                            Toast.makeText(context, context.getString(R.string.contact_added), Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
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
                                onFinished = { 
                                    initialTo = ""
                                    initialSubject = ""
                                    initialBody = ""
                                    initialCc = ""
                                    initialBcc = ""
                                    initialAttachments = emptyList()
                                    currentScreen = Screen.Home 
                                },
                                initialTo = initialTo,
                                initialSubject = initialSubject,
                                initialBody = initialBody,
                                initialCc = initialCc,
                                initialBcc = initialBcc,
                                initialAttachments = initialAttachments
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmailListScreen(emails: LazyPagingItems<EmailMessage>, isLoading: Boolean, textSize: Float, headerTextSize: Float, onEmailClick: (EmailMessage) -> Unit) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isVerySmallScreen = configuration.screenHeightDp < 480
    
    val isPagingLoading = emails.loadState.refresh is LoadState.Loading

    Box(modifier = Modifier.fillMaxSize()) {
        if (emails.itemCount == 0 && !isLoading && !isPagingLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_emails), fontSize = textSize.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(emails.itemCount, key = emails.itemKey { it.uid }) { index ->
                    val email = emails[index]
                    if (email != null) {
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
                                    fontSize = headerTextSize.sp, 
                                    fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = email.subject, 
                                    fontSize = headerTextSize.sp, 
                                    fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = if (isVerySmallScreen) 1 else 2
                                )
                            }
                            if (email.isRead) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(if (isVerySmallScreen) 12.dp else 16.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                    }
                }
                
                // Show loading indicator at the bottom when loading more
                if (emails.loadState.append is LoadState.Loading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        if (isLoading || isPagingLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (emails.itemCount == 0) MaterialTheme.colorScheme.background else Color.Transparent),
                contentAlignment = if (emails.itemCount == 0) Alignment.Center else Alignment.TopCenter
            ) {
                if (emails.itemCount == 0) {
                    CircularProgressIndicator()
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                }
            }
        }
    }
}

@Composable
fun EmailDetailScreen(
    viewModel: EmailViewModel,
    email: EmailMessage, 
    textSize: Float, 
    onReply: () -> Unit, 
    onForward: () -> Unit, 
    onDelete: () -> Unit, 
    onReplyAll: () -> Unit, 
    onReplyToSender: () -> Unit, 
    onAddContact: (String, String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 600
    val isVerySmallScreen = configuration.screenHeightDp < 480
    val isSquareScreen = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp > 0.8f
    val isLP3 = isSquareScreen && isShortScreen && !isVerySmallScreen
    val isStandardPhone = !isLP3 && !isVerySmallScreen && configuration.screenHeightDp >= 600

    val attachments by viewModel.getAttachments(email).collectAsState(initial = emptyList())
    var attachmentsExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (email.htmlContent != null) {
                // HTML Layout: Compact header + WebView + Collapsible Attachments
                Column(modifier = Modifier.fillMaxSize()) {
                    EmailHeader(
                        email = email,
                        textSize = textSize,
                        isVerySmallScreen = isVerySmallScreen,
                        isShortScreen = isShortScreen,
                        onAddContact = onAddContact
                    )
                    
                    HorizontalDivider()
                    
                    Box(modifier = Modifier.weight(1f)) {
                        HtmlView(html = email.htmlContent, isDark = isDark, textSize = textSize)
                    }
                    
                    if (attachments.isNotEmpty()) {
                        AttachmentSection(
                            attachments = attachments,
                            expanded = attachmentsExpanded,
                            onToggle = { attachmentsExpanded = !attachmentsExpanded },
                            textSize = textSize,
                            viewModel = viewModel
                        )
                    }
                }
            } else {
                // Plain Text Layout: Unified scrolling for everything
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(if (isVerySmallScreen) 8.dp else 16.dp)
                ) {
                    item {
                        EmailHeader(
                            email = email,
                            textSize = textSize,
                            isVerySmallScreen = isVerySmallScreen,
                            isShortScreen = isShortScreen,
                            onAddContact = onAddContact
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    item {
                        if (email.content.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black)
                                    .padding(if (isVerySmallScreen) 4.dp else 8.dp)
                            ) {
                                if (!isVerySmallScreen) {
                                    Text(
                                        text = stringResource(R.string.secure_text_email),
                                        color = Color.Green,
                                        fontSize = textSize.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                Text(
                                    text = email.content,
                                    fontSize = textSize.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    
                    if (attachments.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            AttachmentSection(
                                attachments = attachments,
                                expanded = attachmentsExpanded,
                                onToggle = { attachmentsExpanded = !attachmentsExpanded },
                                textSize = textSize,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = if (isVerySmallScreen) 4.dp else 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconModifier = if (isVerySmallScreen) Modifier.size(24.dp) else Modifier.size(32.dp)
            val iconTint = MaterialTheme.colorScheme.onSurface
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error, modifier = iconModifier)
            }
            IconButton(onClick = onForward) {
                Icon(Icons.Default.Forward, contentDescription = stringResource(R.string.forward), tint = iconTint, modifier = iconModifier)
            }
            IconButton(onClick = onReplyToSender) {
                Icon(Icons.Default.Person, contentDescription = stringResource(R.string.reply_to_sender), tint = iconTint, modifier = iconModifier)
            }
            IconButton(onClick = onReplyAll) {
                Icon(Icons.Default.ReplyAll, contentDescription = stringResource(R.string.reply_all), tint = iconTint, modifier = iconModifier)
            }
            IconButton(onClick = onReply) {
                Icon(Icons.Default.Reply, contentDescription = stringResource(R.string.reply), tint = iconTint, modifier = iconModifier)
            }
        }
    }
}

@Composable
fun EmailHeader(
    email: EmailMessage,
    textSize: Float,
    isVerySmallScreen: Boolean,
    isShortScreen: Boolean,
    onAddContact: (String, String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = if (isVerySmallScreen) 8.dp else 16.dp, vertical = 8.dp)) {
        Text(
            text = email.subject, 
            fontSize = textSize.sp, 
            fontWeight = FontWeight.Bold
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier
                .clickable {
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
                }
                .padding(vertical = if (isVerySmallScreen) 2.dp else 4.dp)
        ) {
            Text(
                text = stringResource(R.string.from_label, email.sender), 
                fontSize = textSize.sp, 
                modifier = Modifier.weight(1f), 
                maxLines = 1
            )
            Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", modifier = Modifier.size(if (isVerySmallScreen) 14.dp else 18.dp))
        }
        
        Text(
            text = stringResource(R.string.date_label, email.date), 
            fontSize = textSize.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun AttachmentSection(
    attachments: List<Attachment>,
    expanded: Boolean,
    onToggle: () -> Unit,
    textSize: Float,
    viewModel: EmailViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, Color.Gray.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ATTACHMENTS (${attachments.size})",
                fontSize = textSize.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray
            )
        }
        
        if (expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                attachments.forEach { attachment ->
                    AttachmentItem(attachment, textSize, viewModel)
                }
            }
        }
    }
}

@Composable
fun AttachmentItem(attachment: Attachment, textSize: Float, viewModel: EmailViewModel) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(Color.Gray.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(attachment.fileName, fontSize = textSize.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("${attachment.size / 1024} KB", fontSize = textSize.sp, color = Color.Gray)
        }
        
        if (attachment.localPath != null) {
            IconButton(onClick = {
                val file = java.io.File(attachment.localPath)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, attachment.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No app to open this file", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = {
                    isDownloading = true
                    viewModel.downloadAttachment(attachment) { success ->
                        isDownloading = false
                        if (!success) {
                            Toast.makeText(context, "Failed to download", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun HtmlView(html: String, isDark: Boolean, textSize: Float) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isVerySmallScreen = configuration.screenHeightDp < 480
    
    // Use theme colors for HTML emails
    val backgroundColor = if (isDark) "#121212" else "#FFFFFF"
    val textColor = if (isDark) "#FFFFFF" else "#000000"
    val linkColor = if (isDark) "#8ab4f8" else "#1a73e8"
    
    // Inject dark mode styles more aggressively if dark mode is active
    val darkModeCss = if (isDark) {
        """
        html, body {
            background-color: $backgroundColor !important;
            color: $textColor !important;
        }
        /* Force text color on common elements while allowing background images to show if they are not colors */
        h1, h2, h3, h4, h5, h6, p, span, div, td, th, li, b, i, strong, em {
            color: $textColor !important;
        }
        /* Handle containers that might have white backgrounds */
        div, table, td, section, article {
            background-color: transparent !important;
        }
        """.trimIndent()
    } else ""

    // Clean the input HTML to avoid double wrapping if possible
    val bodyContent = if (html.contains("<body", ignoreCase = true)) {
        // Extract content within body if possible, or just use as is
        // For robustness, we'll use a wrapper that should override nested body styles
        html
    } else {
        "<body>$html</body>"
    }

    val styledHtml = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
        * { 
            max-width: 100% !important;
            box-sizing: border-box !important;
        }
        html, body { 
            margin: 0;
            padding: ${if (isVerySmallScreen) "4px" else "12px"};
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif !important;
            font-size: ${textSize}px !important;
            line-height: 1.6 !important;
            word-wrap: break-word;
        }
        
        h1, h2, h3, h4, h5, h6, p, span, div, td, th, li, b, i, strong, em {
            font-size: ${textSize}px !important;
        }
        
        $darkModeCss
        
        /* Ensure images are visible and responsive */
        img { 
            height: auto !important; 
            display: block !important;
            margin: 10px 0 !important;
            max-width: 100% !important;
        }
        
        /* Keep links visible */
        a {
            color: $linkColor !important;
            text-decoration: underline !important;
        }

        /* Responsive tables */
        table {
            display: block !important;
            width: 100% !important;
            overflow-x: auto !important;
        }
        </style>
        </head>
        $bodyContent
        </html>
    """.trimIndent()

    var webViewError by remember { mutableStateOf(false) }

    if (webViewError) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.secure_text_email),
                color = Color.Green,
                fontSize = textSize.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = html,
                color = MaterialTheme.colorScheme.onBackground,
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
                        settings.blockNetworkImage = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        
                        // Enable algorithmic darkening for better dark mode support (API 33+)
                        settings.isAlgorithmicDarkeningAllowed = isDark
                        
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                } catch (e: Exception) {
                    webViewError = true
                    View(context)
                }
            },
            update = { webView ->
                if (webView is WebView) {
                    webView.settings.isAlgorithmicDarkeningAllowed = isDark
                    if (lastLoadedHtml.value != styledHtml) {
                        webView.loadDataWithBaseURL("https://light-email.local/", styledHtml, "text/html", "utf-8", null)
                        lastLoadedHtml.value = styledHtml
                    }
                }
            },
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeEmailScreen(
    viewModel: EmailViewModel, 
    mode: ComposeMode, 
    originalEmail: EmailMessage?, 
    textSize: Float, 
    onFinished: () -> Unit,
    initialTo: String = "",
    initialSubject: String = "",
    initialBody: String = "",
    initialCc: String = "",
    initialBcc: String = "",
    initialAttachments: List<Uri> = emptyList()
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 600
    val isVerySmallScreen = configuration.screenHeightDp < 480
    val isSquareScreen = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp > 0.8f
    val isLP3 = isSquareScreen && isShortScreen && !isVerySmallScreen
    val isStandardPhone = !isLP3 && !isVerySmallScreen && configuration.screenHeightDp >= 600
    
    val accountEmail by viewModel.accountEmail.collectAsState()
    val signature by viewModel.signature.collectAsState()
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    val pendingAttachments by viewModel.pendingAttachments.collectAsState()

    // Add initial attachments to viewModel if present
    LaunchedEffect(initialAttachments) {
        if (initialAttachments.isNotEmpty()) {
            initialAttachments.forEach { uri ->
                viewModel.addPendingAttachment(uri)
            }
        }
    }

    val replyPrefix = stringResource(R.string.reply_subject_prefix, originalEmail?.subject ?: "")
    val forwardPrefix = stringResource(R.string.forward_subject_prefix, originalEmail?.subject ?: "")
    val attribution = originalEmail?.let { stringResource(R.string.reply_attribution, it.date, it.sender) } ?: ""

    var to by remember(initialTo, mode, originalEmail) { mutableStateOf(
        if (initialTo.isNotEmpty()) initialTo else when(mode) {
            ComposeMode.New, ComposeMode.Forward -> ""
            ComposeMode.Reply -> originalEmail?.replyTo ?: originalEmail?.sender ?: ""
            ComposeMode.ReplyToSender -> originalEmail?.sender ?: ""
            ComposeMode.ReplyAll -> {
                val recipients = mutableListOf<String>()
                recipients.add(originalEmail?.replyTo ?: originalEmail?.sender ?: "")
                originalEmail?.toRecipients?.let { recipients.addAll(it.split(", ")) }
                // Filter out current user email
                recipients.distinct()
                    .filter { it.lowercase() != accountEmail.lowercase() && !it.contains(accountEmail, ignoreCase = true) }
                    .joinToString(", ")
            }
        }
    ) }
    var cc by remember(initialCc, mode, originalEmail) { mutableStateOf(
        if (initialCc.isNotEmpty()) initialCc else if (mode == ComposeMode.ReplyAll) {
            originalEmail?.ccRecipients?.split(", ")
                ?.filter { it.lowercase() != accountEmail.lowercase() && !it.contains(accountEmail, ignoreCase = true) }
                ?.joinToString(", ") ?: ""
        } else ""
    ) }
    var bcc by remember(initialBcc) { mutableStateOf(initialBcc) }
    var subject by remember(initialSubject, mode, originalEmail) { mutableStateOf(
        if (initialSubject.isNotEmpty()) initialSubject else when(mode) {
            ComposeMode.Reply, ComposeMode.ReplyAll, ComposeMode.ReplyToSender -> replyPrefix
            ComposeMode.Forward -> forwardPrefix
            ComposeMode.New -> ""
        }
    ) }
    var content by remember(initialBody) { mutableStateOf(initialBody) }
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
            val headerIconSize = if (isVerySmallScreen) 24.dp else 28.dp
            val headerIconTint = MaterialTheme.colorScheme.onSurface
            
            IconButton(onClick = { 
                viewModel.clearPendingAttachments()
                onFinished() 
            }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = headerIconTint, modifier = Modifier.size(headerIconSize))
            }
            
            if (!isVerySmallScreen) {
                Text(
                    text = stringResource(when(mode) {
                        ComposeMode.Reply -> R.string.reply
                        ComposeMode.ReplyAll -> R.string.reply_all
                        ComposeMode.ReplyToSender -> R.string.reply_to_sender
                        ComposeMode.Forward -> R.string.forward
                        ComposeMode.New -> R.string.new_email
                    }).uppercase(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = textSize.sp,
                    letterSpacing = if (isShortScreen) 1.sp else 2.sp
                )
            }

            IconButton(
                enabled = !isSending,
                onClick = {
                    if (to.isEmpty()) {
                        Toast.makeText(context, "Please specify a recipient", Toast.LENGTH_SHORT).show()
                        return@IconButton
                    }
                    
                    val isHtml = mode != ComposeMode.New && originalEmail?.htmlContent != null
                    val fullBody = if (mode != ComposeMode.New && originalEmail != null) {
                        if (isHtml) {
                            val userMessageHtml = content.replace("\n", "<br>")
                            val signatureHtml = signature.replace("\n", "<br>")
                            val attributionHtml = attribution.trim().replace("\n", "<br>")
                            """
                                <div dir="ltr">
                                    $userMessageHtml
                                    <br><br>
                                    --<br>
                                    $signatureHtml
                                    <br><br>
                                    <div class="gmail_quote">
                                        <div dir="ltr" class="gmail_attr">$attributionHtml</div>
                                        <blockquote class="gmail_quote" style="margin:0px 0px 0px 0.8ex;border-left:1px solid rgb(204,204,204);padding-left:1ex">
                                            ${originalEmail.htmlContent}
                                        </blockquote>
                                    </div>
                                </div>
                            """.trimIndent()
                        } else {
                            val quote = originalEmail.content.lines().joinToString("\n") { "> $it" }
                            "$content\n\n--\n$signature\n$attribution\n$quote"
                        }
                    } else {
                        "$content\n\n--\n$signature"
                    }
                    
                    isSending = true
                    viewModel.sendEmail(to, subject, fullBody, isHtml, cc, pendingAttachments, bcc) { success ->
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.email_sent), Toast.LENGTH_SHORT).show()
                            viewModel.clearPendingAttachments()
                            onFinished()
                        } else {
                            isSending = false
                            Toast.makeText(context, context.getString(R.string.failed_to_send_email), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Icon(
                    Icons.Default.Send, 
                    contentDescription = stringResource(R.string.send), 
                    tint = if (isSending) headerIconTint.copy(alpha = 0.5f) else headerIconTint,
                    modifier = Modifier.size(headerIconSize)
                )
            }
        }

        HorizontalDivider(thickness = if (isShortScreen) 1.dp else 2.dp, color = MaterialTheme.colorScheme.onBackground)

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                uri?.let { viewModel.addPendingAttachment(it) }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                LightTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = stringResource(R.string.to_label),
                    textSize = textSize,
                    singleLine = true,
                    focusRequester = toFocusRequester,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        IconButton(onClick = { showContactPicker = true }, modifier = Modifier.size(if (isShortScreen) 32.dp else 48.dp)) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(if (isShortScreen) 20.dp else 24.dp))
                        }
                    }
                )
                
                IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach File")
                }
            }
            
            if (pendingAttachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    pendingAttachments.forEach { uri ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .background(Color.Gray.copy(alpha = 0.1f))
                                .padding(4.dp)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = getFileName(context, uri),
                                fontSize = textSize.sp,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                maxLines = 1
                            )
                            IconButton(onClick = { viewModel.removePendingAttachment(uri) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            
            if (cc.isNotEmpty() || mode == ComposeMode.ReplyAll) {
                Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
                LightTextField(
                    value = cc,
                    onValueChange = { cc = it },
                    label = "CC",
                    textSize = textSize,
                    singleLine = true
                )
            }

            if (bcc.isNotEmpty()) {
                Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
                LightTextField(
                    value = bcc,
                    onValueChange = { bcc = it },
                    label = "BCC",
                    textSize = textSize,
                    singleLine = true
                )
            }
            
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
                    fontSize = textSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isStandardPhone) 450.dp else 300.dp)
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f))
                        .padding(12.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (originalEmail.htmlContent != null) {
                        HtmlView(html = originalEmail.htmlContent, isDark = isSystemInDarkTheme(), textSize = textSize)
                    } else if (originalEmail.content.isNotEmpty()) {
                        Text(
                            text = originalEmail.content,
                            fontSize = textSize.sp,
                            color = Color.Gray,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.signature_preview, signature),
                fontSize = textSize.sp,
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
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = textSize.sp,
                            letterSpacing = 1.sp
                        )
                        IconButton(onClick = { showContactPicker = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), thickness = 1.dp)
                }
                
                if (contacts.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "NO CONTACTS",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = textSize.sp
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
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = textSize.sp
                            )
                            Text(
                                text = contact.email,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = textSize.sp
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
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
                fontSize = textSize.sp,
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
                        fontSize = textSize.sp
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
                    fontSize = textSize.sp
                )
            }

            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
            HorizontalDivider(thickness = 1.dp)
            Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isShortScreen) 12.dp else 24.dp))
        }

        items(contacts) { contact ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = if (isVerySmallScreen) 4.dp else if (isShortScreen) 8.dp else 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.name.uppercase(), fontWeight = FontWeight.Bold, fontSize = textSize.sp)
                    Text(contact.email, fontSize = textSize.sp, color = Color.Gray)
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
    val isSquareScreen = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp > 0.8f
    val isVerySmallScreen = configuration.screenHeightDp < 480
    val isLP3 = isSquareScreen && isShortScreen && !isVerySmallScreen
    val isStandardPhone = !isLP3 && !isVerySmallScreen && configuration.screenHeightDp >= 600

    val emailVal by viewModel.accountEmail.collectAsState()
    val passwordVal by viewModel.accountPassword.collectAsState()
    val imapHostVal by viewModel.imapHost.collectAsState()
    val smtpHostVal by viewModel.smtpHost.collectAsState()
    val smtpPortVal by viewModel.smtpPort.collectAsState()
    val senderNameVal by viewModel.senderName.collectAsState()
    val textSizeVal by viewModel.textSize.collectAsState()
    val headerTextSizeVal by viewModel.headerTextSize.collectAsState()
    val useColorModeVal by viewModel.useColorMode.collectAsState()
    val useBlueIconVal by viewModel.useBlueIcon.collectAsState()
    val autoCheckUpdatesVal by viewModel.autoCheckUpdates.collectAsState()
    val signatureVal by viewModel.signature.collectAsState()
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()
    val hasCheckedForUpdates by viewModel.hasCheckedForUpdates.collectAsState()

    var email by remember { mutableStateOf(emailVal) }
    var password by remember { mutableStateOf(passwordVal) }
    var imapHost by remember { mutableStateOf(imapHostVal) }
    var smtpHost by remember { mutableStateOf(smtpHostVal) }
    var smtpPort by remember { mutableStateOf(smtpPortVal) }
    var senderName by remember { mutableStateOf(senderNameVal) }
    var textSize by remember { mutableFloatStateOf(textSizeVal) }
    var headerTextSize by remember { mutableFloatStateOf(headerTextSizeVal) }
    var useColorMode by remember { mutableStateOf(useColorModeVal) }
    var useBlueIcon by remember { mutableStateOf(useBlueIconVal) }
    var autoCheckUpdates by remember { mutableStateOf(autoCheckUpdatesVal) }
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
    LaunchedEffect(email, password, imapHost, smtpHost, smtpPort, senderName, textSize, headerTextSize, signature, useColorMode, useBlueIcon, autoCheckUpdates) {
        if (email != emailVal || password != passwordVal || imapHost != imapHostVal ||
            smtpHost != smtpHostVal || smtpPort != smtpPortVal || senderName != senderNameVal ||
            textSize != textSizeVal || headerTextSize != headerTextSizeVal || signature != signatureVal || useColorMode != useColorModeVal ||
            useBlueIcon != useBlueIconVal || autoCheckUpdates != autoCheckUpdatesVal) {
            delay(1000)
            viewModel.saveSettings(email, password, imapHost, smtpHost, smtpPort, senderName, textSize, headerTextSize, signature, useColorMode, autoCheckUpdates, useBlueIcon)
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding().padding(if (isVerySmallScreen) 4.dp else if (isLP3) 20.dp else if (isShortScreen) 8.dp else 16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.settings_title).uppercase(), fontWeight = FontWeight.ExtraBold, fontSize = if (isVerySmallScreen) 14.sp else if (isLP3) 22.sp else if (isShortScreen) 16.sp else 20.sp, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isLP3) 28.dp else if (isShortScreen) 12.dp else 24.dp))

        Text(
            text = stringResource(R.string.outlook_oauth_warning),
            color = MaterialTheme.colorScheme.error,
            fontSize = if (isVerySmallScreen) 9.sp else if (isLP3) 13.sp else 11.sp,
            modifier = Modifier.padding(bottom = if (isVerySmallScreen) 4.dp else if (isLP3) 20.dp else if (isShortScreen) 8.dp else 16.dp)
        )

        Text(stringResource(R.string.add_imap_account_title).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isLP3) 12.dp else 8.dp))
        LightTextField(value = email, onValueChange = { email = it }, label = stringResource(R.string.email_label), textSize = if (isVerySmallScreen) 14f else if (isLP3) 18f else 16f)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isLP3) 20.dp else if (isShortScreen) 8.dp else 16.dp))
        LightTextField(
            value = password, onValueChange = { password = it },
            label = stringResource(R.string.password_label),
            textSize = if (isVerySmallScreen) 14f else if (isLP3) 18f else 16f,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = if (isVerySmallScreen) Modifier.size(24.dp) else Modifier) {
                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, modifier = Modifier.size(if (isVerySmallScreen) 16.dp else if (isLP3) 24.dp else 20.dp))
                }
            }
        )
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isLP3) 20.dp else if (isShortScreen) 8.dp else 16.dp))
        LightTextField(value = imapHost, onValueChange = { imapHost = it }, label = stringResource(R.string.imap_server_label), textSize = if (isVerySmallScreen) 14f else if (isLP3) 18f else 16f)
        
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isLP3) 32.dp else if (isShortScreen) 12.dp else 24.dp))
        Text(stringResource(R.string.add_smtp_account_title).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isLP3) 12.dp else 8.dp))
        LightTextField(value = smtpHost, onValueChange = { smtpHost = it }, label = stringResource(R.string.smtp_server_label), textSize = if (isVerySmallScreen) 14f else if (isLP3) 18f else 16f)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isLP3) 20.dp else if (isShortScreen) 8.dp else 16.dp))
        LightTextField(value = smtpPort, onValueChange = { smtpPort = it }, label = stringResource(R.string.smtp_port_label), textSize = if (isVerySmallScreen) 14f else if (isLP3) 18f else 16f)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isLP3) 20.dp else if (isShortScreen) 8.dp else 16.dp))
        LightTextField(
            value = senderName, 
            onValueChange = { senderName = it }, 
            label = stringResource(R.string.sender_name_label),
            textSize = if (isVerySmallScreen) 14f else if (isLP3) 18f else 16f,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isLP3) 32.dp else if (isShortScreen) 12.dp else 24.dp))

        Text(stringResource(R.string.theme_mode_label).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp, color = Color.Gray)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = if (isVerySmallScreen) 4.dp else if (isLP3) 16.dp else if (isShortScreen) 6.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { useColorMode = false }) {
                Text(stringResource(R.string.black_mode_label), fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp, fontWeight = if (!useColorMode) FontWeight.Bold else FontWeight.Normal)
                Spacer(modifier = Modifier.height(if (isVerySmallScreen) 2.dp else 4.dp))
                LightRadioButton(selected = !useColorMode, onClick = { useColorMode = false }, modifier = if (isVerySmallScreen) Modifier.size(12.dp) else if (isLP3) Modifier.size(20.dp) else Modifier)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { useColorMode = true }) {
                Text(stringResource(R.string.color_mode_label), fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp, fontWeight = if (useColorMode) FontWeight.Bold else FontWeight.Normal)
                Spacer(modifier = Modifier.height(if (isVerySmallScreen) 2.dp else 4.dp))
                LightRadioButton(selected = useColorMode, onClick = { useColorMode = true }, modifier = if (isVerySmallScreen) Modifier.size(12.dp) else if (isLP3) Modifier.size(20.dp) else Modifier)
            }
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isLP3) 32.dp else if (isShortScreen) 12.dp else 24.dp))

        Text(stringResource(R.string.app_icon_label).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp, color = Color.Gray)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = if (isVerySmallScreen) 4.dp else if (isLP3) 16.dp else if (isShortScreen) 6.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { useBlueIcon = false }) {
                Text(stringResource(R.string.black_icon_label), fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp, fontWeight = if (!useBlueIcon) FontWeight.Bold else FontWeight.Normal)
                Spacer(modifier = Modifier.height(if (isVerySmallScreen) 2.dp else 4.dp))
                LightRadioButton(selected = !useBlueIcon, onClick = { useBlueIcon = false }, modifier = if (isVerySmallScreen) Modifier.size(12.dp) else if (isLP3) Modifier.size(20.dp) else Modifier)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { useBlueIcon = true }) {
                Text(stringResource(R.string.blue_icon_label), fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp, fontWeight = if (useBlueIcon) FontWeight.Bold else FontWeight.Normal)
                Spacer(modifier = Modifier.height(if (isVerySmallScreen) 2.dp else 4.dp))
                LightRadioButton(selected = useBlueIcon, onClick = { useBlueIcon = true }, modifier = if (isVerySmallScreen) Modifier.size(12.dp) else if (isLP3) Modifier.size(20.dp) else Modifier)
            }
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isLP3) 32.dp else if (isShortScreen) 12.dp else 24.dp))

        Text(stringResource(R.string.text_size_label, textSize.toInt()).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp, color = Color.Gray)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = if (isVerySmallScreen) 4.dp else if (isLP3) 16.dp else if (isShortScreen) 6.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(12f, 15f, 18f, 21f, 24f).forEach { size ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${size.toInt()}", fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp, fontWeight = if (textSize == size) FontWeight.Bold else FontWeight.Normal)
                    Spacer(modifier = Modifier.height(if (isVerySmallScreen) 2.dp else 4.dp))
                    LightRadioButton(selected = textSize == size, onClick = { textSize = size }, modifier = if (isVerySmallScreen) Modifier.size(12.dp) else if (isLP3) Modifier.size(20.dp) else Modifier)
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isLP3) 32.dp else if (isShortScreen) 12.dp else 24.dp))

        Text(stringResource(R.string.header_text_size_label, headerTextSize.toInt()).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp, color = Color.Gray)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = if (isVerySmallScreen) 4.dp else if (isLP3) 16.dp else if (isShortScreen) 6.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(12f, 15f, 18f, 21f, 24f).forEach { size ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${size.toInt()}", fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp, fontWeight = if (headerTextSize == size) FontWeight.Bold else FontWeight.Normal)
                    Spacer(modifier = Modifier.height(if (isVerySmallScreen) 2.dp else 4.dp))
                    LightRadioButton(selected = headerTextSize == size, onClick = { headerTextSize = size }, modifier = if (isVerySmallScreen) Modifier.size(12.dp) else if (isLP3) Modifier.size(20.dp) else Modifier)
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isLP3) 20.dp else if (isShortScreen) 8.dp else 16.dp))
        LightTextField(
            value = signature, 
            onValueChange = { signature = it }, 
            label = stringResource(R.string.signature_label),
            textSize = if (isVerySmallScreen) 14f else if (isLP3) 18f else 16f,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 8.dp else if (isLP3) 32.dp else if (isShortScreen) 16.dp else 32.dp))

        // Updates Section
        Text(stringResource(R.string.check_for_updates).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isLP3) 12.dp else 8.dp))

        if (updateAvailable != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.primary)
                    .padding(if (isVerySmallScreen) 8.dp else if (isLP3) 16.dp else 12.dp)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ruditimmermans/LightEmail/releases"))
                        context.startActivity(intent)
                    }
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.update_available, updateAvailable!!).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp
                    )
                    if (updateInfo != null && updateInfo!!.isNotEmpty()) {
                        Text(
                            text = updateInfo!!,
                            fontSize = if (isVerySmallScreen) 9.sp else if (isLP3) 12.sp else 11.sp,
                            color = Color.Gray,
                            maxLines = 3,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.download_update).uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else if (isCheckingUpdates) {
            Text(
                text = stringResource(R.string.checking_for_updates).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().clickable { viewModel.checkForUpdates() }) {
                Text(
                    text = stringResource(if (hasCheckedForUpdates && updateAvailable == null) R.string.no_updates_available else R.string.check_for_updates).uppercase(), 
                    fontWeight = FontWeight.Bold, 
                    fontSize = if (isVerySmallScreen) 14.sp else if (isLP3) 18.sp else 16.sp,
                    color = if (hasCheckedForUpdates && updateAvailable == null) Color.Gray else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isLP3) 32.dp else if (isShortScreen) 12.dp else 24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().clickable { autoCheckUpdates = !autoCheckUpdates },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.auto_check_updates_label), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 14.sp else if (isLP3) 18.sp else 16.sp)
            LightSwitch(checked = autoCheckUpdates, onCheckedChange = { autoCheckUpdates = it }, modifier = if (isLP3) Modifier.scale(1.2f) else Modifier)
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 8.dp else if (isLP3) 40.dp else if (isShortScreen) 16.dp else 32.dp))

        // Background Settings Section
        Text(stringResource(R.string.background_settings_title).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isLP3) 12.dp else 8.dp))

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
            Text(stringResource(R.string.battery_optimization_label), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 14.sp else if (isLP3) 18.sp else 16.sp)
            Text(stringResource(R.string.battery_optimization_desc), fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp, color = Color.Gray)
            Text(stringResource(R.string.configure).uppercase(), fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isLP3) 32.dp else if (isShortScreen) 12.dp else 24.dp))

        // App Hibernation / Pause if unused
        Column(modifier = Modifier.fillMaxWidth().clickable {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }) {
            Text(stringResource(R.string.app_hibernation_label), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 14.sp else if (isLP3) 18.sp else 16.sp)
            Text(stringResource(R.string.app_hibernation_desc), fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp, color = Color.Gray)
            Text(stringResource(R.string.configure).uppercase(), fontSize = if (isVerySmallScreen) 10.sp else if (isLP3) 14.sp else 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 8.dp else if (isLP3) 40.dp else if (isShortScreen) 16.dp else 32.dp))

        // Backup & Restore
        Text(stringResource(R.string.backup_restore_title).uppercase(), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 12.sp else if (isLP3) 16.sp else 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 4.dp else if (isLP3) 12.dp else 8.dp))

        Column(modifier = Modifier.fillMaxWidth().clickable {
            backupLauncher.launch("lightemail_backup.json")
        }) {
            Text(stringResource(R.string.backup_label), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 14.sp else if (isLP3) 18.sp else 16.sp)
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 6.dp else if (isLP3) 32.dp else if (isShortScreen) 12.dp else 24.dp))

        Column(modifier = Modifier.fillMaxWidth().clickable {
            restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
        }) {
            Text(stringResource(R.string.restore_label), fontWeight = FontWeight.Bold, fontSize = if (isVerySmallScreen) 14.sp else if (isLP3) 18.sp else 16.sp)
        }

        Spacer(modifier = Modifier.height(if (isVerySmallScreen) 12.dp else if (isLP3) 40.dp else if (isShortScreen) 24.dp else 48.dp))
    }
}

@Composable
fun AboutScreen(viewModel: EmailViewModel) {
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
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isShortScreen = configuration.screenHeightDp < 600
    val isSquareScreen = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp > 0.8f
    val isVerySmallScreen = configuration.screenHeightDp < 480
    val isLP3 = isSquareScreen && isShortScreen && !isVerySmallScreen
    val isStandardPhone = !isLP3 && !isVerySmallScreen && configuration.screenHeightDp >= 600

    val outerSize = if (isLP3) 24.dp else 16.dp
    val innerSize = if (isLP3) 14.dp else 10.dp

    Box(
        modifier = modifier
            .size(outerSize)
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.onBackground),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(innerSize)
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
            fontSize = textSize.sp,
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

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "file"
}
