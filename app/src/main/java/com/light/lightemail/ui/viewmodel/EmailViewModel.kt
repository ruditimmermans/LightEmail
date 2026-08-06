package com.light.lightemail.ui.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.light.lightemail.R
import com.light.lightemail.data.AppDatabase
import com.light.lightemail.data.BackupManager
import com.light.lightemail.data.Contact
import com.light.lightemail.data.EmailMessage
import com.light.lightemail.data.ImapManager
import com.light.lightemail.data.EmailRepository
import com.light.lightemail.data.SyncEvent
import com.light.lightemail.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class EmailViewModel(application: Application) : AndroidViewModel(application) {
    private val imapManager = ImapManager()
    private val repository = EmailRepository(application)
    private val prefs = application.getSharedPreferences("light_email_prefs", Context.MODE_PRIVATE)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _accountEmail = MutableStateFlow(prefs.getString("email", "") ?: "")
    val accountEmail: StateFlow<String> = _accountEmail

    private val _accountPassword = MutableStateFlow(prefs.getString("password", "") ?: "")
    val accountPassword: StateFlow<String> = _accountPassword

    private val _imapHost = MutableStateFlow(prefs.getString("host", "posteo.de") ?: "posteo.de")
    val imapHost: StateFlow<String> = _imapHost

    private val _smtpHost = MutableStateFlow(prefs.getString("smtp_host", "posteo.de") ?: "posteo.de")
    val smtpHost: StateFlow<String> = _smtpHost

    private val _smtpPort = MutableStateFlow(prefs.getString("smtp_port", "465") ?: "465")
    val smtpPort: StateFlow<String> = _smtpPort

    private val _senderName = MutableStateFlow(prefs.getString("sender_name", "") ?: "")
    val senderName: StateFlow<String> = _senderName

    private val _enablePush = MutableStateFlow(true)
    val enablePush: StateFlow<Boolean> = _enablePush

    private val _textSize = MutableStateFlow(prefs.getFloat("text_size", 16f))
    val textSize: StateFlow<Float> = _textSize

    private val _useColorMode = MutableStateFlow(prefs.getBoolean("use_color_mode", false))
    val useColorMode: StateFlow<Boolean> = _useColorMode

    private val _useBlueIcon = MutableStateFlow(prefs.getBoolean("use_blue_icon", false))
    val useBlueIcon: StateFlow<Boolean> = _useBlueIcon

    private val _autoCheckUpdates = MutableStateFlow(prefs.getBoolean("auto_check_updates", true))
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates

    private val _signature = MutableStateFlow(prefs.getString("signature", application.getString(R.string.default_signature)) ?: application.getString(R.string.default_signature))
    val signature: StateFlow<String> = _signature

    private val _folders = MutableStateFlow<List<com.light.lightemail.data.FolderInfo>>(emptyList())
    val folders: StateFlow<List<com.light.lightemail.data.FolderInfo>> = _folders

    private val _currentFolder = MutableStateFlow("Inbox")
    val currentFolder: StateFlow<String> = _currentFolder

    @OptIn(ExperimentalCoroutinesApi::class)
    val emails: StateFlow<List<EmailMessage>> = _currentFolder
        .flatMapLatest { folder -> repository.getEmails(folder) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _updateAvailable = MutableStateFlow<String?>(null)
    val updateAvailable: StateFlow<String?> = _updateAvailable

    private val _updateInfo = MutableStateFlow<String?>(null)
    val updateInfo: StateFlow<String?> = _updateInfo

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates

    private val _hasCheckedForUpdates = MutableStateFlow(false)
    val hasCheckedForUpdates: StateFlow<Boolean> = _hasCheckedForUpdates

    private val db = AppDatabase.getDatabase(application)
    val contacts = db.contactDao().getAllContacts()

    private val backupManager = BackupManager(application)

    init {
        updateAppIcon(_useBlueIcon.value)
        if (_accountEmail.value.isNotEmpty()) {
            refreshEmails()
            refreshFolders()
            updatePushService(true)
        }

        if (_autoCheckUpdates.value) {
            checkForUpdates()
        }

        viewModelScope.launch {
            SyncEvent.events.collectLatest {
                refreshEmails(showLoading = false)
                refreshFolders()
            }
        }
    }

    fun exportBackup(outputStream: java.io.OutputStream, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = backupManager.exportBackup(outputStream)
            onResult(success)
        }
    }

    fun importBackup(inputStream: java.io.InputStream, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = backupManager.importBackup(inputStream)
            if (success) {
                // Reload settings into StateFlows
                _accountEmail.value = prefs.getString("email", "") ?: ""
                _accountPassword.value = prefs.getString("password", "") ?: ""
                _imapHost.value = prefs.getString("host", "posteo.de") ?: "posteo.de"
                _smtpHost.value = prefs.getString("smtp_host", "posteo.de") ?: "posteo.de"
                _smtpPort.value = prefs.getString("smtp_port", "465") ?: "465"
                _senderName.value = prefs.getString("sender_name", "") ?: ""
                _textSize.value = prefs.getFloat("text_size", 16f)
                _useColorMode.value = prefs.getBoolean("use_color_mode", false)
                _useBlueIcon.value = prefs.getBoolean("use_blue_icon", false)
                _autoCheckUpdates.value = prefs.getBoolean("auto_check_updates", true)
                _signature.value = prefs.getString("signature", getApplication<Application>().getString(R.string.default_signature)) ?: getApplication<Application>().getString(R.string.default_signature)
                
                refreshEmails()
                refreshFolders()
                updatePushService(true)
                updateAppIcon(_useBlueIcon.value)
            }
            onResult(success)
        }
    }

    fun saveSettings(
        email: String,
        password: String,
        imapHost: String,
        smtpHost: String,
        smtpPort: String,
        senderName: String,
        textSize: Float,
        signature: String,
        useColorMode: Boolean,
        autoCheckUpdates: Boolean,
        useBlueIcon: Boolean
    ) {
        _accountEmail.value = email
        _accountPassword.value = password
        _imapHost.value = imapHost
        _smtpHost.value = smtpHost
        _smtpPort.value = smtpPort
        _senderName.value = senderName
        _textSize.value = textSize
        _signature.value = signature
        _useColorMode.value = useColorMode
        _useBlueIcon.value = useBlueIcon
        _autoCheckUpdates.value = autoCheckUpdates

        prefs.edit().apply {
            putString("email", email)
            putString("password", password)
            putString("host", imapHost)
            putString("smtp_host", smtpHost)
            putString("smtp_port", smtpPort)
            putString("sender_name", senderName)
            putBoolean("enable_push", true)
            putFloat("text_size", textSize)
            putString("signature", signature)
            putBoolean("use_color_mode", useColorMode)
            putBoolean("use_blue_icon", useBlueIcon)
            putBoolean("auto_check_updates", autoCheckUpdates)
            apply()
        }
        
        refreshEmails()
        refreshFolders()
        updatePushService(true)
        updateAppIcon(useBlueIcon)
    }

    private fun updatePushService(enabled: Boolean) {
        val intent = android.content.Intent(getApplication(), com.light.lightemail.service.EmailPushService::class.java)
        if (enabled) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().stopService(intent)
        }
    }

    private fun updateAppIcon(useBlue: Boolean) {
        val context = getApplication<Application>()
        val pm = context.packageManager
        
        val blackComponent = ComponentName(context, "${context.packageName}.MainActivityBlack")
        val blueComponent = ComponentName(context, "${context.packageName}.MainActivityBlue")
        
        val (enable, disable) = if (useBlue) {
            blueComponent to blackComponent
        } else {
            blackComponent to blueComponent
        }

        // Only change if needed to avoid unnecessary launcher refreshes
        if (pm.getComponentEnabledSetting(enable) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(enable, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(disable, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }


    fun selectFolder(folder: String) {
        if (_currentFolder.value == folder) return
        _currentFolder.value = folder
        refreshEmails()
    }

    fun markAsRead(emailMessage: EmailMessage) {
        // Remove notification when email is read
        val notificationManager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)

        // Always try to fetch content if it's missing, even if already read
        if (emailMessage.content.isEmpty() || (emailMessage.content == getApplication<Application>().getString(R.string.error_reading_content))) {
            fetchEmailContent(emailMessage)
        }

        if (emailMessage.isRead) return

        viewModelScope.launch {
            repository.markAsRead(
                _accountEmail.value,
                _accountPassword.value,
                _imapHost.value,
                emailMessage.folder,
                emailMessage.uid,
                emailMessage.id.toInt()
            )
            // Update folders to reflect unread count change
            refreshFolders()
        }
    }

    fun fetchEmailContent(emailMessage: EmailMessage) {
        // If content already fetched, don't fetch again
        if (emailMessage.content.isNotEmpty() && emailMessage.content != getApplication<Application>().getString(R.string.error_reading_content)) return

        viewModelScope.launch {
            repository.fetchEmailContent(
                _accountEmail.value,
                _accountPassword.value,
                _imapHost.value,
                emailMessage
            )
        }
    }

    fun refreshFolders() {
        val email = _accountEmail.value
        val password = _accountPassword.value
        val host = _imapHost.value
        if (email.isEmpty()) return

        viewModelScope.launch {
            val fetchedFolders = withContext(Dispatchers.IO) {
                imapManager.fetchFolders(email, password, host)
            }
            _folders.value = fetchedFolders
        }
    }

    fun refreshEmails(showLoading: Boolean = true) {
        val email = _accountEmail.value
        val password = _accountPassword.value
        val host = _imapHost.value
        val folder = _currentFolder.value

        if (email.isEmpty()) return

        viewModelScope.launch {
            // Only show loading if we don't have any emails cached for this folder yet
            val currentList = repository.getEmails(folder).first()
            if (showLoading && currentList.isEmpty()) _isLoading.value = true
            
            try {
                repository.syncEmails(
                    email = email,
                    password = password,
                    host = host,
                    folder = folder
                )
                
                // Pre-fetch content for the latest emails in the background
                viewModelScope.launch(Dispatchers.IO) {
                    val updatedEmails = repository.getEmails(folder).first()
                    updatedEmails.take(10).forEach { emailMsg ->
                        if (emailMsg.content.isEmpty() || emailMsg.content == getApplication<Application>().getString(R.string.error_reading_content)) {
                            repository.fetchEmailContent(
                                _accountEmail.value,
                                _accountPassword.value,
                                _imapHost.value,
                                emailMsg
                            )
                        }
                    }
                }

                // Update last seen UID and count for notifications (using cached data)
                if (folder == "Inbox") {
                    val currentEmails = repository.getEmails(folder).first()
                    if (currentEmails.isNotEmpty()) {
                        val latestUid = currentEmails.first().uid
                        val lastSeenUid = prefs.getLong("last_seen_uid", -1L)
                        val unreadCount = currentEmails.count { !it.isRead }
                        
                        if (latestUid > lastSeenUid || unreadCount.toLong() != prefs.getLong("last_unread_count", -1L)) {
                            prefs.edit()
                                .putLong("last_seen_uid", maxOf(latestUid, lastSeenUid))
                                .putLong("last_unread_count", unreadCount.toLong())
                                .apply()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (showLoading) _isLoading.value = false
            }
        }
    }

    fun deleteEmail(emailMessage: EmailMessage) {
        // Remove notification if email is deleted
        val notificationManager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)

        viewModelScope.launch {
            repository.deleteEmail(
                _accountEmail.value,
                _accountPassword.value,
                _imapHost.value,
                emailMessage.folder,
                emailMessage.uid,
                emailMessage.id.toInt()
            )
            refreshFolders()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                imapManager.emptyTrash(
                    _accountEmail.value,
                    _accountPassword.value,
                    _imapHost.value
                )
            }
            if (success) {
                refreshEmails()
                refreshFolders()
            }
        }
    }

    fun sendEmail(to: String, subject: String, content: String, isHtml: Boolean = false, cc: String? = null, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                imapManager.sendEmail(
                    _accountEmail.value,
                    _accountPassword.value,
                    _smtpHost.value,
                    _smtpPort.value,
                    _senderName.value,
                    to,
                    subject,
                    content,
                    isHtml,
                    _imapHost.value,
                    cc
                )
            }
            if (success) refreshFolders()
            onResult(success)
        }
    }

    fun addContact(name: String, email: String) {
        viewModelScope.launch {
            db.contactDao().insertContact(Contact(name = name, email = email))
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch {
            db.contactDao().insertContact(contact)
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            db.contactDao().deleteContact(contact)
        }
    }

    fun checkForUpdates() {
        if (_isCheckingUpdates.value) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingUpdates.value = true
            try {
                val url = java.net.URL("https://api.github.com/repos/ruditimmermans/LightEmail/releases/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connect()

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = org.json.JSONObject(response)
                    val tagName = json.getString("tag_name")
                    val latestVersion = tagName.removePrefix("v")
                    val releaseNotes = json.optString("body")

                    val currentVersion = try {
                        getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0).versionName
                    } catch (e: Exception) {
                        null
                    }

                    if (currentVersion != null && isNewerVersion(currentVersion, latestVersion)) {
                        _updateAvailable.value = latestVersion
                        _updateInfo.value = releaseNotes
                    } else {
                        _updateAvailable.value = null
                        _updateInfo.value = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isCheckingUpdates.value = false
                _hasCheckedForUpdates.value = true
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val curr = if (i < currentParts.size) currentParts[i] else 0
            val late = if (i < latestParts.size) latestParts[i] else 0
            if (late > curr) return true
            if (curr > late) return false
        }
        return false
    }
}
