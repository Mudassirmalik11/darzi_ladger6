package com.example.features.printing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily

private val ThermalPaperSurface = Color(0xFFFCFBF8)
private val ThermalPaperBorder = Color(0xFFCCC9BE)
private val ThermalInkPrimary = Color(0xFF10130E)
private val ThermalInkSecondary = Color(0xFF2E3527)
private val ThermalHighlightCardBg = Color(0xFFEEF3E3)
private val ThermalHighlightBg = Color(0xFF171E12)
private val ThermalHighlightFg = Color(0xFFFCFBF8)
private val ThermalSectionTint = Color(0xFFE4EBD6)
private val ThermalAccentInk = Color(0xFF1B2415)
private val ThermalGridLine = Color(0xFFC2C6B7)
private val ThermalCellBg = Color(0xFFF7F8F3)

@Composable
fun ThermalSlipPaperPreview(
  previewBody: String,
  textDirection: TextDirection,
  parchiData: ParchiSlipData? = null,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = ThermalPaperSurface,
    border = BorderStroke(1.dp, ThermalPaperBorder),
    modifier = modifier
      .fillMaxWidth()
      .heightIn(max = 460.dp),
  ) {
    if (parchiData != null) {
      ParchiPaperCard(data = parchiData)
    } else {
      val styledBody = remember(previewBody) { buildStyledSlipText(previewBody) }
      Text(
        text = styledBody,
        softWrap = false,
        style = TextStyle(
          fontFamily = JetBrainsMonoFamily,
          fontSize = 13.5.sp,
          lineHeight = 22.sp,
          color = ThermalInkPrimary,
          textDirection = textDirection,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 14.dp)
          .testTag("slip_preview_text"),
      )
    }
  }
}

@Composable
private fun ParchiPaperCard(
  data: ParchiSlipData,
  modifier: Modifier = Modifier,
) {
  val layoutDirection = if (data.isUrdu) LayoutDirection.Rtl else LayoutDirection.Ltr
  CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(12.dp)
        .semantics(mergeDescendants = true) {}
        .testTag("slip_preview_text"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. Highlighted Customer Information Section
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(ThermalHighlightCardBg)
          .border(1.5.dp, ThermalAccentInk, RoundedCornerShape(8.dp)),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(ThermalHighlightBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = data.customerInfoTitle,
            style = TextStyle(
              fontSize = 16.sp,
              fontWeight = FontWeight.ExtraBold,
              color = ThermalHighlightFg,
            ),
          )
        }

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          if (!data.orderIdLabel.isNullOrBlank() && !data.orderIdValue.isNullOrBlank()) {
            ParchiInfoLine(
              label = data.orderIdLabel,
              value = data.orderIdValue,
              emphasizeValue = true,
            )
          }
          if (!data.customerIdLabel.isNullOrBlank() && !data.customerIdValue.isNullOrBlank()) {
            ParchiInfoLine(
              label = data.customerIdLabel,
              value = data.customerIdValue,
              emphasizeValue = true,
            )
          }
          ParchiInfoLine(
            label = data.nameLabel,
            value = data.nameValue,
            emphasizeValue = true,
          )
          ParchiInfoLine(
            label = data.mobileLabel,
            value = data.mobileValue,
            emphasizeValue = false,
          )
          if (!data.addressLabel.isNullOrBlank() && !data.addressValue.isNullOrBlank()) {
            ParchiInfoLine(
              label = data.addressLabel,
              value = data.addressValue,
              emphasizeValue = false,
            )
          }
          if (!data.suitsLabel.isNullOrBlank() && !data.suitsValue.isNullOrBlank() &&
            !data.dueLabel.isNullOrBlank() && !data.dueValue.isNullOrBlank()
          ) {
            HorizontalDivider(
              color = ThermalAccentInk.copy(alpha = 0.25f),
              modifier = Modifier.padding(vertical = 2.dp),
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              ParchiInfoLine(
                label = data.suitsLabel,
                value = data.suitsValue,
                emphasizeValue = true,
              )
              ParchiInfoLine(
                label = data.dueLabel,
                value = data.dueValue,
                emphasizeValue = true,
              )
            }
          }
        }
      }

      // 2. Shop Name + Garment Title & Date Bar
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = data.headerBannerText,
            style = TextStyle(
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = ThermalInkPrimary,
            ),
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = data.dateText,
            style = TextStyle(
              fontSize = 12.5.sp,
              fontWeight = FontWeight.SemiBold,
              color = ThermalInkSecondary,
            ),
          )
        }
        HorizontalDivider(thickness = 1.5.dp, color = ThermalAccentInk)
      }

      // 3. 2-Column Stacked Measurements Grid (1..8 Compulsory first, NO cloth icons)
      ParchiMetricsGrid(cells = data.compulsoryMetrics)

      // 4. Optional Measurements Grid (Only entered values > 0)
      if (data.optionalMetrics.isNotEmpty()) {
        HorizontalDivider(thickness = 1.dp, color = ThermalGridLine)
        ParchiMetricsGrid(cells = data.optionalMetrics)
      }

      // 5. Additional Notes & Selected Stitching Options
      if (data.additionalNoteLines.isNotEmpty()) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, ThermalGridLine, RoundedCornerShape(8.dp)),
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(ThermalSectionTint)
              .padding(horizontal = 12.dp, vertical = 6.dp),
          ) {
            Text(
              text = data.additionalNotesTitle,
              style = TextStyle(
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ThermalAccentInk,
              ),
            )
          }
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            data.additionalNoteLines.forEach { noteLine ->
              Text(
                text = noteLine,
                style = TextStyle(
                  fontSize = 14.sp,
                  fontWeight = FontWeight.SemiBold,
                  lineHeight = 20.sp,
                  color = ThermalInkPrimary,
                ),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ParchiInfoLine(
  label: String,
  value: String,
  emphasizeValue: Boolean,
) {
  val text = buildAnnotatedString {
    withStyle(
      SpanStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = ThermalInkSecondary,
      ),
    ) {
      append("$label:")
    }
    if (value.isNotEmpty()) {
      append(" ")
      withStyle(
        SpanStyle(
          fontWeight = if (emphasizeValue) FontWeight.ExtraBold else FontWeight.SemiBold,
          fontSize = if (emphasizeValue) 15.sp else 14.sp,
          color = ThermalInkPrimary,
        ),
      ) {
        append(value)
      }
    }
  }
  Text(text = text)
}

@Composable
private fun ParchiMetricsGrid(cells: List<ParchiMetricCell>) {
  val rows = cells.chunked(2)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .border(1.dp, ThermalGridLine, RoundedCornerShape(8.dp)),
  ) {
    rows.forEachIndexed { rowIndex, pair ->
      if (rowIndex > 0) {
        HorizontalDivider(thickness = 1.dp, color = ThermalGridLine)
      }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(IntrinsicSize.Min)
          .background(if (rowIndex % 2 == 0) ThermalPaperSurface else ThermalCellBg),
      ) {
        ParchiMetricCellBox(
          cell = pair[0],
          modifier = Modifier.weight(1f),
        )
        Box(
          modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(ThermalGridLine),
        )
        if (pair.size > 1) {
          ParchiMetricCellBox(
            cell = pair[1],
            modifier = Modifier.weight(1f),
          )
        } else {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun ParchiMetricCellBox(
  cell: ParchiMetricCell,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(horizontal = 8.dp, vertical = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = cell.label,
      style = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = ThermalInkSecondary,
      ),
      textAlign = TextAlign.Center,
    )
    Text(
      text = cell.value,
      style = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        color = ThermalInkPrimary,
      ),
      textAlign = TextAlign.Center,
    )
  }
}

private fun buildStyledSlipText(raw: String): AnnotatedString = buildAnnotatedString {
  val lines = raw.split('\n')
  lines.forEachIndexed { index, line ->
    if (index > 0) append('\n')
    val trimmed = line.trim()
    val start = length
    append(line)
    val end = length

    when {
      trimmed == "کسٹمر کی معلومات" || trimmed == "CUSTOMER INFORMATION" -> {
        addStyle(
          SpanStyle(
            background = ThermalHighlightBg,
            color = ThermalHighlightFg,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.5.sp,
          ),
          start,
          end,
        )
      }
      trimmed.startsWith("آرڈر آئی ڈی:") ||
        trimmed.startsWith("کسٹمر آئی ڈی:") ||
        trimmed.startsWith("Order ID:") ||
        trimmed.startsWith("Customer ID:") -> {
        addStyle(
          SpanStyle(
            color = ThermalInkPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.5.sp,
          ),
          start,
          end,
        )
      }
      trimmed.startsWith("نام:") || trimmed.startsWith("Name:") -> {
        addStyle(
          SpanStyle(
            color = ThermalInkPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
          ),
          start,
          end,
        )
      }
      trimmed == "ناپ (انچ)" ||
        trimmed == "MEASUREMENTS (Inches)" ||
        trimmed == "سلائی و ڈیزائن" ||
        trimmed == "STITCHING STYLES" ||
        trimmed == "اضافی نوٹ" ||
        trimmed == "ADDITIONAL NOTES" ||
        trimmed == "کاریگر ورکشاپ پرچی" ||
        trimmed == "KARIGAR WORKSHOP SLIP" ||
        trimmed == "ناپ کی پرچی" ||
        trimmed == "MEASUREMENT SLIP" ||
        trimmed == "گاہک کی رسید" ||
        trimmed == "CUSTOMER RECEIPT" -> {
        addStyle(
          SpanStyle(
            background = ThermalSectionTint,
            color = ThermalAccentInk,
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp,
          ),
          start,
          end,
        )
      }
      trimmed.isNotEmpty() && trimmed.all { it == '=' || it == '-' } -> {
        addStyle(
          SpanStyle(
            color = ThermalInkPrimary.copy(alpha = 0.45f),
            fontSize = 11.5.sp,
          ),
          start,
          end,
        )
      }
    }
  }
}
