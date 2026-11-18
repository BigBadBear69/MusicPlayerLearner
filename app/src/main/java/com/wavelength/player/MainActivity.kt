package com.wavelength.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.wavelength.player.ui.MainViewModel
import com.wavelength.player.ui.WavelengthRoot
import com.wavelength.player.ui.theme.WavelengthTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WavelengthTheme {
                PermissionGate(onGranted = vm::onPermissionGranted) { WavelengthRoot(vm) }
            }
        }
    }
}

private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

/** Shows the app once Wavelength may read the phone's music; otherwise explains and asks. */
@Composable
private fun PermissionGate(onGranted: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(granted()) }
    var askedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        askedOnce = true
    }

    // The user may grant the permission in system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasPermission = granted() }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(audioPermission) }
    LaunchedEffect(hasPermission) { if (hasPermission) onGranted() }

    if (hasPermission) {
        content()
        return
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
            Spacer(Modifier.height(24.dp))
            Text("Welcome to Wavelength", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "To find the music on your phone, Wavelength needs permission to read your audio files. " +
                    "Your music and listening data never leave your phone.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { launcher.launch(audioPermission) }) { Text("Allow access to music") }
            if (askedOnce) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }) { Text("Open app settings") }
            }
        }
    }
}
