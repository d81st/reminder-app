@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.h4isenb.reminder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ru = Locale("ru")

const val MODE_SCHEDULE = 0
const val MODE_RULES = 1

private const val TAB_LISTS = 2
private const val TAB_SETTINGS = 3
/** Журнал открывается из настроек, своей кнопки внизу у него нет. */
private const val TAB_LOG = 4

// =====================================================================
// каркас: вкладки, редактор поверх, заготовки
// =====================================================================

@Composable
fun MainScreen(
    vm: MainViewModel,
    perms: PermState,
    soundTitle: String,
    actions: MainActions,
    pendingImport: List<Rule>?,
    onImport: (replace: Boolean) -> Unit,
    onImportCancel: () -> Unit,
) {
    val p = LocalPalette.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var editingList by rememberSaveable { mutableStateOf<Long?>(null) }
    /** Ключ карточки, открытой в редакторе; null — редактор закрыт. */
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val keyboardOpen = WindowInsets.isImeVisible

    BackHandler(enabled = editing != null || editingList != null || tab != 0) {
        when {
            editing != null -> editing = null
            editingList != null -> editingList?.let { vm.closeList(it); editingList = null }
            tab == TAB_LOG -> tab = TAB_SETTINGS
            else -> tab = 0
        }
    }
    LaunchedEffect(tab) {
        if (tab == TAB_LOG) vm.loadLog()
    }
    val editedList = editingList?.let { k -> vm.lists.firstOrNull { it.key == k } }
    LaunchedEffect(editingList, editedList == null, vm.loaded) {
        if (editingList != null && editedList == null && vm.loaded) editingList = null
    }
    val newList: () -> Unit = {
        val l = vm.addList()
        if (l == null) actions.toast("Достигнут предел: ${MainViewModel.MAX_LISTS} списков")
        else {
            tab = TAB_LISTS
            editingList = l.key
        }
    }
    val onListDeleted: (TaskList) -> Unit = { l ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        editingList = null
        val d = vm.deleteList(l.key)
        if (d != null && (l.title.isNotBlank() || l.items.any { it.text.isNotBlank() })) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val res = snackbar.showSnackbar("Список удалён", actionLabel = "Отменить", duration = SnackbarDuration.Long)
                if (res == SnackbarResult.ActionPerformed) vm.restoreList(d)
            }
        }
    }
    // редактор открыт для напоминания, которого больше нет (например, после импорта с заменой)
    val editedRule = editing?.let { k -> vm.rules.firstOrNull { it.key == k } }
    LaunchedEffect(editing, editedRule == null, vm.loaded) {
        if (editing != null && editedRule == null && vm.loaded) editing = null
    }

    val onDeleted: (Rule) -> Unit = { r ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (editing == r.key) editing = null
        val d = vm.delete(r.key)
        if (d != null) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val res = snackbar.showSnackbar("Удалено: ${r.name}", actionLabel = "Отменить", duration = SnackbarDuration.Long)
                if (res == SnackbarResult.ActionPerformed) vm.restore(d)
            }
        }
    }

    if (showExport) ExportDialog(vm.rules, actions) { showExport = false }
    if (showPaste) PasteDialog(onDismiss = { showPaste = false }) { text ->
        showPaste = false
        actions.importText(text)
    }
    pendingImport?.let { ImportDialog(it, vm.rules.size, onImport, onImportCancel) }
    if (showTemplates) {
        TemplateSheet(onDismiss = { showTemplates = false }, onList = { showTemplates = false; newList() }) { t ->
            showTemplates = false
            val r = vm.add(t)
            if (r == null) actions.toast("Достигнут предел: ${MainViewModel.MAX_RULES} напоминаний")
            else editing = r.key
        }
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            when (tab) {
                0 -> HomePage(
                    rules = vm.rules,
                    loaded = vm.loaded,
                    nextByKey = vm.nextByKey,
                    today = vm.today,
                    now = vm.now,
                    perms = perms,
                    listState = listState,
                    onToggleTheme = actions.toggleTheme,
                    onOpen = { editing = it.key },
                    onEnabled = { r, on -> vm.update(r.key) { it.copy(enabled = on) } },
                    showTip = !vm.tipHidden,
                    onHideTip = vm::hideTip,
                    pausedUntil = vm.pausedUntil,
                    onPause = vm::setPause,
                    onMove = vm::move,
                    done = vm.done,
                    onToggleDone = vm::toggleDone,
                )
                1 -> CalendarPage(vm.rules, vm.now, onOpen = { editing = it.key }) { ymd ->
                    val r = vm.add(Templates.onDate(ymd, vm.now))
                    if (r == null) actions.toast("Достигнут предел: ${MainViewModel.MAX_RULES} напоминаний")
                    else editing = r.key
                }
                TAB_LISTS -> ListsPage(vm.lists) { editingList = it.key }
                TAB_LOG -> LogPage(vm, onBack = { tab = TAB_SETTINGS })
                else -> SettingsPage(perms, soundTitle, actions, onExport = { showExport = true }, onPaste = { showPaste = true },
                    onLog = { tab = TAB_LOG })
            }
        }

        if (!keyboardOpen) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                // «+» в середине панели: на вкладке «Списки» сразу новый список, иначе выбор, что создать
                BottomBar(if (tab == TAB_LOG) TAB_SETTINGS else tab, onTab = { tab = it }) {
                    if (tab == TAB_LISTS) newList() else showTemplates = true
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom = if (editing != null || editingList != null) 16.dp else 100.dp),
        )

        AnimatedVisibility(
            visible = editedRule != null,
            enter = slideInVertically { it / 6 } + fadeIn(),
            exit = slideOutVertically { it / 6 } + fadeOut(),
        ) {
            // последнее открытое напоминание, чтобы при закрытии редактор не пропадал раньше анимации
            val shown = remember { mutableStateOf<Rule?>(null) }
            if (editedRule != null) shown.value = editedRule
            shown.value?.let { r ->
                RuleEditor(
                    r = r,
                    allRules = vm.rules,
                    now = vm.now,
                    actions = actions,
                    onChange = { change -> vm.update(r.key, change) },
                    onDelete = { onDeleted(r) },
                    onClose = { editing = null },
                )
            }
        }

        AnimatedVisibility(
            visible = editedList != null,
            enter = slideInVertically { it / 6 } + fadeIn(),
            exit = slideOutVertically { it / 6 } + fadeOut(),
        ) {
            val shown = remember { mutableStateOf<TaskList?>(null) }
            if (editedList != null) shown.value = editedList
            shown.value?.let { l ->
                ListEditor(
                    l = l,
                    onChange = { change -> vm.updateList(l.key, change) },
                    onShare = { ListCodec.toText(l).takeIf { it.isNotBlank() }?.let(actions.shareText) },
                    onDelete = { onListDeleted(l) },
                    onClose = { vm.closeList(l.key); editingList = null },
                )
            }
        }
    }
}

/**
 * Нижняя панель: Сегодня, Календарь, «+» посередине, Списки, Настройки.
 * Подписи мелко под значками, открытая вкладка — с подложкой.
 */
@Composable
internal fun BottomBar(tab: Int, onTab: (Int) -> Unit, onAdd: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            .fillMaxWidth()
            .height(70.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(RoundedCornerShape(24.dp))
            .background(p.card)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabItem("Сегодня", Icons.Filled.Home, tab == 0, Modifier.weight(1f)) { onTab(0) }
        TabItem("Календарь", Icons.Filled.DateRange, tab == 1, Modifier.weight(1f)) { onTab(1) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(52.dp)
                    .shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = p.primary, spotColor = p.primary)
                    .clip(RoundedCornerShape(18.dp))
                    .background(p.primary)
                    .clickable(onClickLabel = "Создать", role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = "Создать напоминание или список" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, contentDescription = null, tint = p.onPrimary, modifier = Modifier.size(28.dp)) }
        }
        TabItem("Списки", Icons.Filled.CheckCircle, tab == TAB_LISTS, Modifier.weight(1f)) { onTab(TAB_LISTS) }
        TabItem("Настройки", Icons.Filled.Settings, tab == TAB_SETTINGS, Modifier.weight(1f)) { onTab(TAB_SETTINGS) }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = on }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.size(width = 44.dp, height = 28.dp).clip(RoundedCornerShape(14.dp)).background(if (on) p.tint else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (on) p.tintText else p.muted, modifier = Modifier.size(20.dp)) }
        Text(label, color = if (on) p.tintText else p.muted, fontSize = 10.sp, maxLines = 1,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
    }
}

// =====================================================================
// страница «Сегодня»
// =====================================================================

/** Главная страница без ViewModel: всё приходит параметрами, поэтому её можно снимать скриншот-тестами. */
@Composable
fun HomePage(
    rules: List<Rule>,
    loaded: Boolean,
    nextByKey: Map<Long, TimeMath.Event>,
    today: List<TimeMath.Event>,
    now: Long,
    perms: PermState,
    listState: LazyListState = rememberLazyListState(),
    onToggleTheme: () -> Unit,
    onOpen: (Rule) -> Unit,
    onEnabled: (Rule, Boolean) -> Unit,
    showTip: Boolean = false,
    onHideTip: () -> Unit = {},
    pausedUntil: Long = 0L,
    onPause: (Long) -> Unit = {},
    onMove: (Long, Long) -> Unit = { _, _ -> },
    done: Map<String, Long> = emptyMap(),
    onToggleDone: (Rule, Long) -> Unit = { _, _ -> },
    initialMode: Int = MODE_SCHEDULE,
    initialDay: Int = 0,
) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    // «Расписание» — лента дня, «Напоминания» — карточки для настройки
    var mode by rememberSaveable { mutableIntStateOf(initialMode) }
    var dayPicked by rememberSaveable { mutableIntStateOf(initialDay) }
    val todayYmd = Rule.ymd(now)
    // 0 — «сегодня», какой бы день ни наступил
    val day = if (dayPicked == 0) todayYmd else dayPicked
    // карточки переставляются долгим нажатием; у прочих строк списка ключи-строки, их не трогаем
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.key as? Long
        val t = to.key as? Long
        if (f != null && t != null) onMove(f, t)
    }
    if (!loaded) return
    // сегодняшние времена каждого напоминания: для полосок на карточках
    val todayByKey = remember(today) { today.groupBy({ it.rule.key }, { it.minute }) }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    LazyColumn(
        state = listState,
        // запас снизу под плавающую панель и кнопку «+»
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { TodayHeader(rules, nextByKey, now, onToggleTheme, pausedUntil, onPause) }
        if (pausedUntil > now) item(key = "paused") { PausedBanner(pausedUntil, now) { onPause(0L) } }
        if (showTip && rules.isNotEmpty()) item(key = "tip") { TipBanner(onHideTip) }
        val warn = when {
            !perms.notifications -> "Уведомления выключены. Разрешите их во вкладке «Настройки»."
            !perms.fullScreen && rules.any { it.isAlarm && it.enabled } ->
                "Будильник не сможет открыться на весь экран. Разрешите во вкладке «Настройки»."
            else -> null
        }
        if (warn != null) {
            item(key = "warn") {
                Text(warn, color = if (p.dark) Color(0xFFFDA4AF) else Rose, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(14.dp))
            }
        }
        item(key = "mode") {
            Box(Modifier.padding(top = 6.dp)) {
                Segmented(listOf("Расписание", "Напоминания · ${rules.size}"), mode) { mode = it }
            }
        }
        if (mode == MODE_SCHEDULE) {
            item(key = "strip") { WeekStrip(rules, now, day) { dayPicked = if (it == todayYmd) 0 else it } }
            item(key = "dayTitle") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(if (day == todayYmd) "Сегодня" else Phrases.date(day, todayYmd).replaceFirstChar { it.uppercase() },
                        color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    if (day != todayYmd) {
                        Text("К сегодня", color = p.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { dayPicked = 0 }
                                .padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            dayTimeline(rules, day, now, done, pausedUntil, onOpen, onToggleDone)
            return@LazyColumn
        }
        if (rules.isEmpty()) {
            item(key = "empty") {
                Text("Пока нет напоминаний. Нажмите «+»: можно начать с готовой заготовки.", color = p.muted, fontSize = 15.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
            }
        }
        itemsIndexed(rules, key = { _, r -> r.key }) { i, r ->
            ReorderableItem(reorder, key = r.key) { dragging ->
                val lift by animateDpAsState(if (dragging) 10.dp else 0.dp, label = "lift")
                // для экранного диктора: те же перестановки без перетаскивания
                val actions = buildList {
                    if (i > 0) add(CustomAccessibilityAction("Переместить выше") { onMove(r.key, rules[i - 1].key); true })
                    if (i < rules.size - 1) add(CustomAccessibilityAction("Переместить ниже") { onMove(r.key, rules[i + 1].key); true })
                }
                Box(
                    Modifier
                        .shadow(lift, RoundedCornerShape(24.dp))
                        .semantics { customActions = actions }
                        .longPressDraggableHandle(onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }),
                ) {
                    RuleCard(r, nextByKey[r.key], now, todayByKey[r.key].orEmpty(), nowMin, onOpen = { onOpen(r) }, onEnabled = { onEnabled(r, it) })
                }
            }
        }
    }
}

/** Дата, тема и крупно — сколько осталось до ближайшего напоминания. */
@Composable
private fun TodayHeader(
    rules: List<Rule>,
    nextByKey: Map<Long, TimeMath.Event>,
    now: Long,
    onToggleTheme: () -> Unit,
    pausedUntil: Long,
    onPause: (Long) -> Unit,
) {
    val p = LocalPalette.current
    val date = remember(now / 60_000 / 60) {
        SimpleDateFormat("EEEE, d MMMM", ru).format(Date(now)).replaceFirstChar { it.titlecase(ru) }
    }
    val soonest = nextByKey.values.minByOrNull { it.at }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (pausedUntil <= now && rules.isNotEmpty()) PauseButton(now, onPause)
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(p.card)
                    .clickable(role = Role.Button, onClickLabel = "Сменить тему", onClick = onToggleTheme)
                    .semantics { contentDescription = if (p.dark) "Светлая тема" else "Тёмная тема" },
                contentAlignment = Alignment.Center,
            ) { Text(if (p.dark) "☀" else "☾", color = p.ink, fontSize = 20.sp) }
        }
        if (soonest == null) {
            Text(if (rules.isEmpty()) "Начнём?" else "Всё спокойно", color = p.ink, fontFamily = Unbounded,
                fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, modifier = Modifier.padding(top = 4.dp))
            Text(if (rules.isEmpty()) "Добавьте первое напоминание кнопкой «+»" else "Ближайшую неделю напоминаний нет",
                color = p.muted, fontSize = 16.sp)
        } else {
            val t = soonest.baseAt + soonest.rule.lead * TimeMath.MIN
            Text(TimeMath.countdown(t, now), color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Bold,
                fontSize = 32.sp, lineHeight = 36.sp, modifier = Modifier.padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (soonest.rule.isAlarm) Amber else accent(soonest.rule.color, p.dark)))
                Text(soonest.rule.name, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false))
                Text(" · " + whenShort(t, now) + TimeMath.fmt(soonest.minute), color = p.muted, fontSize = 16.sp, maxLines = 1)
            }
        }
    }
}

/** Кнопка «Пауза» в шапке: меню на час, на 3 часа, до конца дня. */
@Composable
private fun PauseButton(now: Long, onPause: (Long) -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(p.card)
                .clickable(role = Role.Button, onClickLabel = "Поставить на паузу") { open = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(R.drawable.ic_pause), contentDescription = null, tint = p.ink, modifier = Modifier.size(16.dp))
            Text("Пауза", color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = p.card) {
            for ((label, until) in listOf(
                "На 1 час" to now + Pause.HOUR,
                "На 3 часа" to now + 3 * Pause.HOUR,
                "До конца дня" to TimeMath.endOfDay(now),
            )) {
                DropdownMenuItem(text = { Text(label, color = p.ink) }, onClick = { open = false; onPause(until) })
            }
        }
    }
}

/** Плашка «На паузе до 15:30» с кнопкой «Продолжить». */
@Composable
private fun PausedBanner(until: Long, now: Long, onResume: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.alarmCard).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_pause), contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("На паузе " + Phrases.pauseUntil(until, now), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Напоминания и будильники не придут", color = p.alarmMuted, fontSize = 12.sp)
        }
        Box(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(Amber)
                .clickable(role = Role.Button, onClick = onResume).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Продолжить", color = p.alarmCard, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** «завтра » / «пн » перед временем, если это не сегодня. */
private fun whenShort(t: Long, now: Long): String = when {
    sameDay(t, now) -> ""
    sameDay(t, now + 24 * 60 * TimeMath.MIN) -> "завтра "
    else -> SimpleDateFormat("EE", ru).format(Date(t)) + " "
}

internal fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

/**
 * Лента дня: шкала от утра до вечера, отметки всех сегодняшних напоминаний в их цвете, черта «сейчас».
 * Прошедшие отметки бледнее, ближайшая — с ореолом.
 */
@Composable
fun DayTimeline(today: List<TimeMath.Event>, now: Long, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    var startH = minOf(8, (today.minOfOrNull { it.minute } ?: 8 * 60) / 60)
    var endH = maxOf(18, ((today.maxOfOrNull { it.minute } ?: 18 * 60) + 59) / 60)
    if ((endH - startH) % 2 != 0) { if (endH < 24) endH++ else startH-- }
    val span = ((endH - startH) * 60).toFloat()
    val left = today.count { it.minute > nowMin }
    val nextMinute = today.firstOrNull { it.minute > nowMin }?.minute

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.card)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
            .semantics { contentDescription = "Сегодня осталось $left из ${today.size}" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Сегодня", color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (today.isEmpty()) "напоминаний нет" else "осталось $left из ${today.size}", color = p.muted, fontSize = 13.sp)
        }
        val ink = p.ink
        val track = p.line
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val w = size.width
            val mid = size.height / 2
            fun x(minute: Int) = ((minute - startH * 60) / span).coerceIn(0f, 1f) * w
            val nowX = x(nowMin)
            val inRange = nowMin >= startH * 60 && nowMin <= endH * 60
            drawRoundRect(track, Offset(0f, mid - 1.dp.toPx()), Size(w, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            if (nowMin > startH * 60) {
                drawRoundRect(ink, Offset(0f, mid - 1.dp.toPx()), Size(nowX, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            }
            for (e in today) {
                val cx = x(e.minute)
                val past = e.minute <= nowMin
                val c = if (e.rule.isAlarm) Amber else accent(e.rule.color, p.dark)
                val a = if (past) 0.3f else 1f
                if (e.minute == nextMinute) drawCircle(c.copy(alpha = 0.25f), 10.dp.toPx(), Offset(cx, mid))
                when {
                    e.rule.isAlarm -> drawCircle(c.copy(alpha = a), 7.dp.toPx(), Offset(cx, mid))
                    e.rule.interval >= 60 || e.rule.start == e.rule.end ->
                        drawRoundRect(c.copy(alpha = a), Offset(cx - 2.dp.toPx(), mid - 12.dp.toPx()), Size(4.dp.toPx(), 24.dp.toPx()),
                            CornerRadius(2.dp.toPx()))
                    else -> drawCircle(c.copy(alpha = a), 5.dp.toPx(), Offset(cx, mid))
                }
            }
            if (inRange) drawRect(ink, Offset(nowX - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (h in startH..endH step 2) Text(String.format(Locale.US, "%02d", h), color = p.muted, fontSize = 11.sp)
        }
    }
}

// =====================================================================
// карточка напоминания
// =====================================================================

/** Подпись под крупным временем: «следующее · через 42 мин», «завтра», «в пятницу». */
private fun nextLabel(r: Rule, next: TimeMath.Event?, now: Long): String {
    if (!r.enabled) return "выключено"
    if (next == null) return if (r.isOneDay && r.date <= Rule.ymd(now)) "уже прошло" else "не запланировано"
    val t = next.baseAt + r.lead * TimeMath.MIN
    return when {
        sameDay(t, now) && t - now <= 3 * 60 * TimeMath.MIN -> "следующее · " + TimeMath.countdown(t, now)
        sameDay(t, now) -> "следующее · сегодня"
        sameDay(t, now + 24 * 60 * TimeMath.MIN) -> "завтра"
        t - now < 6 * 24 * 60 * TimeMath.MIN -> SimpleDateFormat("EEEE", ru).format(Date(t))
        else -> SimpleDateFormat("d MMMM", ru).format(Date(t))
    }
}

/** Подсказка для новичка над карточками; закрывается навсегда. */
@Composable
private fun TipBanner(onHide: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.tint).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Нажмите на карточку, чтобы изменить или удалить. Удерживайте, чтобы перетащить.", color = p.tintText, fontSize = 13.sp,
            lineHeight = 19.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Скрыть подсказку", onClick = onHide),
            contentAlignment = Alignment.Center,
        ) { Text("×", color = p.tintText, fontSize = 20.sp) }
    }
}

/**
 * Карточка напоминания: значок и название, внутри — панель «следующее» с прогрессом дня и днями этой недели.
 * Будильник — та же карточка в тёмных тонах.
 */
@Composable
fun RuleCard(
    r: Rule,
    next: TimeMath.Event?,
    now: Long,
    todayTimes: List<Int>,
    nowMin: Int,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    val p = LocalPalette.current
    val alarm = r.isAlarm
    val acc = if (alarm) Amber else accent(r.color, p.dark)
    val container = if (alarm) p.alarmCard else p.card
    val panel = if (alarm) p.alarmTile.copy(alpha = 0.55f) else if (p.dark) p.bg.copy(alpha = 0.6f) else p.bg
    val ink = if (alarm) Color.White else p.ink
    val muted = if (alarm) p.alarmMuted else p.muted
    val track = if (alarm) p.alarmTile else p.line
    val fade = if (r.enabled) 1f else 0.55f
    val icon = when (Phrases.mode(r)) {
        Phrases.MODE_PERSISTENT -> Icons.Filled.Refresh
        Phrases.MODE_ALARM -> AlarmIcon
        else -> Icons.Filled.Notifications
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(container)
            .clickable(onClickLabel = "Изменить", onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).alpha(fade).clip(CircleShape).background(acc.copy(alpha = if (alarm) 0.18f else 0.14f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = acc, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).alpha(fade)) {
                Text(r.name, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Phrases.modeLabel(r), color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AccentSwitch(r.enabled, acc, onEnabled, thumbOn = if (alarm) p.alarmCard else onAccent(p.dark))
        }
        Column(
            Modifier.fillMaxWidth().alpha(fade).clip(RoundedCornerShape(18.dp)).background(panel).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val past = todayTimes.count { it - r.lead <= nowMin }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(nextLabel(r, next, now).replaceFirstChar { it.uppercase() }, color = muted, fontSize = 12.sp, maxLines = 1)
                    Text(TimeMath.fmt(next?.minute ?: r.start), color = acc, fontFamily = Unbounded, fontWeight = FontWeight.Bold,
                        fontSize = 26.sp, lineHeight = 28.sp)
                }
                if (todayTimes.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("сегодня", color = muted, fontSize = 12.sp)
                        Text("$past/${todayTimes.size}", color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (todayTimes.size > 1) {
                // сколько сегодняшних времён уже позади
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)
                    .semantics { contentDescription = "Сегодня позади $past из ${todayTimes.size}" }) {
                    Box(Modifier.fillMaxWidth(past / todayTimes.size.toFloat()).height(6.dp).clip(RoundedCornerShape(3.dp)).background(acc))
                }
            }
            WeekCircles(r, now, acc, ink, muted, track)
        }
        val line = buildString {
            append(Phrases.schedule(r))
            append(" · ")
            append(Phrases.days(r, Rule.ymd(now)))
            if (r.lead > 0) append(" · заранее за ${r.lead} мин")
            if (r.skipOverlap) append(" · без совпадений с другими")
            if (alarm && r.challenge == Rule.CHALLENGE_MATH) append(" · с примером")
        }
        Text(line, color = muted, fontSize = 13.sp, modifier = Modifier.alpha(fade).padding(horizontal = 2.dp))
    }
}

/** Дни этой недели: в какие напоминание работает (заливка), сегодня — ярче. Для «раз в N дней» и дат — по настоящим датам. */
@Composable
private fun WeekCircles(r: Rule, now: Long, acc: Color, ink: Color, muted: Color, track: Color) {
    val c = Calendar.getInstance().apply { timeInMillis = now }
    val todayWd = Rule.weekdayOf(c.get(Calendar.DAY_OF_WEEK))
    c.add(Calendar.DAY_OF_YEAR, -todayWd)
    val days = (0..6).map { wd ->
        val on = r.activeOn(wd, Rule.ymd(c))
        c.add(Calendar.DAY_OF_YEAR, 1)
        on
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = "На этой неделе: " + (0..6).filter { days[it] }.joinToString(", ") { Rule.DAY_SHORT[it] }.ifEmpty { "ни одного дня" }
    }, horizontalArrangement = Arrangement.SpaceBetween) {
        for (wd in 0..6) {
            val on = days[wd]
            val today = wd == todayWd
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(when {
                        on && today -> acc
                        on -> acc.copy(alpha = 0.18f)
                        else -> Color.Transparent
                    })
                    .then(if (!on) Modifier.border(1.5.dp, track, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(Rule.DAY_SHORT[wd], fontSize = 11.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        on && today -> if (acc == Amber) Color(0xFF17163A) else onAccent(LocalPalette.current.dark)
                        on -> acc
                        else -> muted.copy(alpha = 0.6f)
                    })
            }
        }
    }
}

// =====================================================================
// заготовки для «+»
// =====================================================================

@Composable
private fun TemplateSheet(onDismiss: () -> Unit, onList: () -> Unit, onPick: (Rule) -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.bg,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Создать", color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Medium, fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp))
            TemplateRow("Список дел", "чек-лист без времени: покупки, задачи", p.primary, false, Icons.Filled.Check, onList)
            Text("Напоминание", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            for ((i, t) in Templates.all.withIndex()) {
                val sample = t.make()
                TemplateRow(t.title, t.hint, if (sample.isAlarm) Amber else accent(i + 1, p.dark), sample.isAlarm) { onPick(t.make()) }
            }
            TemplateRow("С чистого листа", "все настройки вручную", p.muted, false) { onPick(Templates.blank()) }
        }
    }
}

@Composable
private fun TemplateRow(title: String, hint: String, color: Color, alarm: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon ?: if (alarm) AlarmIcon else Icons.Filled.Notifications, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = p.muted, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(0.dp))
}
