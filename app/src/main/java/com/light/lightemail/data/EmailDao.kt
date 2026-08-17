package com.light.lightemail.data

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {
    @Query("SELECT * FROM emails WHERE folder = :folder ORDER BY uid DESC")
    fun getEmailsByFolder(folder: String): Flow<List<EmailMessage>>

    @Query("SELECT * FROM emails WHERE folder = :folder ORDER BY uid DESC")
    fun getEmailsPaging(folder: String): PagingSource<Int, EmailMessage>

    @Query("SELECT * FROM emails WHERE uid = :uid AND folder = :folder")
    suspend fun getEmailByUid(uid: Long, folder: String): EmailMessage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailMessage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: EmailMessage)

    @Query("UPDATE emails SET content = :content, htmlContent = :htmlContent WHERE uid = :uid AND folder = :folder")
    suspend fun updateContent(uid: Long, folder: String, content: String, htmlContent: String?)

    @Query("UPDATE emails SET isRead = :isRead WHERE uid = :uid AND folder = :folder")
    suspend fun updateReadStatus(uid: Long, folder: String, isRead: Boolean)

    @Query("DELETE FROM emails WHERE uid = :uid AND folder = :folder")
    suspend fun deleteEmail(uid: Long, folder: String)

    @Query("DELETE FROM emails WHERE folder = :folder AND uid NOT IN (:uids)")
    suspend fun deleteOldEmails(folder: String, uids: List<Long>)

    @Query("DELETE FROM emails WHERE folder = :folder")
    suspend fun clearFolder(folder: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<Attachment>)

    @Query("SELECT * FROM attachments WHERE emailUid = :uid AND folder = :folder")
    fun getAttachmentsForEmail(uid: Long, folder: String): Flow<List<Attachment>>

    @Query("UPDATE attachments SET localPath = :localPath WHERE id = :id")
    suspend fun updateAttachmentLocalPath(id: Int, localPath: String)
}
