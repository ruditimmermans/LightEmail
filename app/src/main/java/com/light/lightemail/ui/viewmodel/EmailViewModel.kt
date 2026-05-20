package com.light.lightemail.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.light.lightemail.R
import com.light.lightemail.data.AppDatabase
import com.light.lightemail.data.Contact
import com.light.lightemail.data.EmailMessage
import com.light.lightemail.data.ImapManager
import com.light.lightemail.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class EmailViewModel(application: Application) : AndroidViewModel(application) {
    private val imapManager = ImapManager()
    private val prefs = application.getSharedPreferences("light_email_prefs", Context.MODE_PRIVATE)

    private val _emails = MutableStateFlow<List<EmailMessage>>(emptyList())
    val emails: StateFlow<List<EmailMessage>> = _emails

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

    private val _syncInterval = MutableStateFlow(prefs.getInt("sync_interval", 15))
    val syncInterval: StateFlow<Int> = _syncInterval

    private val _textSize = MutableStateFlow(prefs.getFloat("text_size", 16f))
    val textSize: StateFlow<Float> = _textSize

    private val _signature = MutableStateFlow(prefs.getString("signature", application.getString(R.string.default_signature)) ?: application.getString(R.string.default_signature))
    val signature: StateFlow<String> = _signature

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders

    private val _currentFolder = MutableStateFlow("INBOX")
    val currentFolder: StateFlow<String> = _currentFolder

    private val db = AppDatabase.getDatabase(application)
    val contacts = db.contactDao().getAllContacts()

    init {
        if (_accountEmail.value.isNotEmpty()) {
            refreshEmails()
            refreshFolders()
            scheduleSync(_syncInterval.value)
        }
    }

    fun saveSettings(
        email: String,
        password: String,
        imapHost: String,
        smtpHost: String,
        smtpPort: String,
        senderName: String,
        syncInterval: Int,
        textSize: Float,
        signature: String
    ) {
        _accountEmail.value = email
        _accountPassword.value = password
        _imapHost.value = imapHost
        _smtpHost.value = smtpHost
        _smtpPort.value = smtpPort
        _senderName.value = senderName
        _syncInterval.value = syncInterval
        _textSize.value = textSize
        _signature.value = signature

        prefs.edit().apply {
            putString("email", email)
            putString("password", password)
            putString("host", imapHost)
            putString("smtp_host", smtpHost)
            putString("smtp_port", smtpPort)
            putString("sender_name", senderName)
            putInt("sync_interval", syncInterval)
            putFloat("text_size", textSize)
            putString("signature", signature)
            apply()
        }
        
        refreshEmails()
        refreshFolders()
        scheduleSync(syncInterval)
    }

    private fun scheduleSync(intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(getApplication())
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "email_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    fun selectFolder(folder: String) {
        _currentFolder.value = folder
        refreshEmails()
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

    fun refreshEmails() {
        val email = _accountEmail.value
        val password = _accountPassword.value
        val host = _imapHost.value
        val folder = _currentFolder.value

        if (email.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            val fetchedEmails = withContext(Dispatchers.IO) {
                imapManager.fetchEmails(
                    email = email,
                    password = password,
                    host = host,
                    folderName = folder,
                    noSubjectString = getApplication<Application>().getString(R.string.no_subject),
                    unknownSenderString = getApplication<Application>().getString(R.string.unknown_sender),
                    errorReadingContentString = getApplication<Application>().getString(R.string.error_reading_content)
                )
            }
            _emails.value = fetchedEmails
            
            // Update last seen UID to avoid duplicate notifications for emails already seen in app
            if (folder == "INBOX" && fetchedEmails.isNotEmpty()) {
                val latestUid = fetchedEmails.first().uid
                val lastSeenUid = prefs.getLong("last_seen_uid", -1L)
                if (latestUid > lastSeenUid) {
                    prefs.edit().putLong("last_seen_uid", latestUid).apply()
                }
            }

            _isLoading.value = false
        }
    }

    fun deleteEmail(emailMessage: EmailMessage) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                imapManager.deleteEmail(
                    _accountEmail.value,
                    _accountPassword.value,
                    _imapHost.value,
                    emailMessage.folder,
                    emailMessage.id.toInt()
                )
            }
            if (success) refreshEmails()
        }
    }

    fun sendEmail(to: String, subject: String, content: String, isHtml: Boolean = false, onResult: (Boolean) -> Unit) {
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
                    isHtml
                )
            }
            onResult(success)
        }
    }

    fun addContact(name: String, email: String) {
        viewModelScope.launch {
            db.contactDao().insertContact(Contact(name = name, email = email))
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            db.contactDao().deleteContact(contact)
        }
    }
}
