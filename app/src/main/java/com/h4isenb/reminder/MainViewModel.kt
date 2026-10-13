package com.h4isenb.reminder

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Состояние главного экрана: список напоминаний с отложенным сохранением, журнал и строка «Следующее».
 * Вся работа с базой и расчёт расписания идут в фоне, чтобы интерфейс не подтормаживал.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    var rules by mutableStateOf<List<Rule>>(emptyList())
        private set
    /** false, пока напоминания читаются из базы при запуске. */
    var loaded by mutableStateOf(false)
        private set
    var log by mutableStateOf<List<Store.LogItem>>(emptyList())
        private set
    var nextAt by mutableStateOf<Long?>(null)
        private set
    /** Ближайшее основное уведомление каждого напоминания (по ключу карточки). */
    var nextByKey by mutableStateOf<Map<Long, TimeMath.Event>>(emptyMap())
        private set
    /** Все времена сегодняшнего дня для ленты дня. */
    var today by mutableStateOf<List<TimeMath.Event>>(emptyList())
        private set
    /** Текущее время для подписей; обновляется раз в 30 секунд. */
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set

    /** До какого момента пауза (0 — нет паузы). */
    var pausedUntil by mutableLongStateOf(0L)
        private set

    /** Пауза до момента until (0 — снять). */
    fun setPause(until: Long) {
        pausedUntil = until
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Pause.set(ctx, until) }
            recomputeNext(0)
            refreshStatus()
        }
    }

    /** Подсказка «нажмите на карточку…» скрыта навсегда. */
    var tipHidden by mutableStateOf(true)
        private set

    fun hideTip() {
        tipHidden = true
        viewModelScope.launch(Dispatchers.IO) { Store.prefs(ctx).edit().putBoolean("tipHomeHidden", true).apply() }
    }

    /** Только что удалённое напоминание, которое ещё можно вернуть. */
    class Deleted(val rule: Rule, val index: Int)

    private var dirty = false
    private var saveJob: Job? = null
    private var nextJob: Job? = null
    /**
     * id, полученные новыми карточками при сохранении (ключ карточки -> id). Сохранения могут идти одно за другим,
     * и следующее должно знать id, выданный предыдущим, иначе новое напоминание запишется в базу дважды.
     */
    private val knownIds = ConcurrentHashMap<Long, Long>()
    private val saveLock = Any()
    private val saveMutex = Mutex()

    init {
        viewModelScope.launch {
            rules = withContext(Dispatchers.IO) { Store.load(ctx) }
            tipHidden = withContext(Dispatchers.IO) { Store.prefs(ctx).getBoolean("tipHomeHidden", false) }
            pausedUntil = withContext(Dispatchers.IO) { Pause.until(ctx) }
            loaded = true
            recomputeNext(0)
            refreshStatus()
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                now = System.currentTimeMillis()
                recomputeNext(0)
            }
        }
    }

    /** Изменение пользователем: обновить и сохранить через паузу (чтобы не писать в базу на каждую букву). */
    fun update(key: Long, change: (Rule) -> Rule) {
        rules = rules.map { if (it.key == key) change(it) else it }
        scheduleSave()
    }

    /** Добавить напоминание (из заготовки); null, если места больше нет. */
    fun add(template: Rule = Templates.blank()): Rule? {
        if (rules.size >= MAX_RULES) return null
        val r = template.copy(id = 0, key = Rule.newKey(), color = rules.size % Accents.size)
        rules = rules + r
        scheduleSave()
        return r
    }

    /** Перетаскивание: карточка fromKey встаёт на место карточки toKey. Порядок сохраняется через паузу. */
    fun move(fromKey: Long, toKey: Long) {
        val from = rules.indexOfFirst { it.key == fromKey }
        val to = rules.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0 || from == to) return
        rules = rules.toMutableList().apply { add(to, removeAt(from)) }
        scheduleSave()
    }

    fun delete(key: Long): Deleted? {
        val i = rules.indexOfFirst { it.key == key }
        if (i < 0) return null
        val d = Deleted(rules[i], i)
        rules = rules.filter { it.key != key }
        persist()
        return d
    }

    /** «Отменить» после удаления: карточка возвращается на своё место. */
    fun restore(d: Deleted) {
        if (rules.size >= MAX_RULES || rules.any { it.key == d.rule.key }) return
        val list = rules.toMutableList()
        list.add(d.index.coerceIn(0, list.size), d.rule)
        rules = list
        persist()
    }

    /**
     * Импорт напоминаний из кода. replace = заменить все свои, иначе добавить к своим (сколько поместится).
     * Возвращает, сколько напоминаний добавлено.
     */
    fun importRules(imported: List<Rule>, replace: Boolean): Int {
        val base = if (replace) emptyList() else rules
        val room = (MAX_RULES - base.size).coerceAtLeast(0)
        // новые карточки: без id и со своими ключами, мелодия — стандартная
        val add = imported.take(room).map { it.copy(id = 0, key = Rule.newKey(), alarmSound = "") }
        rules = base + add
        persist()
        return add.size
    }

    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            persist()
        }
        recomputeNext(300)
    }

    /** Сохранить сразу и дождаться (при уходе с экрана, чтобы правка не потерялась). */
    fun flush() {
        if (!dirty) return
        saveJob?.cancel()
        applySaved(saveBlocking(rules))
    }

    /**
     * Сохранить в фоне. Сохранения идут строго по очереди, и каждое берёт самое свежее состояние списка:
     * старое сохранение не может «вернуть» удалённую карточку или откатить правку.
     */
    fun persist() {
        saveJob?.cancel()
        viewModelScope.launch {
            saveMutex.withLock {
                val snapshot = rules
                val saved = withContext(Dispatchers.IO) { saveBlocking(snapshot) }
                applySaved(saved)
            }
            recomputeNext(0)
            refreshStatus()
        }
    }

    private fun saveBlocking(snapshot: List<Rule>): List<Rule> = synchronized(saveLock) {
        // В базу идут исправленные значения, а в полях остаётся то, что вводит человек (например, пустое название)
        val prepared = snapshot.map { r -> sanitize(if (r.id == 0L) knownIds[r.key]?.let { r.copy(id = it) } ?: r else r) }
        val saved = Store.save(ctx, prepared)
        for (s in saved) knownIds[s.key] = s.id
        // правка расписания не должна «догонять» уже прошедшие времена. Запас в 3 секунды нужен, чтобы
        // уведомление, время которого наступило прямо сейчас, но будильник ещё не сработал, не потерялось.
        Store.setLastFired(ctx, maxOf(Store.getLastFired(ctx), System.currentTimeMillis() - 3000L))
        Scheduler.ensureChannel(ctx)
        Scheduler.scheduleNext(ctx)
        saved
    }

    /** Проставляет id, выданные базой, не трогая то, что человек успел изменить за время сохранения. */
    private fun applySaved(saved: List<Rule>) {
        val ids = saved.associate { it.key to it.id }
        rules = rules.map { r -> ids[r.key]?.let { if (it != r.id) r.copy(id = it) else r } ?: r }
        dirty = saveJob?.isActive == true
    }

    /** Пересчитать «след.» для карточек в фоне; delayMs — пауза, чтобы не считать на каждую букву. */
    private fun recomputeNext(delayMs: Long) {
        nextJob?.cancel()
        val snapshot = rules
        // на паузе «следующее» — первое после неё
        val at = maxOf(System.currentTimeMillis(), pausedUntil)
        nextJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val (byIndex, day) = withContext(Dispatchers.Default) { TimeMath.nextByRule(snapshot, at) to TimeMath.today(snapshot, at) }
            nextByKey = byIndex.entries.associate { (i, e) -> snapshot[i].key to e }
            today = day
        }
    }

    fun refreshStatus() {
        now = System.currentTimeMillis()
        viewModelScope.launch {
            nextAt = withContext(Dispatchers.IO) { Scheduler.nextAt(ctx) }
        }
    }

    /** Повторная загрузка после возврата на экран (например, отложенный будильник мог сработать). */
    fun reload() {
        if (!loaded) return
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { Store.load(ctx) }
            // паузу могли поставить или снять плиткой в шторке или на виджете
            pausedUntil = withContext(Dispatchers.IO) { Pause.until(ctx) }
            if (!dirty && saveJob?.isActive != true) {
                // ключи карточек сохраняем, чтобы раскрытые карточки не схлопнулись
                val keys = rules.filter { it.id > 0 }.associate { it.id to it.key }
                rules = fresh.map { r -> keys[r.id]?.let { r.copy(key = it) } ?: r }
                recomputeNext(0)
            }
            refreshStatus()
        }
    }

    fun loadLog() {
        viewModelScope.launch { log = withContext(Dispatchers.IO) { Store.loadLog(ctx, 100) } }
    }

    fun clearLog() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Store.clearLog(ctx) }
            log = emptyList()
        }
    }

    companion object {
        const val MAX_RULES = 30

        fun sanitize(r: Rule) = r.copy(
            name = r.name.trim().ifEmpty { "Напоминание" },
            interval = r.interval.coerceIn(1, 720),
            lead = r.lead.coerceIn(0, 180),
            repeatEvery = r.repeatEvery.coerceIn(1, 120),
            repeatCount = r.repeatCount.coerceIn(0, 20),
            color = r.color.coerceIn(0, Accents.size - 1),
            snoozeMin = r.snoozeMin.coerceIn(1, 60),
            date = if (Rule.validYmd(r.date)) r.date else 0,
            everyDays = if (r.everyDays in 2..Rule.MAX_EVERY_DAYS) r.everyDays else 0,
        )
    }
}
