package com.light.lightemail.data

import android.content.Context
import com.light.lightemail.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class EmailRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val emailDao = db.emailDao()
    private val imapManager = ImapManager()

    fun getEmails(folder: String): Flow<List<EmailMessage>> {
        return emailDao.getEmailsByFolder(folder)
    }

    fun getEmailsPaging(folder: String) = emailDao.getEmailsPaging(folder)

    suspend fun syncEmails(
        email: String,
        password: String,
        host: String,
        folder: String,
        limit: Int = 50
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch latest UIDs from IMAP
            val latestUids = imapManager.fetchLatestUids(email, password, host, folder, limit)
            if (latestUids.isEmpty()) return@withContext

            // 2. Identify which UIDs we don't have in DB and which we do
            val existingUids = mutableListOf<Long>()
            val newUids = mutableListOf<Long>()

            latestUids.forEach { uid ->
                if (emailDao.getEmailByUid(uid, folder) != null) {
                    existingUids.add(uid)
                } else {
                    newUids.add(uid)
                }
            }

            // 3. Fetch headers only for new UIDs
            if (newUids.isNotEmpty()) {
                val newEmails = imapManager.fetchEmailsByUids(
                    email, password, host, folder, newUids,
                    context.getString(R.string.no_subject),
                    context.getString(R.string.unknown_sender)
                )
                emailDao.insertEmails(newEmails)
            }

            // 4. Update read status for existing emails efficiently
            if (existingUids.isNotEmpty()) {
                val flags = imapManager.fetchFlagsByUids(email, password, host, folder, existingUids)
                flags.forEach { (uid, isRead) ->
                    emailDao.updateReadStatus(uid, folder, isRead)
                }
            }

            // 5. Cleanup: delete emails that are no longer on the server (within our limit)
            emailDao.deleteOldEmails(folder, latestUids)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchEmailContent(
        email: String,
        password: String,
        host: String,
        emailMessage: EmailMessage
    ) = withContext(Dispatchers.IO) {
        val (text, html, attachments) = imapManager.fetchEmailContent(
            email, password, host, emailMessage.folder, emailMessage.uid,
            context.getString(R.string.error_reading_content)
        )
        emailDao.updateContent(emailMessage.uid, emailMessage.folder, text, html)
        if (attachments.isNotEmpty()) {
            emailDao.insertAttachments(attachments)
        }
    }

    fun getAttachmentsForEmail(uid: Long, folder: String) = emailDao.getAttachmentsForEmail(uid, folder)

    suspend fun downloadAttachment(
        email: String,
        password: String,
        host: String,
        attachment: Attachment
    ): Boolean = withContext(Dispatchers.IO) {
        val fileName = attachment.fileName
        val file = java.io.File(context.filesDir, "attachments/${attachment.emailUid}_${attachment.id}_$fileName")
        file.parentFile?.mkdirs()
        
        val success = file.outputStream().use { output ->
            imapManager.fetchAttachment(
                email, password, host, attachment.folder, attachment.emailUid, attachment.partIndex, output
            )
        }
        
        if (success) {
            emailDao.updateAttachmentLocalPath(attachment.id, file.absolutePath)
        }
        success
    }

    suspend fun markAsRead(
        email: String,
        password: String,
        host: String,
        folder: String,
        uid: Long,
        messageId: Int
    ) = withContext(Dispatchers.IO) {
        // Optimistic update
        emailDao.updateReadStatus(uid, folder, true)
        
        val success = imapManager.markAsRead(email, password, host, folder, messageId)
        if (!success) {
            // Revert if failed? Or just let next sync fix it.
        }
    }

    suspend fun deleteEmail(
        email: String,
        password: String,
        host: String,
        folder: String,
        uid: Long,
        messageId: Int
    ) = withContext(Dispatchers.IO) {
        // Optimistic update
        emailDao.deleteEmail(uid, folder)
        
        val success = imapManager.deleteEmail(email, password, host, folder, messageId)
        if (!success) {
            // Revert or let next sync fix it
        }
    }
    
    suspend fun clearCache(folder: String) {
        emailDao.clearFolder(folder)
    }
}
