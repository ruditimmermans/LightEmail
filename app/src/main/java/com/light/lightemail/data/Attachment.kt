package com.light.lightemail.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "attachments")
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val emailUid: Long,
    val folder: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val partIndex: String, // String index to find the part in IMAP
    val localPath: String? = null
) : Serializable
