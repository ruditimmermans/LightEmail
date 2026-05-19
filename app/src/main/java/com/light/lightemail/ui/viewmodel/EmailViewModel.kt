package com.light.lightemail.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.light.lightemail.data.EmailMessage
import com.light.lightemail.data.ImapManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmailViewModel : ViewModel() {
    private val imapManager = ImapManager()

    private val _emails = MutableStateFlow<List<EmailMessage>>(emptyList())
    val emails: StateFlow<List<EmailMessage>> = _emails

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _accountEmail = MutableStateFlow("")
    val accountEmail: StateFlow<String> = _accountEmail

    private val _accountPassword = MutableStateFlow("")
    private val _imapHost = MutableStateFlow("")

    fun setAccount(email: String, password: String, host: String) {
        _accountEmail.value = email
        _accountPassword.value = password
        _imapHost.value = host
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
                imapManager.fetchEmails(email, password, host)
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
                // Inferring SMTP host from IMAP host for common providers
                val smtpHost = host.replace("imap", "smtp")
                imapManager.sendReply(
                    email = email,
                    password = password,
                    smtpHost = smtpHost,
                    originalMessage = originalMessage,
                    replyContent = replyContent,
                    signature = signature
                )
            }
            onResult(success)
        }
    }
}
