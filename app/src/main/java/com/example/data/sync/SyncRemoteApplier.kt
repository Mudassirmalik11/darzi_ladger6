package com.example.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.example.core.model.SyncEntityType
import com.example.data.local.database.DarziDatabase
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.entities.OrderEntity
import com.example.data.local.entities.OrderNumberReservationEntity
import com.example.data.local.entities.OrderSyncLogEntity
import com.example.data.local.entities.OrderSyncLogOperation
import com.example.data.local.entities.OrderSyncLogStatus
import com.example.data.local.entities.PaymentEntity
import java.util.UUID

internal class SyncRemoteApplier(
  private val database: DarziDatabase,
) {
  private val customerDao = database.customerDao()
  private val measurementDao = database.measurementDao()
  private val orderDao = database.orderDao()
  private val paymentDao = database.paymentDao()
  private val deviceSyncDao = database.deviceSyncDao()
  private val outboxDao = database.syncOutboxDao()
  private val orderSyncLogDao = database.orderSyncLogDao()

  suspend fun applyRemoteCustomers(remoteList: List<CustomerEntity>): Int {
    if (remoteList.isEmpty()) return 0
    var applied = 0
    database.withTransaction {
      val existingById = if (remoteList.size <= DELTA_LOOKUP_THRESHOLD) {
        customerDao.getByIds(remoteList.map { it.customerId }.distinct()).associateBy { it.customerId }
      } else {
        customerDao.getAllForBackup().associateBy { it.customerId }
      }
      val activeKeys = outboxDao.getActiveEntityKeys().toHashSet()
      val toUpsert = ArrayList<CustomerEntity>(remoteList.size)
      for (remote in remoteList) {
        if ("${SyncEntityType.CUSTOMER.name}:${remote.customerId}" in activeKeys) continue
        val local = existingById[remote.customerId]
        if (local == null || remote.version > local.version || remote.updatedAt > local.updatedAt) {
          toUpsert.add(remote)
        }
      }
      if (toUpsert.isNotEmpty()) {
        customerDao.insertAll(toUpsert)
        applied = toUpsert.size
      }
    }
    return applied
  }

  suspend fun applyRemoteMeasurements(
    remoteList: List<MeasurementEntity>,
    fetchMissingCustomers: (suspend (List<String>) -> List<CustomerEntity>)? = null,
  ): Int {
    if (remoteList.isEmpty()) return 0
    if (fetchMissingCustomers != null && remoteList.size <= DELTA_LOOKUP_THRESHOLD) {
      val neededCustomerIds = remoteList.map { it.customerId }.distinct()
      val existingCustomerIds = customerDao.getExistingIds(neededCustomerIds).toHashSet()
      val missingCustomerIds = neededCustomerIds.filter { it !in existingCustomerIds }
      if (missingCustomerIds.isNotEmpty()) {
        val fetched = fetchMissingCustomers(missingCustomerIds)
        if (fetched.isNotEmpty()) {
          applyRemoteCustomers(fetched)
        }
      }
    }

    var applied = 0
    database.withTransaction {
      val existingCustomerIds = if (remoteList.size <= DELTA_LOOKUP_THRESHOLD) {
        customerDao.getExistingIds(remoteList.map { it.customerId }.distinct()).toHashSet()
      } else {
        customerDao.getAllIds().toHashSet()
      }
      val existingById = if (remoteList.size <= DELTA_LOOKUP_THRESHOLD) {
        measurementDao.getByIds(remoteList.map { it.measurementId }.distinct()).associateBy { it.measurementId }
      } else {
        measurementDao.getAllForBackup().associateBy { it.measurementId }
      }
      val activeKeys = outboxDao.getActiveEntityKeys().toHashSet()
      val candidates = ArrayList<MeasurementEntity>(remoteList.size)

      for (remote in remoteList) {
        if (remote.customerId !in existingCustomerIds) continue
        if ("${SyncEntityType.MEASUREMENT.name}:${remote.measurementId}" in activeKeys) continue
        val local = existingById[remote.measurementId]
        if (local == null || remote.version > local.version || remote.updatedAt > local.updatedAt) {
          candidates.add(remote)
        }
      }

      if (candidates.isEmpty()) return@withTransaction

      val winningCurrentByCustomer = HashMap<String, MeasurementEntity>()
      for (candidate in candidates) {
        if (!candidate.isCurrent) continue
        val currentWinner = winningCurrentByCustomer[candidate.customerId]
        if (currentWinner == null || isNewerCurrentMeasurement(candidate, currentWinner)) {
          winningCurrentByCustomer[candidate.customerId] = candidate
        }
      }

      val toUpsert = candidates.map { candidate ->
        val winner = winningCurrentByCustomer[candidate.customerId]
        if (candidate.isCurrent && winner != null && winner.measurementId != candidate.measurementId) {
          candidate.copy(isCurrent = false)
        } else {
          candidate
        }
      }

      if (winningCurrentByCustomer.isNotEmpty()) {
        val maxUpdatedAt = winningCurrentByCustomer.values.maxOf { it.updatedAt }
        winningCurrentByCustomer.keys.toList().chunked(SQLITE_IN_CHUNK_SIZE).forEach { chunk ->
          measurementDao.clearCurrentFlagForCustomers(chunk, maxUpdatedAt)
        }
      }

      measurementDao.insertAll(toUpsert)
      applied = toUpsert.size
    }
    return applied
  }

  suspend fun applyRemoteOrders(
    remoteList: List<OrderEntity>,
    fetchMissingCustomers: (suspend (List<String>) -> List<CustomerEntity>)? = null,
  ): Int {
    if (remoteList.isEmpty()) return 0
    if (fetchMissingCustomers != null && remoteList.size <= DELTA_LOOKUP_THRESHOLD) {
      val neededCustomerIds = remoteList.map { it.customerId }.distinct()
      val existingCustomerIds = customerDao.getExistingIds(neededCustomerIds).toHashSet()
      val missingCustomerIds = neededCustomerIds.filter { it !in existingCustomerIds }
      if (missingCustomerIds.isNotEmpty()) {
        val fetched = fetchMissingCustomers(missingCustomerIds)
        if (fetched.isNotEmpty()) {
          applyRemoteCustomers(fetched)
        }
      }
    }

    var applied = 0
    database.withTransaction {
      val existingCustomerIds = if (remoteList.size <= DELTA_LOOKUP_THRESHOLD) {
        customerDao.getExistingIds(remoteList.map { it.customerId }.distinct()).toHashSet()
      } else {
        customerDao.getAllIds().toHashSet()
      }
      val existingById = if (remoteList.size <= DELTA_LOOKUP_THRESHOLD) {
        orderDao.getByIds(remoteList.map { it.orderId }.distinct()).associateBy { it.orderId }
      } else {
        orderDao.getAllForBackup().associateBy { it.orderId }
      }
      val activeKeys = outboxDao.getActiveEntityKeys().toHashSet()
      val toUpsert = ArrayList<OrderEntity>(remoteList.size)
      val logsToInsert = ArrayList<OrderSyncLogEntity>()
      val now = System.currentTimeMillis()
      for (remote in remoteList) {
        if (remote.customerId !in existingCustomerIds) {
          logsToInsert.add(
            OrderSyncLogEntity(
              logId = UUID.randomUUID().toString(),
              orderId = remote.orderId,
              orderNumber = remote.orderNumber,
              operation = OrderSyncLogOperation.REMOTE_SYNC,
              status = OrderSyncLogStatus.SKIPPED,
              summary = "Deferred cloud v${remote.version}: customer ${remote.customerId} not synced locally yet",
              deviceId = remote.lastModifiedByDevice,
              orderVersion = remote.version,
              timestamp = now,
            ),
          )
          runCatching {
            Log.w("OrderSyncLog", "[SKIPPED][REMOTE_SYNC] order=${remote.orderNumber ?: remote.orderId} missing customer=${remote.customerId}")
          }
          continue
        }
        val local = existingById[remote.orderId]
        val isNewerRemote = local == null || remote.version > local.version || remote.updatedAt > local.updatedAt
        if ("${SyncEntityType.ORDER.name}:${remote.orderId}" in activeKeys) {
          if (isNewerRemote) {
            logsToInsert.add(
              OrderSyncLogEntity(
                logId = UUID.randomUUID().toString(),
                orderId = remote.orderId,
                orderNumber = remote.orderNumber ?: local?.orderNumber,
                operation = OrderSyncLogOperation.REMOTE_SYNC,
                status = OrderSyncLogStatus.SKIPPED,
                summary = "Held cloud v${remote.version} (${remote.orderStatus.name}/${remote.paymentStatus.name}, deleted=${remote.isDeleted}) while local outbox change is active",
                deviceId = remote.lastModifiedByDevice,
                orderVersion = remote.version,
                timestamp = now,
              ),
            )
            runCatching {
              Log.w("OrderSyncLog", "[SKIPPED][REMOTE_SYNC] order=${remote.orderNumber ?: remote.orderId} held by active outbox")
            }
          }
          continue
        }
        if (isNewerRemote) {
          toUpsert.add(remote)
          val summary = describeRemoteOrderChange(local, remote)
          logsToInsert.add(
            OrderSyncLogEntity(
              logId = UUID.randomUUID().toString(),
              orderId = remote.orderId,
              orderNumber = remote.orderNumber,
              operation = OrderSyncLogOperation.REMOTE_SYNC,
              status = OrderSyncLogStatus.REMOTE_APPLIED,
              summary = summary,
              deviceId = remote.lastModifiedByDevice,
              orderVersion = remote.version,
              timestamp = now,
            ),
          )
          runCatching {
            Log.i("OrderSyncLog", "[REMOTE_APPLIED] order=${remote.orderNumber ?: remote.orderId} v${remote.version}: $summary")
          }
        }
      }
      if (toUpsert.isNotEmpty()) {
        orderDao.insertAll(toUpsert)
        applied = toUpsert.size
      }
      if (logsToInsert.isNotEmpty()) {
        orderSyncLogDao.insertAll(logsToInsert)
        orderSyncLogDao.pruneExcessLogs()
      }
    }
    return applied
  }

  private fun describeRemoteOrderChange(local: OrderEntity?, remote: OrderEntity): String {
    if (local == null) {
      return if (remote.isDeleted) {
        "Applied deleted cloud order (#${remote.orderNumber ?: "pending"})"
      } else {
        "Applied new cloud order (${remote.orderStatus.name}, ${remote.paymentStatus.name})"
      }
    }
    val changes = ArrayList<String>(4)
    if (remote.isDeleted && !local.isDeleted) {
      changes.add("deleted remotely")
    } else if (!remote.isDeleted && local.isDeleted) {
      changes.add("restored")
    }
    if (remote.paymentStatus != local.paymentStatus || remote.paidAmountPkr != local.paidAmountPkr) {
      changes.add("payment ${local.paymentStatus.name} -> ${remote.paymentStatus.name} (Rs. ${remote.paidAmountPkr}/${remote.totalAmountPkr})")
    }
    if (remote.orderStatus != local.orderStatus) {
      changes.add("status ${local.orderStatus.name} -> ${remote.orderStatus.name}")
    }
    if (remote.orderNumber != local.orderNumber && remote.orderNumber != null) {
      changes.add("order #${remote.orderNumber}")
    }
    val detail = if (changes.isEmpty()) "v${local.version} -> v${remote.version}" else changes.joinToString(", ")
    return "Applied cloud update: $detail"
  }

  suspend fun applyRemotePayments(
    remoteList: List<PaymentEntity>,
    fetchMissingOrders: (suspend (List<String>) -> List<OrderEntity>)? = null,
    fetchMissingCustomers: (suspend (List<String>) -> List<CustomerEntity>)? = null,
  ): Int {
    if (remoteList.isEmpty()) return 0
    if (fetchMissingOrders != null && remoteList.size <= DELTA_LOOKUP_THRESHOLD) {
      val neededOrderIds = remoteList.map { it.orderId }.distinct()
      val existingOrderIds = orderDao.getExistingIds(neededOrderIds).toHashSet()
      val missingOrderIds = neededOrderIds.filter { it !in existingOrderIds }
      if (missingOrderIds.isNotEmpty()) {
        val fetchedOrders = fetchMissingOrders(missingOrderIds)
        if (fetchedOrders.isNotEmpty()) {
          applyRemoteOrders(fetchedOrders, fetchMissingCustomers)
        }
      }
    }

    var applied = 0
    database.withTransaction {
      val existingOrderIds = if (remoteList.size <= DELTA_LOOKUP_THRESHOLD) {
        orderDao.getExistingIds(remoteList.map { it.orderId }.distinct()).toHashSet()
      } else {
        orderDao.getAllIds().toHashSet()
      }
      val existingPaymentIds = if (remoteList.size <= DELTA_LOOKUP_THRESHOLD) {
        paymentDao.getExistingIds(remoteList.map { it.paymentId }.distinct()).toHashSet()
      } else {
        paymentDao.getAllIds().toHashSet()
      }
      val toInsert = ArrayList<PaymentEntity>(remoteList.size)
      for (remote in remoteList) {
        if (remote.orderId !in existingOrderIds) continue
        if (remote.paymentId !in existingPaymentIds) {
          toInsert.add(remote)
        }
      }
      if (toInsert.isNotEmpty()) {
        paymentDao.insertAll(toInsert)
        applied = toInsert.size
      }
    }
    return applied
  }

  suspend fun applyRemoteReservations(remoteList: List<OrderNumberReservationEntity>) {
    if (remoteList.isEmpty()) return
    database.withTransaction {
      for (remote in remoteList) {
        if (deviceSyncDao.getReservationById(remote.reservationId) == null) {
          deviceSyncDao.upsertReservation(remote)
        }
      }
    }
  }

  private fun isNewerCurrentMeasurement(
    candidate: MeasurementEntity,
    currentWinner: MeasurementEntity,
  ): Boolean {
    if (candidate.updatedAt != currentWinner.updatedAt) {
      return candidate.updatedAt > currentWinner.updatedAt
    }
    if (candidate.createdAt != currentWinner.createdAt) {
      return candidate.createdAt > currentWinner.createdAt
    }
    if (candidate.version != currentWinner.version) {
      return candidate.version > currentWinner.version
    }
    return candidate.measurementId > currentWinner.measurementId
  }

  private companion object {
    const val DELTA_LOOKUP_THRESHOLD = 50
    const val SQLITE_IN_CHUNK_SIZE = 400
  }
}
