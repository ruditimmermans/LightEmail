package com.light.lightemail.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.light.lightemail.R
import com.light.lightemail.data.EmailMessage
import com.light.lightemail.data.ImapManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _textSize = MutableStateFlow(prefs.getFloat("text_size", 16f))
    val textSize: StateFlow<Float> = _textSize

    private val _signature = MutableStateFlow(prefs.getString("signature", application.getString(R.string.default_signature)) ?: application.getString(R.string.default_signature))
    val signature: StateFlow<String> = _signature

    init {
        if (_accountEmail.value.isNotEmpty()) {
            refreshEmails()
        }
    }

    fun saveSettings(email: String, password: String, host: String, textSize: Float, signature: String) {
        _accountEmail.value = email
        _accountPassword.value = password
        _imapHost.value = host
        _textSize.value = textSize
        _signature.value = signature

        prefs.edit().apply {
            putString("email", email)
            putString("password", password)
            putString("host", host)
            putFloat("text_size", textSize)
            putString("signature", signature)
            apply()
        }
        
        refreshEmails()
    }

    fun refreshEmails() {
        val email = _accountEmail.value
        val password = _accountPassword.value
        val host = _imapHost.value

        if (email.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            val fetchedEmails = withContext(Dispatchers.IO) {
                imapManager.fetchEmails(
                    email = email,
                    password = password,
                    host = host,
                    noSubjectString = getApplication<Application>().getString(R.string.no_subject),
                    unknownSenderString = getApplication<Application>().getString(R.string.unknown_sender),
                    errorReadingContentString = getApplication<Application>().getString(R.string.error_reading_content)
                )
            }
            _emails.value = fetchedEmails
            _isLoading.value = false
        }
    }

    fun sendReply(originalMessage: EmailMessage, replyContent: String, signature: String, onResult: (Boolean) -> Unit) {
        val email = _accountEmail.value
        val password = _accountPassword.value
        val host = _imapHost.value

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                val smtpHost = host.replace("imap", "smtp")
                imapManager.sendReply(
                    email = email,
                    password = password,
                    smtpHost = smtpHost,
                    originalMessage = originalMessage,
                    replyContent = replyContent,
                    signature = signature,
                    subjectPrefix = getApplication<Application>().getString(R.string.reply_subject_prefix, ""),
                    attributionFormat = getApplication<Application>().getString(R.string.reply_attribution)
                )
            }
            onResult(success)
        }
    }
}
