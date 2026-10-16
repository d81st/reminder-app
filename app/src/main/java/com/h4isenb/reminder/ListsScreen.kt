package com.h4isenb.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
// вкладка «Списки»: карточки в две колонки, как заметки
// =====================================================================

@Composable
fun ListsPage(lists: List<TaskList>, onOpen: (TaskList) -> Unit) {
    val p = LocalPalette.current
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "title", span = StaggeredGridItemSpan.FullLine) {
            Row(verticalAlignment = Alignment.Bottom) {
                PageTitle("Списки", Modifier.weight(1f))
                if (lists.isNotEmpty()) Text(lists.size.toString(), color = p.muted, fontSize = 13.sp)
            }
        }
        if (lists.isEmpty()) {
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                Text("Пока нет списков. Нажмите «+» внизу и выберите «Список дел»: покупки, задачи на день, что взять в поездку.",
                    color = p.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 24.dp))
            }
        }
        items(lists, key = { it.key }) { l -> ListCard(l) { onOpen(l) } }
    }
}

@Composable
private fun ListCard(l: TaskList, onClick: () -> Unit) {
    val p = LocalPalette.current
    val acc = accent(l.color, p.dark)
    val open = l.open.filter { it.text.isNotBlank() }
    val total = l.items.count { it.text.isNotBlank() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.card).clickable(onClickLabel = "Открыть", onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(acc))
            Text(l.title.ifBlank { "Без названия" }, color = if (l.title.isBlank()) p.muted else p.ink, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        for (it in open.take(6)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top = 3.dp).size(12.dp).clip(RoundedCornerShape(4.dp)).border(1.5.dp, acc, RoundedCornerShape(4.dp)))
                Text(it.text, color = p.ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (open.size > 6) Text("ещё ${open.size - 6}", color = p.muted, fontSize = 12.sp)
        if (total > 0) {
            val done = total - open.size
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.line)) {
                Box(Modifier.fillMaxWidth(done / total.toFloat()).height(4.dp).clip(RoundedCornerShape(2.dp)).background(acc))
            }
            Text(if (open.isEmpty()) "Всё сделано · $total" else "Сделано $done из $total", color = p.muted, fontSize = 11.sp)
        }
    }
}

// =====================================================================
// редактор списка
// =====================================================================

@Composable
fun ListEditor(
    l: TaskList,
    onChange: ((TaskList) -> TaskList) -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val p = LocalPalette.current
    val acc = accent(l.color, p.dark)
    var showDone by rememberSaveable(l.key) { mutableStateOf(true) }
    // ключ пункта, на который нужно поставить курсор (только что добавленный)
    var focusKey by remember { mutableStateOf<Long?>(null) }
    val requesters = remember { HashMap<Long, FocusRequester>() }
    fun requester(k: Long) = requesters.getOrPut(k) { FocusRequester() }

    fun addAfter(afterKey: Long?) {
        val item = TaskItem("")
        onChange { cur ->
            if (cur.items.size >= ListCodec.MAX_ITEMS) cur
            else {
                // после пункта afterKey, иначе в конец неотмеченных
                val i = cur.items.indexOfFirst { it.key == afterKey }
                cur.copy(items = cur.items.toMutableList().apply { add(if (i < 0) cur.items.count { !it.done } else i + 1, item) })
            }
        }
        focusKey = item.key
    }

    val open = l.items.filter { !it.done }
    val done = l.items.filter { it.done }

    Column(Modifier.fillMaxSize().background(p.bg).windowInsetsPadding(WindowInsets.statusBars).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = p.ink) }
            Box(Modifier.weight(1f))
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "Отправить текстом", tint = p.muted) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Удалить список", tint = p.muted) }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                BasicTextField(
                    value = l.title,
                    onValueChange = { v -> onChange { it.copy(title = v.take(ListCodec.MAX_TITLE)) } },
                    textStyle = TextStyle(color = p.ink, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                    cursorBrush = SolidColor(acc),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { addAfter(open.lastOrNull()?.key) }),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    decorationBox = { inner ->
                        if (l.title.isEmpty()) Text("Название", color = p.muted, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        inner()
                    },
                )
            }
            item(key = "colors") {
                Box(Modifier.padding(bottom = 10.dp)) { ColorPicker(l.color) { i -> onChange { it.copy(color = i) } } }
            }
            items(open, key = { it.key }) { item ->
                ItemRow(item, acc, requester(item.key),
                    onText = { v -> onChange { cur -> cur.copy(items = cur.items.map { if (it.key == item.key) it.copy(text = v.take(ListCodec.MAX_TEXT)) else it }) } },
                    onToggle = { onChange { cur -> cur.copy(items = cur.items.map { if (it.key == item.key) it.copy(done = !it.done) else it }) } },
                    onNext = { addAfter(item.key) },
                    onRemove = { onChange { cur -> cur.copy(items = cur.items.filter { it.key != item.key }) } },
                )
            }
            item(key = "add") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { addAfter(open.lastOrNull()?.key) }.padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = acc, modifier = Modifier.size(22.dp))
                    Text("Пункт", color = p.muted, fontSize = 16.sp)
                }
            }
            if (done.isNotEmpty()) {
                item(key = "doneHeader") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClickLabel = if (showDone) "Свернуть" else "Показать") { showDone = !showDone },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(if (showDone) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = p.muted)
                        Text("Отмечено · ${done.size}", color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Убрать", color = acc, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button) { onChange { cur -> cur.copy(items = cur.items.filter { !it.done }) } }
                                .padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                if (showDone) {
                    items(done, key = { it.key }) { item ->
                        ItemRow(item, acc, requester(item.key),
                            onText = { v -> onChange { cur -> cur.copy(items = cur.items.map { if (it.key == item.key) it.copy(text = v.take(ListCodec.MAX_TEXT)) else it }) } },
                            onToggle = { onChange { cur -> cur.copy(items = cur.items.map { if (it.key == item.key) it.copy(done = !it.done) else it }) } },
                            onNext = { addAfter(null) },
                            onRemove = { onChange { cur -> cur.copy(items = cur.items.filter { it.key != item.key }) } },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(focusKey, l.items.size) {
        val k = focusKey ?: return@LaunchedEffect
        if (l.items.any { it.key == k }) {
            try {
                requesters[k]?.requestFocus()
            } catch (ignored: IllegalStateException) {
                // поле ещё не на экране
            }
            focusKey = null
        }
    }
}

@Composable
private fun ItemRow(
    item: TaskItem,
    acc: Color,
    focus: FocusRequester,
    onText: (String) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onRemove: () -> Unit,
) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
                .background(if (item.done) acc else Color.Transparent)
                .border(2.dp, if (item.done) acc else p.muted.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .semantics { contentDescription = item.text.ifBlank { "Пункт" }; stateDescription = if (item.done) "Отмечено" else "Не отмечено" },
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) Icon(Icons.Filled.Check, contentDescription = null, tint = onAccent(p.dark), modifier = Modifier.size(16.dp))
        }
        BasicTextField(
            value = item.text,
            onValueChange = { v ->
                // Enter в конце строки — новый пункт, а не перенос строки
                if (v.endsWith("\n")) onNext() else onText(v)
            },
            textStyle = TextStyle(
                color = if (item.done) p.muted else p.ink, fontSize = 16.sp,
                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
            cursorBrush = SolidColor(acc),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier.weight(1f).focusRequester(focus).padding(vertical = 8.dp),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Удалить пункт", tint = p.muted.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}
