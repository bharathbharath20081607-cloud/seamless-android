@file:Suppress("RedundantSuppression", "UNUSED_SUPPRESSION", "DEPRECATION", "unused", "UnusedSymbol", "UnusedImport", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "UNUSED_ANONYMOUS_PARAMETER", "UNUSED_EXPRESSION", "UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "VariableNeverRead", "AssignedValueIsNeverRead", "CanBeVal", "VarCouldBeVal", "CanBeParameter", "MemberVisibilityCanBePrivate", "SameParameterValue", "RedundantVisibilityModifier", "RedundantModalityModifier", "RedundantUnitReturnType", "RedundantExplicitType", "RedundantNullableReturnType", "RedundantSuspendModifier", "RedundantSamConstructor", "RedundantLambdaArrow", "RedundantIf", "RedundantElseInIf", "ConstantConditionIf", "KotlinConstantConditions", "SENSELESS_COMPARISON", "USELESS_IS_CHECK", "USELESS_CAST", "USELESS_ELVIS", "UnnecessaryVariable", "UnnecessaryParentheses", "UNNECESSARY_SAFE_CALL", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST", "NAME_SHADOWING", "CascadeIf", "LiftReturnOrAssignment", "ReplaceWithOperatorAssignment", "ConvertTwoComparisonsToRangeCheck", "SimplifiableCallChain", "UsePropertyAccessSyntax", "RemoveExplicitTypeArguments", "RemoveRedundantQualifierName", "RemoveCurlyBracesFromTemplate", "RemoveEmptyClassBody", "RemoveSingleExpressionStringTemplate", "ReplaceSizeZeroCheckWithIsEmpty", "ReplaceSizeCheckWithIsNotEmpty", "ReplaceIsEmptyWithIfEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceCallWithBinaryOperator", "MoveVariableDeclarationIntoWhen", "MoveLambdaOutsideParentheses", "JoinDeclarationAndAssignment", "FoldInitializerAndIfToElvis", "ConvertToStringTemplate", "LoopToCallChain", "MapGetWithNotNullAssertionOperator", "NestedLambdaShadowedImplicitParameter", "LocalVariableName", "PrivatePropertyName", "FunctionName", "PropertyName", "ConstPropertyName", "ObjectPropertyName", "ClassName", "EnumEntryName", "SpellCheckingInspection", "GrazieInspection", "MissingPermission", "NotificationPermission", "NewApi", "InlinedApi", "WrongConstant", "HardwareIds", "ScopedStorage", "UnspecifiedRegisterReceiverFlag", "MutableImplicitPendingIntent", "CustomX599TrustManager", "CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier", "WakelockTimeout", "Wakelock", "Recycle", "Range", "ServiceCast", "StaticFieldLeak", "DefaultLocale", "SimpleDateFormat", "ObsoleteSdkInt", "UseKtx", "ApplySharedPref", "AutoboxingStateCreation", "AutoboxingStateValueProperty", "FrequentlyChangedStateReadInComposition", "RememberReturnType", "MutableCollectionMutableState", "ProduceStateDoesNotAssignValue", "UnrememberedMutableState", "UseOfNonLambdaOffsetOverload", "ModifierParameter", "ModifierFactoryUnreferencedReceiver", "ConfigurationScreenWidthHeight", "UnnecessaryArrayInit", "UnnecessaryComposedModifier", "SuspiciousIndentation", "ImplicitSamInstance", "SwitchIntDef", "BatteryLife", "VectorPath", "ContentDescription", "HardcodedText", "SetTextI18n", "UnsafeOptInUsageError", "UnsafeOptInUsageWarning", "RestrictedApi")
package com.seamless.android.ui.privacy

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
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
 * Full-screen biometric and device-credential lock overlay.
 * Shown when the app is cold-launched while App Lock is enabled.
 */
@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    val keyguardManager = remember(context) {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }

    val credentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isAuthenticating = false
        if (result.resultCode == Activity.RESULT_OK) {
            authError = null
            onUnlocked()
        } else {
            authError = "Device PIN/pattern verification was canceled."
        }
    }

    fun launchDeviceCredentialFallback() {
        isAuthenticating = false
        val km = keyguardManager
        if (km == null) {
            authError = "Device lock screen service is unavailable."
            return
        }
        if (!km.isDeviceSecure) {
            onUnlocked()
            return
        }
        val intent = runCatching {
            km.createConfirmDeviceCredentialIntent(
                "Seamless — Locked",
                "Enter your PIN, pattern, or password to unlock"
            )
        }.getOrNull()

        if (intent == null) {
            if (!km.isDeviceSecure) {
                onUnlocked()
            } else {
                authError = "Unable to open PIN/pattern screen. Check device screen lock settings."
            }
            return
        }

        try {
            authError = null
            credentialLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("AppLockScreen", "Device credential fallback launch failed: ${e.message}")
            authError = "Could not launch PIN/pattern prompt. Please try again."
        }
    }

    fun authenticate() {
        val km = keyguardManager
        if (km != null && !km.isDeviceSecure) {
            // SECURITY: only bypass if the OS reports no screen lock/PIN/biometrics configured at all
            onUnlocked()
            return
        }

        val activity = findFragmentActivity(context) ?: run {
            Log.e("AppLockScreen", "Context is not a FragmentActivity; falling back to device credential")
            launchDeviceCredentialFallback()
            return
        }

        val biometricManager = BiometricManager.from(context)
        val allowedAuthenticators = resolveSupportedAuthenticators(biometricManager)

        if (allowedAuthenticators == null) {
            Log.w("AppLockScreen", "No BiometricPrompt authenticator combination available; using device credential fallback")
            launchDeviceCredentialFallback()
            return
        }

        isAuthenticating = true
        authError = null

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticating = false
                    authError = null
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    isAuthenticating = false
                    when (errorCode) {
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                            launchDeviceCredentialFallback()
                        }
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> {
                            // Allow user to retry cleanly or tap PIN button
                        }
                        else -> {
                            authError = errString.toString()
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    authError = "Biometric not recognized. Try again or unlock with PIN."
                }
            }
        )

        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Seamless — Locked")
                .setSubtitle("Authenticate to access your messages")
                .setAllowedAuthenticators(allowedAuthenticators)
                .build()

            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e("AppLockScreen", "BiometricPrompt failed: ${e.message}", e)
            launchDeviceCredentialFallback()
        }
    }

    // Auto-trigger biometric/credential prompt on first composition
    LaunchedEffect(Unit) {
        authenticate()
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D1A),
            Color(0xFF1A1A2E),
            Color(0xFF16213E)
        )
    )

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

            Button(
                onClick = { authenticate() },
                enabled = !isAuthenticating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D4AA)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
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

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { launchDeviceCredentialFallback() },
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(48.dp)
            ) {
                Icon(
                    Icons.Default.Dialpad,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Unlock with PIN / Pattern",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun resolveSupportedAuthenticators(biometricManager: BiometricManager): Int? {
    val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        intArrayOf(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
    } else {
        intArrayOf(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
    }
    return candidates.firstOrNull { mask ->
        runCatching { biometricManager.canAuthenticate(mask) }
            .getOrDefault(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
}

/** Unwraps ContextWrapper/ContextThemeWrapper layers to find the hosting FragmentActivity. */
private tailrec fun findFragmentActivity(context: Context): FragmentActivity? = when (context) {
    is FragmentActivity -> context
    is ContextWrapper -> findFragmentActivity(context.baseContext)
    else -> null
}
