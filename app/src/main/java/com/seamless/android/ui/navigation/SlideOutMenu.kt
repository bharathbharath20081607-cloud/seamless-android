package com.seamless.android.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seamless.android.ui.theme.SeamlessCyan

/**
 * Slide-out navigation menu component
 */
@Composable
fun SlideOutMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onMenuItemClick: (MenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val menuItems = remember { MenuItemFactory.createMenuItems() }
    
    // Menu content
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            MenuHeader()
            
            HorizontalDivider(
                color = colorScheme.outline.copy(alpha = 0.3f),
                thickness = 1.dp
            )
            
            // Menu items
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(menuItems) { item ->
                    MenuItemRow(
                        item = item,
                        onClick = {
                            if (item.enabled) {
                                onMenuItemClick(item)
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Menu header with app branding
 */
@Composable
private fun MenuHeader() {
    val colorScheme = MaterialTheme.colorScheme
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                SeamlessCyan.copy(alpha = 0.1f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "seamless",
                style = MaterialTheme.typography.headlineMedium,
                color = SeamlessCyan,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Navigation Menu",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Individual menu item row
 */
@Composable
private fun MenuItemRow(
    item: MenuItem,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val alpha = if (item.enabled) 1f else 0.5f
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = item.enabled, onClick = onClick)
            .alpha(alpha),
        color = if (item.enabled) Color.Transparent else colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = if (item.enabled) SeamlessCyan else colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = if (item.enabled) FontWeight.Normal else FontWeight.Light
                )
            }
            
            // Badge
            item.badge?.let { badge ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (item.enabled) SeamlessCyan.copy(alpha = 0.2f) else colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.enabled) SeamlessCyan else colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
