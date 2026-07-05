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

                    try {
                        val mt = android.net.MailTo.parse(mailto)
                        to = mt.to ?: ""
                        subject = mt.subject ?: ""
                        body = mt.body ?: ""
                    } catch (e: Exception) {
                        val uri = Uri.parse(mailto)
                        to = uri.schemeSpecificPart?.split("?")?.firstOrNull() ?: ""
                        subject = uri.getQueryParameter("subject") ?: ""
                        body = uri.getQueryParameter("body") ?: ""
                    }

                    if (subject.isEmpty()) subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
                    if (body.isEmpty()) body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: ""
                    if (to.isEmpty()) {
                        val toArray = intent.getStringArrayExtra(Intent.EXTRA_EMAIL)
                        to = toArray?.joinToString(", ") ?: intent.getStringExtra(Intent.EXTRA_EMAIL) ?: ""
                    }

                    initialComposeData = ComposeData(to, subject, body)
                }
            }
            Intent.ACTION_SEND -> {
                val type = intent.type
                if (type == null || type.startsWith("text/") || type == "*/*") {
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
                    val body = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: ""
                    val toArray = intent.getStringArrayExtra(Intent.EXTRA_EMAIL)
                    val to = toArray?.joinToString(", ") ?: intent.getStringExtra(Intent.EXTRA_EMAIL) ?: ""
                    initialComposeData = ComposeData(to, subject, body)
                }
            }
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
