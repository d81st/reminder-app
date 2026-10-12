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


// =====================================================================
// обмен: окна экспорта и импорта
// =====================================================================

/** Краткая строка о напоминании для списков обмена. */
internal fun shortInfo(r: Rule): String {
    val time = if (r.start == r.end) TimeMath.fmt(r.start)
    else TimeMath.fmt(r.start) + "–" + TimeMath.fmt(r.end) + ", каждые " + r.interval + " мин"
    return (if (r.isAlarm) "⏰ " else "") + time + " · " + (if (r.isDated) Phrases.date(r.date, Rule.ymd(System.currentTimeMillis())) else Rule.daysText(r.days))
}

@Composable
internal fun DialogCard(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(p.card)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) { content() }
    }
}

@Composable
internal fun ExportDialog(rules: List<Rule>, actions: MainActions, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val picked = remember { mutableStateMapOf<Long, Boolean>() }
    val chosen = rules.filter { picked[it.key] != false }
    val code = remember(chosen) { if (chosen.isEmpty()) "" else RuleCodec.encode(chosen) }
    val bmp = remember(code) { if (code.isEmpty()) null else QrCodes.bitmap(code) }

    DialogCard(onDismiss) {
        Text("Поделиться напоминаниями", color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Hint("Коллега открывает «Настройки» → «Сканировать QR-код» и наводит камеру на экран. Мелодии будильников не передаются.")
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            when {
                chosen.isEmpty() -> Text("Выберите хотя бы одно напоминание", color = p.muted, fontSize = 14.sp)
                bmp == null -> Text("Слишком много для одного QR-кода. Уберите часть напоминаний или отправьте текстом.",
                    color = Rose, fontSize = 14.sp, textAlign = TextAlign.Center)
                else -> Image(
                    bmp.asImageBitmap(), contentDescription = "QR-код с напоминаниями",
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
        }
        for (r in rules) {
            Row(
                Modifier.fillMaxWidth().clickable { picked[r.key] = picked[r.key] == false },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = picked[r.key] != false, onCheckedChange = { picked[r.key] = it })
                Column(Modifier.weight(1f)) {
                    Text(r.name, color = p.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(shortInfo(r), color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (bmp != null) {
            PrimaryButton("Отправить картинкой", Modifier.fillMaxWidth()) {
                val caption = "QR-код с напоминаниями для «Напоминалки». В приложении: «Настройки» → «Из картинки»" +
                    if (code.length < 900) ".\n\n$code" else "."
                actions.shareImage(bmp, caption)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (chosen.isNotEmpty()) {
            TonalButton("Отправить текстом", Modifier.fillMaxWidth()) {
                actions.shareText(
                    "Напоминания для приложения «Напоминалка». Чтобы добавить: «Поделиться» → «Напоминалка», " +
                        "или скопируйте сообщение и в приложении нажмите «Вставить текст».\n\n$code"
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Закрыть", color = p.muted) }
    }
}

@Composable
internal fun PasteDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    var text by remember {
        mutableStateOf(clipboard.getText()?.text?.takeIf { RuleCodec.find(it) != null } ?: "")
    }
    DialogCard(onDismiss) {
        Text("Вставить текст", color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Hint("Вставьте присланное сообщение целиком: код начинается с «${RuleCodec.PREFIX}».")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(RuleCodec.PREFIX + "…") },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(16.dp),
            colors = appFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row {
            TonalButton("Из буфера", Modifier.weight(1f).padding(end = 4.dp)) { text = clipboard.getText()?.text ?: "" }
            PrimaryButton("Прочитать", Modifier.weight(1f).padding(start = 4.dp)) { onImport(text) }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена", color = p.muted) }
    }
}

@Composable
internal fun ImportDialog(list: List<Rule>, mine: Int, onImport: (replace: Boolean) -> Unit, onCancel: () -> Unit) {
    val p = LocalPalette.current
    var confirmReplace by remember { mutableStateOf(false) }
    DialogCard(onCancel) {
        Text(
            "Получено: ${list.size} ${TimeMath.plural(list.size, "напоминание", "напоминания", "напоминаний")}",
            color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        for (r in list) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 10.dp).size(10.dp).clip(CircleShape).background(accent(r.color, p.dark)))
                Column {
                    Text(r.name, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(shortInfo(r) + if (r.enabled) "" else " · выключено", color = p.muted, fontSize = 12.sp)
                }
            }
        }
        if (list.any { it.isAlarm }) {
            Hint("Будильники добавятся выключенными: проверьте время и включите их сами.")
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Добавить к моим", Modifier.fillMaxWidth()) { onImport(false) }
        if (mine > 0) {
            Spacer(Modifier.height(8.dp))
            if (!confirmReplace) {
                TonalButton("Заменить все мои", Modifier.fillMaxWidth()) { confirmReplace = true }
            } else {
                Hint("Ваши напоминания ($mine) будут удалены и заменены полученными.")
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { onImport(true) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) { Text("Да, заменить", fontWeight = FontWeight.Bold) }
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена", color = p.muted) }
    }
}
