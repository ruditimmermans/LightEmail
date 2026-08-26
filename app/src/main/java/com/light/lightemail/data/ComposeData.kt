package com.light.lightemail.data

import android.net.Uri

data class ComposeData(
    val to: String = "",
    val subject: String = "",
    val body: String = "",
    val cc: String = "",
    val bcc: String = "",
    val attachments: List<Uri> = emptyList()
)
