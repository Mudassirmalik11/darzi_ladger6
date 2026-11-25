package com.example.data.local.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.OrderSyncLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderSyncLogDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(log: OrderSyncLogEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(logs: List<OrderSyncLogEntity>)

  @Query("SELECT * FROM order_sync_logs ORDER BY timestamp DESC LIMIT :limit")
  fun observeRecentLogs(limit: Int = 60): Flow<List<OrderSyncLogEntity>>

  @Query("SELECT * FROM order_sync_logs WHERE orderId = :orderId ORDER BY timestamp DESC")
  fun observeLogsForOrder(orderId: String): Flow<List<OrderSyncLogEntity>>

  @Query("SELECT * FROM order_sync_logs ORDER BY timestamp DESC LIMIT :limit")
  suspend fun getRecentLogs(limit: Int = 60): List<OrderSyncLogEntity>

  @Query("SELECT * FROM order_sync_logs WHERE orderId = :orderId ORDER BY timestamp DESC")
  suspend fun getLogsForOrder(orderId: String): List<OrderSyncLogEntity>

  @Query(
    """
    DELETE FROM order_sync_logs
    WHERE logId NOT IN (
      SELECT logId FROM order_sync_logs ORDER BY timestamp DESC LIMIT :keepCount
    )
    """
  )
  suspend fun pruneExcessLogs(keepCount: Int = 250)

  @Query("DELETE FROM order_sync_logs")
  suspend fun clearLogs()
}
