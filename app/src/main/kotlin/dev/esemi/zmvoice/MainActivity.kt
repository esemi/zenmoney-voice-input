package dev.esemi.zmvoice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import dev.esemi.zmvoice.ui.RootScreen
import dev.esemi.zmvoice.ui.theme.ZmVoiceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZmVoiceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state = viewModel.state.collectAsState()
                    RootScreen(
                        state = state.value,
                        viewModel = viewModel,
                        hasMicPermission = ::hasMicPermission,
                    )
                }
            }
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
