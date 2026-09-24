package com.seamless.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks the Vector Clock (sync state) for each contact.
 * This ensures we only send messages they haven't seen yet during a Bramble Sync Session.
 */
@Entity(
    tableName = "sync_states",
    indices = [Index(value = ["lastSyncedTimestamp"])]
)
data class SyncStateEntity(
    @PrimaryKey val contactId: String, // Can be a contact's Onion Address or Mesh ID
    val lastSyncedTimestamp: Long      // The timestamp of the latest message successfully synced
)
