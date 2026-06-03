package com.light.lightemail.ui.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.light.lightemail.R
import com.light.lightemail.data.AppDatabase
import com.light.lightemail.data.Contact
import com.light.lightemail.data.EmailMessage
import com.light.lightemail.data.ImapManager
import com.light.lightemail.data.SyncEvent
import com.light.lightemail.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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

    private val _enablePush = MutableStateFlow(true)
    val enablePush: StateFlow<Boolean> = _enablePush

    private val _textSize = MutableStateFlow(prefs.getFloat("text_size", 16f))
    val textSize: StateFlow<Float> = _textSize

    private val _signature = MutableStateFlow(prefs.getString("signature", application.getString(R.string.default_signature)) ?: application.getString(R.string.default_signature))
    val signature: StateFlow<String> = _signature

    private val _folders = MutableStateFlow<List<com.light.lightemail.data.FolderInfo>>(emptyList())
    val folders: StateFlow<List<com.light.lightemail.data.FolderInfo>> = _folders

    private val _currentFolder = MutableStateFlow("Inbox")
    val currentFolder: StateFlow<String> = _currentFolder

    private val db = AppDatabase.getDatabase(application)
    val contacts = db.contactDao().getAllContacts()

    init {
        if (_accountEmail.value.isNotEmpty()) {
            refreshEmails()
            refreshFolders()
            updatePushService(true)
        }

        viewModelScope.launch {
            SyncEvent.events.collectLatest {
                refreshEmails(showLoading = false)
                refreshFolders()
            }
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
        signature: String
    ) {
        _accountEmail.value = email
        _accountPassword.value = password
        _imapHost.value = imapHost
        _smtpHost.value = smtpHost
        _smtpPort.value = smtpPort
        _senderName.value = senderName
        _textSize.value = textSize
        _signature.value = signature

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
            apply()
        }
        
        refreshEmails()
        refreshFolders()
        updatePushService(true)
    }

    private fun updatePushService(enabled: Boolean) {
        val intent = android.content.Intent(getApplication(), com.light.lightemail.service.EmailPushService::class.java)
        if (enabled) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().stopService(intent)
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

        // Update local state to show as read immediately (optimistic UI)
        if (!emailMessage.isRead) {
            _emails.value = _emails.value.map {
                if (it.uid == emailMessage.uid) it.copy(isRead = true) else it
            }
        }

        // Always try to fetch content if it's missing, even if already read
        if (emailMessage.content.isEmpty() || (emailMessage.content == getApplication<Application>().getString(R.string.error_reading_content))) {
            fetchEmailContent(emailMessage)
        }

        if (emailMessage.isRead) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                imapManager.markAsRead(
                    _accountEmail.value,
                    _accountPassword.value,
                    _imapHost.value,
                    emailMessage.folder,
                    emailMessage.id.toInt()
                )
            }
            // Update folders to reflect unread count change
            refreshFolders()
        }
    }

    fun fetchEmailContent(emailMessage: EmailMessage) {
        // If content already fetched, don't fetch again
        if (emailMessage.content.isNotEmpty() && emailMessage.content != getApplication<Application>().getString(R.string.error_reading_content)) return

        viewModelScope.launch {
            val (text, html) = withContext(Dispatchers.IO) {
                imapManager.fetchEmailContent(
                    _accountEmail.value,
                    _accountPassword.value,
                    _imapHost.value,
                    emailMessage.folder,
                    emailMessage.uid,
                    getApplication<Application>().getString(R.string.error_reading_content)
                )
            }
            _emails.value = _emails.value.map {
                if (it.uid == emailMessage.uid) it.copy(content = text, htmlContent = html) else it
            }
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
            if (showLoading) _isLoading.value = true
            try {
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
                
                // Merge with existing emails to preserve fetched content and avoid flickering
                val currentEmailsMap = _emails.value.associateBy { it.uid }
                val mergedEmails = fetchedEmails.map { newEmail ->
                    val existing = currentEmailsMap[newEmail.uid]
                    if (existing != null) {
                        newEmail.copy(
                            content = if (newEmail.content.isEmpty()) existing.content else newEmail.content,
                            htmlContent = newEmail.htmlContent ?: existing.htmlContent,
                            // Preserve local read status if it was changed recently
                            isRead = if (existing.isRead) true else newEmail.isRead
                        )
                    } else {
                        newEmail
                    }
                }
                _emails.value = mergedEmails
                
                // Pre-fetch content for the latest emails in the background to make them open "immediately"
                launch(Dispatchers.IO) {
                    val currentList = _emails.value.toMutableList()
                    var anyChanged = false
                    
                    // Fetch content for top 10 emails that don't have it yet
                    mergedEmails.take(10).forEach { email ->
                        if (email.content.isEmpty() || email.content == getApplication<Application>().getString(R.string.error_reading_content)) {
                            val (text, html) = imapManager.fetchEmailContent(
                                _accountEmail.value,
                                _accountPassword.value,
                                _imapHost.value,
                                email.folder,
                                email.uid,
                                getApplication<Application>().getString(R.string.error_reading_content)
                            )
                            val index = currentList.indexOfFirst { it.uid == email.uid }
                            if (index != -1) {
                                currentList[index] = currentList[index].copy(content = text, htmlContent = html)
                                anyChanged = true
                            }
                        }
                    }
                    
                    if (anyChanged) {
                        withContext(Dispatchers.Main) {
                            _emails.value = currentList.toList()
                        }
                    }
                }

                // Update last seen UID to avoid duplicate notifications
                if (folder == "Inbox" && fetchedEmails.isNotEmpty()) {
                    val latestUid = fetchedEmails.first().uid
                    val lastSeenUid = prefs.getLong("last_seen_uid", -1L)
                    if (latestUid > lastSeenUid) {
                        prefs.edit().putLong("last_seen_uid", latestUid).apply()
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

        // Optimistic UI update: remove from local list immediately
        val originalEmails = _emails.value
        _emails.value = _emails.value.filter { it.uid != emailMessage.uid }

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
            if (success) {
                // Refresh folders in background to update counts, but don't force a full email refresh
                // unless it's necessary. Since we already removed it locally, we're good.
                refreshFolders()
            } else {
                // If it failed, restore the original list
                _emails.value = originalEmails
            }
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
                    isHtml,
                    _imapHost.value
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
}
