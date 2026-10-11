package com.h4isenb.reminder

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Состояние главного экрана: список напоминаний с отложенным сохранением, журнал и строка «Следующее». */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    var rules by mutableStateOf(Store.load(app))
        private set
    var log by mutableStateOf<List<Store.LogItem>>(emptyList())
        private set
    var nextAt by mutableStateOf<Long?>(null)
        private set
    /** Текущее время для подписей; обновляется раз в 30 секунд. */
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set

    private var dirty = false
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                now = System.currentTimeMillis()
            }
        }
        refreshStatus()
    }

    /** Изменение пользователем: обновить и сохранить через паузу (чтобы не писать в базу на каждую букву). */
    fun update(key: Long, change: (Rule) -> Rule) {
        rules = rules.map { if (it.key == key) change(it) else it }
        scheduleSave()
    }

    fun add(): Rule? {
        if (rules.size >= MAX_RULES) return null
        val r = Rule("Новое напоминание", 9 * 60, 17 * 60, 60, false, "Текст напоминания", color = rules.size % Accents.size)
        rules = rules + r
        scheduleSave()
        return r
    }

    fun delete(key: Long) {
        rules = rules.filter { it.key != key }
        persist()
    }

    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            persist()
        }
    }

    fun flush() {
        if (dirty) persist()
    }

    fun persist() {
        saveJob?.cancel()
        // В базу идут исправленные значения, а в полях остаётся то, что вводит человек (например, пустое название)
        val saved = Store.save(ctx, rules.map(::sanitize))
        val ids = saved.associate { it.key to it.id }
        rules = rules.map { r -> ids[r.key]?.let { r.copy(id = it) } ?: r }
        // правка расписания не должна «догонять» уже прошедшие времена. Запас в 3 секунды нужен, чтобы
        // уведомление, время которого наступило прямо сейчас, но будильник ещё не сработал, не потерялось.
        Store.setLastFired(ctx, maxOf(Store.getLastFired(ctx), System.currentTimeMillis() - 3000L))
        Scheduler.ensureChannel(ctx)
        Scheduler.scheduleNext(ctx)
        dirty = false
        refreshStatus()
    }

    fun refreshStatus() {
        now = System.currentTimeMillis()
        nextAt = Scheduler.nextAt(ctx)
    }

    /** Повторная загрузка после возврата на экран (например, отложенный будильник мог сработать). */
    fun reload() {
        if (!dirty) {
            // ключи карточек сохраняем, чтобы раскрытые карточки не схлопнулись
            val keys = rules.filter { it.id > 0 }.associate { it.id to it.key }
            rules = Store.load(ctx).map { r -> keys[r.id]?.let { r.copy(key = it) } ?: r }
        }
        refreshStatus()
    }

    fun loadLog() {
        log = Store.loadLog(ctx, 100)
    }

    fun clearLog() {
        Store.clearLog(ctx)
        loadLog()
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
        )
    }
}
