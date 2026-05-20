package com.light.lightemail.data

import com.sun.mail.imap.IMAPFolder
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.search.FlagTerm

class ImapManager {
    fun fetchEmails(
        email: String,
        password: String,
        host: String,
        folderName: String = "INBOX",
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
            val result = messages.takeLast(limit).reversed().map { msg ->
                val (text, html) = getContent(msg, errorReadingContentString)
                EmailMessage(
                    id = msg.messageNumber.toString(),
                    uid = if (folder is IMAPFolder) folder.getUID(msg) else -1L,
                    subject = msg.subject ?: noSubjectString,
                    sender = msg.from?.firstOrNull()?.toString() ?: unknownSenderString,
                    content = text,
                    htmlContent = html,
                    date = msg.sentDate?.toString() ?: "",
                    folder = folderName
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
            extractContent(message, textBuilder, htmlBuilder)
            
            val text = textBuilder.toString().ifEmpty { errorReadingContentString }
            val html = htmlBuilder.toString().ifEmpty { null }
            Pair(text, html)
        } catch (e: Exception) {
            Pair(errorReadingContentString, null)
        }
    }

    private fun extractContent(part: Part, text: StringBuilder, html: StringBuilder) {
        if (part.isMimeType("text/plain")) {
            text.append(part.content.toString())
        } else if (part.isMimeType("text/html")) {
            html.append(part.content.toString())
        } else if (part.isMimeType("multipart/*")) {
            val multiPart = part.content as MimeMultipart
            for (i in 0 until multiPart.count) {
                extractContent(multiPart.getBodyPart(i), text, html)
            }
        }
    }

    fun fetchFolders(email: String, password: String, host: String): List<String> {
        val properties = Properties()
        properties["mail.store.protocol"] = "imaps"
        properties["mail.imaps.host"] = host
        properties["mail.imaps.port"] = "993"
        properties["mail.imaps.ssl.enable"] = "true"

        return try {
            val session = Session.getInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)
            val folders = store.defaultFolder.list("*").map { it.fullName }
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
            message.setFlag(Flags.Flag.DELETED, true)
            folder.close(true)
            store.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
        isHtml: Boolean = false
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
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
