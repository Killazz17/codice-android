package una.codice.feature.enrollment.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import una.codice.core.domain.model.Enrollment
import una.codice.core.domain.model.EnrollmentStatus
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.feature.enrollment.R
import androidx.compose.ui.res.stringResource
import una.codice.core.designsystem.theme.LocalAccent
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.sp

/**
 * Tarjeta que muestra la información de un curso al que el alumno está matriculado
 * o ha solicitado unirse.
 */
@Composable
fun EnrollmentItem(
    enrollment: Enrollment,
    onClick: () -> Unit,
    onLeave: (() -> Unit)? = null,
) {
    val statusColor = when (enrollment.status) {
        EnrollmentStatus.ACTIVE -> CodiceColors.Lime
        EnrollmentStatus.PENDING -> CodiceColors.Yellow
        EnrollmentStatus.REJECTED -> CodiceColors.Orange
    }

    val statusText = when (enrollment.status) {
        EnrollmentStatus.ACTIVE -> stringResource(R.string.enr_active)
        EnrollmentStatus.PENDING -> stringResource(R.string.enr_pending)
        EnrollmentStatus.REJECTED -> stringResource(R.string.enr_rejected)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CodiceColors.Glass)
            .clickable(enabled = enrollment.status == EnrollmentStatus.ACTIVE) { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Punto de estado
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(10.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = enrollment.group.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        color = CodiceColors.TextHigh,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (enrollment.status == EnrollmentStatus.PENDING) {
                        StatusPill(text = statusText, color = CodiceColors.Amber)
                    } else if (enrollment.status == EnrollmentStatus.REJECTED) {
                        StatusPill(text = statusText, color = CodiceColors.Orange)
                    } else if (enrollment.status == EnrollmentStatus.ACTIVE) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = CodiceColors.TextLow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.enr_group, enrollment.group.courseCode.ifBlank { enrollment.group.courseId }, enrollment.group.groupNumber),
                    style = MaterialTheme.typography.bodySmall,
                    color = CodiceColors.TextMid
                )
                // Solo un curso activo se puede abrir: la tarjeta lo dice, para que nadie tenga que adivinar que se toca.
                if (enrollment.status == EnrollmentStatus.ACTIVE) {
                    Text(
                        text = stringResource(R.string.enr_view_map),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalAccent.current.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                // Salir del curso (o cancelar la solicitud): un botón con texto, no un ícono suelto. La pantalla pide confirmar.
                if (onLeave != null && (enrollment.status == EnrollmentStatus.ACTIVE || enrollment.status == EnrollmentStatus.PENDING)) {
                    TextButton(
                        onClick = onLeave,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (enrollment.status == EnrollmentStatus.PENDING) R.string.enr_cancel_request else R.string.enr_leave_course
                            ),
                            color = CodiceColors.Coral,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pequeña etiqueta (pill) para mostrar el estado de la matrícula.
 */
@Composable
fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
