package com.seamless.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Data model for menu items in the slide-out navigation menu
 */
data class MenuItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val badge: String? = null,
    val enabled: Boolean = true,
    val action: MenuAction
)

/**
 * Sealed class representing different navigation actions
 */
sealed class MenuAction {
    object NavigateToProfile : MenuAction()
    object NavigateToSettings : MenuAction()
    object NavigateToShareThings : MenuAction()
    object NavigateToReceivedFiles : MenuAction()
    object NavigateToLocations : MenuAction()
    object NavigateToMesh : MenuAction()  // Private Mesh feature
    object NavigateToOnionP2P : MenuAction()  // Zero-server P2P messaging via Tor
    data class NavigateToFutureFeature(val featureId: String, val featureName: String) : MenuAction()
}

/**
 * Factory for creating menu items
 */
object MenuItemFactory {
    fun createMenuItems(): List<MenuItem> = listOf(
        // Active items
        MenuItem(
            id = "profile",
            title = "Profile",
            icon = Icons.Outlined.AccountCircle,
            enabled = true,
            action = MenuAction.NavigateToProfile
        ),

        MenuItem(
            id = "settings",
            title = "Settings",
            icon = Icons.Outlined.Settings,
            enabled = true,
            action = MenuAction.NavigateToSettings
        ),
        
        // File sharing features
        MenuItem(
            id = "hyperdrop",
            title = "HyperDrop",
            icon = Icons.Outlined.Share,
            enabled = true,
            action = MenuAction.NavigateToShareThings
        ),
        
        // Private Mesh feature
        MenuItem(
            id = "mesh",
            title = "Mesh",
            icon = Icons.Outlined.Hub,
            enabled = true,
            action = MenuAction.NavigateToMesh
        ),
        
        MenuItem(
            id = "received_files",
            title = "Received Files",
            icon = Icons.Outlined.Folder,
            enabled = true,
            action = MenuAction.NavigateToReceivedFiles
        ),
        
        // Tor P2P - Zero-server messaging via Tor Hidden Services
        MenuItem(
            id = "onion_p2p",
            title = "Tor P2P Messaging",
            icon = Icons.Outlined.Security, // Security icon for encrypted P2P
            badge = "NEW",
            enabled = true,
            action = MenuAction.NavigateToOnionP2P
        ),
        
        // Location Channels navigation
        MenuItem(
            id = "locations",
            title = "Locations",
            icon = Icons.Outlined.Tag,
            enabled = true,
            action = MenuAction.NavigateToLocations
        )
    )
}
