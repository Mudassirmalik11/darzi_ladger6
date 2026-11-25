package com.example.features.measurements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementFieldKey
import com.example.core.model.MeasurementSnapshot
import com.example.core.model.NeckStyle
import com.example.core.model.PocketStyle
import com.example.core.model.ShalwarType
import com.example.core.model.ShoulderType
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.model.TailorValidators
import com.example.core.ui.localizedAppText
import com.example.data.local.entities.MeasurementEntity
import com.example.data.local.repository.CustomerRepository
import com.example.data.local.repository.DeviceSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeasurementFormState(
  val customerName: String = "",
  val label: String = localizedAppText("Standard", "معیاری"),
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
  val neckStyle: NeckStyle? = null,
  val damanStyle: DamanStyle? = null,
  val pocketStyles: Set<PocketStyle> = emptySet(),
  val sleeveStyle: SleeveStyle? = null,
  val silaiStyle: SilaiStyle? = null,
  val shalwarType: ShalwarType? = null,
  val shoulderType: ShoulderType? = null,
  val notes: String = "",
  val isLoaded: Boolean = false,
  val isSaving: Boolean = false,
  val hasAttemptedSave: Boolean = false,
  val errorMessage: String? = null,
) {
  fun valueFor(key: MeasurementFieldKey): String = when (key) {
    MeasurementFieldKey.KAMEEZ_LAMBAI -> kameezLambai
    MeasurementFieldKey.TERA -> tera
    MeasurementFieldKey.BAZU -> bazu
    MeasurementFieldKey.GALA_COLLAR -> galaCollar
    MeasurementFieldKey.CHATI -> chati
    MeasurementFieldKey.GHERA_DAMAN -> gheraDaman
    MeasurementFieldKey.SHALWAR_LAMBAI -> shalwarLambai
    MeasurementFieldKey.PAINCHA -> paincha
    MeasurementFieldKey.KAMAR -> kamar
    MeasurementFieldKey.CUFF -> cuff
    MeasurementFieldKey.GHERA_ASAN -> gheraAsan
    MeasurementFieldKey.SHALWAR_GHAIR -> shalwarGhair
  }

  fun errorFor(key: MeasurementFieldKey): String? =
    TailorValidators.validateMeasurementFieldInput(
      key = key,
      rawText = valueFor(key),
      allowBlankRequired = !hasAttemptedSave,
    )
}

class MeasurementEditorViewModel(
  private val customerId: String,
  private val sourceMeasurementId: String?,
  private val customerRepository: CustomerRepository,
  private val deviceSyncRepository: DeviceSyncRepository,
) : ViewModel() {

  private val _formState = MutableStateFlow(MeasurementFormState())
  val formState: StateFlow<MeasurementFormState> = _formState.asStateFlow()

  init {
    viewModelScope.launch {
      val customer = customerRepository.getCustomer(customerId)
      val existing: MeasurementEntity? = sourceMeasurementId?.let {
        customerRepository.getMeasurementById(it)
      }
      val seed = existing ?: customerRepository.getCurrentMeasurement(customerId)
      _formState.update { state ->
        if (seed != null) {
          val rawMap = seed.rawInputsMap()
          state.copy(
            customerName = customer?.name ?: "",
            label = if (seed.label.equals("Standard", ignoreCase = true)) {
              localizedAppText("Standard", "معیاری")
            } else {
              seed.label
            },
            kameezLambai = initialFieldText(rawMap, MeasurementFieldKey.KAMEEZ_LAMBAI, seed.kameezLambai),
            tera = initialFieldText(rawMap, MeasurementFieldKey.TERA, seed.tera),
            bazu = initialFieldText(rawMap, MeasurementFieldKey.BAZU, seed.bazu),
            chati = initialFieldText(rawMap, MeasurementFieldKey.CHATI, seed.chati),
            kamar = initialFieldText(rawMap, MeasurementFieldKey.KAMAR, seed.kamar),
            gheraDaman = initialFieldText(rawMap, MeasurementFieldKey.GHERA_DAMAN, seed.gheraDaman),
            galaCollar = initialFieldText(rawMap, MeasurementFieldKey.GALA_COLLAR, seed.galaCollar),
            cuff = initialFieldText(rawMap, MeasurementFieldKey.CUFF, seed.cuff),
            shalwarLambai = initialFieldText(rawMap, MeasurementFieldKey.SHALWAR_LAMBAI, seed.shalwarLambai),
            paincha = initialFieldText(rawMap, MeasurementFieldKey.PAINCHA, seed.paincha),
            gheraAsan = initialFieldText(rawMap, MeasurementFieldKey.GHERA_ASAN, seed.gheraAsan),
            shalwarGhair = initialFieldText(rawMap, MeasurementFieldKey.SHALWAR_GHAIR, seed.shalwarGhair),
            neckStyle = seed.neckStyle,
            damanStyle = seed.damanStyle,
            pocketStyles = seed.pocketStyles,
            sleeveStyle = seed.sleeveStyle,
            silaiStyle = seed.silaiStyle,
            shalwarType = seed.shalwarType,
            shoulderType = seed.shoulderType,
            notes = seed.notes,
            isLoaded = true,
          )
        } else {
          state.copy(
            customerName = customer?.name ?: "",
            isLoaded = true,
          )
        }
      }
    }
  }

  fun loadFromMeasurement(measurement: MeasurementEntity) {
    val rawMap = measurement.rawInputsMap()
    _formState.update { state ->
      state.copy(
        label = if (measurement.label.equals("Standard", ignoreCase = true)) {
          localizedAppText("Standard", "معیاری")
        } else {
          measurement.label
        },
        kameezLambai = initialFieldText(rawMap, MeasurementFieldKey.KAMEEZ_LAMBAI, measurement.kameezLambai),
        tera = initialFieldText(rawMap, MeasurementFieldKey.TERA, measurement.tera),
        bazu = initialFieldText(rawMap, MeasurementFieldKey.BAZU, measurement.bazu),
        chati = initialFieldText(rawMap, MeasurementFieldKey.CHATI, measurement.chati),
        kamar = initialFieldText(rawMap, MeasurementFieldKey.KAMAR, measurement.kamar),
        gheraDaman = initialFieldText(rawMap, MeasurementFieldKey.GHERA_DAMAN, measurement.gheraDaman),
        galaCollar = initialFieldText(rawMap, MeasurementFieldKey.GALA_COLLAR, measurement.galaCollar),
        cuff = initialFieldText(rawMap, MeasurementFieldKey.CUFF, measurement.cuff),
        shalwarLambai = initialFieldText(rawMap, MeasurementFieldKey.SHALWAR_LAMBAI, measurement.shalwarLambai),
        paincha = initialFieldText(rawMap, MeasurementFieldKey.PAINCHA, measurement.paincha),
        gheraAsan = initialFieldText(rawMap, MeasurementFieldKey.GHERA_ASAN, measurement.gheraAsan),
        shalwarGhair = initialFieldText(rawMap, MeasurementFieldKey.SHALWAR_GHAIR, measurement.shalwarGhair),
        neckStyle = measurement.neckStyle,
        damanStyle = measurement.damanStyle,
        pocketStyles = measurement.pocketStyles,
        sleeveStyle = measurement.sleeveStyle,
        silaiStyle = measurement.silaiStyle,
        shalwarType = measurement.shalwarType,
        shoulderType = measurement.shoulderType,
        notes = measurement.notes,
        hasAttemptedSave = false,
        errorMessage = null,
      )
    }
  }

  fun updateField(transform: (MeasurementFormState) -> MeasurementFormState) {
    _formState.update { transform(it).copy(errorMessage = null) }
  }

  fun updateMeasurementField(key: MeasurementFieldKey, rawText: String) {
    updateField { state ->
      when (key) {
        MeasurementFieldKey.KAMEEZ_LAMBAI -> state.copy(kameezLambai = rawText)
        MeasurementFieldKey.TERA -> state.copy(tera = rawText)
        MeasurementFieldKey.BAZU -> state.copy(bazu = rawText)
        MeasurementFieldKey.GALA_COLLAR -> state.copy(galaCollar = rawText)
        MeasurementFieldKey.CHATI -> state.copy(chati = rawText)
        MeasurementFieldKey.GHERA_DAMAN -> state.copy(gheraDaman = rawText)
        MeasurementFieldKey.SHALWAR_LAMBAI -> state.copy(shalwarLambai = rawText)
        MeasurementFieldKey.PAINCHA -> state.copy(paincha = rawText)
        MeasurementFieldKey.KAMAR -> state.copy(kamar = rawText)
        MeasurementFieldKey.CUFF -> state.copy(cuff = rawText)
        MeasurementFieldKey.GHERA_ASAN -> state.copy(gheraAsan = rawText)
        MeasurementFieldKey.SHALWAR_GHAIR -> state.copy(shalwarGhair = rawText)
      }
    }
  }

  fun save(onSaved: () -> Unit) {
    _formState.update { it.copy(hasAttemptedSave = true) }
    val s = _formState.value

    for (key in MeasurementFieldKey.entries) {
      val fieldErr = TailorValidators.validateMeasurementFieldInput(
        key = key,
        rawText = s.valueFor(key),
        allowBlankRequired = false,
      )
      if (fieldErr != null) {
        return setError(fieldErr)
      }
    }

    val rawValues = TailorValidators.buildRawValuesMap(
      MeasurementFieldKey.entries.map { it to s.valueFor(it) },
    )

    val snapshot = MeasurementSnapshot(
      kameezLambai = TailorValidators.parseMeasurementInches(s.kameezLambai)
        ?: return setError(localizedAppText("Enter valid Kameez Lambai (inches).", "درست قمیض لمبائی (انچ) درج کریں۔")),
      tera = TailorValidators.parseMeasurementInches(s.tera)
        ?: return setError(localizedAppText("Enter valid Teera (inches).", "درست تیرا (انچ) درج کریں۔")),
      bazu = TailorValidators.parseMeasurementInches(s.bazu)
        ?: return setError(localizedAppText("Enter valid Asteen (inches).", "درست آستین (انچ) درج کریں۔")),
      chati = TailorValidators.parseMeasurementInches(s.chati)
        ?: return setError(localizedAppText("Enter valid Seena (inches).", "درست سینہ (انچ) درج کریں۔")),
      kamar = if (s.kamar.isBlank()) {
        0.0
      } else {
        TailorValidators.parseMeasurementInches(s.kamar)
          ?: return setError(localizedAppText("Enter valid Kamar (inches).", "درست کمر (انچ) درج کریں۔"))
      },
      gheraDaman = TailorValidators.parseMeasurementInches(s.gheraDaman)
        ?: return setError(localizedAppText("Enter valid Ghera / Daman (inches).", "درست گھیرا / دامن (انچ) درج کریں۔")),
      galaCollar = TailorValidators.parseMeasurementInches(s.galaCollar)
        ?: return setError(localizedAppText("Enter valid Gala (inches).", "درست گلا (انچ) درج کریں۔")),
      cuff = if (s.cuff.isBlank()) {
        0.0
      } else {
        TailorValidators.parseMeasurementInches(s.cuff)
          ?: return setError(localizedAppText("Enter valid Cuff (inches).", "درست کف (انچ) درج کریں۔"))
      },
      shalwarLambai = TailorValidators.parseMeasurementInches(s.shalwarLambai)
        ?: return setError(localizedAppText("Enter valid Shalwar (inches).", "درست شلوار (انچ) درج کریں۔")),
      paincha = TailorValidators.parseMeasurementInches(s.paincha)
        ?: return setError(localizedAppText("Enter valid Poncha (inches).", "درست پانچہ (انچ) درج کریں۔")),
      gheraAsan = if (s.gheraAsan.isBlank()) {
        0.0
      } else {
        TailorValidators.parseMeasurementInches(s.gheraAsan)
          ?: return setError(localizedAppText("Enter valid Asan (inches).", "درست آسن (انچ) درج کریں۔"))
      },
      shalwarGhair = if (s.shalwarGhair.isBlank()) {
        0.0
      } else {
        TailorValidators.parseMeasurementInches(s.shalwarGhair)
          ?: return setError(localizedAppText("Enter valid Shalwar Ghair (inches).", "درست شلوار گھیرا (انچ) درج کریں۔"))
      },
      rawValues = rawValues,
      notes = s.notes,
    )

    viewModelScope.launch {
      _formState.update { it.copy(isSaving = true, errorMessage = null) }
      val device = deviceSyncRepository.ensureLocalDeviceConfig()
      val editingExisting = sourceMeasurementId?.let { id ->
        customerRepository.getMeasurementById(id)?.takeIf { !it.isDeleted }
      }
      val persistLabel = if (s.label.trim() == "معیاری") "Standard" else s.label
      val result = if (editingExisting != null) {
        customerRepository.updateMeasurement(
          customerId = customerId,
          measurementId = editingExisting.measurementId,
          snapshot = snapshot,
          neckStyle = s.neckStyle,
          damanStyle = s.damanStyle,
          pocketStyles = s.pocketStyles,
          sleeveStyle = s.sleeveStyle,
          silaiStyle = s.silaiStyle,
          shalwarType = s.shalwarType,
          shoulderType = s.shoulderType,
          label = persistLabel,
          deviceId = device.deviceId,
        )
      } else {
        customerRepository.saveMeasurement(
          customerId = customerId,
          snapshot = snapshot,
          neckStyle = s.neckStyle,
          damanStyle = s.damanStyle,
          pocketStyles = s.pocketStyles,
          sleeveStyle = s.sleeveStyle,
          silaiStyle = s.silaiStyle,
          shalwarType = s.shalwarType,
          shoulderType = s.shoulderType,
          label = persistLabel,
          deviceId = device.deviceId,
        )
      }
      result.fold(
        onSuccess = {
          _formState.update { it.copy(isSaving = false) }
          onSaved()
        },
        onFailure = { error ->
          _formState.update {
            it.copy(
              isSaving = false,
              errorMessage = error.message
                ?: localizedAppText("Failed to save measurements.", "ناپ محفوظ نہیں ہو سکے۔"),
            )
          }
        },
      )
    }
  }

  private fun setError(message: String) {
    _formState.update { it.copy(errorMessage = message) }
  }

  private fun initialFieldText(
    rawMap: Map<String, String>,
    key: MeasurementFieldKey,
    numericValue: Double,
  ): String {
    val raw = rawMap[key.name]?.trim()
    if (!raw.isNullOrEmpty()) return raw
    return if (key.isRequired) formatNumeric(numericValue) else formatOptionalNumeric(numericValue)
  }

  private fun formatNumeric(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString()
    else value.toString().trimEnd('0').trimEnd('.')
  }

  private fun formatOptionalNumeric(value: Double): String =
    if (value <= 0.0) "" else formatNumeric(value)

  class Factory(
    private val customerId: String,
    private val sourceMeasurementId: String?,
    private val customerRepository: CustomerRepository,
    private val deviceSyncRepository: DeviceSyncRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MeasurementEditorViewModel(
        customerId = customerId,
        sourceMeasurementId = sourceMeasurementId,
        customerRepository = customerRepository,
        deviceSyncRepository = deviceSyncRepository,
      ) as T
    }
  }
}
