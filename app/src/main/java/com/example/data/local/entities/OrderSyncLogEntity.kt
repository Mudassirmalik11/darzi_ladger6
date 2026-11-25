package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object OrderSyncLogOperation {
  const val CREATE = "CREATE"
  const val PAYMENT = "PAYMENT"
  const val STATUS = "STATUS"
  const val DELETE = "DELETE"
  const val ORDER_NUMBER = "ORDER_NUMBER"
  const val UPDATE = "UPDATE"
  const val REMOTE_SYNC = "REMOTE_SYNC"
}

object OrderSyncLogStatus {
  const val QUEUED = "QUEUED"
  const val ATTEMPTING = "ATTEMPTING"
  const val SUCCEEDED = "SUCCEEDED"
  const val FAILED = "FAILED"
  const val CONFLICT = "CONFLICT"
  const val REMOTE_APPLIED = "REMOTE_APPLIED"
  const val SKIPPED = "SKIPPED"
}

@Entity(
  tableName = "order_sync_logs",
  indices = [
    Index(value = ["orderId"]),
    Index(value = ["timestamp"]),
    Index(value = ["status"]),
  ],
)
data class OrderSyncLogEntity(
  @PrimaryKey val logId: String,
  val orderId: String,
  val orderNumber: Int? = null,
  val operation: String,
  val status: String,
  val summary: String,
  val errorMessage: String? = null,
  val deviceId: String = "",
  val orderVersion: Long = 1L,
  val timestamp: Long = System.currentTimeMillis(),
)
