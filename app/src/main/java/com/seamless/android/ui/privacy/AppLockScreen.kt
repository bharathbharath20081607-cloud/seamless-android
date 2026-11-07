package com.seamless.android.ui.privacy

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Full-screen biometric lock overlay.
 * Shown when the app is cold-launched or resumed from background while App Lock is enabled.
 */
@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D1A),
            Color(0xFF1A1A2E),
            Color(0xFF16213E)
        )
    )

    fun authenticate() {
        val activity = findFragmentActivity(context) ?: run {
            Log.e("AppLockScreen", "Context is not a FragmentActivity")
            authError = "Unable to show the unlock prompt. Please try again." // SECURITY: fail closed
            return
        }

        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // SECURITY: only skip the lock when the device genuinely has no screen lock/biometrics
            // (otherwise the user would be locked out forever). Transient errors must NOT unlock.
            if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
                canAuth == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
                onUnlocked()
            } else {
                authError = "Biometric authentication is currently unavailable. Please try again."
            }
            return
        }

        isAuthenticating = true
        authError = null

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticating = false
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    isAuthenticating = false
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        authError = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    isAuthenticating = false
                    authError = "Authentication failed. Try again."
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Seamless — Locked")
            .setSubtitle("Authenticate to access your messages")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    // Auto-trigger biometric prompt on first composition
    LaunchedEffect(Unit) {
        authenticate()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated lock icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Seamless is Locked",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Your messages are protected.\nAuthenticate to continue.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            if (authError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    authError!!,
                    color = Color(0xFFFF5555),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Unlock button
            Button(
                onClick = { authenticate() },
                enabled = !isAuthenticating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D4AA)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(52.dp)
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isAuthenticating) "Authenticating..." else "Unlock",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/** Unwraps ContextWrapper/ContextThemeWrapper layers to find the hosting FragmentActivity. */
private tailrec fun findFragmentActivity(context: android.content.Context): FragmentActivity? = when (context) {
    is FragmentActivity -> context
    is android.content.ContextWrapper -> findFragmentActivity(context.baseContext)
    else -> null
}
