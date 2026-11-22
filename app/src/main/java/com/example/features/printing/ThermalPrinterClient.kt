package com.example.features.printing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class PrinterPacingMode {
  FAST,
  SAFE,
}

data class DiscoveredPrinter(
  val name: String,
  val address: String,
  val isBonded: Boolean,
)

interface ThermalPrinterClient {
  fun isBluetoothSupported(): Boolean
  fun isBluetoothEnabled(): Boolean
  fun hasConnectPermission(): Boolean
  fun hasScanPermission(): Boolean
  suspend fun getPairedPrinters(): Result<List<DiscoveredPrinter>>
  fun discoverNearbyPrinters(): Flow<DiscoveredPrinter>
  suspend fun printBytes(printerAddress: String, bytes: ByteArray): Result<Unit>
  suspend fun printBytes(
    printerAddress: String,
    bytes: ByteArray,
    pacingMode: PrinterPacingMode,
  ): Result<Unit> = printBytes(printerAddress, bytes)
}

class AndroidBluetoothPrinterClient(
  private val context: Context,
) : ThermalPrinterClient {

  private val appContext = context.applicationContext
  private val printMutex = Mutex()
  private val bluetoothAdapter: BluetoothAdapter? by lazy {
    val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    manager?.adapter
  }

  override fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

  override fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

  override fun hasConnectPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.BLUETOOTH_CONNECT,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

  override fun hasScanPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.BLUETOOTH_SCAN,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

  @SuppressLint("MissingPermission")
  override suspend fun getPairedPrinters(): Result<List<DiscoveredPrinter>> = withContext(Dispatchers.IO) {
    val adapter = bluetoothAdapter
      ?: return@withContext Result.failure(IllegalStateException("Bluetooth hardware is not supported on this device."))
    if (!hasConnectPermission()) {
      return@withContext Result.failure(SecurityException("Bluetooth permission is required to view paired printers."))
    }
    if (!adapter.isEnabled) {
      return@withContext Result.failure(IllegalStateException("Bluetooth is turned off. Please enable Bluetooth."))
    }

    runCatching {
      adapter.bondedDevices.orEmpty()
        .map { device ->
          DiscoveredPrinter(
            name = device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Printer",
            address = device.address,
            isBonded = true,
          )
        }
        .sortedBy { it.name.lowercase() }
    }
  }

  @SuppressLint("MissingPermission")
  override fun discoverNearbyPrinters(): Flow<DiscoveredPrinter> = callbackFlow {
    val adapter = bluetoothAdapter
    if (adapter == null || !adapter.isEnabled || !hasScanPermission() || !hasConnectPermission()) {
      close()
      return@callbackFlow
    }

    val seenAddresses = mutableSetOf<String>()
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        if (intent?.action != BluetoothDevice.ACTION_FOUND) return
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (device != null && seenAddresses.add(device.address)) {
          trySend(
            DiscoveredPrinter(
              name = device.name?.takeIf { it.isNotBlank() } ?: "Nearby Device",
              address = device.address,
              isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
            ),
          )
        }
      }
    }

    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
    ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    if (adapter.isDiscovering) {
      adapter.cancelDiscovery()
    }
    adapter.startDiscovery()

    awaitClose {
      runCatching { appContext.unregisterReceiver(receiver) }
      runCatching {
        if (hasScanPermission() && adapter.isDiscovering) {
          adapter.cancelDiscovery()
        }
      }
    }
  }

  override suspend fun printBytes(
    printerAddress: String,
    bytes: ByteArray,
  ): Result<Unit> = printBytes(printerAddress, bytes, PrinterPacingMode.FAST)

  @SuppressLint("MissingPermission")
  override suspend fun printBytes(
    printerAddress: String,
    bytes: ByteArray,
    pacingMode: PrinterPacingMode,
  ): Result<Unit> = printMutex.withLock {
    withContext(Dispatchers.IO) {
      val adapter = bluetoothAdapter
        ?: return@withContext Result.failure(IllegalStateException("Bluetooth hardware is not available on this device."))
      if (!hasConnectPermission()) {
        return@withContext Result.failure(SecurityException("Bluetooth permission is required to print."))
      }
      if (!adapter.isEnabled) {
        return@withContext Result.failure(IllegalStateException("Bluetooth is turned off. Turn on Bluetooth and retry."))
      }
      if (printerAddress.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("No Bluetooth printer address configured."))
      }

      var socket: BluetoothSocket? = null
      try {
        if (hasScanPermission() && adapter.isDiscovering) {
          adapter.cancelDiscovery()
        }
        val device = adapter.getRemoteDevice(printerAddress)
        socket = connectRfcommSocket(device)
        val out = socket.outputStream
        val chunkSize = when (pacingMode) {
          PrinterPacingMode.FAST -> 2048
          PrinterPacingMode.SAFE -> 512
        }
        val interChunkDelayMs = when (pacingMode) {
          PrinterPacingMode.FAST -> 3L
          PrinterPacingMode.SAFE -> 12L
        }
        var offset = 0
        while (offset < bytes.size) {
          val len = (bytes.size - offset).coerceAtMost(chunkSize)
          out.write(bytes, offset, len)
          offset += len
          if (offset < bytes.size && bytes.size > chunkSize) {
            delay(interChunkDelayMs)
          }
        }
        out.flush()
        // Wait proportionally for the RFCOMM radio buffer to drain so the printer never
        // receives a truncated GS v 0 band (which causes raw binary bytes to print as text).
        val drainDelayMs = when {
          bytes.size <= 1024 -> 300L
          pacingMode == PrinterPacingMode.SAFE -> (bytes.size / 22L).coerceIn(1000L, 2800L)
          else -> (bytes.size / 42L).coerceIn(850L, 1800L)
        }
        delay(drainDelayMs)
        Result.success(Unit)
      } catch (e: IOException) {
        Result.failure(
          IOException("Could not reach thermal printer ($printerAddress). Check that it is powered on and paired.", e),
        )
      } catch (e: IllegalArgumentException) {
        Result.failure(IllegalArgumentException("Invalid Bluetooth printer address: $printerAddress", e))
      } catch (e: Exception) {
        Result.failure(e)
      } finally {
        runCatching { socket?.close() }
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun connectRfcommSocket(device: BluetoothDevice): BluetoothSocket {
    val secureSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
    return try {
      secureSocket.connect()
      secureSocket
    } catch (primaryError: IOException) {
      runCatching { secureSocket.close() }
      val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
      try {
        insecureSocket.connect()
        insecureSocket
      } catch (fallbackError: IOException) {
        runCatching { insecureSocket.close() }
        val channelSocket = runCatching {
          @Suppress("DiscouragedPrivateApi")
          val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
          method.invoke(device, 1) as? BluetoothSocket
        }.getOrNull()
        if (channelSocket != null) {
          try {
            channelSocket.connect()
            channelSocket
          } catch (_: IOException) {
            runCatching { channelSocket.close() }
            throw primaryError
          }
        } else {
          throw primaryError
        }
      }
    }
  }

  companion object {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun requiredRuntimePermissions(): Array<String> =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
          Manifest.permission.BLUETOOTH_CONNECT,
          Manifest.permission.BLUETOOTH_SCAN,
        )
      } else {
        emptyArray()
      }
  }
}
