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
                val (text, html, _) = if (fetchContent) {
                    val uid = if (folder is IMAPFolder) folder.getUID(msg) else -1L
                    getContent(msg, uid, folderName, errorReadingContentString)
                } else {
                    Triple("", null, emptyList())
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

    fun fetchFlagsByUids(
        email: String,
        password: String,
        host: String,
        folderName: String,
        uids: List<Long>
    ): Map<Long, Boolean> {
        if (uids.isEmpty()) return emptyMap()
        
        val properties = getImapProperties(host)
        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)

            val messages = folder.getMessagesByUID(uids.toLongArray())
            
            val fp = FetchProfile()
            fp.add(FetchProfile.Item.FLAGS)
            fp.add(UIDFolder.FetchProfileItem.UID)
            folder.fetch(messages, fp)

            val result = messages.associate { msg ->
                folder.getUID(msg) to msg.flags.contains(Flags.Flag.SEEN)
            }

            folder.close(false)
            store.close()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
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
    ): Triple<String, String?, List<Attachment>> {
        val properties = getImapProperties(host)
        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)

            val msg = folder.getMessageByUID(uid)
            val result = if (msg != null) getContent(msg, uid, folderName, errorReadingContentString) 
                         else Triple(errorReadingContentString, null, emptyList())

            folder.close(false)
            store.close()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            Triple(errorReadingContentString, null, emptyList())
        }
    }

    private fun getContent(message: Message, uid: Long, folderName: String, errorReadingContentString: String): Triple<String, String?, List<Attachment>> {
        return try {
            val textBuilder = StringBuilder()
            val htmlBuilder = StringBuilder()
            val images = mutableMapOf<String, String>()
            val attachments = mutableListOf<Attachment>()
            extractContent(message, uid, folderName, "", textBuilder, htmlBuilder, images, attachments)
            
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
                        // More robust regex to handle various CID formats in src attributes
                        val encodedCid = cid.replace("@", "%40")
                        val pattern = "cid:<?(${Regex.escape(cid)}|${Regex.escape(encodedCid)})>?".toRegex(RegexOption.IGNORE_CASE)
                        updatedHtml = updatedHtml.replace(pattern, base64)
                    }
                    html = updatedHtml
                }
            }
            
            Triple(text, html, attachments)
        } catch (e: Exception) {
            Triple(errorReadingContentString, null, emptyList())
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

    private fun extractContent(
        part: Part, 
        uid: Long, 
        folderName: String, 
        partPath: String,
        text: StringBuilder, 
        html: StringBuilder, 
        images: MutableMap<String, String>,
        attachments: MutableList<Attachment>
    ) {
        try {
            val disposition = try { part.disposition } catch (e: Exception) { null }
            val fileName = try { part.fileName } catch (e: Exception) { null }
            
            // Check if this part has a Content-ID (inline image)
            val cid = part.getHeader("Content-ID")?.firstOrNull()?.trim()?.removeSurrounding("<", ">")

            val isAttachment = disposition?.equals(Part.ATTACHMENT, ignoreCase = true) == true || 
                              (disposition?.equals(Part.INLINE, ignoreCase = true) == true && cid == null && fileName != null)
            
            if (isAttachment && fileName != null) {
                attachments.add(
                    Attachment(
                        emailUid = uid,
                        folder = folderName,
                        fileName = fileName,
                        mimeType = part.contentType.substringBefore(";"),
                        size = part.size.toLong(),
                        partIndex = partPath
                    )
                )
                return // Don't process content of attachments as body text
            }

            if (part.isMimeType("text/plain")) {
                val content = try { part.content } catch (e: Exception) { null }
                if (content is String) {
                    text.append(content)
                } else {
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
                    val nextPath = if (partPath.isEmpty()) "$i" else "$partPath.$i"
                    extractContent(multiPart.getBodyPart(i), uid, folderName, nextPath, text, html, images, attachments)
                }
            } else if (part.isMimeType("message/rfc822")) {
                val content = part.content
                if (content is Part) {
                    extractContent(content, uid, folderName, partPath, text, html, images, attachments)
                }
            } else if (part.isMimeType("image/*")) {
                val imageCid = cid ?: part.getHeader("Content-ID")?.firstOrNull()?.removeSurrounding("<", ">")
                if (imageCid != null) {
                    try {
                        val inputStream = part.inputStream
                        val bytes = inputStream.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        images[imageCid] = "data:${part.contentType.substringBefore(";")};base64,$base64"
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (fileName != null) {
                    // Inline image without CID, treat as attachment
                    attachments.add(
                        Attachment(
                            emailUid = uid,
                            folder = folderName,
                            fileName = fileName,
                            mimeType = part.contentType.substringBefore(";"),
                            size = part.size.toLong(),
                            partIndex = partPath
                        )
                    )
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
            
            val folders = store.defaultFolder.list().map { folder ->
                if (folder is IMAPFolder) {
                    try {
                        // STATUS command is efficient for getting counts without opening the folder
                        // Some servers might not support all attributes, but message count and unread are standard
                        folder.messageCount
                        val unread = folder.unreadMessageCount
                        FolderInfo(folder.fullName, folder.messageCount, unread)
                    } catch (e: Exception) {
                        // Fallback if STATUS fails
                        folder.open(Folder.READ_ONLY)
                        val info = FolderInfo(folder.fullName, folder.messageCount, folder.unreadMessageCount)
                        folder.close(false)
                        info
                    }
                } else {
                    folder.open(Folder.READ_ONLY)
                    val info = FolderInfo(folder.fullName, folder.messageCount, folder.unreadMessageCount)
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
        context: android.content.Context,
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
        cc: String? = null,
        attachments: List<android.net.Uri> = emptyList()
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

            if (attachments.isEmpty()) {
                if (isHtml) {
                    message.setContent(content, "text/html; charset=utf-8")
                } else {
                    message.setText(content)
                }
            } else {
                val multipart = MimeMultipart()

                // Body part
                val messageBodyPart = javax.mail.internet.MimeBodyPart()
                if (isHtml) {
                    messageBodyPart.setContent(content, "text/html; charset=utf-8")
                } else {
                    messageBodyPart.setText(content)
                }
                multipart.addBodyPart(messageBodyPart)

                // Attachment parts
                for (uri in attachments) {
                    val attachmentBodyPart = javax.mail.internet.MimeBodyPart()
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: continue
                    val dataSource = javax.mail.util.ByteArrayDataSource(bytes, context.contentResolver.getType(uri) ?: "application/octet-stream")
                    attachmentBodyPart.dataHandler = javax.activation.DataHandler(dataSource)
                    
                    // Get filename from URI
                    var fileName = "attachment"
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) fileName = it.getString(nameIndex)
                        }
                    }
                    attachmentBodyPart.fileName = fileName
                    multipart.addBodyPart(attachmentBodyPart)
                }
                message.setContent(multipart)
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

    fun fetchAttachment(
        email: String,
        password: String,
        host: String,
        folderName: String,
        uid: Long,
        partIndex: String,
        outputStream: java.io.OutputStream
    ): Boolean {
        val properties = getImapProperties(host)
        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = store.getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_ONLY)

            val msg = folder.getMessageByUID(uid)
            if (msg == null) {
                folder.close(false)
                store.close()
                return false
            }

            val part = findPartByIndex(msg, partIndex)
            if (part != null) {
                part.inputStream.use { input ->
                    input.copyTo(outputStream)
                }
                folder.close(false)
                store.close()
                true
            } else {
                folder.close(false)
                store.close()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun findPartByIndex(part: Part, index: String): Part? {
        if (index.isEmpty()) return part
        
        val parts = index.split(".")
        var currentPart = part
        
        for (p in parts) {
            val idx = p.toIntOrNull() ?: return null
            val content = currentPart.content
            if (content is MimeMultipart) {
                if (idx < content.count) {
                    currentPart = content.getBodyPart(idx)
                } else {
                    return null
                }
            } else {
                return null
            }
        }
        return currentPart
    }
}
