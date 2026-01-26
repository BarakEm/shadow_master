package com.shadowmaster

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.shadowmaster.ui.navigation.NavGraph
import com.shadowmaster.ui.navigation.Screen
import com.shadowmaster.ui.theme.ShadowMasterTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen for Android 12+
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Keep splash visible while loading
        var keepSplashVisible = true
        splashScreen.setKeepOnScreenCondition { keepSplashVisible }

        enableEdgeToEdge()

        // Handle shared content from intent
        val sharedContent = handleIntent(intent)

        setContent {
            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(1500) // Show splash for 1.5 seconds
                showSplash = false
                keepSplashVisible = false
            }

            ShadowMasterTheme {
                if (showSplash) {
                    SplashContent()
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        NavGraph(
                            navController = navController,
                            sharedContent = sharedContent
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Activity will be recreated with new intent
        recreate()
    }

    private fun handleIntent(intent: Intent?): SharedContent? {
        if (intent == null) return null

        return when (intent.action) {
            Intent.ACTION_SEND -> {
                when {
                    intent.type == "text/plain" -> {
                        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                        text?.let { extractUrlFromText(it) }?.let { SharedContent.Url(it) }
                    }
                    intent.type?.startsWith("audio/") == true -> {
                        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        uri?.let { SharedContent.AudioFile(it) }
                    }
                    else -> null
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { SharedContent.Url(it.toString()) }
            }
            else -> null
        }
    }

    private fun extractUrlFromText(text: String): String? {
        // Extract URL from shared text (YouTube/Spotify often share with extra text)
        val urlPattern = Regex("https?://[\\w.-]+[\\w/._-]*(?:\\?[\\w=&.-]*)?")
        return urlPattern.find(text)?.value
    }
}

sealed class SharedContent {
    data class Url(val url: String) : SharedContent()
    data class AudioFile(val uri: Uri) : SharedContent()
}

@Composable
private fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF16213E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Shadow Master Logo",
                modifier = Modifier.size(200.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Shadow Master",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Language Learning Through Shadowing",
                color = Color(0xFF8892b0),
                fontSize = 14.sp
            )
        }
    }
}
