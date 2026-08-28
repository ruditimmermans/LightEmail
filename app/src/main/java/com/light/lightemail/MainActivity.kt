package com.light.lightemail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.light.lightemail.ui.theme.LightEmailTheme
import com.light.lightemail.ui.screens.MainScreen
import com.light.lightemail.ui.viewmodel.EmailViewModel
import com.light.lightemail.data.ComposeData

class MainActivity : ComponentActivity() {
    private var initialEmailUid by mutableStateOf<Long?>(null)
    private var initialComposeData by mutableStateOf<ComposeData?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)
        requestNotificationPermission()
        requestIgnoreBatteryOptimizations()
        startPushService()

        enableEdgeToEdge()
        setContent {
            val viewModel: EmailViewModel = viewModel()
            val useColorMode by viewModel.useColorMode.collectAsState()
            
            LightEmailTheme(useColorMode = useColorMode) {
                MainScreen(
                    viewModel = viewModel,
                    initialEmailUid = initialEmailUid,
                    initialComposeData = initialComposeData,
                    onEmailOpened = { initialEmailUid = null },
                    onComposeStarted = { initialComposeData = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        try {
            val uid = intent.getLongExtra("EXTRA_EMAIL_UID", -1L)
            if (uid != -1L) {
                initialEmailUid = uid
                return
            }

            when (intent.action) {
                Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                    if (intent.data?.scheme == "mailto") {
                        val mailto = intent.dataString ?: ""
                        var to = ""
                        var subject = ""
                        var body = ""
                        var cc = ""
                        var bcc = ""

                        if (mailto.isNotEmpty()) {
                            try {
                                val mt = android.net.MailTo.parse(mailto)
                                to = mt.to ?: ""
                                subject = mt.subject ?: ""
                                body = mt.body ?: ""
                                cc = mt.cc ?: ""

                                // bcc isn't explicitly in MailTo but can be in query
                                val uri = Uri.parse(mailto)
                                bcc = uri.getQueryParameter("bcc") ?: ""
                            } catch (e: Exception) {
                                val uri = Uri.parse(mailto)
                                to = uri.schemeSpecificPart?.split("?")?.firstOrNull() ?: ""
                                subject = uri.getQueryParameter("subject") ?: ""
                                body = uri.getQueryParameter("body") ?: ""
                                cc = uri.getQueryParameter("cc") ?: ""
                                bcc = uri.getQueryParameter("bcc") ?: ""
                            }
                        }

                        if (subject.isEmpty()) subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
                        if (body.isEmpty()) body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: ""
                        if (to.isEmpty()) to = getRecipients(intent, Intent.EXTRA_EMAIL)
                        if (cc.isEmpty()) cc = getRecipients(intent, Intent.EXTRA_CC)
                        if (bcc.isEmpty()) bcc = getRecipients(intent, Intent.EXTRA_BCC)

                        initialComposeData = ComposeData(to, subject, body, cc, bcc)
                    }
                }
                Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
                    val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: ""
                    val to = getRecipients(intent, Intent.EXTRA_EMAIL)
                    val cc = getRecipients(intent, Intent.EXTRA_CC)
                    val bcc = getRecipients(intent, Intent.EXTRA_BCC)

                    val attachments = mutableListOf<Uri>()
                    if (intent.action == Intent.ACTION_SEND) {
                        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_STREAM)
                        }
                        uri?.let { attachments.add(it) }
                    } else {
                        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                        }
                        uris?.let { attachments.addAll(it) }
                    }

                    initialComposeData = ComposeData(to, subject, body, cc, bcc, attachments)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getRecipients(intent: Intent, extra: String): String {
        // 1. Try String Array (Standard)
        try {
            val recipients = intent.getStringArrayExtra(extra)
            if (recipients != null && recipients.isNotEmpty()) {
                return recipients.joinToString(", ")
            }
        } catch (e: Exception) {}

        // 2. Try String ArrayList (Common in some apps)
        try {
            val recipientsList = intent.getStringArrayListExtra(extra)
            if (recipientsList != null && recipientsList.isNotEmpty()) {
                return recipientsList.joinToString(", ")
            }
        } catch (e: Exception) {}

        // 3. Fallback to single String
        return try {
            intent.getStringExtra(extra) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPushService() {
        val serviceIntent = Intent(this, com.light.lightemail.service.EmailPushService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
