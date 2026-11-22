package com.example.features.printing

import com.example.core.model.ShopUserRole
import com.example.core.model.SnapshotJson
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.ShopPreferences
import com.example.core.preferences.ShopPreferencesRepository
import com.example.data.local.daos.OrderWithCustomer
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.entities.PaymentEntity
import com.example.data.local.entities.PrinterConfigEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.ShopRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class SlipPreviewPayload(
  val slipType: PrintSlipType,
  val previewText: String,
  val parchiData: ParchiSlipData? = null,
)

class ThermalPrintManager(
  private val printerClient: ThermalPrinterClient,
  private val deviceSyncRepository: DeviceSyncRepository,
  private val shopRepository: ShopRepository,
  private val shopPreferencesRepository: ShopPreferencesRepository,
  private val customerRepository: CustomerRepository? = null,
) {

  fun observeDefaultPrinter(): Flow<PrinterConfigEntity?> =
    deviceSyncRepository.observeDefaultPrinter()

  private suspend fun resolveCustomerDisplayId(orderDetail: OrderWithCustomer): String {
    val explicit = orderDetail.customerCustomId.trim()
    if (explicit.isNotEmpty()) return explicit
    val repo = customerRepository ?: return orderDetail.order.customerId.take(6)
    val all = runCatching { repo.observeAllCustomers().first() }.getOrElse { emptyList() }
    val seqIndex = all
      .sortedWith(compareBy<CustomerEntity> { it.createdAt }.thenBy { it.customerId })
      .indexOfFirst { it.customerId == orderDetail.order.customerId }
    return if (seqIndex >= 0) (seqIndex + 1).toString() else orderDetail.order.customerId.take(6)
  }

  private suspend fun safePreferences(): ShopPreferences {
    val basePrefs = runCatching { shopPreferencesRepository.preferencesFlow.first() }
      .getOrElse { e ->
        if (e is CancellationException) throw e
        ShopPreferences()
      }
    val activeRole = runCatching { deviceSyncRepository.observeDeviceConfig().first()?.userRole }
      .getOrNull()
    return if (activeRole == ShopUserRole.STAFF.name) {
      basePrefs.copy(language = AppLanguage.URDU)
    } else {
      basePrefs
    }
  }

  private fun textFor(language: AppLanguage, english: String, urdu: String): String =
    if (language == AppLanguage.URDU) urdu else english

  suspend fun buildPreviewText(
    orderDetail: OrderWithCustomer,
    payments: List<PaymentEntity>,
    slipType: PrintSlipType,
  ): String = buildPreviewPayload(orderDetail, payments, slipType).previewText

  suspend fun buildPreviewPayload(
    orderDetail: OrderWithCustomer,
    payments: List<PaymentEntity>,
    slipType: PrintSlipType,
  ): SlipPreviewPayload {
    val prefs = safePreferences()
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val shopContact = shop.contactNumber.takeIf { it.isNotBlank() } ?: prefs.shopContact
    val paperWidth = PaperWidth.fromMm(prefs.paperWidthMm)

    return when (slipType) {
      PrintSlipType.CUSTOMER_RECEIPT -> SlipPreviewPayload(
        slipType = slipType,
        previewText = ThermalReceiptFormatter.formatCustomerReceiptLines(
          shopName = shopName,
          shopContact = shopContact,
          orderDetail = orderDetail,
          payments = payments,
          paperWidth = paperWidth,
          preserveUnicode = true,
          language = prefs.language,
        ).joinToString("\n"),
      )

      PrintSlipType.KARIGAR_SLIP -> {
        val measurement = runCatching {
          SnapshotJson.decodeMeasurement(orderDetail.order.measurementSnapshotJson)
        }.getOrElse {
          return SlipPreviewPayload(
            slipType = slipType,
            previewText = textFor(
              prefs.language,
              "Order measurement snapshot is unavailable.",
              "آرڈر کے ناپ کا ریکارڈ دستیاب نہیں ہے۔",
            ),
          )
        }
        val style = runCatching {
          SnapshotJson.decodeStyle(orderDetail.order.styleSnapshotJson)
        }.getOrElse {
          return SlipPreviewPayload(
            slipType = slipType,
            previewText = textFor(
              prefs.language,
              "Order style snapshot is unavailable.",
              "آرڈر کے ڈیزائن کا ریکارڈ دستیاب نہیں ہے۔",
            ),
          )
        }
        val customerDisplayId = resolveCustomerDisplayId(orderDetail)
        SlipPreviewPayload(
          slipType = slipType,
          previewText = ThermalReceiptFormatter.formatKarigarSlipLines(
            shopName = shopName,
            orderDetail = orderDetail,
            measurement = measurement,
            style = style,
            paperWidth = paperWidth,
            preserveUnicode = true,
            language = prefs.language,
            customerDisplayId = customerDisplayId,
          ).joinToString("\n"),
          parchiData = ThermalReceiptFormatter.buildKarigarParchiData(
            shopName = shopName,
            orderDetail = orderDetail,
            measurement = measurement,
            style = style,
            language = prefs.language,
            customerDisplayId = customerDisplayId,
          ),
        )
      }

      PrintSlipType.MEASUREMENT_SLIP -> {
        val measurement = runCatching {
          SnapshotJson.decodeMeasurement(orderDetail.order.measurementSnapshotJson)
        }.getOrElse {
          return SlipPreviewPayload(
            slipType = slipType,
            previewText = textFor(
              prefs.language,
              "Order measurement snapshot is unavailable.",
              "آرڈر کے ناپ کا ریکارڈ دستیاب نہیں ہے۔",
            ),
          )
        }
        val style = runCatching {
          SnapshotJson.decodeStyle(orderDetail.order.styleSnapshotJson)
        }.getOrElse {
          return SlipPreviewPayload(
            slipType = slipType,
            previewText = textFor(
              prefs.language,
              "Order style snapshot is unavailable.",
              "آرڈر کے ڈیزائن کا ریکارڈ دستیاب نہیں ہے۔",
            ),
          )
        }
        val customerDisplayId = resolveCustomerDisplayId(orderDetail)
        SlipPreviewPayload(
          slipType = slipType,
          previewText = ThermalReceiptFormatter.formatMeasurementSlipLines(
            shopName = shopName,
            customerName = orderDetail.customerName,
            customerPhone = orderDetail.customerPhone,
            customerDisplayId = customerDisplayId,
            measurementLabel = "Standard",
            measurement = measurement,
            style = style,
            recordedAtMillis = orderDetail.order.createdAt,
            paperWidth = paperWidth,
            preserveUnicode = true,
            language = prefs.language,
          ).joinToString("\n"),
          parchiData = ThermalReceiptFormatter.buildMeasurementParchiData(
            shopName = shopName,
            customerName = orderDetail.customerName,
            customerPhone = orderDetail.customerPhone,
            customerDisplayId = customerDisplayId,
            measurementLabel = "Standard",
            measurement = measurement,
            style = style,
            recordedAtMillis = orderDetail.order.createdAt,
            language = prefs.language,
          ),
        )
      }

      PrintSlipType.TEST_PAGE -> {
        val printerName = deviceSyncRepository.observeDefaultPrinter().first()?.printerName
          ?: textFor(prefs.language, "Thermal Printer", "تھرمل پرنٹر")
        SlipPreviewPayload(
          slipType = slipType,
          previewText = ThermalReceiptFormatter.formatTestPageLines(
            shopName = shopName,
            printerName = printerName,
            paperWidth = paperWidth,
            preserveUnicode = true,
            language = prefs.language,
          ).joinToString("\n"),
        )
      }
    }
  }

  suspend fun buildMeasurementPreviewText(
    customer: CustomerEntity,
    customerDisplayId: String?,
    measurement: MeasurementEntity,
  ): String = buildMeasurementPreviewPayload(customer, customerDisplayId, measurement).previewText

  suspend fun buildMeasurementPreviewPayload(
    customer: CustomerEntity,
    customerDisplayId: String?,
    measurement: MeasurementEntity,
  ): SlipPreviewPayload {
    val prefs = safePreferences()
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val paperWidth = PaperWidth.fromMm(prefs.paperWidthMm)
    val measSnapshot = measurement.toMeasurementSnapshot()
    val styleSnapshot = measurement.toStyleSnapshot()

    return SlipPreviewPayload(
      slipType = PrintSlipType.MEASUREMENT_SLIP,
      previewText = ThermalReceiptFormatter.formatMeasurementSlipLines(
        shopName = shopName,
        customerName = customer.name,
        customerPhone = customer.phone,
        customerDisplayId = customerDisplayId,
        measurementLabel = measurement.label,
        measurement = measSnapshot,
        style = styleSnapshot,
        recordedAtMillis = measurement.createdAt,
        paperWidth = paperWidth,
        preserveUnicode = true,
        language = prefs.language,
      ).joinToString("\n"),
      parchiData = ThermalReceiptFormatter.buildMeasurementParchiData(
        shopName = shopName,
        customerName = customer.name,
        customerPhone = customer.phone,
        customerDisplayId = customerDisplayId,
        measurementLabel = measurement.label,
        measurement = measSnapshot,
        style = styleSnapshot,
        recordedAtMillis = measurement.createdAt,
        language = prefs.language,
      ),
    )
  }

  suspend fun printCustomerReceipt(
    orderDetail: OrderWithCustomer,
    payments: List<PaymentEntity>,
  ): Result<String> {
    val prefs = safePreferences()
    val printer = deviceSyncRepository.observeDefaultPrinter().first()
      ?: return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Select a Bluetooth thermal printer in Settings first.",
            "پہلے ترتیبات میں بلوٹوتھ تھرمل پرنٹر منتخب کریں۔",
          ),
        ),
      )
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val shopContact = shop.contactNumber.takeIf { it.isNotBlank() } ?: prefs.shopContact
    val width = PaperWidth.fromMm(prefs.paperWidthMm)

    val bytes = ThermalReceiptFormatter.formatCustomerReceiptBytes(
      shopName = shopName,
      shopContact = shopContact,
      orderDetail = orderDetail,
      payments = payments,
      paperWidth = width,
      language = prefs.language,
    )
    return printerClient.printBytes(printer.printerAddress, bytes).map {
      textFor(
        prefs.language,
        "Customer receipt sent to ${printer.printerName}",
        "گاہک کی رسید ${printer.printerName} پر بھیج دی گئی",
      )
    }
  }

  suspend fun printKarigarSlip(orderDetail: OrderWithCustomer): Result<String> {
    val prefs = safePreferences()
    val printer = deviceSyncRepository.observeDefaultPrinter().first()
      ?: return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Select a Bluetooth thermal printer in Settings first.",
            "پہلے ترتیبات میں بلوٹوتھ تھرمل پرنٹر منتخب کریں۔",
          ),
        ),
      )
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val width = PaperWidth.fromMm(prefs.paperWidthMm)

    val measurement = runCatching {
      SnapshotJson.decodeMeasurement(orderDetail.order.measurementSnapshotJson)
    }.getOrElse {
      return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Order measurement snapshot is corrupted or missing.",
            "آرڈر کے ناپ کا ریکارڈ خراب یا غائب ہے۔",
          ),
        ),
      )
    }
    val style = runCatching {
      SnapshotJson.decodeStyle(orderDetail.order.styleSnapshotJson)
    }.getOrElse {
      return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Order style snapshot is corrupted or missing.",
            "آرڈر کے ڈیزائن کا ریکارڈ خراب یا غائب ہے۔",
          ),
        ),
      )
    }

    val customerDisplayId = resolveCustomerDisplayId(orderDetail)
    val bytes = ThermalReceiptFormatter.formatKarigarSlipBytes(
      shopName = shopName,
      orderDetail = orderDetail,
      measurement = measurement,
      style = style,
      paperWidth = width,
      language = prefs.language,
      customerDisplayId = customerDisplayId,
    )
    return printerClient.printBytes(printer.printerAddress, bytes).map {
      textFor(
        prefs.language,
        "Karigar workshop slip sent to ${printer.printerName}",
        "کاریگر ورکشاپ پرچی ${printer.printerName} پر بھیج دی گئی",
      )
    }
  }

  suspend fun printMeasurementSlip(
    customer: CustomerEntity,
    customerDisplayId: String?,
    measurement: MeasurementEntity,
  ): Result<String> {
    val prefs = safePreferences()
    val printer = deviceSyncRepository.observeDefaultPrinter().first()
      ?: return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Select a Bluetooth thermal printer in Settings first.",
            "پہلے ترتیبات میں بلوٹوتھ تھرمل پرنٹر منتخب کریں۔",
          ),
        ),
      )
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val width = PaperWidth.fromMm(prefs.paperWidthMm)

    val bytes = ThermalReceiptFormatter.formatMeasurementSlipBytes(
      shopName = shopName,
      customerName = customer.name,
      customerPhone = customer.phone,
      customerDisplayId = customerDisplayId,
      measurementLabel = measurement.label,
      measurement = measurement.toMeasurementSnapshot(),
      style = measurement.toStyleSnapshot(),
      recordedAtMillis = measurement.createdAt,
      paperWidth = width,
      language = prefs.language,
    )
    return printerClient.printBytes(printer.printerAddress, bytes).map {
      textFor(
        prefs.language,
        "Measurement slip sent to ${printer.printerName}",
        "ناپ کی پرچی ${printer.printerName} پر بھیج دی گئی",
      )
    }
  }

  suspend fun printTestPage(): Result<String> {
    val prefs = safePreferences()
    val printer = deviceSyncRepository.observeDefaultPrinter().first()
      ?: return Result.failure(
        IllegalStateException(
          textFor(
            prefs.language,
            "Select a Bluetooth thermal printer first.",
            "پہلے بلوٹوتھ تھرمل پرنٹر منتخب کریں۔",
          ),
        ),
      )
    val shop = shopRepository.ensureDefaultShop()
    val shopName = shop.shopName.takeIf { it.isNotBlank() } ?: prefs.shopName
    val width = PaperWidth.fromMm(prefs.paperWidthMm)

    val bytes = ThermalReceiptFormatter.formatTestPageBytes(
      shopName = shopName,
      printerName = printer.printerName,
      paperWidth = width,
      language = prefs.language,
    )
    return printerClient.printBytes(printer.printerAddress, bytes).map {
      textFor(
        prefs.language,
        "Test slip sent to ${printer.printerName}",
        "ٹیسٹ پرچی ${printer.printerName} پر بھیج دی گئی",
      )
    }
  }
}
