package una.codice.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.component.Meter
import una.codice.core.designsystem.motion.AuroraParallaxEffect
import una.codice.core.designsystem.motion.neonBorder
import una.codice.core.designsystem.motion.rememberCountUp
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.designsystem.motion.staggeredEntrance
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.domain.model.UserRole
import una.codice.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.fragment.app.FragmentActivity
import una.codice.settings.AppLanguage
import una.codice.settings.AppSettings
import una.codice.settings.BiometricGate
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import android.widget.Toast
import androidx.compose.runtime.saveable.rememberSaveable

private object NavColors {
    val Canvas = CodiceColors.Canvas
    val Lime = CodiceColors.Lime
    val Purple = CodiceColors.Purple
    val Orange = CodiceColors.Orange
    val Coral = CodiceColors.Coral
    val Glass = CodiceColors.Glass
    val GlassHigh = CodiceColors.GlassHigh
    val Border = CodiceColors.Border
    val TextHigh = CodiceColors.TextHigh
    val TextMid = CodiceColors.TextMid
    val TextLow = CodiceColors.TextLow
}

/** Los intervalos del repaso. Los fija el servidor (review.interval_day), por eso aquí solo se muestran. */
private val REPETITION_DAYS = listOf(1, 3, 7, 14, 30)

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(NavColors.Canvas),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .background(NavColors.Lime, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(text = "C", color = NavColors.Canvas, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Pantalla de Perfil: los datos de la cuenta, un resumen de la actividad del usuario según su rol y, para el
 * estudiante, cómo se programan sus repasos. Solo muestra lo que es real: los ajustes de notificaciones y el
 * recordatorio llegan con las notificaciones push.
 */
@Composable
fun StudentProfilePlaceholder(
    viewModel: ProfileViewModel = hiltViewModel(),
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var mostrarConfirmacion by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    // El fondo aurora se mueve a otra velocidad que las tarjetas mientras el usuario desplaza el Perfil.
    val scroll = rememberScrollState()
    AuroraParallaxEffect(scroll)

    // El ViewModel sobrevive al cambio de pestaña y las cifras viven en el servidor: se piden al abrir el Perfil.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(scroll)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GradientText(text = stringResource(R.string.app_profile), fontSize = 26.sp, fontWeight = FontWeight.Bold)

        ProfileCard(index = 0, glow = true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        // Un anillo de luz con los colores del rol gira alrededor del avatar.
                        .neonBorder(cornerRadius = 38.dp, width = 2.dp)
                        .padding(6.dp)
                        .background(accent.brush(), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.initials,
                        color = NavColors.Canvas,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.userName,
                        color = NavColors.TextHigh,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.email,
                        color = NavColors.TextMid,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .background(accent.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, accent.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(state.roleLabelRes),
                            color = accent.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Mi actividad: cifras reales del servidor según el rol. Una cifra que no se pudo pedir se ve como "–".
        if (state.activity.stats.isNotEmpty()) {
            SectionLabel(stringResource(R.string.app_my_activity))
            BentoStats(state.activity.stats)
        }

        state.activity.coverage?.let { coverage ->
            ProfileCard(index = 4) {
                Meter(
                    progress = coverage,
                    label = stringResource(R.string.app_average_coverage_across_your_groups),
                    color = coverageColor(coverage),
                )
            }
        }

        // Repetición espaciada (solo estudiante). Es informativa: el servidor reprograma cada repaso por su cuenta.
        if (state.role == UserRole.STUDENT) {
            ProfileCard(title = stringResource(R.string.app_spaced_repetition), index = 5) {
                Text(
                    text = stringResource(R.string.app_spaced_repetition_info),
                    color = NavColors.TextMid,
                    fontSize = 13.sp,
                )

                IntervalDays(REPETITION_DAYS)
            }
        }

        LanguageCard(index = 6)
        SecurityCard(index = 7)
        PasswordCard(index = 8)

        ProfileCard(title = stringResource(R.string.app_account), index = 9) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_app_version), color = NavColors.TextHigh, fontSize = 14.sp)
                Text(appVersion(), color = NavColors.TextMid, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NavColors.Border),
            )
            LogoutRow(onClick = { mostrarConfirmacion = true })
        }
    }

    if (mostrarConfirmacion) {
        LogoutConfirmationDialog(
            onConfirm = {
                mostrarConfirmacion = false
                onLogout()
            },
            onDismiss = { mostrarConfirmacion = false },
        )
    }
}

/** La tarjeta de vidrio que usan todas las secciones del Perfil; con [title] lleva su encabezado en mayúsculas. */
@Composable
private fun ProfileCard(
    title: String? = null,
    index: Int = 0,
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Cada tarjeta entra un poco después de la anterior: el Perfil "se despliega" de arriba abajo.
            .staggeredEntrance(index)
            .background(NavColors.Glass.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            // La tarjeta protagonista lleva el borde de luz neón; las demás, un filo discreto.
            .then(
                if (glow) {
                    Modifier.neonBorder(cornerRadius = 20.dp)
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                }
            )
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    color = NavColors.TextLow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = NavColors.TextLow,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** Mismos umbrales que el mapa de cobertura: verde desde 80 %, amarillo desde 50 %, naranja por debajo. */
private fun coverageColor(coverage: Float): Color = when {
    coverage >= 0.8f -> CodiceColors.Lime
    coverage >= 0.5f -> CodiceColors.Yellow
    else -> CodiceColors.Orange
}

/** La versión que figura en el APK instalado; "–" si el sistema no la entrega. */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: UNKNOWN
    }
}

@Composable
private fun LogoutRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = NavColors.Coral)
        Spacer(Modifier.width(10.dp))
        Text(text = stringResource(R.string.app_sign_out), color = NavColors.Coral, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavColors.Glass,
        title = { Text(stringResource(R.string.app_sign_out_2), color = NavColors.TextHigh) },
        text = { Text(stringResource(R.string.app_you_re_about_to_leave_your_account_you), color = NavColors.TextMid) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.app_sign_out), color = NavColors.Orange, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.app_cancel), color = NavColors.TextMid)
            }
        },
    )
}

/** Elegir el idioma de la app. Cambiarlo recrea la pantalla, y todos los textos se vuelven a leer en el idioma nuevo. */
@Composable
private fun LanguageCard(index: Int) {
    val context = LocalContext.current
    val current = remember { AppSettings.language(context) }
    ProfileCard(title = stringResource(R.string.app_language_title), index = index) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { option ->
                LanguageChip(
                    label = stringResource(option.labelRes),
                    selected = option == current,
                    modifier = Modifier.weight(1f),
                ) {
                    if (option != current) {
                        AppSettings.setLanguage(context, option)
                        context.findActivity()?.recreate()
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(accent.brush())
                } else {
                    Modifier
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                },
            )
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) NavColors.Canvas else NavColors.TextHigh,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** El interruptor del bloqueo con huella. Activarlo pide verificarse una vez, para no dejar a nadie fuera de su app. */
@Composable
private fun SecurityCard(index: Int) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val available = remember { BiometricGate.isAvailable(context) }
    var enabled by remember { mutableStateOf(AppSettings.biometricLock(context)) }

    ProfileCard(title = stringResource(R.string.app_security_title), index = index) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_biometric_lock_title),
                    color = NavColors.TextHigh,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(if (available) R.string.app_biometric_lock_hint else R.string.app_biometric_unavailable),
                    color = NavColors.TextLow,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                // Si el teléfono ya no tiene con qué verificar, solo se deja apagarlo.
                enabled = available || enabled,
                onCheckedChange = { wantOn ->
                    if (!wantOn) {
                        AppSettings.setBiometricLock(context, false)
                        enabled = false
                    } else {
                        (context.findActivity() as? FragmentActivity)?.let { activity ->
                            BiometricGate.authenticate(activity, onSuccess = {
                                AppSettings.setBiometricLock(context, true)
                                enabled = true
                            })
                        }
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NavColors.Canvas,
                    checkedTrackColor = accent.primary,
                ),
            )
        }
    }
}

/** "Cambiar contraseña": abre el diálogo con la actual, la nueva y su confirmación. Está en el Perfil de estudiantes y de profesores. */
@Composable
private fun PasswordCard(index: Int) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }

    ProfileCard(index = index) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_change_password_title),
                    color = NavColors.TextHigh,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = stringResource(R.string.app_change_password_hint), color = NavColors.TextLow, fontSize = 12.sp)
            }
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = NavColors.TextMid)
        }
    }

    if (open) {
        ChangePasswordDialog(
            onDismiss = { open = false },
            onChanged = {
                open = false
                Toast.makeText(context, R.string.app_password_changed, Toast.LENGTH_LONG).show()
            },
        )
    }
}

/** La Activity detrás del contexto de Compose (puede venir envuelto en varios ContextWrapper). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
