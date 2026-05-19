package com.light.lightemail.data

import java.io.Serializable

data class EmailMessage(
    val id: String,
    val subject: String,
    val sender: String,
    val content: String,
    val date: String
) : Serializable
