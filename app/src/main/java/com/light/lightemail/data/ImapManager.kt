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
        // Prevent sockets from hanging indefinitely and causing ANRs
        properties["mail.imaps.connectiontimeout"] = "10000" // 10s
        properties["mail.imaps.timeout"] = "10000" // 10s
        properties["mail.imaps.writetimeout"] = "10000" // 10s
        properties["mail.imaps.compress.enable"] = "true" // Enable compression
        properties["mail.imaps.partialfetch"] = "false"
        properties["mail.imaps.fetchsize"] = "2097152" // Increase fetch size to 2MB for faster and more complete transfers
        properties["mail.imaps.connectionpoolsize"] = "5"
        properties["mail.imaps.connectionpooltimeout"] = "60000" // 1 min
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

            folder = getFolderSafely(store, folderName) as? IMAPFolder
            if (folder == null) return
            folder.open(Folder.READ_ONLY)

            if (store is com.sun.mail.imap.IMAPStore && !store.hasCapability("IDLE")) {
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

            folder.addMessageChangedListener(object : MessageChangedListener {
                override fun messageChanged(e: MessageChangedEvent) {
                    onFolderChanged()
                }
            })

            while (!Thread.currentThread().isInterrupted && store.isConnected) {
                try {
                    if (!folder.isOpen) folder.open(Folder.READ_ONLY)
                    folder.idle()
                } catch (e: Exception) {
                    if (e is InterruptedException || Thread.currentThread().isInterrupted || !store.isConnected) break
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
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
        fetchContent: Boolean = false
    ): List<EmailMessage> {
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = getFolderSafely(store, folderName) ?: return emptyList()
            folder.open(Folder.READ_ONLY)

            val totalMessages = folder.messageCount
            if (totalMessages == 0) {
                try { folder.close(false) } catch (e: Exception) {}
                try { store.close() } catch (e: Exception) {}
                return emptyList()
            }

            val start = (totalMessages - limit + 1).coerceAtLeast(1)
            val end = totalMessages
            val messages = folder.getMessages(start, end)
            
            val lastMessages = messages.filterNotNull().filter { msg ->
                try {
                    !msg.flags.contains(Flags.Flag.DELETED)
                } catch (e: Exception) {
                    false
                }
            }.toTypedArray()
            
            val fp = FetchProfile()
            fp.add(FetchProfile.Item.ENVELOPE)
            fp.add(FetchProfile.Item.FLAGS)
            if (fetchContent) {
                fp.add(FetchProfile.Item.CONTENT_INFO)
            }
            if (folder is IMAPFolder) {
                fp.add(UIDFolder.FetchProfileItem.UID)
            }
            try {
                folder.fetch(lastMessages, fp)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val result = lastMessages.reversedArray().mapNotNull { msg ->
                try {
                    val uid = if (folder is IMAPFolder) {
                        try { folder.getUID(msg) } catch (e: Exception) { -1L }
                    } else -1L

                    val (text, html, atts) = if (fetchContent && uid > 0) {
                        getContent(msg, uid, folderName, errorReadingContentString)
                    } else {
                        Triple("", null, emptyList())
                    }

                    val subject = try { msg.subject } catch (e: Exception) { null } ?: noSubjectString
                    val sender = try { msg.from?.firstOrNull()?.toString() } catch (e: Exception) { null } ?: unknownSenderString
                    val replyTo = try { msg.replyTo?.firstOrNull()?.toString() } catch (e: Exception) { null }
                    val toRecipients = try { msg.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { it.toString() } } catch (e: Exception) { null }
                    val ccRecipients = try { msg.getRecipients(Message.RecipientType.CC)?.joinToString(", ") { it.toString() } } catch (e: Exception) { null }
                    val date = try { msg.sentDate?.toString() } catch (e: Exception) { null } ?: ""
                    val isRead = try { msg.flags.contains(Flags.Flag.SEEN) } catch (e: Exception) { false }
                    val hasAttachments = atts.isNotEmpty() || try { checkHasAttachments(msg) } catch (e: Exception) { false }
                    val id = try { msg.messageNumber.toString() } catch (e: Exception) { uid.toString() }

                    EmailMessage(
                        id = id,
                        uid = uid,
                        subject = subject,
                        sender = sender,
                        replyTo = replyTo,
                        toRecipients = toRecipients,
                        ccRecipients = ccRecipients,
                        content = text,
                        htmlContent = html,
                        date = date,
                        folder = folderName,
                        isRead = isRead,
                        hasAttachments = hasAttachments
                    )
                } catch (e: Exception) {
                    null
                }
            }

            try { folder.close(false) } catch (e: Exception) {}
            try { store.close() } catch (e: Exception) {}
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun fetchLatestUids(
        email: String,
        password: String,
        host: String,
        folderName: String = "Inbox",
        limit: Int = 50
    ): List<Long>? {
        val properties = getImapProperties(host)
        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = (getFolderSafely(store, folderName) as? IMAPFolder) ?: return null
            folder.open(Folder.READ_ONLY)

            val totalMessages = folder.messageCount
            if (totalMessages == 0) {
                try { folder.close(false) } catch (e: Exception) {}
                try { store.close() } catch (e: Exception) {}
                return emptyList()
            }

            val start = (totalMessages - limit + 1).coerceAtLeast(1)
            val end = totalMessages
            val messages = folder.getMessages(start, end)
            val validMessages = messages.filterNotNull().toTypedArray()

            if (validMessages.isNotEmpty()) {
                val fp = FetchProfile()
                fp.add(FetchProfile.Item.FLAGS)
                fp.add(UIDFolder.FetchProfileItem.UID)
                try {
                    folder.fetch(validMessages, fp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val uids = validMessages.filter { msg ->
                try {
                    !msg.flags.contains(Flags.Flag.DELETED)
                } catch (e: Exception) {
                    true
                }
            }.mapNotNull { msg ->
                try {
                    val uid = folder.getUID(msg)
                    if (uid > 0) uid else null
                } catch (e: Exception) {
                    null
                }
            }.reversed()
            
            try { folder.close(false) } catch (e: Exception) {}
            try { store.close() } catch (e: Exception) {}
            uids
        } catch (e: Exception) {
            e.printStackTrace()
            null
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

            val folder = (getFolderSafely(store, folderName) as? IMAPFolder) ?: return emptyMap()
            folder.open(Folder.READ_ONLY)

            val messages = folder.getMessagesByUID(uids.toLongArray())
            val validMessages = messages.filterNotNull().toTypedArray()

            if (validMessages.isNotEmpty()) {
                val fp = FetchProfile()
                fp.add(FetchProfile.Item.FLAGS)
                fp.add(UIDFolder.FetchProfileItem.UID)
                try {
                    folder.fetch(validMessages, fp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val result = validMessages.mapNotNull { msg ->
                try {
                    val uid = folder.getUID(msg)
                    if (uid <= 0) return@mapNotNull null
                    val flags = try { msg.flags } catch (e: Exception) { null }
                    val isRead = flags?.contains(Flags.Flag.SEEN) == true
                    uid to isRead
                } catch (e: Exception) {
                    null
                }
            }.toMap()

            try { folder.close(false) } catch (e: Exception) {}
            try { store.close() } catch (e: Exception) {}
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

            val folder = (getFolderSafely(store, folderName) as? IMAPFolder) ?: return emptyList()
            folder.open(Folder.READ_ONLY)

            val messages = folder.getMessagesByUID(uids.toLongArray())
            val validMessages = messages.filterNotNull().toTypedArray()

            if (validMessages.isNotEmpty()) {
                val fp = FetchProfile()
                fp.add(FetchProfile.Item.ENVELOPE)
                fp.add(FetchProfile.Item.FLAGS)
                fp.add(UIDFolder.FetchProfileItem.UID)
                try {
                    folder.fetch(validMessages, fp)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val result = validMessages.mapNotNull { msg ->
                try {
                    val uid = folder.getUID(msg)
                    if (uid <= 0) return@mapNotNull null

                    val flags = try { msg.flags } catch (e: Exception) { null }
                    if (flags?.contains(Flags.Flag.DELETED) == true) return@mapNotNull null

                    val subject = try { msg.subject } catch (e: Exception) { null } ?: noSubjectString
                    val sender = try { msg.from?.firstOrNull()?.toString() } catch (e: Exception) { null } ?: unknownSenderString
                    val replyTo = try { msg.replyTo?.firstOrNull()?.toString() } catch (e: Exception) { null }
                    val toRecipients = try { msg.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { it.toString() } } catch (e: Exception) { null }
                    val ccRecipients = try { msg.getRecipients(Message.RecipientType.CC)?.joinToString(", ") { it.toString() } } catch (e: Exception) { null }
                    val date = try { msg.sentDate?.toString() } catch (e: Exception) { null } ?: ""
                    val isRead = flags?.contains(Flags.Flag.SEEN) == true
                    val hasAttachments = try { checkHasAttachments(msg) } catch (e: Exception) { false }
                    val messageNumber = try { msg.messageNumber.toString() } catch (e: Exception) { uid.toString() }

                    EmailMessage(
                        id = messageNumber,
                        uid = uid,
                        subject = subject,
                        sender = sender,
                        replyTo = replyTo,
                        toRecipients = toRecipients,
                        ccRecipients = ccRecipients,
                        content = "",
                        htmlContent = null,
                        date = date,
                        folder = folderName,
                        isRead = isRead,
                        hasAttachments = hasAttachments
                    )
                } catch (e: Exception) {
                    null
                }
            }.reversed()

            try { folder.close(false) } catch (e: Exception) {}
            try { store.close() } catch (e: Exception) {}
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

            val folder = getFolderSafely(store, folderName) ?: return emptyList()
            folder.open(Folder.READ_ONLY)

            val unreadMessages = folder.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
            
            if (unreadMessages.isEmpty()) {
                try { folder.close(false) } catch (e: Exception) {}
                try { store.close() } catch (e: Exception) {}
                return emptyList()
            }

            val lastUnread = unreadMessages.filterNotNull().reversed().take(limit).toTypedArray()
            
            val fp = FetchProfile()
            fp.add(FetchProfile.Item.ENVELOPE)
            fp.add(FetchProfile.Item.FLAGS)
            if (folder is IMAPFolder) {
                fp.add(UIDFolder.FetchProfileItem.UID)
            }
            try {
                folder.fetch(lastUnread, fp)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val result = lastUnread.mapNotNull { msg ->
                try {
                    val uid = if (folder is IMAPFolder) {
                        try { folder.getUID(msg) } catch (e: Exception) { -1L }
                    } else -1L

                    val subject = try { msg.subject } catch (e: Exception) { null } ?: noSubjectString
                    val sender = try { msg.from?.firstOrNull()?.toString() } catch (e: Exception) { null } ?: unknownSenderString
                    val replyTo = try { msg.replyTo?.firstOrNull()?.toString() } catch (e: Exception) { null }
                    val toRecipients = try { msg.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { it.toString() } } catch (e: Exception) { null }
                    val ccRecipients = try { msg.getRecipients(Message.RecipientType.CC)?.joinToString(", ") { it.toString() } } catch (e: Exception) { null }
                    val date = try { msg.sentDate?.toString() } catch (e: Exception) { null } ?: ""
                    val hasAttachments = try { checkHasAttachments(msg) } catch (e: Exception) { false }
                    val id = try { msg.messageNumber.toString() } catch (e: Exception) { uid.toString() }

                    EmailMessage(
                        id = id,
                        uid = uid,
                        subject = subject,
                        sender = sender,
                        replyTo = replyTo,
                        toRecipients = toRecipients,
                        ccRecipients = ccRecipients,
                        content = "",
                        htmlContent = null,
                        date = date,
                        folder = folderName,
                        isRead = false,
                        hasAttachments = hasAttachments
                    )
                } catch (e: Exception) {
                    null
                }
            }

            try { folder.close(false) } catch (e: Exception) {}
            try { store.close() } catch (e: Exception) {}
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun checkHasAttachments(msg: Message): Boolean {
        return try {
            val contentType = msg.contentType?.lowercase() ?: ""
            contentType.contains("multipart/mixed") || contentType.contains("multipart/related") ||
            contentType.contains("attachment") || msg.isMimeType("multipart/mixed")
        } catch (e: Exception) {
            false
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

            val folder = (getFolderSafely(store, folderName) as? IMAPFolder)
            if (folder == null) return Triple(errorReadingContentString, null, emptyList())
            folder.open(Folder.READ_ONLY)

            val msg = try { folder.getMessageByUID(uid) } catch (e: Exception) { null }
            val result = if (msg != null) {
                getContent(msg, uid, folderName, errorReadingContentString)
            } else {
                Triple(errorReadingContentString, null, emptyList())
            }

            try { folder.close(false) } catch (e: Exception) {}
            try { store.close() } catch (e: Exception) {}
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
        return html.replace(Regex("(?s)<style.*?>.*?</style>"), "")
            .replace(Regex("(?s)<script.*?>.*?</script>"), "")
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
            val contentType = try { part.contentType } catch (e: Exception) { null }

            val cid = try { part.getHeader("Content-ID")?.firstOrNull()?.trim()?.removeSurrounding("<", ">") } catch (e: Exception) { null }

            val isCalendar = (try { part.isMimeType("text/calendar") } catch (e: Exception) { false }) ||
                             (try { part.isMimeType("application/ics") } catch (e: Exception) { false }) ||
                             fileName?.lowercase()?.endsWith(".ics") == true
            val effectiveFileName = fileName ?: if (isCalendar) "invite.ics" else null

            val isAttachment = disposition?.equals(Part.ATTACHMENT, ignoreCase = true) == true || 
                              (disposition?.equals(Part.INLINE, ignoreCase = true) == true && cid == null && effectiveFileName != null) ||
                              isCalendar
            
            if (isAttachment && effectiveFileName != null) {
                val mime = if (isCalendar) "text/calendar" else (contentType?.substringBefore(";") ?: "application/octet-stream")
                val size = try { part.size.toLong().coerceAtLeast(0L) } catch (e: Exception) { 0L }
                attachments.add(
                    Attachment(
                        emailUid = uid,
                        folder = folderName,
                        fileName = effectiveFileName,
                        mimeType = mime,
                        size = size,
                        partIndex = partPath
                    )
                )
                return
            }

            val isTextPlain = try { part.isMimeType("text/plain") } catch (e: Exception) { false }
            val isTextHtml = try { part.isMimeType("text/html") } catch (e: Exception) { false }
            val isMultipart = try { part.isMimeType("multipart/*") } catch (e: Exception) { false }
            val isRfc822 = try { part.isMimeType("message/rfc822") } catch (e: Exception) { false }
            val isImage = try { part.isMimeType("image/*") } catch (e: Exception) { false }

            if (isTextPlain) {
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
            } else if (isTextHtml) {
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
            } else if (isMultipart) {
                val multiPart = try { part.content as? MimeMultipart } catch (e: Exception) { null }
                if (multiPart != null) {
                    val count = try { multiPart.count } catch (e: Exception) { 0 }
                    for (i in 0 until count) {
                        val bodyPart = try { multiPart.getBodyPart(i) } catch (e: Exception) { null } ?: continue
                        val nextPath = if (partPath.isEmpty()) "$i" else "$partPath.$i"
                        extractContent(bodyPart, uid, folderName, nextPath, text, html, images, attachments)
                    }
                }
            } else if (isRfc822) {
                val content = try { part.content } catch (e: Exception) { null }
                if (content is Part) {
                    extractContent(content, uid, folderName, partPath, text, html, images, attachments)
                }
            } else if (isImage) {
                val imageCid = cid ?: try { part.getHeader("Content-ID")?.firstOrNull()?.removeSurrounding("<", ">") } catch (e: Exception) { null }
                if (imageCid != null) {
                    try {
                        val inputStream = part.inputStream
                        val bytes = inputStream.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val mime = contentType?.substringBefore(";") ?: "image/jpeg"
                        images[imageCid] = "data:$mime;base64,$base64"
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (fileName != null) {
                    val mime = contentType?.substringBefore(";") ?: "image/jpeg"
                    val size = try { part.size.toLong().coerceAtLeast(0L) } catch (e: Exception) { 0L }
                    attachments.add(
                        Attachment(
                            emailUid = uid,
                            folder = folderName,
                            fileName = fileName,
                            mimeType = mime,
                            size = size,
                            partIndex = partPath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAllFolders(store: Store): Array<Folder> {
        return try {
            store.defaultFolder.list("*")
        } catch (e: Exception) {
            try {
                store.defaultFolder.list()
            } catch (e2: Exception) {
                emptyArray()
            }
        }
    }

    private fun getFolderSafely(store: Store, folderName: String): Folder? {
        return try {
            val folder = store.getFolder(folderName)
            if (folder.exists()) {
                folder
            } else {
                val allFolders = getAllFolders(store)
                val exactOrNameMatch = allFolders.find { 
                    it.fullName.equals(folderName, ignoreCase = true) || 
                    it.name.equals(folderName, ignoreCase = true) 
                }
                if (exactOrNameMatch != null) return exactOrNameMatch

                val lower = folderName.lowercase()
                when {
                    lower.contains("trash") || lower.contains("bin") || lower.contains("deleted") || lower.contains("papierkorb") || lower.contains("corbeille") -> findTrashFolder(store)
                    lower.contains("sent") || lower.contains("gesendet") || lower.contains("envoyés") -> findSentFolder(store)
                    lower.contains("draft") || lower.contains("brouillon") || lower.contains("entwürfe") -> findDraftsFolder(store)
                    lower.contains("inbox") -> store.getFolder("INBOX")
                    else -> folder
                }
            }
        } catch (e: Exception) {
            try { store.getFolder(folderName) } catch (e2: Exception) { null }
        }
    }

    private fun findTrashFolder(store: Store): Folder? {
        return try {
            val allFolders = getAllFolders(store)
            allFolders.find { folder ->
                val name = folder.name.lowercase()
                val fullName = folder.fullName.lowercase()
                name.contains("trash") || fullName.contains("trash") || 
                name.contains("bin") || fullName.contains("bin") || 
                name.contains("deleted") || fullName.contains("deleted") || 
                name.contains("papierkorb") || fullName.contains("papierkorb") || 
                name.contains("corbeille") || fullName.contains("corbeille")
            } ?: store.getFolder("Trash")
        } catch (e: Exception) {
            try { store.getFolder("Trash") } catch (e2: Exception) { null }
        }
    }

    private fun findSentFolder(store: Store): Folder? {
        return try {
            val allFolders = getAllFolders(store)
            allFolders.find { folder ->
                val name = folder.name.lowercase()
                val fullName = folder.fullName.lowercase()
                name.contains("sent") || fullName.contains("sent") || 
                name.contains("gesendet") || fullName.contains("gesendet") || 
                name.contains("envoyés") || fullName.contains("envoyés")
            } ?: store.getFolder("Sent")
        } catch (e: Exception) {
            try { store.getFolder("Sent") } catch (e2: Exception) { null }
        }
    }

    private fun findDraftsFolder(store: Store): Folder? {
        return try {
            val allFolders = getAllFolders(store)
            allFolders.find { folder ->
                val name = folder.name.lowercase()
                val fullName = folder.fullName.lowercase()
                name.contains("draft") || fullName.contains("draft") || 
                name.contains("brouillon") || fullName.contains("brouillon") || 
                name.contains("entwürfe") || fullName.contains("entwürfe")
            } ?: store.getFolder("Drafts")
        } catch (e: Exception) {
            try { store.getFolder("Drafts") } catch (e2: Exception) { null }
        }
    }

    fun isTrashFolder(folderName: String, folder: Folder?, trashFolder: Folder?): Boolean {
        if (trashFolder != null && folder != null && trashFolder.fullName.equals(folder.fullName, ignoreCase = true)) {
            return true
        }
        val nameLower = folderName.lowercase()
        val fullNameLower = folder?.fullName?.lowercase() ?: ""
        val nameShortLower = folder?.name?.lowercase() ?: ""
        val trashKeywords = listOf("trash", "bin", "deleted", "papierkorb", "corbeille", "rubbish")
        return trashKeywords.any { 
            nameLower.contains(it) || fullNameLower.contains(it) || nameShortLower.contains(it) 
        }
    }

    fun fetchFolders(email: String, password: String, host: String): List<FolderInfo> {
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)
            
            val rawFolders = getAllFolders(store)
            val folders = rawFolders.mapNotNull { folder ->
                try {
                    if ((folder.type and Folder.HOLDS_MESSAGES) == 0) {
                        return@mapNotNull null
                    }
                    val fullName = folder.fullName
                    var messageCount = 0
                    var unreadCount = 0
                    if (folder is IMAPFolder) {
                        try {
                            messageCount = folder.messageCount
                            unreadCount = folder.unreadMessageCount
                        } catch (e: Exception) {
                            try {
                                folder.open(Folder.READ_ONLY)
                                messageCount = folder.messageCount
                                unreadCount = folder.unreadMessageCount
                                folder.close(false)
                            } catch (e2: Exception) {}
                        }
                    } else {
                        try {
                            folder.open(Folder.READ_ONLY)
                            messageCount = folder.messageCount
                            unreadCount = folder.unreadMessageCount
                            folder.close(false)
                        } catch (e2: Exception) {}
                    }
                    FolderInfo(fullName, messageCount, unreadCount)
                } catch (e: Exception) {
                    null
                }
            }
            try { store.close() } catch (e: Exception) {}
            if (folders.none { it.name.equals("Inbox", ignoreCase = true) }) {
                listOf(FolderInfo("Inbox", 0, 0)) + folders
            } else {
                folders
            }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(
                FolderInfo("Inbox", 0, 0),
                FolderInfo("Sent", 0, 0),
                FolderInfo("Drafts", 0, 0),
                FolderInfo("Trash", 0, 0)
            )
        }
    }

    fun deleteEmail(email: String, password: String, host: String, folderName: String, uid: Long): Boolean {
        if (email.isEmpty() || password.isEmpty() || host.isEmpty()) return false
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)
            val folder = getFolderSafely(store, folderName) as? IMAPFolder
            if (folder == null) {
                try { store.close() } catch (e: Exception) {}
                return false
            }
            folder.open(Folder.READ_WRITE)
            val message = try { folder.getMessageByUID(uid) } catch (e: Exception) { null }

            if (message != null) {
                val trashFolder = findTrashFolder(store)
                val inTrash = isTrashFolder(folderName, folder, trashFolder)

                if (inTrash) {
                    try { message.setFlag(Flags.Flag.DELETED, true) } catch (e: Exception) {}
                } else {
                    if (trashFolder != null && !trashFolder.fullName.equals(folder.fullName, ignoreCase = true)) {
                        if (!trashFolder.exists()) {
                            try {
                                trashFolder.create(Folder.HOLDS_MESSAGES)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        try {
                            folder.copyMessages(arrayOf(message), trashFolder)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    try { message.setFlag(Flags.Flag.DELETED, true) } catch (e: Exception) {}
                }
                try {
                    folder.expunge()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            try { folder.close(true) } catch (e: Exception) {}
            try { store.close() } catch (e: Exception) {}
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    fun moveEmail(
        email: String,
        password: String,
        host: String,
        srcFolderName: String,
        targetFolderName: String,
        uid: Long
    ): Boolean {
        if (email.isEmpty() || password.isEmpty() || host.isEmpty()) return false
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val srcFolder = getFolderSafely(store, srcFolderName) as? IMAPFolder
            val targetFolder = getFolderSafely(store, targetFolderName)

            if (srcFolder == null || targetFolder == null) {
                try { store.close() } catch (e: Exception) {}
                return false
            }

            srcFolder.open(Folder.READ_WRITE)
            val message = try { srcFolder.getMessageByUID(uid) } catch (e: Exception) { null }

            if (message != null) {
                if (!targetFolder.exists()) {
                    try {
                        targetFolder.create(Folder.HOLDS_MESSAGES)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (!targetFolder.fullName.equals(srcFolder.fullName, ignoreCase = true)) {
                    try {
                        srcFolder.copyMessages(arrayOf(message), targetFolder)
                        message.setFlag(Flags.Flag.DELETED, true)
                        srcFolder.expunge()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            try { srcFolder.close(true) } catch (e: Exception) {}
            try { store.close() } catch (e: Exception) {}
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    fun markAsRead(email: String, password: String, host: String, folderName: String, uid: Long): Boolean {
        return updateReadStatusOnServer(email, password, host, folderName, uid, true)
    }

    fun updateReadStatusOnServer(email: String, password: String, host: String, folderName: String, uid: Long, isRead: Boolean): Boolean {
        if (email.isEmpty() || password.isEmpty() || host.isEmpty()) return false
        val properties = getImapProperties(host)

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)
            val folder = getFolderSafely(store, folderName) as? IMAPFolder
            if (folder == null) {
                try { store.close() } catch (e: Exception) {}
                return false
            }
            folder.open(Folder.READ_WRITE)
            val message = try { folder.getMessageByUID(uid) } catch (e: Exception) { null }
            try { message?.setFlag(Flags.Flag.SEEN, isRead) } catch (e: Exception) {}
            try { folder.close(true) } catch (e: Exception) {}
            try { store.close() } catch (e: Exception) {}
            true
        } catch (e: Throwable) {
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
            val trashFolder = findTrashFolder(store)
            if (trashFolder != null) {
                trashFolder.open(Folder.READ_WRITE)
                val messages = trashFolder.messages
                for (msg in messages.filterNotNull()) {
                    try { msg.setFlag(Flags.Flag.DELETED, true) } catch (e: Exception) {}
                }
                try { trashFolder.expunge() } catch (e: Exception) {}
                try { trashFolder.close(true) } catch (e: Exception) {}
            }
            try { store.close() } catch (e: Exception) {}
            true
        } catch (e: Throwable) {
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
            val sentFolder = findSentFolder(store) ?: store.getFolder("Sent")
            if (!sentFolder.exists()) {
                try { sentFolder.create(Folder.HOLDS_MESSAGES) } catch (e: Exception) {}
            }
            sentFolder.appendMessages(arrayOf(message))
            try { store.close() } catch (e: Exception) {}
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
        attachments: List<android.net.Uri> = emptyList(),
        bcc: String? = null
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
            bcc?.let {
                if (it.isNotEmpty()) {
                    message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(it))
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

                val messageBodyPart = javax.mail.internet.MimeBodyPart()
                if (isHtml) {
                    messageBodyPart.setContent(content, "text/html; charset=utf-8")
                } else {
                    messageBodyPart.setText(content)
                }
                multipart.addBodyPart(messageBodyPart)

                for (uri in attachments) {
                    val attachmentBodyPart = javax.mail.internet.MimeBodyPart()
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: continue
                    val dataSource = javax.mail.util.ByteArrayDataSource(bytes, context.contentResolver.getType(uri) ?: "application/octet-stream")
                    attachmentBodyPart.dataHandler = javax.activation.DataHandler(dataSource)
                    
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

            val folder = (getFolderSafely(store, folderName) as? IMAPFolder)
            if (folder == null) {
                try { store.close() } catch (e: Exception) {}
                return false
            }
            folder.open(Folder.READ_ONLY)

            val msg = try { folder.getMessageByUID(uid) } catch (e: Exception) { null }
            if (msg == null) {
                try { folder.close(false) } catch (e: Exception) {}
                try { store.close() } catch (e: Exception) {}
                return false
            }

            val part = findPartByIndex(msg, partIndex)
            if (part != null) {
                part.inputStream.use { input ->
                    input.copyTo(outputStream)
                }
                try { folder.close(false) } catch (e: Exception) {}
                try { store.close() } catch (e: Exception) {}
                true
            } else {
                try { folder.close(false) } catch (e: Exception) {}
                try { store.close() } catch (e: Exception) {}
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
            val content = try { currentPart.content } catch (e: Exception) { return null }
            if (content is MimeMultipart) {
                if (idx < content.count) {
                    currentPart = try { content.getBodyPart(idx) } catch (e: Exception) { return null }
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
