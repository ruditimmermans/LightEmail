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

    suspend fun getEmailsList(folder: String): List<EmailMessage> {
        return emailDao.getEmailsListByFolder(folder)
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
            if (latestUids == null) {
                // Fetch failed due to network or IMAP error. Preserve local cache!
                return@withContext
            }

            if (latestUids.isEmpty()) {
                // Server confirmed folder is completely empty.
                emailDao.clearFolder(folder)
                emailDao.clearAttachmentsForFolder(folder)
                return@withContext
            }

            // 2. Identify existing vs new UIDs
            val existingUids = mutableListOf<Long>()
            val newUids = mutableListOf<Long>()

            latestUids.forEach { uid ->
                if (emailDao.getEmailByUid(uid, folder) != null) {
                    existingUids.add(uid)
                } else {
                    newUids.add(uid)
                }
            }

            // 3. Fetch headers for new UIDs
            if (newUids.isNotEmpty()) {
                val newEmails = imapManager.fetchEmailsByUids(
                    email, password, host, folder, newUids,
                    context.getString(R.string.no_subject),
                    context.getString(R.string.unknown_sender)
                )
                if (newEmails.isNotEmpty()) {
                    emailDao.insertEmails(newEmails)
                }
            }

            // 4. Update read status for existing emails efficiently
            if (existingUids.isNotEmpty()) {
                val flags = imapManager.fetchFlagsByUids(email, password, host, folder, existingUids)
                flags.forEach { (uid, isRead) ->
                    emailDao.updateReadStatus(uid, folder, isRead)
                }
            }

            // 5. Cleanup: delete old emails in range that are no longer on the server
            val minUid = latestUids.minOrNull()
            if (minUid != null) {
                emailDao.deleteOldEmailsInRange(folder, minUid, latestUids)
            }

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
        try {
            val (text, html, attachments) = imapManager.fetchEmailContent(
                email, password, host, emailMessage.folder, emailMessage.uid,
                context.getString(R.string.error_reading_content)
            )
            emailDao.updateContent(emailMessage.uid, emailMessage.folder, text, html)
            if (attachments.isNotEmpty()) {
                emailDao.insertAttachments(attachments)
                emailDao.updateHasAttachments(emailMessage.uid, emailMessage.folder, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAttachmentsForEmail(uid: Long, folder: String) = emailDao.getAttachmentsForEmail(uid, folder)

    suspend fun downloadAttachment(
        email: String,
        password: String,
        host: String,
        attachment: Attachment
    ): Boolean = withContext(Dispatchers.IO) {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun markAsRead(
        email: String,
        password: String,
        host: String,
        folder: String,
        uid: Long
    ) = withContext(Dispatchers.IO) {
        try {
            emailDao.updateReadStatus(uid, folder, true)
            imapManager.markAsRead(email, password, host, folder, uid)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateReadStatus(
        email: String,
        password: String,
        host: String,
        folder: String,
        uid: Long,
        isRead: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            emailDao.updateReadStatus(uid, folder, isRead)
            imapManager.updateReadStatusOnServer(email, password, host, folder, uid, isRead)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteEmail(
        email: String,
        password: String,
        host: String,
        folder: String,
        uid: Long
    ) = withContext(Dispatchers.IO) {
        try {
            emailDao.deleteEmail(uid, folder)
            emailDao.deleteAttachmentsForEmail(uid, folder)
            imapManager.deleteEmail(email, password, host, folder, uid)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun moveEmail(
        email: String,
        password: String,
        host: String,
        srcFolder: String,
        targetFolder: String,
        uid: Long
    ) = withContext(Dispatchers.IO) {
        try {
            emailDao.deleteEmail(uid, srcFolder)
            emailDao.deleteAttachmentsForEmail(uid, srcFolder)

            val success = imapManager.moveEmail(email, password, host, srcFolder, targetFolder, uid)
            if (success) {
                syncEmails(email, password, host, targetFolder)
                syncEmails(email, password, host, srcFolder)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun clearCache(folder: String) {
        try {
            emailDao.clearFolder(folder)
            emailDao.clearAttachmentsForFolder(folder)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
