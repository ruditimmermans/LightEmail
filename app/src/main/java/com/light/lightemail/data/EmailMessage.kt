package com.light.lightemail.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "emails", primaryKeys = ["uid", "folder"])
data class EmailMessage(
    val id: String, // IMAP Message Number (can change, but useful for some operations)
    val uid: Long,  // IMAP Unique ID (constant for the message)
    val subject: String,
    val sender: String,
    val replyTo: String? = null,
    val toRecipients: String? = null,
    val ccRecipients: String? = null,
    val content: String,
    val htmlContent: String? = null,
    val date: String,
    val folder: String = "Inbox",
    val isRead: Boolean = false
) : Serializable
