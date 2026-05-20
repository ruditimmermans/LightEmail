package com.light.lightemail.data

import java.io.Serializable

data class EmailMessage(
    val id: String,
    val uid: Long,
    val subject: String,
    val sender: String,
    val content: String,
    val htmlContent: String? = null,
    val date: String,
    val folder: String = "INBOX"
) : Serializable
