package com.light.lightemail.data

import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.search.FlagTerm

class ImapManager {
    fun fetchEmails(email: String, password: String, host: String): List<EmailMessage> {
        val properties = Properties()
        properties["mail.store.protocol"] = "imaps"
        properties["mail.imaps.host"] = host
        properties["mail.imaps.port"] = "993"
        properties["mail.imaps.ssl.enable"] = "true"

        return try {
            val session = Session.getDefaultInstance(properties, null)
            val store = session.getStore("imaps")
            store.connect(host, email, password)

            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)

            val messages = inbox.messages
            val result = messages.takeLast(20).reversed().map { msg ->
                EmailMessage(
                    id = msg.messageNumber.toString(),
                    subject = msg.subject ?: "(No Subject)",
                    sender = msg.from?.firstOrNull()?.toString() ?: "Unknown",
                    content = getTextFromMessage(msg),
                    date = msg.sentDate?.toString() ?: ""
                )
            }

            inbox.close(false)
            store.close()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun getTextFromMessage(message: Message): String {
        return try {
            if (message.isMimeType("text/plain")) {
                message.content.toString()
            } else if (message.isMimeType("multipart/*")) {
                val mimeMultipart = message.content as MimeMultipart
                getTextFromMimeMultipart(mimeMultipart)
            } else {
                message.content.toString()
            }
        } catch (e: Exception) {
            "Error reading content"
        }
    }

    private fun getTextFromMimeMultipart(mimeMultipart: MimeMultipart): String {
        val result = StringBuilder()
        for (i in 0 until mimeMultipart.count) {
            val bodyPart = mimeMultipart.getBodyPart(i)
            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.content)
            } else if (bodyPart.isMimeType("text/html")) {
                // For simplicity, we just take the plain text or ignore HTML
            } else if (bodyPart.content is MimeMultipart) {
                result.append(getTextFromMimeMultipart(bodyPart.content as MimeMultipart))
            }
        }
        return result.toString()
    }

    fun sendReply(
        email: String,
        password: String,
        smtpHost: String,
        originalMessage: EmailMessage,
        replyContent: String,
        signature: String
    ): Boolean {
        val properties = Properties()
        properties["mail.smtp.auth"] = "true"
        properties["mail.smtp.starttls.enable"] = "true"
        properties["mail.smtp.host"] = smtpHost
        properties["mail.smtp.port"] = "587"

        val session = Session.getInstance(properties, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(email, password)
            }
        })

        return try {
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(email))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(originalMessage.sender))
            message.subject = "Re: ${originalMessage.subject}"
            
            val fullBody = "$replyContent\n\n--\n$signature\n\nOn ${originalMessage.date}, ${originalMessage.sender} wrote:\n> ${originalMessage.content.replace("\n", "\n> ")}"
            message.setText(fullBody)

            Transport.send(message)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
