package com.light.lightemail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class EmailUtilsTest {

    private fun extractCleanAddress(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.contains("<") && trimmed.contains(">")) {
            trimmed.substringAfter("<").substringBefore(">").trim()
        } else trimmed
    }

    private fun filterRecipients(raw: String?, selfEmail: String): String {
        if (raw.isNullOrBlank()) return ""
        val regex = Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)(?=(?:[^<]*<[^>]*>)*[^<]*$)")
        val list = raw.split(regex).map { it.trim() }.filter { it.isNotEmpty() }
        val cleanSelf = extractCleanAddress(selfEmail).lowercase()
        return list.filter { addr ->
            val cleanAddr = extractCleanAddress(addr).lowercase()
            cleanAddr != cleanSelf && !cleanAddr.contains(cleanSelf)
        }.distinct().joinToString(", ")
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val curr = if (i < currentParts.size) currentParts[i] else 0
            val late = if (i < latestParts.size) latestParts[i] else 0
            if (late > curr) return true
            if (curr > late) return false
        }
        return false
    }

    @Test
    fun testExtractCleanAddress() {
        assertEquals("john@example.com", extractCleanAddress("John Doe <john@example.com>"))
        assertEquals("john@example.com", extractCleanAddress("john@example.com"))
        assertEquals("jane@domain.org", extractCleanAddress("\"Doe, Jane\" <jane@domain.org>"))
    }

    @Test
    fun testFilterRecipients() {
        val raw = "\"Doe, John\" <john@example.com>, Alice <alice@example.com>, Self <me@myhost.com>"
        val filtered = filterRecipients(raw, "me@myhost.com")
        assertEquals("\"Doe, John\" <john@example.com>, Alice <alice@example.com>", filtered)
    }

    @Test
    fun testVersionComparison() {
        assertTrue(isNewerVersion("6.1", "6.2"))
        assertTrue(isNewerVersion("6.1.0", "6.1.1"))
        assertFalse(isNewerVersion("6.2", "6.1"))
        assertFalse(isNewerVersion("6.1", "6.1"))
    }
}
