package com.example.features.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementFieldKey
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.PaymentMethod
import com.example.core.model.PocketStyle
import com.example.core.model.PocketStyleCodec
import com.example.core.model.ShalwarType
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.StyleSnapshot
import com.example.core.model.TailorValidators
import com.example.core.ui.PkrFormatUtils
import com.example.core.ui.localizedAppText
import com.example.data.local.entities.CustomerEntity
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import com.example.data.local.repository.OrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateOrderFormState(
  val selectedCustomer: CustomerEntity? = null,
  val availableMeasurements: List<MeasurementEntity> = emptyList(),
  val hasSavedMeasurement: Boolean = false,
  val sourceMeasurementId: String? = null,
  val alsoUpdateCustomerMeasurements: Boolean = true,
  // Measurement fields
  val kameezLambai: String = "",
  val tera: String = "",
  val bazu: String = "",
  val chati: String = "",
  val kamar: String = "",
  val gheraDaman: String = "",
  val galaCollar: String = "",
  val cuff: String = "",
  val shalwarLambai: String = "",
  val paincha: String = "",
  val gheraAsan: String = "",
  val shalwarGhair: String = "",
  // Style fields
  val neckStyle: NeckStyle? = null,
  val damanStyle: DamanStyle? = null,
  val pocketStyles: Set<PocketStyle> = emptySet(),
  val sleeveStyle: SleeveStyle? = null,
  val silaiStyle: SilaiStyle? = null,
  val shalwarType: ShalwarType? = null,
  // Order & billing fields
  val suitCount: Int = 1,
  val deliveryDateMillis: Long = PkrFormatUtils.startOfDayPlusDays(3),
  val totalBillPkrText: String = "",
  val advancePkrText: String = "0",
  val advancePaymentMethod: PaymentMethod = PaymentMethod.CASH,
  val notes: String = "",
  val isSaving: Boolean = false,
  val errorMessage: String? = null,
) {
  val selectedMeasurement: MeasurementEntity?
    get() = sourceMeasurementId?.let { id ->
      availableMeasurements.firstOrNull { it.measurementId == id }
    } ?: availableMeasurements.firstOrNull()

  val totalBillPkr: Long? get() = totalBillPkrText.toLongOrNull()
  val advancePkr: Long? get() = if (advancePkrText.isBlank()) 0L else advancePkrText.toLongOrNull()
  val remainingPreviewPkr: Long?
    get() {
      val total = totalBillPkr ?: return null
      val adv = advancePkr ?: return null
      return total - adv
    }
}

class CreateOrderViewModel(
  initialCustomerId: String?,
  private val customerRepository: CustomerRepository,
  private val orderRepository: OrderRepository,
  private val deviceSyncRepository: DeviceSyncRepository,
) : ViewModel() {

  val allCustomers: StateFlow<List<CustomerEntity>> =
    customerRepository.observeAllCustomers().stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000),
      initialValue = emptyList(),
    )

  private val _formState = MutableStateFlow(CreateOrderFormState())
  val formState: StateFlow<CreateOrderFormState> = _formState.asStateFlow()

  init {
    if (initialCustomerId != null) {
      selectCustomerById(initialCustomerId)
    }
  }

  fun selectCustomerById(customerId: String) {
    viewModelScope.launch {
      val customer = customerRepository.getCustomer(customerId) ?: return@launch
      loadCustomerMeasurements(customer)
    }
  }

  fun selectCustomer(customer: CustomerEntity) {
    viewModelScope.launch {
      loadCustomerMeasurements(customer)
    }
  }

  private suspend fun loadCustomerMeasurements(customer: CustomerEntity) {
    val measurements = customerRepository.getMeasurementHistory(customer.customerId)
    val defaultMeasurement = measurements.firstOrNull { it.isCurrent }
      ?: measurements.firstOrNull()
      ?: customerRepository.getCurrentMeasurement(customer.customerId)
    val resolvedList = if (measurements.isNotEmpty()) {
      measurements
    } else {
      listOfNotNull(defaultMeasurement)
    }
    applyCustomerAndMeasurements(customer, resolvedList, defaultMeasurement)
  }

  fun selectMeasurement(measurement: MeasurementEntity) {
    _formState.update { state ->
      applyMeasurementToState(state, measurement)
    }
  }

  private fun applyCustomerAndMeasurements(
    customer: CustomerEntity,
    measurements: List<MeasurementEntity>,
    selectedMeas: MeasurementEntity?,
  ) {
    _formState.update { state ->
      val base = state.copy(
        selectedCustomer = customer,
        availableMeasurements = measurements,
      )
      if (selectedMeas != null) {
        applyMeasurementToState(base, selectedMeas).copy(
          alsoUpdateCustomerMeasurements = false,
        )
      } else {
        base.copy(
          hasSavedMeasurement = false,
          sourceMeasurementId = null,
          alsoUpdateCustomerMeasurements = true,
          kameezLambai = "",
          tera = "",
          bazu = "",
          chati = "",
          kamar = "",
          gheraDaman = "",
          galaCollar = "",
          cuff = "",
          shalwarLambai = "",
          paincha = "",
          gheraAsan = "",
          shalwarGhair = "",
          neckStyle = null,
          damanStyle = null,
          pocketStyles = emptySet(),
          sleeveStyle = null,
          silaiStyle = null,
          shalwarType = null,
          errorMessage = null,
        )
      }
    }
  }

  private fun applyMeasurementToState(
    state: CreateOrderFormState,
    meas: MeasurementEntity,
  ): CreateOrderFormState {
    val rawMap = meas.rawInputsMap()
    return state.copy(
      hasSavedMeasurement = true,
      sourceMeasurementId = meas.measurementId,
      kameezLambai = initialFieldText(rawMap, MeasurementFieldKey.KAMEEZ_LAMBAI, meas.kameezLambai, required = true),
      tera = initialFieldText(rawMap, MeasurementFieldKey.TERA, meas.tera, required = true),
      bazu = initialFieldText(rawMap, MeasurementFieldKey.BAZU, meas.bazu, required = true),
      chati = initialFieldText(rawMap, MeasurementFieldKey.CHATI, meas.chati, required = true),
      kamar = initialFieldText(rawMap, MeasurementFieldKey.KAMAR, meas.kamar, required = false),
      gheraDaman = initialFieldText(rawMap, MeasurementFieldKey.GHERA_DAMAN, meas.gheraDaman, required = true),
      galaCollar = initialFieldText(rawMap, MeasurementFieldKey.GALA_COLLAR, meas.galaCollar, required = true),
      cuff = initialFieldText(rawMap, MeasurementFieldKey.CUFF, meas.cuff, required = false),
      shalwarLambai = initialFieldText(rawMap, MeasurementFieldKey.SHALWAR_LAMBAI, meas.shalwarLambai, required = true),
      paincha = initialFieldText(rawMap, MeasurementFieldKey.PAINCHA, meas.paincha, required = true),
      gheraAsan = initialFieldText(rawMap, MeasurementFieldKey.GHERA_ASAN, meas.gheraAsan, required = false),
      shalwarGhair = initialFieldText(rawMap, MeasurementFieldKey.SHALWAR_GHAIR, meas.shalwarGhair, required = false),
      neckStyle = meas.neckStyle,
      damanStyle = meas.damanStyle,
      pocketStyles = meas.pocketStyles,
      sleeveStyle = meas.sleeveStyle,
      silaiStyle = meas.silaiStyle,
      shalwarType = meas.shalwarType,
      errorMessage = null,
    )
  }

  fun updateForm(transform: (CreateOrderFormState) -> CreateOrderFormState) {
    _formState.update { transform(it).copy(errorMessage = null) }
  }

  fun submitOrder(onOrderCreated: (orderId: String) -> Unit) {
    val s = _formState.value
    val customer = s.selectedCustomer
      ?: return setError(localizedAppText("Please select a customer first.", "براہ کرم پہلے گاہک منتخب کریں۔"))

    val rawValues = TailorValidators.buildRawValuesMap(
      listOf(
        MeasurementFieldKey.KAMEEZ_LAMBAI to s.kameezLambai,
        MeasurementFieldKey.TERA to s.tera,
        MeasurementFieldKey.BAZU to s.bazu,
        MeasurementFieldKey.GALA_COLLAR to s.galaCollar,
        MeasurementFieldKey.CHATI to s.chati,
        MeasurementFieldKey.GHERA_DAMAN to s.gheraDaman,
        MeasurementFieldKey.SHALWAR_LAMBAI to s.shalwarLambai,
        MeasurementFieldKey.PAINCHA to s.paincha,
        MeasurementFieldKey.KAMAR to s.kamar,
        MeasurementFieldKey.CUFF to s.cuff,
        MeasurementFieldKey.GHERA_ASAN to s.gheraAsan,
        MeasurementFieldKey.SHALWAR_GHAIR to s.shalwarGhair,
      ),
    )

    val snapshot = MeasurementSnapshot(
      sourceMeasurementId = s.sourceMeasurementId,
      kameezLambai = TailorValidators.parseMeasurementInches(s.kameezLambai)
        ?: return setError(localizedAppText("Enter valid Kameez Lambai.", "درست قمیض لمبائی درج کریں۔")),
      tera = TailorValidators.parseMeasurementInches(s.tera)
        ?: return setError(localizedAppText("Enter valid Teera.", "درست تیرا درج کریں۔")),
      bazu = TailorValidators.parseMeasurementInches(s.bazu)
        ?: return setError(localizedAppText("Enter valid Asteen.", "درست آستین درج کریں۔")),
      chati = TailorValidators.parseMeasurementInches(s.chati)
        ?: return setError(localizedAppText("Enter valid Seena.", "درست سینہ درج کریں۔")),
      kamar = if (s.kamar.isBlank()) {
        0.0
      } else {
        TailorValidators.parseMeasurementInches(s.kamar)
          ?: return setError(localizedAppText("Enter valid Kamar.", "درست کمر درج کریں۔"))
      },
      gheraDaman = TailorValidators.parseMeasurementInches(s.gheraDaman)
        ?: return setError(localizedAppText("Enter valid Ghera / Daman.", "درست گھیرا / دامن درج کریں۔")),
      galaCollar = TailorValidators.parseMeasurementInches(s.galaCollar)
        ?: return setError(localizedAppText("Enter valid Gala.", "درست گلا درج کریں۔")),
      cuff = if (s.cuff.isBlank()) {
        0.0
      } else {
        TailorValidators.parseMeasurementInches(s.cuff)
          ?: return setError(localizedAppText("Enter valid Cuff.", "درست کف درج کریں۔"))
      },
      shalwarLambai = TailorValidators.parseMeasurementInches(s.shalwarLambai)
        ?: return setError(localizedAppText("Enter valid Shalwar.", "درست شلوار درج کریں۔")),
      paincha = TailorValidators.parseMeasurementInches(s.paincha)
        ?: return setError(localizedAppText("Enter valid Poncha.", "درست پانچہ درج کریں۔")),
      gheraAsan = if (s.gheraAsan.isBlank()) {
        0.0
      } else {
        TailorValidators.parseMeasurementInches(s.gheraAsan)
          ?: return setError(localizedAppText("Enter valid Asan.", "درست آسن درج کریں۔"))
      },
      shalwarGhair = if (s.shalwarGhair.isBlank()) {
        0.0
      } else {
        TailorValidators.parseMeasurementInches(s.shalwarGhair)
          ?: return setError(localizedAppText("Enter valid Shalwar Ghair.", "درست شلوار گھیرا درج کریں۔"))
      },
      rawValues = rawValues,
    )

    val orderedPockets = PocketStyleCodec.ordered(s.pocketStyles)
    val styleSnapshot = StyleSnapshot(
      neckStyle = s.neckStyle,
      damanStyle = s.damanStyle,
      pocketStyle = orderedPockets.firstOrNull(),
      pocketStyles = orderedPockets,
      sleeveStyle = s.sleeveStyle,
      silaiStyle = s.silaiStyle,
      shalwarType = s.shalwarType,
    )

    val totalPkr = s.totalBillPkr
      ?: return setError(localizedAppText("Enter valid Total Bill in PKR.", "روپے میں درست کل بل درج کریں۔"))
    val advancePkr = s.advancePkr
      ?: return setError(localizedAppText("Enter valid Advance Payment in PKR.", "روپے میں درست پیشگی ادائیگی درج کریں۔"))

    viewModelScope.launch {
      _formState.update { it.copy(isSaving = true, errorMessage = null) }
      val device = deviceSyncRepository.ensureLocalDeviceConfig()

      if (s.alsoUpdateCustomerMeasurements) {
        val measResult = customerRepository.saveMeasurement(
          customerId = customer.customerId,
          snapshot = snapshot,
          neckStyle = s.neckStyle,
          damanStyle = s.damanStyle,
          pocketStyles = s.pocketStyles,
          sleeveStyle = s.sleeveStyle,
          silaiStyle = s.silaiStyle,
          shalwarType = s.shalwarType,
          label = s.selectedMeasurement?.label ?: "Standard",
          deviceId = device.deviceId,
        )
        if (measResult.isFailure) {
          _formState.update {
            it.copy(
              isSaving = false,
              errorMessage = measResult.exceptionOrNull()?.message
                ?: localizedAppText("Invalid measurements.", "ناپ درست نہیں ہیں۔"),
            )
          }
          return@launch
        }
      }

      orderRepository.createOrder(
        customerId = customer.customerId,
        suitCount = s.suitCount,
        deliveryDateMillis = s.deliveryDateMillis,
        totalAmountPkr = totalPkr,
        initialAdvancePkr = advancePkr,
        initialPaymentMethod = s.advancePaymentMethod,
        measurementSnapshot = snapshot,
        styleSnapshot = styleSnapshot,
        notes = s.notes,
        deviceId = device.deviceId,
      ).fold(
        onSuccess = { created ->
          _formState.update { it.copy(isSaving = false) }
          onOrderCreated(created.orderId)
        },
        onFailure = { err ->
          _formState.update {
            it.copy(
              isSaving = false,
              errorMessage = err.message
                ?: localizedAppText("Failed to create order.", "آرڈر محفوظ نہیں ہو سکا۔"),
            )
          }
        },
      )
    }
  }

  private fun setError(msg: String) {
    _formState.update { it.copy(errorMessage = msg) }
  }

  private fun initialFieldText(
    rawMap: Map<String, String>,
    key: MeasurementFieldKey,
    numericValue: Double,
    required: Boolean,
  ): String {
    val raw = rawMap[key.name]?.trim()
    if (!raw.isNullOrEmpty()) return raw
    return if (required) formatNumeric(numericValue) else formatOptionalNumeric(numericValue)
  }

  private fun formatNumeric(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString()
    else value.toString().trimEnd('0').trimEnd('.')
  }

  private fun formatOptionalNumeric(value: Double): String =
    if (value <= 0.0) "" else formatNumeric(value)

  class Factory(
    private val initialCustomerId: String?,
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val deviceSyncRepository: DeviceSyncRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return CreateOrderViewModel(
        initialCustomerId = initialCustomerId,
        customerRepository = customerRepository,
        orderRepository = orderRepository,
        deviceSyncRepository = deviceSyncRepository,
      ) as T
    }
  }
}
