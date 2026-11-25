package com.example.features.measurements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.DamanStyle
import com.example.core.model.MeasurementFieldKey
import com.example.core.model.NeckStyle
import com.example.core.model.PocketStyle
import com.example.core.model.PocketStyleCodec
import com.example.core.model.ShalwarType
import com.example.core.model.ShoulderType
import com.example.core.model.SilaiStyle
import com.example.core.model.SleeveStyle
import com.example.core.ui.DateFormatUtils
import com.example.features.customers.localizedDamanStyle
import com.example.features.customers.localizedNeckStyle
import com.example.features.customers.localizedPocketStyles
import com.example.features.customers.localizedShalwarType
import com.example.features.customers.localizedShoulderType
import com.example.features.customers.localizedSilaiStyle
import com.example.features.customers.localizedSleeveStyle
import com.example.ui.theme.JetBrainsMonoFamily

private data class SavedMetricEntry(
  val tagSuffix: String,
  val label: String,
  val value: String,
)

@Composable
fun SavedMeasurementMetricsGrid(
  kameezLambai: Double,
  tera: Double,
  bazu: Double,
  galaCollar: Double,
  chati: Double,
  gheraDaman: Double,
  shalwarLambai: Double,
  paincha: Double,
  kamar: Double,
  cuff: Double,
  gheraAsan: Double,
  dividerColor: Color,
  valueColor: Color,
  mutedText: Color,
  modifier: Modifier = Modifier,
  shalwarGhair: Double = 0.0,
  rawValues: Map<String, String> = emptyMap(),
) {
  // Compulsory 1..8 in exact measurement entry order
  val compulsoryItems = listOf(
    SavedMetricEntry(
      "kameez_lambai",
      stringResource(R.string.meas_kameez_lambai),
      DateFormatUtils.formatInches(kameezLambai, rawValues[MeasurementFieldKey.KAMEEZ_LAMBAI.name]),
    ),
    SavedMetricEntry(
      "tera",
      stringResource(R.string.meas_tera),
      DateFormatUtils.formatInches(tera, rawValues[MeasurementFieldKey.TERA.name]),
    ),
    SavedMetricEntry(
      "bazu",
      stringResource(R.string.meas_bazu),
      DateFormatUtils.formatInches(bazu, rawValues[MeasurementFieldKey.BAZU.name]),
    ),
    SavedMetricEntry(
      "gala_collar",
      stringResource(R.string.meas_gala_collar),
      DateFormatUtils.formatInches(galaCollar, rawValues[MeasurementFieldKey.GALA_COLLAR.name]),
    ),
    SavedMetricEntry(
      "chati",
      stringResource(R.string.meas_chati),
      DateFormatUtils.formatInches(chati, rawValues[MeasurementFieldKey.CHATI.name]),
    ),
    SavedMetricEntry(
      "ghera_daman",
      stringResource(R.string.meas_ghera_daman),
      DateFormatUtils.formatInches(gheraDaman, rawValues[MeasurementFieldKey.GHERA_DAMAN.name]),
    ),
    SavedMetricEntry(
      "shalwar_lambai",
      stringResource(R.string.meas_shalwar_lambai),
      DateFormatUtils.formatInches(shalwarLambai, rawValues[MeasurementFieldKey.SHALWAR_LAMBAI.name]),
    ),
    SavedMetricEntry(
      "paincha",
      stringResource(R.string.meas_paincha),
      DateFormatUtils.formatInches(paincha, rawValues[MeasurementFieldKey.PAINCHA.name]),
    ),
  )

  // Optional measurements appear ONLY below compulsory if entered (> 0.0 or raw dual value)
  val optionalItems = buildList {
    val kamarRaw = rawValues[MeasurementFieldKey.KAMAR.name]
    if (kamar > 0.0 || !kamarRaw.isNullOrBlank()) {
      add(SavedMetricEntry("kamar", stringResource(R.string.meas_kamar), DateFormatUtils.formatInches(kamar, kamarRaw)))
    }
    val cuffRaw = rawValues[MeasurementFieldKey.CUFF.name]
    if (cuff > 0.0 || !cuffRaw.isNullOrBlank()) {
      add(SavedMetricEntry("cuff", stringResource(R.string.meas_cuff), DateFormatUtils.formatInches(cuff, cuffRaw)))
    }
    val asanRaw = rawValues[MeasurementFieldKey.GHERA_ASAN.name]
    if (gheraAsan > 0.0 || !asanRaw.isNullOrBlank()) {
      add(SavedMetricEntry("ghera_asan", stringResource(R.string.meas_ghera_asan), DateFormatUtils.formatInches(gheraAsan, asanRaw)))
    }
    val ghairRaw = rawValues[MeasurementFieldKey.SHALWAR_GHAIR.name]
    if (shalwarGhair > 0.0 || !ghairRaw.isNullOrBlank()) {
      add(SavedMetricEntry("shalwar_ghair", stringResource(R.string.meas_shalwar_ghair), DateFormatUtils.formatInches(shalwarGhair, ghairRaw)))
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    compulsoryItems.chunked(2).forEach { pair ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        pair.forEach { metric ->
          SavedMetricBox(
            label = metric.label,
            value = metric.value,
            isRequired = true,
            dividerColor = dividerColor,
            valueColor = valueColor,
            mutedText = mutedText,
            tagSuffix = metric.tagSuffix,
            modifier = Modifier.weight(1f),
          )
        }
        if (pair.size == 1) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }

    if (optionalItems.isNotEmpty()) {
      HorizontalDivider(
        color = dividerColor,
        modifier = Modifier.padding(vertical = 2.dp),
      )
      optionalItems.chunked(2).forEach { pair ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          pair.forEach { metric ->
            SavedMetricBox(
              label = metric.label,
              value = metric.value,
              isRequired = false,
              dividerColor = dividerColor,
              valueColor = valueColor,
              mutedText = mutedText,
              tagSuffix = metric.tagSuffix,
              modifier = Modifier.weight(1f),
            )
          }
          if (pair.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun SavedMetricBox(
  label: String,
  value: String,
  isRequired: Boolean,
  dividerColor: Color,
  valueColor: Color,
  mutedText: Color,
  tagSuffix: String,
  modifier: Modifier = Modifier,
) {
  val borderColor = if (isRequired) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
  } else {
    dividerColor
  }
  val containerColor = if (isRequired) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
  } else {
    Color.Transparent
  }
  val labelColor = if (isRequired) {
    MaterialTheme.colorScheme.primary
  } else {
    mutedText
  }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = containerColor,
    border = BorderStroke(width = if (isRequired) 1.25.dp else 1.dp, color = borderColor),
    modifier = modifier.testTag("saved_meas_$tagSuffix"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = labelColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        style = TextStyle(
          fontFamily = JetBrainsMonoFamily,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = valueColor,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun SavedStylePreferencesGrid(
  neckStyle: NeckStyle?,
  damanStyle: DamanStyle?,
  pocketStyles: Collection<PocketStyle>,
  sleeveStyle: SleeveStyle?,
  silaiStyle: SilaiStyle?,
  dividerColor: Color,
  primaryText: Color,
  mutedText: Color,
  modifier: Modifier = Modifier,
  shalwarType: ShalwarType? = null,
  shoulderType: ShoulderType? = null,
) {
  val orderedPockets = PocketStyleCodec.ordered(pocketStyles)
  val styleItems = buildList {
    neckStyle?.let {
      add(Triple("neck", stringResource(R.string.style_neck), localizedNeckStyle(it)))
    }
    damanStyle?.let {
      add(Triple("daman", stringResource(R.string.style_daman), localizedDamanStyle(it)))
    }
    if (orderedPockets.isNotEmpty()) {
      add(Triple("pockets", stringResource(R.string.style_pockets), localizedPocketStyles(orderedPockets)))
    }
    sleeveStyle?.let {
      add(Triple("sleeves", stringResource(R.string.style_sleeves), localizedSleeveStyle(it)))
    }
    shoulderType?.let {
      add(Triple("shoulder", stringResource(R.string.style_shoulder), localizedShoulderType(it)))
    }
    shalwarType?.let {
      add(Triple("shalwar", stringResource(R.string.style_shalwar), localizedShalwarType(it)))
    }
    silaiStyle?.let {
      add(Triple("silai", stringResource(R.string.style_silai), localizedSilaiStyle(it)))
    }
  }

  if (styleItems.isEmpty()) return

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    HorizontalDivider(color = dividerColor)
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      styleItems.chunked(2).forEach { pair ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          pair.forEach { (tagSuffix, categoryLabel, valueLabel) ->
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color.Transparent,
              border = BorderStroke(1.dp, dividerColor),
              modifier = Modifier
                .weight(1f)
                .testTag("saved_style_$tagSuffix"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = categoryLabel,
                  style = MaterialTheme.typography.labelSmall,
                  color = mutedText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = valueLabel,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = primaryText,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
          if (pair.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}
