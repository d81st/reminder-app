@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.h4isenb.reminder

import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics



// =====================================================================
// мелкие элементы
// =====================================================================

@Composable
internal fun Label(s: String, top: Int = 0) {
    Text(s, color = LocalPalette.current.muted, fontSize = 12.sp, modifier = Modifier.padding(top = top.dp, bottom = 3.dp))
}

@Composable
internal fun Hint(s: String) {
    Text(s, color = LocalPalette.current.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
internal fun Chip(s: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        s, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

@Composable
internal fun DayChip(label: String, on: Boolean, acc: Color, weekend: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (on) acc else p.tint)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = when {
                on -> if (acc.luminance() > 0.5f) Color(0xFF1B1F3B) else Color.White
                weekend -> Rose.copy(alpha = 0.8f)
                else -> p.muted
            },
        )
    }
}

@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = p.primary, activeContentColor = p.onPrimary,
                    inactiveContainerColor = p.tint, inactiveContentColor = p.tintText,
                    activeBorderColor = p.primary, inactiveBorderColor = p.line,
                ),
                icon = {},
            ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
    }
}

@Composable
internal fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 15.sp)
            if (hint != null) Text(hint, color = p.muted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun appFieldColors() = LocalPalette.current.let { p ->
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = p.bg, unfocusedContainerColor = p.bg,
        focusedBorderColor = p.primary, unfocusedBorderColor = p.line,
        focusedTextColor = p.ink, unfocusedTextColor = p.ink,
        focusedPlaceholderColor = p.muted, unfocusedPlaceholderColor = p.muted,
    )
}

@Composable
internal fun AppField(value: String, onChange: (String) -> Unit, hint: String, singleLine: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(16.dp),
        colors = appFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Числовое поле: пока вводят, значение применяется в допустимых пределах; при уходе фокуса текст выравнивается. */
@Composable
internal fun NumberField(label: String, value: Int, range: IntRange, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    // значение могло измениться не отсюда (например, загрузка из базы)
    LaunchedEffect(value) {
        if (!focused && text != value.toString()) text = value.toString()
    }
    Column(modifier) {
        Label(label)
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }.take(4)
                text.toIntOrNull()?.let { onChange(it.coerceIn(range)) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            colors = appFieldColors(),
            modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                if (focused && !f.isFocused) {
                    val n = (text.toIntOrNull() ?: range.first).coerceIn(range)
                    text = n.toString()
                    if (n != value) onChange(n)
                }
                focused = f.isFocused
            },
        )
    }
}

@Composable
internal fun TonalButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = p.tint, contentColor = p.tintText),
        modifier = modifier.heightIn(min = 44.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = p.primary, contentColor = p.onPrimary),
        border = BorderStroke(0.dp, Color.Transparent),
        modifier = modifier.heightIn(min = 44.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
internal fun PermRow(label: String, hint: String, ok: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (ok) "Разрешено" else "Нужно разрешить", color = if (ok) Green else Rose, fontSize = 13.sp)
            }
            if (!ok) PrimaryButton("Разрешить", onClick = onClick)
        }
        Text(hint, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

// =====================================================================
// элементы дизайна «Лента дня»
// =====================================================================

/** Заголовок страницы крупным шрифтом Unbounded. */
@Composable
internal fun PageTitle(s: String, modifier: Modifier = Modifier) {
    Text(s, color = LocalPalette.current.ink, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = modifier)
}

/** Значок будильника (контур Material «alarm»), в стандартном наборе значков его нет. */
internal val AlarmIcon: ImageVector by lazy {
    ImageVector.Builder("Alarm", 24.dp, 24.dp, 24f, 24f).addPath(
        PathParser().parsePathString(
            "M22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72zM7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85z" +
                "M12.5,8L11,8v6l4.75,2.85 0.75,-1.23 -4,-2.37L12.5,8zM12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9" +
                "c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Переключатель в цвет карточки. */
@Composable
internal fun AccentSwitch(checked: Boolean, color: Color, onChange: (Boolean) -> Unit, thumbOn: Color = Color.White) {
    val p = LocalPalette.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = color, checkedThumbColor = thumbOn, checkedBorderColor = color,
            uncheckedTrackColor = p.tint, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.line,
        ),
    )
}

/** Семь точек дней недели: закрашены дни, в которые напоминание работает. */
@Composable
internal fun DayDots(days: Int, on: Color, off: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.semantics { contentDescription = Rule.daysText(days) }) {
        for (d in 0..6) Box(Modifier.size(6.dp).clip(CircleShape).background(if (days and (1 shl d) != 0) on else off))
    }
}

/** Круглая кнопка дня недели в редакторе. */
@Composable
internal fun DayCircle(label: String, on: Boolean, acc: Color, weekend: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (on) acc else p.tint)
            .semantics { selected = on }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = when {
                on -> onAccent(p.dark)
                weekend -> if (p.dark) Color(0xFFFB7185) else Color(0xFFA3123B)
                else -> p.muted
            },
        )
    }
}

/** Крупная кнопка времени: подпись сверху, время шрифтом Unbounded. Нажатие открывает циферблат. */
@Composable
internal fun TimeBox(caption: String, minutes: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Column(
        modifier
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.bg)
            .clickable { open = true }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(caption, color = p.muted, fontSize = 11.sp)
        Text(TimeMath.fmt(minutes), color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
    if (open) TimePickDialog(minutes, onDismiss = { open = false }) { m -> open = false; onPick(m) }
}

/** Выбор времени циферблатом (24 часа). */
@Composable
internal fun TimePickDialog(minutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val st = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(st.hour * 60 + st.minute) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        text = { TimePicker(st) },
    )
}

/** Номер шага в редакторе и его название. */
@Composable
internal fun StepTitle(n: Int, title: String, acc: Color, trailing: String? = null) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(acc), contentAlignment = Alignment.Center) {
            Text(n.toString(), color = onAccent(p.dark), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp).weight(1f))
        if (trailing != null) Text(trailing, color = p.muted, fontSize = 13.sp)
    }
}

/** Белая карточка-блок с отступами. */
@Composable
internal fun Block(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** Кнопка-«таблетка» для выбора варианта. */
@Composable
internal fun Pill(label: String, on: Boolean, acc: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) acc else Color.Transparent)
            .border(1.5.dp, if (on) acc else p.line, RoundedCornerShape(18.dp))
            .semantics { selected = on }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) onAccent(p.dark) else p.ink)
    }
}
