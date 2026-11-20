package una.codice.feature.notes.presentation.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.feature.notes.R
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource

/** Tope de páginas por apunte: el backend acepta unas 10 páginas de cuaderno transcritas por apunte. */
internal const val MAX_PAGES = 10

/**
 * La cámara de pantalla completa para fotografiar un apunte, de una o varias páginas (issue #42).
 *
 * Cada toque del obturador agrega una página y la cámara sigue abierta para la siguiente; "Listo" entrega todas, en
 * orden, como JPEG listos para subir. La IA corre en el servidor: aquí solo se toman las fotos.
 *
 * @param onPagesCaptured Las páginas tomadas, al tocar "Listo".
 * @param onPermissionDenied El estudiante no dio permiso de cámara.
 * @param onClose Salir sin entregar nada; las páginas tomadas se descartan.
 */
@Composable
fun CameraCaptureOverlay(
    onPagesCaptured: (List<ByteArray>) -> Unit,
    onPermissionDenied: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    // Convertir la foto en JPEG es trabajo pesado: corre en este hilo y no en el principal.
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    val pages = remember { mutableStateListOf<ByteArray>() }
    var isCapturing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Los listeners de la cámara no son @Composable: los mensajes de error se resuelven aquí, una vez.
    val cameraOpenError = stringResource(R.string.notes_couldn_t_open_the_camera_close_other_a)
    val photoProcessError = stringResource(R.string.notes_couldn_t_process_the_photo_try_again)
    val photoTakeError = stringResource(R.string.notes_couldn_t_take_the_photo_try_again)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (!granted) onPermissionDenied()
    }

    DisposableEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { cameraExecutor.shutdown() }
    }

    // La cámara se abre cuando hay permiso y se suelta al salir de la pantalla: sin esto seguiría ocupada hasta que
    // la actividad se detenga, aunque el estudiante ya haya cerrado esta pantalla.
    DisposableEffect(hasCameraPermission) {
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false
        if (hasCameraPermission) {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                if (disposed) return@addListener
                cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                try {
                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                } catch (error: Exception) {
                    errorMessage = cameraOpenError
                }
            }, mainExecutor)
        }
        onDispose {
            disposed = true
            cameraProvider?.unbindAll()
        }
    }

    fun takePage() {
        if (isCapturing || pages.size >= MAX_PAGES) return
        isCapturing = true
        errorMessage = null
        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val jpeg = try {
                        image.toUploadJpeg()
                    } catch (error: Exception) {
                        null
                    } finally {
                        image.close()
                    }
                    mainExecutor.execute {
                        isCapturing = false
                        if (jpeg != null) pages.add(jpeg) else errorMessage = photoProcessError
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    mainExecutor.execute {
                        isCapturing = false
                        errorMessage = photoTakeError
                    }
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.notes_close_camera), tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(stringResource(R.string.notes_scan_your_note), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(stringResource(pagesLabelRes(pages.size), pages.size), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            errorMessage?.let { Text(it, color = CodiceColors.Orange, fontSize = 13.sp) }
            if (pages.size >= MAX_PAGES) {
                Text(stringResource(R.string.notes_you_ve_reached_the_maximum_of_pages, MAX_PAGES), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (pages.isNotEmpty()) {
                        TextButton(onClick = { pages.removeAt(pages.lastIndex) }) {
                            Text(stringResource(R.string.notes_remove_last), color = Color.White)
                        }
                    }
                }

                Shutter(enabled = hasCameraPermission && !isCapturing && pages.size < MAX_PAGES, isBusy = isCapturing, onClick = { takePage() })

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (pages.isNotEmpty()) {
                        Button(
                            onClick = { onPagesCaptured(pages.toList()) },
                            enabled = !isCapturing,
                            colors = ButtonDefaults.buttonColors(containerColor = CodiceColors.Lime),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.notes_done, pages.size), color = CodiceColors.Canvas, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** El obturador: un círculo grande con aro blanco que se vuelve un indicador mientras se procesa la foto. */
@Composable
private fun Shutter(enabled: Boolean, isBusy: Boolean, onClick: () -> Unit) {
    val takePhotoLabel = stringResource(R.string.notes_take_photo)
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(if (enabled) CodiceColors.Lime else CodiceColors.Lime.copy(alpha = 0.35f), CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = takePhotoLabel
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
        } else {
            Box(modifier = Modifier.size(60.dp).background(Color.White, CircleShape))
        }
    }
}

/** El recurso del contador de páginas. Devuelve el id (no el texto) para poder probarlo sin Android; el texto lo arma la pantalla. */
@StringRes
internal fun pagesLabelRes(count: Int): Int = when (count) {
    0 -> R.string.notes_place_the_note_in_view_and_take_the_ph
    1 -> R.string.notes_1_page_taken
    else -> R.string.notes_pages_taken
}
