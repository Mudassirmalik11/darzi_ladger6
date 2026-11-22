package com.example.features.measurements

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.model.MeasurementFieldKey
import com.example.core.ui.AtelierCard
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalLayoutApi::class,
  ExperimentalComposeUiApi::class,
  ExperimentalFoundationApi::class,
)
@Composable
fun MeasurementEditorScreen(
  viewModel: MeasurementEditorViewModel,
  onNavigateBack: () -> Unit,
  onSaved: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.formState.collectAsStateWithLifecycle()
  val focusManager = LocalFocusManager.current
  val coroutineScope = rememberCoroutineScope()

  var activeMeasurementKey by remember { mutableStateOf<MeasurementFieldKey?>(null) }
  val selectionMap = remember { mutableStateMapOf<MeasurementFieldKey, TextRange>() }
  val focusRequesters = remember {
    MeasurementFieldKey.entries.associateWith { FocusRequester() }
  }
  val bringIntoViewRequesters = remember {
    MeasurementFieldKey.entries.associateWith { BringIntoViewRequester() }
  }

  LaunchedEffect(activeMeasurementKey) {
    val active = activeMeasurementKey ?: return@LaunchedEffect
    bringIntoViewRequesters[active]?.bringIntoView()
  }

  BackHandler(enabled = activeMeasurementKey != null) {
    focusManager.clearFocus()
    activeMeasurementKey = null
  }

  val suppressSystemImeInterceptor = remember {
    PlatformTextInputInterceptor { _, _ ->
      awaitCancellation()
    }
  }

  val compulsoryRows = listOf(
    MeasurementFieldKey.KAMEEZ_LAMBAI to MeasurementFieldKey.TERA,
    MeasurementFieldKey.BAZU to MeasurementFieldKey.GALA_COLLAR,
    MeasurementFieldKey.CHATI to MeasurementFieldKey.GHERA_DAMAN,
    MeasurementFieldKey.SHALWAR_LAMBAI to MeasurementFieldKey.PAINCHA,
  )

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("measurement_editor_screen"),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = stringResource(R.string.measurement_editor_title),
              style = MaterialTheme.typography.titleLarge,
            )
            if (state.customerName.isNotBlank()) {
              Text(
                text = state.customerName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
    bottomBar = {
      AnimatedVisibility(
        visible = activeMeasurementKey != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
      ) {
        val currentKey = activeMeasurementKey
        if (currentKey != null) {
          val ordered = MeasurementFieldKey.entries
          val currentIndex = ordered.indexOf(currentKey)
          val isLastField = currentIndex == ordered.lastIndex

          TailorMeasurementKeyboard(
            activeField = currentKey,
            fieldError = state.errorFor(currentKey),
            isLastField = isLastField,
            onAction = { action ->
              val currentText = state.valueFor(currentKey)
              val storedSel = selectionMap[currentKey]
              val clampedSel = if (storedSel != null) {
                TextRange(
                  storedSel.start.coerceIn(0, currentText.length),
                  storedSel.end.coerceIn(0, currentText.length),
                )
              } else {
                TextRange(currentText.length)
              }
              val nextValue = applyMeasurementKeyToTextFieldValue(
                current = TextFieldValue(text = currentText, selection = clampedSel),
                action = action,
              )
              selectionMap[currentKey] = nextValue.selection
              viewModel.updateMeasurementField(currentKey, nextValue.text)
            },
            onNext = {
              if (!isLastField) {
                val nextKey = ordered[currentIndex + 1]
                val nextText = state.valueFor(nextKey)
                selectionMap[nextKey] = TextRange(nextText.length)
                activeMeasurementKey = nextKey
                focusRequesters[nextKey]?.requestFocus()
                coroutineScope.launch {
                  bringIntoViewRequesters[nextKey]?.bringIntoView()
                }
              } else {
                focusManager.clearFocus()
                activeMeasurementKey = null
              }
            },
            onDismiss = {
              focusManager.clearFocus()
              activeMeasurementKey = null
            },
          )
        }
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 820.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          OutlinedTextField(
            value = state.label,
            onValueChange = { value -> viewModel.updateField { it.copy(label = value) } },
            label = { Text(stringResource(R.string.measurement_label_hint)) },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .onFocusChanged { if (it.isFocused) activeMeasurementKey = null }
              .testTag("meas_label_input"),
          )

          // Unified Shalwar Kameez Measurements Card (1-8 Compulsory Highlighted, 9-11 Optional)
          AtelierCard(
            modifier = Modifier.testTag("measurement_unified_section"),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = stringResource(R.string.measurement_unified_section),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.weight(1f),
                )
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                ) {
                  Text(
                    text = stringResource(R.string.measurement_compulsory_legend),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
              }

              InterceptPlatformTextInput(interceptor = suppressSystemImeInterceptor) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  // Compulsory fields 1..8
                  compulsoryRows.forEach { (leftKey, rightKey) ->
                    MeasurementFieldRow(
                      leftKey = leftKey,
                      rightKey = rightKey,
                      state = state,
                      selectionMap = selectionMap,
                      focusRequesters = focusRequesters,
                      bringIntoViewRequesters = bringIntoViewRequesters,
                      onFocused = { key -> activeMeasurementKey = key },
                      onValueChanged = { key, raw, sel ->
                        selectionMap[key] = sel
                        viewModel.updateMeasurementField(key, raw)
                      },
                      onNextKey = { key ->
                        val ordered = MeasurementFieldKey.entries
                        val nextIdx = ordered.indexOf(key) + 1
                        if (nextIdx < ordered.size) {
                          val nextKey = ordered[nextIdx]
                          selectionMap[nextKey] = TextRange(state.valueFor(nextKey).length)
                          activeMeasurementKey = nextKey
                          focusRequesters[nextKey]?.requestFocus()
                        } else {
                          focusManager.clearFocus()
                          activeMeasurementKey = null
                        }
                      },
                    )
                  }

                  HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                  )

                  Text(
                    text = stringResource(R.string.measurement_optional_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )

                  // Optional fields 9..10
                  MeasurementFieldRow(
                    leftKey = MeasurementFieldKey.KAMAR,
                    rightKey = MeasurementFieldKey.CUFF,
                    state = state,
                    selectionMap = selectionMap,
                    focusRequesters = focusRequesters,
                    bringIntoViewRequesters = bringIntoViewRequesters,
                    onFocused = { key -> activeMeasurementKey = key },
                    onValueChanged = { key, raw, sel ->
                      selectionMap[key] = sel
                      viewModel.updateMeasurementField(key, raw)
                    },
                    onNextKey = { key ->
                      val ordered = MeasurementFieldKey.entries
                      val nextIdx = ordered.indexOf(key) + 1
                      if (nextIdx < ordered.size) {
                        val nextKey = ordered[nextIdx]
                        selectionMap[nextKey] = TextRange(state.valueFor(nextKey).length)
                        activeMeasurementKey = nextKey
                        focusRequesters[nextKey]?.requestFocus()
                      }
                    },
                  )

                  // Optional field 11
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    MeasurementFieldInput(
                      key = MeasurementFieldKey.GHERA_ASAN,
                      value = state.valueFor(MeasurementFieldKey.GHERA_ASAN),
                      errorText = state.errorFor(MeasurementFieldKey.GHERA_ASAN),
                      storedSelection = selectionMap[MeasurementFieldKey.GHERA_ASAN],
                      focusRequester = focusRequesters.getValue(MeasurementFieldKey.GHERA_ASAN),
                      bringIntoViewRequester = bringIntoViewRequesters.getValue(MeasurementFieldKey.GHERA_ASAN),
                      onFocused = { activeMeasurementKey = MeasurementFieldKey.GHERA_ASAN },
                      onValueChanged = { raw, sel ->
                        selectionMap[MeasurementFieldKey.GHERA_ASAN] = sel
                        viewModel.updateMeasurementField(MeasurementFieldKey.GHERA_ASAN, raw)
                      },
                      onImeNext = {
                        focusManager.clearFocus()
                        activeMeasurementKey = null
                      },
                      isLastField = true,
                      modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
              }
            }
          }

          // Style Selectors
          StylePreferencesCard(
            neckStyle = state.neckStyle,
            onSelectNeckStyle = { style ->
              activeMeasurementKey = null
              viewModel.updateField { it.copy(neckStyle = style) }
            },
            damanStyle = state.damanStyle,
            onSelectDamanStyle = { style ->
              activeMeasurementKey = null
              viewModel.updateField { it.copy(damanStyle = style) }
            },
            pocketStyles = state.pocketStyles,
            onTogglePocketStyle = { style ->
              activeMeasurementKey = null
              viewModel.updateField { current ->
                val updated = if (style in current.pocketStyles) {
                  current.pocketStyles - style
                } else {
                  current.pocketStyles + style
                }
                current.copy(pocketStyles = updated)
              }
            },
            sleeveStyle = state.sleeveStyle,
            onSelectSleeveStyle = { style ->
              activeMeasurementKey = null
              viewModel.updateField { it.copy(sleeveStyle = style) }
            },
            silaiStyle = state.silaiStyle,
            onSelectSilaiStyle = { style ->
              activeMeasurementKey = null
              viewModel.updateField { it.copy(silaiStyle = style) }
            },
          )

          OutlinedTextField(
            value = state.notes,
            onValueChange = { v -> viewModel.updateField { it.copy(notes = v) } },
            label = { Text(stringResource(R.string.customer_notes_label)) },
            maxLines = 3,
            modifier = Modifier
              .fillMaxWidth()
              .onFocusChanged { if (it.isFocused) activeMeasurementKey = null }
              .testTag("meas_notes_input"),
          )

          if (!state.errorMessage.isNullOrBlank()) {
            Text(
              text = state.errorMessage!!,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("meas_error_text"),
            )
          }

          Button(
            onClick = {
              activeMeasurementKey = null
              focusManager.clearFocus()
              viewModel.save(onSaved = onSaved)
            },
            enabled = !state.isSaving,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("meas_save_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              modifier = Modifier.padding(end = 8.dp),
            )
            Text(stringResource(R.string.measurement_save))
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MeasurementFieldRow(
  leftKey: MeasurementFieldKey,
  rightKey: MeasurementFieldKey,
  state: MeasurementFormState,
  selectionMap: Map<MeasurementFieldKey, TextRange>,
  focusRequesters: Map<MeasurementFieldKey, FocusRequester>,
  bringIntoViewRequesters: Map<MeasurementFieldKey, BringIntoViewRequester>,
  onFocused: (MeasurementFieldKey) -> Unit,
  onValueChanged: (MeasurementFieldKey, String, TextRange) -> Unit,
  onNextKey: (MeasurementFieldKey) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    MeasurementFieldInput(
      key = leftKey,
      value = state.valueFor(leftKey),
      errorText = state.errorFor(leftKey),
      storedSelection = selectionMap[leftKey],
      focusRequester = focusRequesters.getValue(leftKey),
      bringIntoViewRequester = bringIntoViewRequesters.getValue(leftKey),
      onFocused = { onFocused(leftKey) },
      onValueChanged = { raw, sel -> onValueChanged(leftKey, raw, sel) },
      onImeNext = { onNextKey(leftKey) },
      isLastField = false,
      modifier = Modifier.weight(1f),
    )
    MeasurementFieldInput(
      key = rightKey,
      value = state.valueFor(rightKey),
      errorText = state.errorFor(rightKey),
      storedSelection = selectionMap[rightKey],
      focusRequester = focusRequesters.getValue(rightKey),
      bringIntoViewRequester = bringIntoViewRequesters.getValue(rightKey),
      onFocused = { onFocused(rightKey) },
      onValueChanged = { raw, sel -> onValueChanged(rightKey, raw, sel) },
      onImeNext = { onNextKey(rightKey) },
      isLastField = false,
      modifier = Modifier.weight(1f),
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MeasurementFieldInput(
  key: MeasurementFieldKey,
  value: String,
  errorText: String?,
  storedSelection: TextRange?,
  focusRequester: FocusRequester,
  bringIntoViewRequester: BringIntoViewRequester,
  onFocused: () -> Unit,
  onValueChanged: (String, TextRange) -> Unit,
  onImeNext: () -> Unit,
  isLastField: Boolean,
  modifier: Modifier = Modifier,
) {
  val isError = errorText != null
  val baseLabel = key.localizedLabel()
  val labelText = if (key.isRequired) "$baseLabel *" else baseLabel
  val safeSelection = storedSelection?.let {
    TextRange(it.start.coerceIn(0, value.length), it.end.coerceIn(0, value.length))
  } ?: TextRange(value.length)

  val tfv = TextFieldValue(text = value, selection = safeSelection)

  val fieldColors = if (key.isRequired) {
    OutlinedTextFieldDefaults.colors(
      focusedBorderColor = MaterialTheme.colorScheme.primary,
      unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
      focusedLabelColor = MaterialTheme.colorScheme.primary,
      unfocusedLabelColor = MaterialTheme.colorScheme.primary,
      focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
      unfocusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.03f),
      errorBorderColor = MaterialTheme.colorScheme.error,
      errorLabelColor = MaterialTheme.colorScheme.error,
    )
  } else {
    OutlinedTextFieldDefaults.colors(
      focusedBorderColor = MaterialTheme.colorScheme.outline,
      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
      errorBorderColor = MaterialTheme.colorScheme.error,
      errorLabelColor = MaterialTheme.colorScheme.error,
    )
  }

  OutlinedTextField(
    value = tfv,
    onValueChange = { updated ->
      val sanitized = sanitizeMeasurementInput(updated.text)
      val clampedSel = TextRange(
        updated.selection.start.coerceIn(0, sanitized.length),
        updated.selection.end.coerceIn(0, sanitized.length),
      )
      onValueChanged(sanitized, clampedSel)
    },
    label = {
      Text(
        text = labelText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    suffix = { Text(stringResource(R.string.unit_in_short)) },
    isError = isError,
    supportingText = if (errorText != null) {
      {
        Text(
          text = errorText,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    } else {
      null
    },
    singleLine = true,
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Number,
      imeAction = if (isLastField) ImeAction.Done else ImeAction.Next,
    ),
    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
      onNext = { onImeNext() },
      onDone = { onImeNext() },
    ),
    colors = fieldColors,
    modifier = modifier
      .bringIntoViewRequester(bringIntoViewRequester)
      .focusRequester(focusRequester)
      .onFocusChanged { focusState ->
        if (focusState.isFocused) {
          onFocused()
        }
      }
      .testTag(key.testTag),
  )
}
