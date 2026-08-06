package com.light.lightemail.data

import com.sun.mail.imap.IMAPFolder
import java.util.Properties
import javax.mail.*
import javax.mail.event.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.search.FlagTerm

data class FolderInfo(val name: String, val messageCount: Int, val unreadCount: Int)

class ImapManager {
    private fun getImapProperties(host: String): Properties {
        val properties = Properties()
        properties["mail.store.protocol"] = "imaps"
        properties["mail.imaps.host"] = host
        properties["mail.imaps.port"] = "993"
        properties["mail.imaps.ssl.enable"] = "true"
        // Battery and Performance Optimizations
        properties["mail.imaps.connectiontimeout"] = "30000" // 30s
        properties["mail.imaps.timeout"] = "30000" // 30s
        properties["mail.imaps.compress.enable"] = "true" // Enable compression
        properties["mail.imaps.partialfetch"] = "false"
        properties["mail.imaps.fetchsize"] = "2097152" // Increase fetch size to 2MB for faster and more complete transfers
        properties["mail.imaps.connectionpoolsize"] = "5"
        properties["mail.imaps.connectionpooltimeout"] = "300000"
        // IDLE optimization
        properties["mail.imaps.peek"] = "true"
        return properties
    }

    fun startIdle(
        email: String,
        password: String,
        host: String,
        folderName: String = "Inbox",
        onFolderChanged: () -> Unit
    ) {
        val properties = getImapProperties(host)
        // IDLE requires keeping the connection open
        properties["mail.imaps.connectionpooltimeout"] = "300000" // 5 min
        
        var store: Store? = null
        var folder: IMAPFolder? = null

        try {
            val session = Session.getInstance(properties, null)
            store = session.getStore("imaps")
            store.connect(host, email, password)

            folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)

            if (store is com.sun.mail.imap.IMAPStore && !store.hasCapability("IDLE")) {
                // If IDLE is not supported, we can't do much here except return
                // and let the caller handle it (e.g. by falling back to polling)
                return
            }

            val listener = object : MessageCountAdapter() {
                override fun messagesAdded(e: MessageCountEvent) {
                    onFolderChanged()
                }

                override fun messagesRemoved(e: MessageCountEvent) {
                    onFolderChanged()
                }
            }
            folder.addMessageCountListener(listener)

            // Also listen for flag changes (e.g. marked as read on other device)
            folder.addMessageChangedListener(object : MessageChangedListener {
                override fun messageChanged(e: MessageChangedEvent) {
                    onFolderChanged()
                }
            })

            while (!Thread.interrupted() && store.isConnected) {
                if (!folder.isOpen) folder.open(Folder.READ_ONLY)
                
                try {
                    // Start IDLE. This blocks until a message arrives or server kicks us.
                    folder.idle()
                } catch (e: Exception) {
                    // If IDLE fails, try to recover
                    if (!store.isConnected) break
                }
                
                // Periodically check if connection is still alive if idle() returns
                // or just loop back and re-enter IDLE.
                // Some servers time out after 29-30 minutes.
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e // Rethrow to let EmailPushService handle the delay/restart
        } finally {
            try {
                folder?.close(false)
                store?.close()
            } catch (e: Exception) {}
        }
    }

    fun fetchEmails(
        email: String,
        password: String,
        host: String,
        folderName: String = "Inbox",
        limit: Int = 20,
        noSubjectString: String = "(No Subject)",
        unknownSenderString: String = "Unknown",
        errorReadingContentString: String = "Error reading content",
        fetchContent: Boolean = false // Default to false for faster refresh
    ): List<EmailMessage> {
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = store.getFolder(folderName)
            folder.open(Folder.READ_ONLY)

            val totalMessages = folder.messageCount
            if (totalMessages == 0) {
                folder.close(false)
                store.close()
                return emptyList()
            }

            // Get last 'limit' messages more efficiently
            val start = (totalMessages - limit + 1).coerceAtLeast(1)
            val end = totalMessages
            val messages = folder.getMessages(start, end)
            
            // Filter out deleted messages if necessary, though getMessages is faster than search
            val lastMessages = messages.filter { !it.flags.contains(Flags.Flag.DELETED) }.toTypedArray()
            
            // Optimize fetching by using a FetchProfile
            val fp = FetchProfile()
            fp.add(FetchProfile.Item.ENVELOPE)
            fp.add(FetchProfile.Item.FLAGS)
            if (fetchContent) {
                fp.add(FetchProfile.Item.CONTENT_INFO)
            }
            if (folder is IMAPFolder) {
                fp.add(UIDFolder.FetchProfileItem.UID)
            }
            folder.fetch(lastMessages, fp)

            val result = lastMessages.reversedArray().map { msg ->
                val (text, html) = if (fetchContent) {
                    getContent(msg, errorReadingContentString)
                } else {
                    Pair("", null)
                }
                EmailMessage(
                    id = msg.messageNumber.toString(),
                    uid = if (folder is IMAPFolder) folder.getUID(msg) else -1L,
                    subject = msg.subject ?: noSubjectString,
                    sender = msg.from?.firstOrNull()?.toString() ?: unknownSenderString,
                    replyTo = msg.replyTo?.firstOrNull()?.toString(),
                    toRecipients = msg.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { it.toString() },
                    ccRecipients = msg.getRecipients(Message.RecipientType.CC)?.joinToString(", ") { it.toString() },
                    content = text,
                    htmlContent = html,
                    date = msg.sentDate?.toString() ?: "",
                    folder = folderName,
                    isRead = msg.flags.contains(Flags.Flag.SEEN)
                )
            }

            folder.close(false)
            store.close()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    fun fetchLatestUids(
        email: String,
        password: String,
        host: String,
        folderName: String = "Inbox",
        limit: Int = 50
    ): List<Long> {
        val properties = getImapProperties(host)
        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)

            val totalMessages = folder.messageCount
            if (totalMessages == 0) {
                folder.close(false)
                store.close()
                return emptyList()
            }

            val start = (totalMessages - limit + 1).coerceAtLeast(1)
            val end = totalMessages
            val messages = folder.getMessages(start, end)
            
            val fp = FetchProfile()
            fp.add(UIDFolder.FetchProfileItem.UID)
            folder.fetch(messages, fp)

            val uids = messages.map { folder.getUID(it) }.reversed()
            
            folder.close(false)
            store.close()
            uids
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun fetchEmailsByUids(
        email: String,
        password: String,
        host: String,
        folderName: String,
        uids: List<Long>,
        noSubjectString: String = "(No Subject)",
        unknownSenderString: String = "Unknown"
    ): List<EmailMessage> {
        if (uids.isEmpty()) return emptyList()
        
        val properties = getImapProperties(host)
        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)

            val messages = folder.getMessagesByUID(uids.toLongArray())
            
            val fp = FetchProfile()
            fp.add(FetchProfile.Item.ENVELOPE)
            fp.add(FetchProfile.Item.FLAGS)
            fp.add(UIDFolder.FetchProfileItem.UID)
            folder.fetch(messages, fp)

            val result = messages.map { msg ->
                EmailMessage(
                    id = msg.messageNumber.toString(),
                    uid = folder.getUID(msg),
                    subject = msg.subject ?: noSubjectString,
                    sender = msg.from?.firstOrNull()?.toString() ?: unknownSenderString,
                    replyTo = msg.replyTo?.firstOrNull()?.toString(),
                    toRecipients = msg.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { it.toString() },
                    ccRecipients = msg.getRecipients(Message.RecipientType.CC)?.joinToString(", ") { it.toString() },
                    content = "",
                    htmlContent = null,
                    date = msg.sentDate?.toString() ?: "",
                    folder = folderName,
                    isRead = msg.flags.contains(Flags.Flag.SEEN)
                )
            }.reversed()

            folder.close(false)
            store.close()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun fetchUnreadEmails(
        email: String,
        password: String,
        host: String,
        folderName: String = "Inbox",
        limit: Int = 10,
        noSubjectString: String = "(No Subject)",
        unknownSenderString: String = "Unknown"
    ): List<EmailMessage> {
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = store.getFolder(folderName)
            folder.open(Folder.READ_ONLY)

            val unreadMessages = folder.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
            
            if (unreadMessages.isEmpty()) {
                folder.close(false)
                store.close()
                return emptyList()
            }

            // Take the latest 'limit' unread messages
            val lastUnread = unreadMessages.reversedArray().take(limit).toTypedArray()
            
            val fp = FetchProfile()
            fp.add(FetchProfile.Item.ENVELOPE)
            fp.add(FetchProfile.Item.FLAGS)
            if (folder is IMAPFolder) {
                fp.add(UIDFolder.FetchProfileItem.UID)
            }
            folder.fetch(lastUnread, fp)

            val result = lastUnread.map { msg ->
                EmailMessage(
                    id = msg.messageNumber.toString(),
                    uid = if (folder is IMAPFolder) folder.getUID(msg) else -1L,
                    subject = msg.subject ?: noSubjectString,
                    sender = msg.from?.firstOrNull()?.toString() ?: unknownSenderString,
                    replyTo = msg.replyTo?.firstOrNull()?.toString(),
                    toRecipients = msg.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { it.toString() },
                    ccRecipients = msg.getRecipients(Message.RecipientType.CC)?.joinToString(", ") { it.toString() },
                    content = "",
                    htmlContent = null,
                    date = msg.sentDate?.toString() ?: "",
                    folder = folderName,
                    isRead = false
                )
            }

            folder.close(false)
            store.close()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    fun fetchEmailContent(
        email: String,
        password: String,
        host: String,
        folderName: String,
        uid: Long,
        errorReadingContentString: String = "Error reading content"
    ): Pair<String, String?> {
        val properties = getImapProperties(host)
        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)

            val msg = folder.getMessageByUID(uid)
            val content = if (msg != null) getContent(msg, errorReadingContentString) else Pair(errorReadingContentString, null)

            folder.close(false)
            store.close()
            content
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(errorReadingContentString, null)
        }
    }

    private fun getContent(message: Message, errorReadingContentString: String): Pair<String, String?> {
        return try {
            val textBuilder = StringBuilder()
            val htmlBuilder = StringBuilder()
            val images = mutableMapOf<String, String>()
            extractContent(message, textBuilder, htmlBuilder, images)
            
            var text = textBuilder.toString().trim()
            var html = htmlBuilder.toString().trim().ifEmpty { null }
            
            // Fallback: if text is empty or looks like a placeholder, and HTML is not, strip HTML to get text
            val isPlaceholder = text.isEmpty() || 
                               text.lowercase().contains("cannot load") || 
                               text.lowercase().contains("view this email in a browser") ||
                               text.lowercase().contains("fout bij lezen") ||
                               text == errorReadingContentString

            if (isPlaceholder && html != null) {
                val stripped = stripHtml(html)
                if (stripped.length > text.length) {
                    text = stripped
                }
            }
            
            if (text.isEmpty()) {
                text = errorReadingContentString
            }
            
            html?.let { h ->
                if (images.isNotEmpty()) {
                    var updatedHtml = h
                    for ((cid, base64) in images) {
                        updatedHtml = updatedHtml.replace("cid:$cid", base64)
                    }
                    html = updatedHtml
                }
            }
            
            Pair(text, html)
        } catch (e: Exception) {
            Pair(errorReadingContentString, null)
        }
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<style.*?>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<script.*?>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun extractContent(part: Part, text: StringBuilder, html: StringBuilder, images: MutableMap<String, String>) {
        try {
            if (part.isMimeType("text/plain")) {
                val content = try { part.content } catch (e: Exception) { null }
                if (content is String) {
                    text.append(content)
                } else {
                    // Fallback to input stream if content is not a string or failed to parse
                    try {
                        val bytes = part.inputStream.readBytes()
                        text.append(bytes.toString(Charsets.UTF_8))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (part.isMimeType("text/html")) {
                val content = try { part.content } catch (e: Exception) { null }
                if (content is String) {
                    html.append(content)
                } else {
                    // Fallback to input stream if content is not a string
                    try {
                        val bytes = part.inputStream.readBytes()
                        html.append(bytes.toString(Charsets.UTF_8))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (part.isMimeType("multipart/*")) {
                val multiPart = part.content as MimeMultipart
                for (i in 0 until multiPart.count) {
                    extractContent(multiPart.getBodyPart(i), text, html, images)
                }
            } else if (part.isMimeType("message/rfc822")) {
                val content = part.content
                if (content is Part) {
                    extractContent(content, text, html, images)
                }
            } else if (part.isMimeType("image/*")) {
                val cid = part.getHeader("Content-ID")?.firstOrNull()?.removeSurrounding("<", ">")
                if (cid != null) {
                    try {
                        val inputStream = part.inputStream
                        val bytes = inputStream.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        images[cid] = "data:${part.contentType.substringBefore(";")};base64,$base64"
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fetchFolders(email: String, password: String, host: String): List<FolderInfo> {
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)
            
            // Limit to top-level folders or common folders to speed up if many folders exist
            val folders = store.defaultFolder.list().map { folder ->
                if (folder is IMAPFolder) {
                    try {
                        // Use STATUS command which is much faster than opening the folder
                        // We use JavaMail's internal way of getting status if possible
                        // or just open it briefly. Actually, folder.messageCount on IMAPFolder 
                        // often triggers a STATUS if not open.
                        val count = folder.messageCount
                        val unread = folder.unreadMessageCount
                        FolderInfo(folder.fullName, count, unread)
                    } catch (e: Exception) {
                        folder.open(Folder.READ_ONLY)
                        val count = folder.messageCount
                        val unread = folder.unreadMessageCount
                        val info = FolderInfo(folder.fullName, count, unread)
                        folder.close(false)
                        info
                    }
                } else {
                    folder.open(Folder.READ_ONLY)
                    val count = folder.messageCount
                    val unread = folder.unreadMessageCount
                    val info = FolderInfo(folder.fullName, count, unread)
                    folder.close(false)
                    info
                }
            }
            store.close()
            folders
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun deleteEmail(email: String, password: String, host: String, folderName: String, messageId: Int): Boolean {
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)
            val folder = store.getFolder(folderName)
            folder.open(Folder.READ_WRITE)
            val message = folder.getMessage(messageId)

            if (folderName.lowercase().contains("trash")) {
                message.setFlag(Flags.Flag.DELETED, true)
            } else {
                val trashFolder = store.defaultFolder.list().find { it.name.lowercase().contains("trash") }
                    ?: store.getFolder("Trash")
                if (!trashFolder.exists()) trashFolder.create(Folder.HOLDS_MESSAGES)
                folder.copyMessages(arrayOf(message), trashFolder)
                message.setFlag(Flags.Flag.DELETED, true)
            }
            
            folder.expunge()
            folder.close(true)
            store.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun markAsRead(email: String, password: String, host: String, folderName: String, messageId: Int): Boolean {
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)
            val folder = store.getFolder(folderName)
            folder.open(Folder.READ_WRITE)
            val message = folder.getMessage(messageId)
            message.setFlag(Flags.Flag.SEEN, true)
            folder.close(true)
            store.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun emptyTrash(email: String, password: String, host: String): Boolean {
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)
            val trashFolder = store.defaultFolder.list().find { it.name.lowercase().contains("trash") }
            if (trashFolder != null) {
                trashFolder.open(Folder.READ_WRITE)
                val messages = trashFolder.messages
                for (msg in messages) {
                    msg.setFlag(Flags.Flag.DELETED, true)
                }
                trashFolder.expunge()
                trashFolder.close(true)
            }
            store.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun saveSentEmail(
        email: String,
        password: String,
        host: String,
        message: MimeMessage
    ) {
        val properties = getImapProperties(host)

        try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)
            val sentFolder = store.defaultFolder.list().find { it.name.lowercase().contains("sent") }
                ?: store.getFolder("Sent")
            if (!sentFolder.exists()) sentFolder.create(Folder.HOLDS_MESSAGES)
            sentFolder.appendMessages(arrayOf(message))
            store.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSmtpProperties(host: String, port: String): Properties {
        val properties = Properties()
        properties["mail.smtp.auth"] = "true"
        properties["mail.smtp.host"] = host
        properties["mail.smtp.port"] = port
        properties["mail.smtp.connectiontimeout"] = "10000"
        properties["mail.smtp.timeout"] = "10000"

        if (port == "465") {
            properties["mail.smtp.socketFactory.port"] = port
            properties["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
            properties["mail.smtp.socketFactory.fallback"] = "false"
            properties["mail.smtp.ssl.enable"] = "true"
        } else {
            properties["mail.smtp.starttls.enable"] = "true"
        }
        return properties
    }

    fun sendEmail(
        email: String,
        password: String,
        smtpHost: String,
        smtpPort: String,
        senderName: String,
        to: String,
        subject: String,
        content: String,
        isHtml: Boolean = false,
        imapHost: String? = null,
        cc: String? = null
    ): Boolean {
        val properties = getSmtpProperties(smtpHost, smtpPort)

        val session = Session.getInstance(properties, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(email, password)
            }
        })

        return try {
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(email, senderName))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            cc?.let {
                if (it.isNotEmpty()) {
                    message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(it))
                }
            }
            message.subject = subject
            if (isHtml) {
                message.setContent(content, "text/html; charset=utf-8")
            } else {
                message.setText(content)
            }
            Transport.send(message)
            
            if (imapHost != null) {
                saveSentEmail(email, password, imapHost, message)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
