package com.seamless.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_states WHERE contactId = :contactId")
    suspend fun getSyncState(contactId: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSyncState(syncState: SyncStateEntity)
    
    @Query("DELETE FROM sync_states WHERE contactId = :contactId")
    suspend fun deleteSyncState(contactId: String)

    /** BSP: Get all sync states for bulk offer generation */
    @Query("SELECT * FROM sync_states")
    suspend fun getAllSyncStates(): List<SyncStateEntity>

    /** BSP: Get stale sync states for periodic cleanup */
    @Query("SELECT * FROM sync_states WHERE lastSyncedTimestamp < :beforeTimestamp")
    suspend fun getStatesOlderThan(beforeTimestamp: Long): List<SyncStateEntity>

    /** BSP: Purge stale sync states */
    @Query("DELETE FROM sync_states WHERE lastSyncedTimestamp < :beforeTimestamp")
    suspend fun deleteStatesOlderThan(beforeTimestamp: Long)
}
