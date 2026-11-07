package com.seamless.android.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.net.OnionContactManager
import com.seamless.android.ui.theme.SeamlessTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AddContactActivity - Handles seamless:// deep links for adding P2P contacts
 * 
 * Receives links like: seamless://add?o=abc123.onion:18888&k=pubkey&n=Nickname
 * Parses contact info and adds to OnionContactManager
 */
class AddContactActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "AddContactActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val contactLink = intent?.data?.toString() ?: ""
        // SECURITY: never log the link itself (contains .onion address, public key, nickname)
        Log.i(TAG, "Received contact deep link (length=${contactLink.length})")
        
        setContent {
            SeamlessTheme {
                AddContactScreen(
                    contactLink = contactLink,
                    onComplete = { success ->
                        if (success) {
                            // Navigate to main app
                            val mainIntent = Intent(this, com.seamless.android.MainActivity::class.java)
                            mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                            startActivity(mainIntent)
                        }
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun AddContactScreen(
    contactLink: String,
    onComplete: (Boolean) -> Unit
) {
    var isProcessing by remember { mutableStateOf(true) }
    var contactAdded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var contactName by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val contactManager = remember { OnionContactManager.getInstance(context) }
    
    // SECURITY: a deep link can be fired by any web page or app, so we only PARSE it here
    // and require an explicit user tap on "Add" before anything is saved.
    var pendingContact by remember { mutableStateOf<OnionContactManager.OnionContact?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(contactLink) {
        if (contactLink.isNotEmpty()) {
            try {
                val contact = OnionContactManager.OnionContact.fromShareableLink(contactLink)
                if (contact != null) {
                    contactName = contact.nickname.take(64)
                    pendingContact = contact
                } else {
                    errorMessage = "Invalid contact link"
                }
            } catch (e: Exception) {
                Log.e("AddContactScreen", "Error parsing contact link: ${e.javaClass.simpleName}")
                errorMessage = "Invalid contact link"
            }
            isProcessing = false
        } else {
            errorMessage = "No contact link provided"
            isProcessing = false
        }
    }

    // After a successful, user-confirmed add: show success briefly then continue.
    LaunchedEffect(contactAdded) {
        if (contactAdded) {
            delay(1500)
            onComplete(true)
        }
    }
    
    // Premium gradient background
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A1A2E),
            Color(0xFF16213E),
            Color(0xFF0F3460)
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    isProcessing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = Color(0xFF00D4AA),
                            strokeWidth = 4.dp
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Adding Contact...",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    pendingContact != null && !contactAdded -> {
                        Text(
                            text = "Add this contact?",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = contactName,
                            color = Color(0xFF00D4AA),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Only add people you trust. This link was opened from outside the app.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { onComplete(false) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF94A3B8))
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    val contact = pendingContact
                                    pendingContact = null
                                    if (contact != null) {
                                        scope.launch {
                                            try {
                                                if (contactManager.addContact(contact)) {
                                                    contactAdded = true
                                                } else {
                                                    errorMessage = "Contact already exists"
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Failed to add contact"
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA))
                            ) { Text("Add") }
                        }
                    }

                    contactAdded -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            modifier = Modifier.size(80.dp),
                            tint = Color(0xFF00D4AA)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Contact Added!",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = contactName,
                            color = Color(0xFF00D4AA),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "You can now chat securely\nwithout any servers",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    errorMessage != null -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Error",
                            modifier = Modifier.size(80.dp),
                            tint = Color(0xFFFF5252)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Failed to Add",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = errorMessage ?: "Unknown error",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = { onComplete(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF94A3B8)
                            )
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}
