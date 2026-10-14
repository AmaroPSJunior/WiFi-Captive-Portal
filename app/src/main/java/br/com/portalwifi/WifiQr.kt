package br.com.portalwifi

import android.graphics.Bitmap
import android.graphics.Color
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

    fun bitmap(ssid: String, password: String, pixels: Int = 640): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(
            payload(ssid, password), BarcodeFormat.QR_CODE, pixels, pixels,
            mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
        )
        return Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until pixels) for (x in 0 until pixels) {
                setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
    }
}
