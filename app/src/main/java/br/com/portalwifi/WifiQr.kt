package br.com.portalwifi

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/** Standard WIFI QR schema. Only encode credentials confirmed by Android. */
object WifiQr {
    fun payload(ssid: String, password: String): String {
        fun escape(value: String): String = buildString {
            value.forEach { char ->
                if (char == '\\' || char == ';' || char == ':' || char == ',' || char == '"') append('\\')
                append(char)
            }
        }
        return "WIFI:T:WPA;S:${escape(ssid)};P:${escape(password)};;"
    }

    fun urlBitmap(url: String, pixels: Int = 640): Bitmap = qrBitmap(url, pixels, "home")
    fun bitmap(ssid: String, password: String, pixels: Int = 640): Bitmap =
        qrBitmap(payload(ssid, password), pixels, "wifi")
    private fun qrBitmap(value: String, pixels: Int, icon: String): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(
            value, BarcodeFormat.QR_CODE, pixels, pixels,
            mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H, EncodeHintType.CHARACTER_SET to "UTF-8")
        )
        val result = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
        for (y in 0 until pixels) for (x in 0 until pixels) {
            result.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
        val canvas = Canvas(result)
        val center = pixels / 2f
        val size = pixels * 0.145f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(center-size/2, center-size/2, center+size/2, center+size/2), size*.18f, size*.18f, paint)
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size*.085f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        if (icon == "home") {
            val path = Path().apply {
                moveTo(center-size*.28f, center-size*.02f)
                lineTo(center, center-size*.28f)
                lineTo(center+size*.28f, center-size*.02f)
                moveTo(center-size*.21f, center-size*.07f)
                lineTo(center-size*.21f, center+size*.25f)
                lineTo(center+size*.21f, center+size*.25f)
                lineTo(center+size*.21f, center-size*.07f)
            }
            canvas.drawPath(path, paint)
        } else {
            for (scale in listOf(.36f, .24f, .12f)) {
                canvas.drawArc(RectF(center-size*scale,center-size*scale,center+size*scale,center+size*scale), 215f,110f,false,paint)
            }
            paint.style = Paint.Style.FILL
            canvas.drawCircle(center, center+size*.19f, size*.04f, paint)
        }
        return result
    }
}
