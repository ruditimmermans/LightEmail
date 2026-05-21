package com.light.lightemail.data

import com.sun.mail.imap.IMAPFolder
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.search.FlagTerm

data class FolderInfo(val name: String, val messageCount: Int, val unreadCount: Int)

class ImapManager {
    fun fetchEmails(
        email: String,
        password: String,
        host: String,
        folderName: String = "Inbox",
        limit: Int = 20,
        noSubjectString: String = "(No Subject)",
        unknownSenderString: String = "Unknown",
        errorReadingContentString: String = "Error reading content"
    ): List<EmailMessage> {
        val properties = Properties()
        properties["mail.store.protocol"] = "imaps"
        properties["mail.imaps.host"] = host
        properties["mail.imaps.port"] = "993"
        properties["mail.imaps.ssl.enable"] = "true"

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val folder = store.getFolder(folderName)
            folder.open(Folder.READ_ONLY)

            val messages = folder.messages
            val lastMessages = messages.takeLast(limit).toTypedArray()
            
            // Optimize fetching by using a FetchProfile
            val fp = FetchProfile()
            fp.add(FetchProfile.Item.ENVELOPE)
            fp.add(FetchProfile.Item.FLAGS)
            fp.add(FetchProfile.Item.CONTENT_INFO)
            folder.fetch(lastMessages, fp)

            val result = lastMessages.reversedArray().map { msg ->
                val (text, html) = getContent(msg, errorReadingContentString)
                EmailMessage(
                    id = msg.messageNumber.toString(),
                    uid = if (folder is IMAPFolder) folder.getUID(msg) else -1L,
                    subject = msg.subject ?: noSubjectString,
                    sender = msg.from?.firstOrNull()?.toString() ?: unknownSenderString,
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
            emptyList()
        }
    }

    private fun getContent(message: Message, errorReadingContentString: String): Pair<String, String?> {
        return try {
            val textBuilder = StringBuilder()
            val htmlBuilder = StringBuilder()
            val images = mutableMapOf<String, String>()
            extractContent(message, textBuilder, htmlBuilder, images)
            
            val text = textBuilder.toString().ifEmpty { errorReadingContentString }
            var html = htmlBuilder.toString().ifEmpty { null }
            
            if (html != null && images.isNotEmpty()) {
                for ((cid, base64) in images) {
                    html = html!!.replace("cid:$cid", base64)
                }
            }
            
            Pair(text, html)
        } catch (e: Exception) {
            Pair(errorReadingContentString, null)
        }
    }

    private fun extractContent(part: Part, text: StringBuilder, html: StringBuilder, images: MutableMap<String, String>) {
        if (part.isMimeType("text/plain")) {
            text.append(part.content.toString())
        } else if (part.isMimeType("text/html")) {
            html.append(part.content.toString())
        } else if (part.isMimeType("multipart/*")) {
            val multiPart = part.content as MimeMultipart
            for (i in 0 until multiPart.count) {
                extractContent(multiPart.getBodyPart(i), text, html, images)
            }
        } else if (part.isMimeType("image/*")) {
            val cid = part.getHeader("Content-ID")?.firstOrNull()?.removeSurrounding("<", ">")
            if (cid != null) {
                val inputStream = part.inputStream
                val bytes = inputStream.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                images[cid] = "data:${part.contentType.substringBefore(";")};base64,$base64"
            }
        }
    }

    fun fetchFolders(email: String, password: String, host: String): List<FolderInfo> {
        val properties = Properties()
        properties["mail.store.protocol"] = "imaps"
        properties["mail.imaps.host"] = host
        properties["mail.imaps.port"] = "993"
        properties["mail.imaps.ssl.enable"] = "true"

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)
            val folders = store.defaultFolder.list("*").map { folder ->
                folder.open(Folder.READ_ONLY)
                val count = folder.messageCount
                val unread = folder.unreadMessageCount
                val info = FolderInfo(folder.fullName, count, unread)
                folder.close(false)
                info
            }
            store.close()
            folders
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun deleteEmail(email: String, password: String, host: String, folderName: String, messageId: Int): Boolean {
        val properties = Properties()
        properties["mail.store.protocol"] = "imaps"
        properties["mail.imaps.host"] = host
        properties["mail.imaps.port"] = "993"
        properties["mail.imaps.ssl.enable"] = "true"

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
        val properties = Properties()
        properties["mail.store.protocol"] = "imaps"
        properties["mail.imaps.host"] = host
        properties["mail.imaps.port"] = "993"
        properties["mail.imaps.ssl.enable"] = "true"

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
        val properties = Properties()
        properties["mail.store.protocol"] = "imaps"
        properties["mail.imaps.host"] = host
        properties["mail.imaps.port"] = "993"
        properties["mail.imaps.ssl.enable"] = "true"

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
        val properties = Properties()
        properties["mail.store.protocol"] = "imaps"
        properties["mail.imaps.host"] = host
        properties["mail.imaps.port"] = "993"
        properties["mail.imaps.ssl.enable"] = "true"

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
        imapHost: String? = null
    ): Boolean {
        val properties = Properties()
        properties["mail.smtp.auth"] = "true"
        properties["mail.smtp.host"] = smtpHost
        properties["mail.smtp.port"] = smtpPort

        if (smtpPort == "465") {
            properties["mail.smtp.socketFactory.port"] = smtpPort
            properties["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
            properties["mail.smtp.socketFactory.fallback"] = "false"
            properties["mail.smtp.ssl.enable"] = "true"
        } else {
            properties["mail.smtp.starttls.enable"] = "true"
        }

        val session = Session.getInstance(properties, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(email, password)
            }
        })

        return try {
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(email, senderName))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
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
