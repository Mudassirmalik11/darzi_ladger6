package com.example.features.measurements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.MeasurementFieldKey
import com.example.core.model.TailorValidators
import com.example.ui.theme.AtelierOlive
import com.example.ui.theme.AtelierOliveDarkBadge
import com.example.ui.theme.AtelierOliveDarkCard
import com.example.ui.theme.OnAtelierOlive

sealed interface MeasurementKeypadAction {
  data class Digit(val char: Char) : MeasurementKeypadAction
  data class Fraction(val value: String) : MeasurementKeypadAction
  data object Dash : MeasurementKeypadAction
  data object Space : MeasurementKeypadAction
  data object Backspace : MeasurementKeypadAction
  data object DotOrSlash : MeasurementKeypadAction
  data object SlashSymbol : MeasurementKeypadAction
}

fun sanitizeMeasurementInput(raw: String): String {
  val filtered = buildString {
    for (ch in raw) {
      if (ch.isDigit() || ch == '.' || ch == '/' || ch == '-' || ch == ' ') {
        append(ch)
      }
    }
  }
  return filtered.take(16)
}

fun applyMeasurementKeyToTextFieldValue(
  current: TextFieldValue,
  action: MeasurementKeypadAction,
): TextFieldValue {
  val text = current.text
  val start = current.selection.min.coerceIn(0, text.length)
  val end = current.selection.max.coerceIn(0, text.length)

  return when (action) {
    is MeasurementKeypadAction.Digit -> {
      insertText(text, start, end, action.char.toString())
    }
    MeasurementKeypadAction.Dash -> {
      insertText(text, start, end, "-")
    }
    MeasurementKeypadAction.Space -> {
      insertText(text, start, end, " ")
    }
    MeasurementKeypadAction.SlashSymbol -> {
      insertText(text, start, end, "/")
    }
    MeasurementKeypadAction.DotOrSlash -> {
      val prefix = text.substring(0, start)
      val tokenToInsert = if (prefix.contains(' ') || prefix.contains('-') || prefix.contains('.')) {
        if (prefix.contains('/')) "." else "/"
      } else {
        "."
      }
      insertText(text, start, end, tokenToInsert)
    }
    MeasurementKeypadAction.Backspace -> {
      if (start != end) {
        val next = text.removeRange(start, end)
        TextFieldValue(next, TextRange(start))
      } else if (start > 0) {
        val next = text.removeRange(start - 1, start)
        TextFieldValue(next, TextRange(start - 1))
      } else {
        current
      }
    }
    is MeasurementKeypadAction.Fraction -> {
      val trimmed = text.trimEnd()
      val trailingFractionRegex = Regex("^(.*?)(\\s+|-)\\d+/\\d+$")
      val match = trailingFractionRegex.matchEntire(trimmed)
      val nextText = when {
        trimmed.isEmpty() || trimmed == "1/4" || trimmed == "1/2" || trimmed == "3/4" -> {
          action.value
        }
        match != null -> {
          val base = match.groupValues[1]
          val sep = match.groupValues[2]
          "$base$sep${action.value}"
        }
        trimmed.endsWith("-") || text.endsWith(" ") -> {
          "${text.trimEnd()}${if (trimmed.endsWith("-")) "-" else " "}${action.value}"
        }
        else -> {
          "$trimmed ${action.value}"
        }
      }
      val sanitized = sanitizeMeasurementInput(nextText)
      TextFieldValue(sanitized, TextRange(sanitized.length))
    }
  }
}

private fun insertText(
  text: String,
  start: Int,
  end: Int,
  insertion: String,
): TextFieldValue {
  val raw = buildString {
    append(text, 0, start)
    append(insertion)
    append(text, end, text.length)
  }
  val sanitized = sanitizeMeasurementInput(raw)
  val cursor = (start + insertion.length).coerceIn(0, sanitized.length)
  return TextFieldValue(sanitized, TextRange(cursor))
}

@Composable
fun MeasurementFieldKey.localizedLabel(): String = stringResource(
  when (this) {
    MeasurementFieldKey.KAMEEZ_LAMBAI -> R.string.meas_kameez_lambai
    MeasurementFieldKey.TERA -> R.string.meas_tera
    MeasurementFieldKey.BAZU -> R.string.meas_bazu
    MeasurementFieldKey.GALA_COLLAR -> R.string.meas_gala_collar
    MeasurementFieldKey.CHATI -> R.string.meas_chati
    MeasurementFieldKey.GHERA_DAMAN -> R.string.meas_ghera_daman
    MeasurementFieldKey.SHALWAR_LAMBAI -> R.string.meas_shalwar_lambai
    MeasurementFieldKey.PAINCHA -> R.string.meas_paincha
    MeasurementFieldKey.KAMAR -> R.string.meas_kamar
    MeasurementFieldKey.CUFF -> R.string.meas_cuff
    MeasurementFieldKey.GHERA_ASAN -> R.string.meas_ghera_asan
    MeasurementFieldKey.SHALWAR_GHAIR -> R.string.meas_shalwar_ghair
  },
)

@Composable
fun TailorMeasurementKeyboard(
  activeField: MeasurementFieldKey,
  fieldError: String?,
  isLastField: Boolean,
  onAction: (MeasurementKeypadAction) -> Unit,
  onNext: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isDarkCanvas = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val trayColor = if (isDarkCanvas) AtelierOliveDarkCard else MaterialTheme.colorScheme.surfaceContainerHigh
  val keyColor = if (isDarkCanvas) Color(0xFF23271F) else MaterialTheme.colorScheme.surfaceContainerLowest
  val utilityKeyColor = if (isDarkCanvas) AtelierOliveDarkBadge else MaterialTheme.colorScheme.surfaceContainerHighest
  val keyTextColor = if (isDarkCanvas) OnAtelierOlive else MaterialTheme.colorScheme.onSurface
  val mutedColor = if (isDarkCanvas) OnAtelierOlive.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
  val dividerColor = if (isDarkCanvas) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant

  val minStr = TailorValidators.formatLimitNumber(activeField.minInches)
  val maxStr = TailorValidators.formatLimitNumber(activeField.maxInches)
  val rangeSummary = stringResource(R.string.measurement_range_error_hint, minStr, maxStr)

  Surface(
    color = trayColor,
    tonalElevation = 6.dp,
    shadowElevation = 10.dp,
    modifier = modifier
      .fillMaxWidth()
      .testTag("tailor_measurement_keyboard"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding(),
    ) {
      HorizontalDivider(color = dividerColor)

      // Compact active field status header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = activeField.localizedLabel(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (fieldError != null) MaterialTheme.colorScheme.error else keyTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (fieldError != null) {
              MaterialTheme.colorScheme.errorContainer
            } else if (activeField.isRequired) {
              AtelierOlive.copy(alpha = 0.25f)
            } else {
              utilityKeyColor
            },
          ) {
            Text(
              text = fieldError ?: rangeSummary,
              style = MaterialTheme.typography.labelSmall,
              color = if (fieldError != null) MaterialTheme.colorScheme.onErrorContainer else mutedColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(48.dp)
            .testTag("keypad_hide_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.KeyboardHide,
            contentDescription = stringResource(R.string.keypad_hide_cd),
            tint = mutedColor,
          )
        }
      }

      // 5-row custom tailor keypad always rendered LTR for consistent numeric layout
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Row 1: 1/4, 1/2, 3/4
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            KeypadTextKey(
              label = "1/4",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Fraction("1/4")) },
              modifier = Modifier
                .weight(1f)
                .testTag("keypad_key_quarter"),
            )
            KeypadTextKey(
              label = "1/2",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Fraction("1/2")) },
              modifier = Modifier
                .weight(1f)
                .testTag("keypad_key_half"),
            )
            KeypadTextKey(
              label = "3/4",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Fraction("3/4")) },
              modifier = Modifier
                .weight(1f)
                .testTag("keypad_key_three_quarter"),
            )
          }

          // Row 2: 1, 2, 3, -
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            KeypadTextKey(
              label = "1",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Digit('1')) },
              modifier = Modifier.weight(1f).testTag("keypad_key_1"),
            )
            KeypadTextKey(
              label = "2",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Digit('2')) },
              modifier = Modifier.weight(1f).testTag("keypad_key_2"),
            )
            KeypadTextKey(
              label = "3",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Digit('3')) },
              modifier = Modifier.weight(1f).testTag("keypad_key_3"),
            )
            KeypadTextKey(
              label = "-",
              containerColor = utilityKeyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Dash) },
              modifier = Modifier.weight(1f).testTag("keypad_key_dash"),
            )
          }

          // Row 3: 4, 5, 6, Space
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            KeypadTextKey(
              label = "4",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Digit('4')) },
              modifier = Modifier.weight(1f).testTag("keypad_key_4"),
            )
            KeypadTextKey(
              label = "5",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Digit('5')) },
              modifier = Modifier.weight(1f).testTag("keypad_key_5"),
            )
            KeypadTextKey(
              label = "6",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Digit('6')) },
              modifier = Modifier.weight(1f).testTag("keypad_key_6"),
            )
            KeypadIconKey(
              icon = Icons.Filled.SpaceBar,
              contentDescription = stringResource(R.string.keypad_space_cd),
              containerColor = utilityKeyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Space) },
              modifier = Modifier.weight(1f).testTag("keypad_key_space"),
            )
          }

          // Row 4: 7, 8, 9, Backspace
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            KeypadTextKey(
              label = "7",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Digit('7')) },
              modifier = Modifier.weight(1f).testTag("keypad_key_7"),
            )
            KeypadTextKey(
              label = "8",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Digit('8')) },
              modifier = Modifier.weight(1f).testTag("keypad_key_8"),
            )
            KeypadTextKey(
              label = "9",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Digit('9')) },
              modifier = Modifier.weight(1f).testTag("keypad_key_9"),
            )
            KeypadIconKey(
              icon = Icons.AutoMirrored.Filled.Backspace,
              contentDescription = stringResource(R.string.keypad_backspace_cd),
              containerColor = utilityKeyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Backspace) },
              modifier = Modifier.weight(1f).testTag("keypad_key_backspace"),
            )
          }

          // Row 5: *#, 0+, ./, Next (or Done)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            KeypadTextKey(
              label = "/",
              containerColor = utilityKeyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.SlashSymbol) },
              modifier = Modifier.weight(1f).testTag("keypad_key_symbol"),
            )
            KeypadTextKey(
              label = "0+",
              containerColor = keyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.Digit('0')) },
              modifier = Modifier.weight(1f).testTag("keypad_key_0"),
            )
            KeypadTextKey(
              label = "./",
              containerColor = utilityKeyColor,
              contentColor = keyTextColor,
              onClick = { onAction(MeasurementKeypadAction.DotOrSlash) },
              modifier = Modifier.weight(1f).testTag("keypad_key_dot_slash"),
            )
            KeypadTextKey(
              label = stringResource(if (isLastField) R.string.keypad_done else R.string.keypad_next),
              containerColor = AtelierOlive,
              contentColor = OnAtelierOlive,
              fontWeight = FontWeight.Bold,
              borderColor = Color.Transparent,
              onClick = onNext,
              modifier = Modifier.weight(1f).testTag("keypad_key_next"),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun KeypadTextKey(
  label: String,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  fontWeight: FontWeight = FontWeight.SemiBold,
  borderColor: Color = Color.White.copy(alpha = 0.06f),
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier.heightIn(min = 48.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
    ) {
      Text(
        text = label,
        color = contentColor,
        fontSize = 18.sp,
        fontWeight = fontWeight,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun KeypadIconKey(
  icon: ImageVector,
  contentDescription: String,
  containerColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = containerColor,
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    modifier = modifier.heightIn(min = 48.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = contentColor,
        modifier = Modifier.size(22.dp),
      )
    }
  }
}
