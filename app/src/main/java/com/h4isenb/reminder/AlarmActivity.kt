package com.h4isenb.reminder

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Экран звонящего будильника: поверх блокировки, большие кнопки «Отложить» и «Выключить». */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val r by AlarmService.ringing.collectAsState()
            val current = r
            // будильник выключен (кнопкой в уведомлении, по таймауту или отсюда): экран больше не нужен
            LaunchedEffect(current == null) {
                if (current == null) finish()
            }
            if (current != null) {
                ReminderTheme(dark = true) {
                    AlarmScreen(
                        current,
                        onSnooze = { AlarmService.snooze(this); finish() },
                        onDismiss = { AlarmService.dismiss(this); finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmScreen(r: AlarmService.Ringing, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    // пример для выключения: новый для каждого звонка
    val a = rememberSaveable(r.startedAt) { Random.nextInt(12, 60) }
    val b = rememberSaveable(r.startedAt) { Random.nextInt(12, 60) }
    var answer by rememberSaveable(r.startedAt) { mutableStateOf("") }
    val needMath = r.challenge == Rule.CHALLENGE_MATH
    val solved = !needMath || answer.trim() == (a + b).toString()

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1E1B4B), IndigoDark, Color(0xFF0F1220))))
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                SimpleDateFormat("HH:mm", Locale("ru")).format(Date(now)),
                color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Light,
            )
            Text(
                SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date(now)),
                color = Color(0xCCFFFFFF), fontSize = 18.sp,
            )
            Spacer(Modifier.height(32.dp))
            Text(r.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (r.text.isNotBlank() && r.text != r.title) {
                Spacer(Modifier.height(8.dp))
                Text(r.text, color = Color(0xDDFFFFFF), fontSize = 17.sp, textAlign = TextAlign.Center)
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (needMath) {
                Text("Чтобы выключить, решите пример", color = Color(0xCCFFFFFF), fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text("$a + $b = ?", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { v -> answer = v.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("Ответ") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
            Button(
                onClick = onDismiss,
                enabled = solved,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = IndigoDark),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("Выключить", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSnooze,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Отложить на ${r.snoozeMin} мин", fontSize = 17.sp) }
        }
    }
}
