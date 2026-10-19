package com.light.lightemail.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

class BackupManager(private val context: Context) {

    suspend fun exportBackup(outputStream: OutputStream): Boolean {
        return try {
            val backupJson = JSONObject()

            // 1. Export SharedPreferences
            val prefs = context.getSharedPreferences("light_email_prefs", Context.MODE_PRIVATE)
            val prefsJson = JSONObject()
            prefs.all.forEach { (key, value) ->
                prefsJson.put(key, value)
            }
            backupJson.put("prefs", prefsJson)

            // 2. Export Contacts
            val db = AppDatabase.getDatabase(context)
            val contacts = db.contactDao().getAllContactsList() // Need to add this method to DAO
            val contactsArray = JSONArray()
            contacts.forEach { contact ->
                val contactJson = JSONObject()
                contactJson.put("name", contact.name)
                contactJson.put("email", contact.email)
                contactsArray.put(contactJson)
            }
            backupJson.put("contacts", contactsArray)

            outputStream.use { it.write(backupJson.toString(4).toByteArray()) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importBackup(inputStream: InputStream): Boolean {
        return try {
            val content = inputStream.bufferedReader().use { it.readText() }
            val backupJson = JSONObject(content)

            // 1. Import SharedPreferences
            if (backupJson.has("prefs")) {
                val prefsJson = backupJson.getJSONObject("prefs")
                val prefs = context.getSharedPreferences("light_email_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.clear()
                val keys = prefsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (prefsJson.isNull(key)) continue
                    
                    when (val value = prefsJson.get(key)) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is Float -> editor.putFloat(key, value)
                        is Long -> editor.putLong(key, value)
                        is Int -> editor.putLong(key, value.toLong())
                    }
                }
                editor.apply()
            }

            // 2. Import Contacts
            if (backupJson.has("contacts")) {
                val contactsArray = backupJson.getJSONArray("contacts")
                val db = AppDatabase.getDatabase(context)
                db.contactDao().deleteAllContacts() // Need to add this method to DAO
                for (i in 0 until contactsArray.length()) {
                    val contactJson = contactsArray.getJSONObject(i)
                    val contact = Contact(
                        name = contactJson.getString("name"),
                        email = contactJson.getString("email")
                    )
                    db.contactDao().insertContact(contact)
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
