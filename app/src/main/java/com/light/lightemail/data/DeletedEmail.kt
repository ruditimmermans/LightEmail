package com.light.lightemail.data

import androidx.room.Entity

@Entity(tableName = "deleted_emails", primaryKeys = ["uid", "folder"])
data class DeletedEmail(
    val uid: Long,
    val folder: String
)
