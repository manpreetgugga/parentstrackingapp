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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            ParentsTrackerTheme {
                ParentsTrackerRoot(
                    pendingJoinCode = pendingJoinCode,
                    onJoinCodeHandled = { pendingJoinCode.value = null },
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
        if (data.scheme == "parentstracker" && data.host == "join") {
            pendingJoinCode.value = InviteCodes.normalize(data.getQueryParameter("code").orEmpty())
        }
    }
}
