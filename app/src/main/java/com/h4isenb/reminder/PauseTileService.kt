package com.h4isenb.reminder

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Плитка «Пауза» в шторке: нажатие ставит паузу на час или снимает её. */
class PauseTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        Pause.toggle(this)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val now = System.currentTimeMillis()
        val paused = Pause.active(this, now)
        tile.state = if (paused) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (paused) "На паузе" else "Пауза"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_pause)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (paused) Phrases.pauseUntil(Pause.until(this), now) else "на 1 час"
        }
        tile.updateTile()
    }

    companion object {
        /** Попросить систему перерисовать плитку (например, после паузы из приложения). */
        fun refresh(c: Context) {
            try {
                requestListeningState(c, ComponentName(c, PauseTileService::class.java))
            } catch (ignored: Exception) {
            }
        }
    }
}
