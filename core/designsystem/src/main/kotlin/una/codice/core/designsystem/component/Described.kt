package una.codice.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Le pone a un elemento la descripción que leen los lectores de pantalla (TalkBack).
 *
 * El texto llega ya resuelto: el bloque `semantics { }` no es `@Composable`, así que no puede llamar a
 * `stringResource` por dentro. Quien llama lo resuelve antes: `describedAs(stringResource(R.string.x))`.
 */
fun Modifier.describedAs(description: String, mergeDescendants: Boolean = false): Modifier =
    semantics(mergeDescendants = mergeDescendants) { contentDescription = description }
