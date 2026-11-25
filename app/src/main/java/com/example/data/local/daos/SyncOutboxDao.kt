package com.example.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.model.SyncStatus
import com.example.data.local.entities.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun enqueue(event: SyncOutboxEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun enqueueAll(events: List<SyncOutboxEntity>)

  @Query(
    """
    SELECT * FROM sync_outbox
    WHERE syncStatus IN ('PENDING', 'FAILED')
      AND nextRetryAt <= :nowMillis
    ORDER BY
      CASE entityType
        WHEN 'SHOP' THEN 0
        WHEN 'CUSTOMER' THEN 1
        WHEN 'MEASUREMENT' THEN 2
        WHEN 'ORDER' THEN 3
        WHEN 'PAYMENT' THEN 4
        ELSE 5
      END ASC,
      createdAt ASC
    LIMIT :limit
    """
  )
  suspend fun getReadyEvents(nowMillis: Long, limit: Int = 300): List<SyncOutboxEntity>

  @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC")
  suspend fun getAllEvents(): List<SyncOutboxEntity>

  @Query("SELECT COUNT(*) FROM sync_outbox WHERE syncStatus IN ('PENDING', 'IN_PROGRESS', 'FAILED')")
  fun observePendingCount(): Flow<Int>

  @Query("SELECT MAX(createdAt) FROM sync_outbox WHERE syncStatus = 'PENDING'")
  fun observeLatestPendingCreatedAt(): Flow<Long?>

  @Query("SELECT MIN(nextRetryAt) FROM sync_outbox WHERE syncStatus = 'FAILED'")
  fun observeNextFailedRetryAt(): Flow<Long?>

  @Query("SELECT COUNT(*) FROM sync_outbox WHERE syncStatus = 'FAILED'")
  fun observeFailedCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM sync_outbox WHERE syncStatus = 'FAILED'")
  suspend fun getFailedCount(): Int

  @Query(
    """
    SELECT lastError FROM sync_outbox
    WHERE syncStatus = 'FAILED' AND lastError IS NOT NULL
    ORDER BY createdAt DESC
    LIMIT 1
    """
  )
  suspend fun getLatestFailedError(): String?

  @Query("UPDATE sync_outbox SET syncStatus = 'PENDING', lastError = NULL WHERE syncStatus = 'IN_PROGRESS'")
  suspend fun resetStaleInProgress()

  @Query("UPDATE sync_outbox SET syncStatus = 'PENDING', nextRetryAt = :nowMillis, lastError = NULL WHERE syncStatus = 'FAILED'")
  suspend fun resetFailedForImmediateRetry(nowMillis: Long)

  @Query("UPDATE sync_outbox SET syncStatus = 'IN_PROGRESS', lastError = NULL WHERE eventId IN (:eventIds)")
  suspend fun markInProgressBatch(eventIds: List<String>)

  @Query("UPDATE sync_outbox SET syncStatus = 'SYNCED', lastError = NULL WHERE eventId IN (:eventIds)")
  suspend fun markSyncedBatch(eventIds: List<String>)

  @Query(
    """
    UPDATE sync_outbox
    SET syncStatus = 'SYNCED', lastError = NULL
    WHERE entityType = :entityType
      AND entityId = :entityId
      AND createdAt <= :upToCreatedAt
      AND syncStatus IN ('PENDING', 'IN_PROGRESS', 'FAILED')
    """
  )
  suspend fun markSyncedForEntityUpTo(
    entityType: com.example.core.model.SyncEntityType,
    entityId: String,
    upToCreatedAt: Long,
  )

  @Query("UPDATE sync_outbox SET syncStatus = 'PENDING', lastError = NULL WHERE eventId IN (:eventIds)")
  suspend fun resetToPendingBatch(eventIds: List<String>)

  @Query("UPDATE sync_outbox SET syncStatus = :status, lastError = :lastError WHERE eventId = :eventId")
  suspend fun updateStatus(eventId: String, status: SyncStatus, lastError: String? = null)

  @Query(
    """
    UPDATE sync_outbox
    SET syncStatus = 'FAILED',
        retryCount = retryCount + 1,
        nextRetryAt = :nextRetryAt,
        lastError = :error
    WHERE eventId = :eventId
    """
  )
  suspend fun markRetry(eventId: String, nextRetryAt: Long, error: String)

  @Query(
    """
    UPDATE sync_outbox
    SET syncStatus = 'CONFLICT',
        lastError = :error
    WHERE eventId = :eventId
    """
  )
  suspend fun markConflict(eventId: String, error: String)

  @Query(
    """
    UPDATE sync_outbox
    SET syncStatus = 'PENDING',
        payloadJson = :payloadJson,
        retryCount = 0,
        nextRetryAt = :nextRetryAt,
        lastError = NULL
    WHERE eventId = :eventId
    """
  )
  suspend fun updatePayloadAndReset(eventId: String, payloadJson: String, nextRetryAt: Long)

  @Query(
    """
    SELECT COUNT(*) FROM sync_outbox
    WHERE entityType = :entityType
      AND entityId = :entityId
      AND syncStatus IN ('PENDING', 'IN_PROGRESS', 'FAILED', 'CONFLICT')
    """
  )
  suspend fun countActiveForEntity(entityType: com.example.core.model.SyncEntityType, entityId: String): Int

  @Query(
    """
    SELECT entityType || ':' || entityId FROM sync_outbox
    WHERE syncStatus IN ('PENDING', 'IN_PROGRESS', 'FAILED', 'CONFLICT')
    """
  )
  suspend fun getActiveEntityKeys(): List<String>

  @Query(
    """
    SELECT DISTINCT entityId FROM sync_outbox
    WHERE entityType = :entityType
      AND operation = 'CREATE'
      AND syncStatus IN ('PENDING', 'IN_PROGRESS', 'FAILED', 'CONFLICT')
    """
  )
  suspend fun getEntityIdsWithUnresolvedCreate(entityType: com.example.core.model.SyncEntityType): List<String>

  @Query("SELECT * FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId AND syncStatus = 'CONFLICT' LIMIT 1")
  suspend fun findConflictEventForEntity(entityType: com.example.core.model.SyncEntityType, entityId: String): SyncOutboxEntity?
}
