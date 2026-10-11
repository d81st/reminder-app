package com.h4isenb.reminder

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

/** QR-коды для обмена напоминаниями: нарисовать, найти на картинке, отправить. */
object QrCodes {
    /** Картинка с QR-кодом или null, если текст не помещается в один код. */
    fun bitmap(text: String, size: Int = 900): Bitmap? {
        // крупные наборы рисуем с меньшей коррекцией ошибок, чтобы влезли
        for (level in listOf(ErrorCorrectionLevel.M, ErrorCorrectionLevel.L)) {
            try {
                val hints = mapOf(EncodeHintType.ERROR_CORRECTION to level, EncodeHintType.MARGIN to 2)
                val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
                val px = IntArray(m.width * m.height)
                for (y in 0 until m.height) {
                    val row = y * m.width
                    for (x in 0 until m.width) px[row + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
                }
                return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
            } catch (e: Exception) {
                // не влезло на этом уровне: пробуем следующий
            }
        }
        return null
    }

    /** Ищет QR-код на картинке (например, на снимке экрана из мессенджера) и возвращает его текст. */
    fun decode(c: Context, uri: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val bmp = c.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        bmp?.let(::decode)
    } catch (e: Exception) {
        null
    }

    fun decode(bmp: Bitmap): String? {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        )
        return try {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, px))), hints).text
        } catch (e: Exception) {
            null
        }
    }

    /** Отправить картинку с кодом через любое приложение (Telegram, почта и т. п.). */
    fun shareImage(c: Context, bmp: Bitmap, caption: String) {
        val dir = File(c.cacheDir, "qr").apply { mkdirs() }
        val f = File(dir, "napominalka.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, caption)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(send, "Отправить QR-код").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(c: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        c.startActivity(Intent.createChooser(send, "Отправить напоминания").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
