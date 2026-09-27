package com.parentstracker.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.parentstracker.app.ui.theme.ParentsTrackerTheme
import com.parentstracker.app.util.InviteCodes
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Invite code from a parentstracker://join?code=... link, waiting to be opened. */
    private val pendingJoinCode = MutableStateFlow<String?>(null)

    /** The home-screen widget's SOS button opens parentstracker://sos. */
    private val pendingSos = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on first launch; after a rotation the link has already been handled.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            ParentsTrackerTheme {
                ParentsTrackerRoot(
                    pendingJoinCode = pendingJoinCode,
                    onJoinCodeHandled = { pendingJoinCode.value = null },
                    pendingSos = pendingSos,
                    onSosHandled = { pendingSos.value = false },
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
        val data = intent?.data ?: return
        if (data.scheme != "parentstracker") return
        when (data.host) {
            "join" -> pendingJoinCode.value = InviteCodes.normalize(data.getQueryParameter("code").orEmpty())
            "sos" -> pendingSos.value = true
        }
    }
}
