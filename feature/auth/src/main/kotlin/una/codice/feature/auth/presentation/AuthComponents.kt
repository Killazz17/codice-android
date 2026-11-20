package una.codice.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import una.codice.core.designsystem.motion.neonBorder
import una.codice.core.designsystem.theme.CodiceAccents
import una.codice.core.designsystem.theme.LocalAccent
import una.codice.core.domain.model.UserRole
import una.codice.feature.auth.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Piezas visuales que comparten [AuthScreen] (login) y `RegisterScreen`: el
 * logo, el campo de texto con su etiqueta y su error en coral, y el selector de rol.
 *
 * ### Por qué se sacaron de `AuthScreen.kt`
 *
 * Antes de separar login y registro en dos pantallas, estas funciones eran
 * `private fun` dentro de `AuthScreen.kt` porque solo esa pantalla existía.
 * Al nacer `RegisterScreen` con el mismo lenguaje visual (misma tarjeta de
 * vidrio, mismo campo con error, mismo botón de Google), duplicar ese código
 * hubiera significado mantener dos copias sincronizadas a mano. Quedan sin
 * `private` (visibilidad de módulo) porque ambas pantallas viven en el mismo
 * paquete de `:feature:auth` — no hace falta exponerlas fuera del módulo.
 */

@Composable
fun LogoBadge() {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(20.dp)
    // El logo toma el color de la zona (ámbar en el acceso), con un resplandor del mismo color y el borde neón girando.
    Box(
        modifier = Modifier
            .size(64.dp)
            .shadow(18.dp, shape, ambientColor = accent.primary, spotColor = accent.primary)
            .clip(shape)
            .background(accent.brush())
            .neonBorder(cornerRadius = 20.dp, width = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "C", color = AuthColors.Canvas, fontSize = 32.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * Un campo de texto del formulario: etiqueta SIEMPRE visible encima, el campo, y debajo el error en coral (nunca en
 * un `Toast` genérico, como pide el criterio de aceptación del issue #6) o, si no hay error, una ayuda opcional.
 *
 * La etiqueta va fuera del campo a propósito: un `placeholder` desaparece en cuanto se escribe, y la persona pierde
 * qué se le estaba pidiendo. El `placeholder` queda solo para un ejemplo de cómo se ve el dato.
 */
@Composable
fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    placeholder: String = "",
    helperText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    Column {
        Text(
            text = label,
            color = AuthColors.TextMid,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            // El lector de pantalla anuncia la etiqueta al entrar al campo.
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder, color = AuthColors.TextLow) }
            } else {
                null
            },
            singleLine = true,
            isError = errorMessage != null,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AuthColors.TextHigh,
                unfocusedTextColor = AuthColors.TextHigh,
                focusedContainerColor = AuthColors.Canvas.copy(alpha = 0.55f),
                unfocusedContainerColor = AuthColors.Canvas.copy(alpha = 0.55f),
                focusedBorderColor = accent.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.07f),
                errorBorderColor = AuthColors.Coral,
                cursorColor = accent.primary,
            ),
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = AuthColors.Coral,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        } else if (helperText != null) {
            Text(
                text = helperText,
                color = AuthColors.TextLow,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

/**
 * Las dos tarjetas de rol: lima para Estudiante, violeta para Profesor, cada una con su descripción de una línea.
 * Elegir un rol es "una decisión explícita, no un dato derivado", así que no hay ningún valor seleccionado por
 * defecto; por eso la elegida tiene que verse sin dudas: relleno fuerte, borde grueso del color del rol y una marca.
 *
 * Vive solo en la pantalla de Registro, ARRIBA del formulario: el rol únicamente importa al crear la cuenta, y al
 * final del formulario casi nadie llegaba a verlo.
 */
@Composable
fun RoleSelector(selectedRole: UserRole?, onRoleSelected: (UserRole) -> Unit) {
    // IntrinsicSize.Max: las dos tarjetas miden lo mismo aunque una descripción ocupe dos renglones.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Max).selectableGroup()) {
        RoleCard(
            label = stringResource(R.string.auth_student),
            description = stringResource(R.string.auth_i_photograph_my_notes),
            accent = CodiceAccents.Student.primary,
            isSelected = selectedRole == UserRole.STUDENT,
            modifier = Modifier.weight(1f),
            onClick = { onRoleSelected(UserRole.STUDENT) },
        )
        RoleCard(
            label = stringResource(R.string.auth_teacher),
            description = stringResource(R.string.auth_i_create_and_manage_courses),
            accent = CodiceAccents.Teacher.primary,
            isSelected = selectedRole == UserRole.TEACHER,
            modifier = Modifier.weight(1f),
            onClick = { onRoleSelected(UserRole.TEACHER) },
        )
    }
}

@Composable
private fun RoleCard(
    label: String,
    description: String,
    accent: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    // remember + MutableInteractionSource, indication = null: evita el ripple gris por defecto de Material.
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(if (isSelected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent else Color.White.copy(alpha = 0.14f),
                shape = shape,
            )
            // `selectable` con rol de botón de opción: TalkBack anuncia "seleccionado" o "no seleccionado".
            .selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(modifier = Modifier.size(8.dp).background(accent, CircleShape))
            Text(
                text = label,
                color = if (isSelected) AuthColors.TextHigh else AuthColors.TextMid,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            color = if (isSelected) AuthColors.TextMid else AuthColors.TextLow,
            fontSize = 12.sp,
        )
    }
}
