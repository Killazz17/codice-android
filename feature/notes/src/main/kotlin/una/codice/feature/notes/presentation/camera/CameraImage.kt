package una.codice.feature.notes.presentation.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

/**
 * El lado largo mínimo que conserva una foto al reducirla: de sobra para leer letra manuscrita y, a diferencia de los
 * 12 a 50 megapíxeles de un celular reciente, deja el JPEG bien por debajo del máximo de 10 MB que acepta el backend.
 */
internal const val UPLOAD_MIN_LONG_EDGE_PX = 2048

internal const val UPLOAD_JPEG_QUALITY = 85

/**
 * El `inSampleSize` de [BitmapFactory] (siempre potencia de 2) para reducir una foto de [width] x [height] lo más posible
 * sin que su lado largo baje de [minLongEdge]. Una foto que ya es chica se queda como está (1).
 */
internal fun sampleSizeFor(width: Int, height: Int, minLongEdge: Int = UPLOAD_MIN_LONG_EDGE_PX): Int {
    val longEdge = maxOf(width, height)
    var sampleSize = 1
    while (longEdge / (sampleSize * 2) >= minLongEdge) sampleSize *= 2
    return sampleSize
}

/**
 * La foto que tomó CameraX lista para subir: un JPEG ya girado, porque al volver a comprimir se pierde la orientación
 * EXIF y la IA vería el apunte de lado, y reducido si la cámara entregó una foto enorme. No cierra la imagen: eso le
 * toca a quien la recibió.
 */
internal fun ImageProxy.toUploadJpeg(): ByteArray {
    val buffer = planes[0].buffer
    val jpeg = ByteArray(buffer.remaining()).also { buffer.get(it) }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
    val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options) ?: return jpeg

    val rotation = imageInfo.rotationDegrees
    val upright = if (rotation == 0) {
        decoded
    } else {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { decoded.recycle() }
    }

    return ByteArrayOutputStream().use { output ->
        upright.compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, output)
        upright.recycle()
        output.toByteArray()
    }
}
