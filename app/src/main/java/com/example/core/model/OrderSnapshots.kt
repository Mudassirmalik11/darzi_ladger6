package com.example.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MeasurementSnapshot(
  val sourceMeasurementId: String? = null,
  val kameezLambai: Double,
  val tera: Double,
  val bazu: Double,
  val chati: Double,
  val kamar: Double,
  val gheraDaman: Double,
  val galaCollar: Double,
  val cuff: Double,
  val shalwarLambai: Double,
  val paincha: Double,
  val gheraAsan: Double,
  val shalwarGhair: Double = 0.0,
  val rawValues: Map<String, String> = emptyMap(),
  val notes: String = "",
)

@Serializable
data class StyleSnapshot(
  val neckStyle: NeckStyle? = null,
  val damanStyle: DamanStyle? = null,
  val pocketStyle: PocketStyle? = null,
  val pocketStyles: List<PocketStyle> = emptyList(),
  val sleeveStyle: SleeveStyle? = null,
  val silaiStyle: SilaiStyle? = null,
  val shalwarType: ShalwarType? = null,
  val shoulderType: ShoulderType? = null,
  // Extensible per-suit overrides for future versions without breaking existing orders
  val perSuitOverrides: List<SuitStyleOverride> = emptyList(),
) {
  fun effectivePocketStyles(): List<PocketStyle> = when {
    pocketStyles.isNotEmpty() -> PocketStyleCodec.ordered(pocketStyles)
    pocketStyle != null -> listOf(pocketStyle)
    else -> emptyList()
  }
}

@Serializable
data class SuitStyleOverride(
  val suitIndex: Int,
  val label: String = "",
  val measurementOverride: MeasurementSnapshot? = null,
  val neckStyle: NeckStyle? = null,
  val damanStyle: DamanStyle? = null,
  val pocketStyle: PocketStyle? = null,
  val pocketStyles: List<PocketStyle>? = null,
  val sleeveStyle: SleeveStyle? = null,
  val silaiStyle: SilaiStyle? = null,
  val shalwarType: ShalwarType? = null,
  val shoulderType: ShoulderType? = null,
)

object SnapshotJson {
  val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  fun encodeMeasurement(snapshot: MeasurementSnapshot): String =
    json.encodeToString(snapshot)

  fun decodeMeasurement(raw: String): MeasurementSnapshot =
    json.decodeFromString(raw)

  fun encodeStyle(snapshot: StyleSnapshot): String =
    json.encodeToString(snapshot)

  fun decodeStyle(raw: String): StyleSnapshot =
    json.decodeFromString(raw)

  fun encodeRawMap(rawMap: Map<String, String>): String =
    if (rawMap.isEmpty()) "{}" else json.encodeToString(rawMap)

  fun decodeRawMap(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank() || raw.trim() == "{}") return emptyMap()
    return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
  }
}
