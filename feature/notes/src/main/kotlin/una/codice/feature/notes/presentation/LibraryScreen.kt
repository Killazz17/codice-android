package una.codice.feature.notes.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar
import una.codice.core.designsystem.component.CourseSelector
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.component.glassPanel
import una.codice.core.designsystem.component.CtaButton
import una.codice.core.designsystem.motion.SkeletonCard
import una.codice.core.designsystem.motion.staggeredEntrance
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.domain.model.Note
import una.codice.core.domain.model.SyncStatus
import una.codice.core.domain.usecase.note.NoteFilter
import una.codice.feature.notes.R
import androidx.compose.ui.res.stringResource
import una.codice.core.designsystem.component.describedAs
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import una.codice.core.designsystem.theme.LocalAccent
import androidx.compose.material.icons.filled.Add

/**
 * Punto de entrada de la pantalla de Biblioteca: conecta el [LibraryViewModel]
 * real (inyectado por Hilt) con el composable sin estado [LibraryScreen].
 * Mismo criterio que `CoverageRoute`/`CoverageScreen` en `:feature:coverage`.
 */
@Composable
fun LibraryRoute(
    onNavigateToEnrollment: () -> Unit,
    onNavigateToCapture: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // El ViewModel sobrevive al cambio de pestaña y los apuntes viven en el servidor: sin esto, un apunte creado desde
    // otro dispositivo (o tras abrir la app con la lista vacía) no aparecía hasta cerrar la app.
    LaunchedEffect(Unit) { viewModel.onRetry() }

    LibraryScreen(
        state = state,
        onNavigateToEnrollment = onNavigateToEnrollment,
        onNavigateToCapture = onNavigateToCapture,
        onQueryChange = viewModel::onQueryChange,
        onModuleFilter = viewModel::onModuleFilter,
        onSyncFilter = viewModel::onSyncFilter,
        onToggleSinClasificar = viewModel::onToggleSinClasificar,
        onClearFilters = viewModel::onClearFilters,
        onCourseSelected = viewModel::onCourseSelected,
        onRetry = viewModel::onRetry,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        modifier = modifier,
    )
}

/**
 * La pantalla de Biblioteca: el buscador y los chips de filtro (issue #17)
 * sobre la tarjeta de apunte y la lista (issue #14), agrupadas por fecha. Sin
 * estado propio — todo sale de [state], igual que `CoverageScreen`.
 *
 * El buscador/chips se dibujan siempre, sin importar [LibraryUiState.notes] —
 * es la separación que `LibraryUiState` dejó lista a propósito (ver su KDoc).
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onNavigateToEnrollment: () -> Unit,
    onNavigateToCapture: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onModuleFilter: (String?) -> Unit = {},
    onSyncFilter: (SyncStatus?) -> Unit = {},
    onToggleSinClasificar: (Boolean) -> Unit = {},
    onClearFilters: () -> Unit = {},
    onCourseSelected: (String?) -> Unit = {},
    onRetry: () -> Unit = {},
    onDeleteRequested: (Note) -> Unit = {},
    onDeleteConfirmed: () -> Unit = {},
    onDeleteDismissed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                GradientText(text = stringResource(R.string.notes_notes_library), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.notes_search_over_the_transcribed_text),
                    color = CodiceColors.TextMid,
                    fontSize = 14.sp,
                )
            }

            // Agregar un apunte también se puede desde aquí, donde se miran los apuntes: abre el mismo diálogo que Inicio.
            // Unirse a un curso vive en la pestaña Cursos, no aquí.
            AddNoteButton(onClick = { CaptureRequest.ask(); onNavigateToCapture() })
        }

        CourseSelector(
            options = state.courseOptions,
            selectedId = state.selectedGroupId,
            onSelect = onCourseSelected,
            allLabel = stringResource(R.string.notes_all_courses),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 26.dp),
        )
        Spacer(Modifier.height(12.dp))
        FilterChipsRow(
            filter = state.filter,
            moduleOptions = state.moduleOptions,
            onModuleFilter = onModuleFilter,
            onSyncFilter = onSyncFilter,
            onToggleSinClasificar = onToggleSinClasificar,
            onClearFilters = onClearFilters,
        )
        ResultCountText(state.notes)
        Spacer(Modifier.height(8.dp))

        when (val notes = state.notes) {
            is NotesStatus.Loading -> LoadingContent()
            is NotesStatus.EmptySinApuntes -> EmptySinApuntesContent(onNavigateToEnrollment)
            is NotesStatus.EmptySinResultados -> EmptySinResultadosContent()
            is NotesStatus.Error -> ErrorContent(notes.message, onRetry)
            is NotesStatus.Content -> NoteList(notes.notes, onDeleteRequested)
        }
    }

    DeleteNoteDialog(state = state.delete, onConfirm = onDeleteConfirmed, onDismiss = onDeleteDismissed)
}

/**
 * Confirma el borrado de un apunte.
 *
 * Es un paso obligatorio y no un "deshacer" posterior porque el borrado no se puede revertir: en cuanto el apunte
 * deja de existir, el servidor recalcula la cobertura del subtema que cubría. Por eso el cuerpo del diálogo muestra
 * también las primeras líneas del apunte — en una lista de tarjetas parecidas, el título del subtema no basta para
 * saber cuál se está por borrar.
 */
@Composable
private fun DeleteNoteDialog(state: DeleteState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val note = state.pending ?: return

    AlertDialog(
        // Mientras la llamada está en vuelo el diálogo no se cierra: el resultado todavía puede ser un error, y este
        // es el único lugar donde se muestra.
        onDismissRequest = { if (!state.deleting) onDismiss() },
        containerColor = CodiceColors.Glass,
        title = { Text(stringResource(R.string.notes_delete_confirm_title), color = CodiceColors.TextHigh) },
        text = {
            Column {
                Text(
                    text = note.confirmedText.ifBlank { stringResource(R.string.notes_unclassified) },
                    color = CodiceColors.TextHigh,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.notes_delete_confirm_body),
                    color = CodiceColors.TextMid,
                    fontSize = 13.sp,
                )
                state.error?.let { mensaje ->
                    Spacer(Modifier.height(10.dp))
                    Text(text = mensaje, color = CodiceColors.Coral, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.deleting) {
                Text(
                    text = stringResource(if (state.deleting) R.string.notes_deleting else R.string.notes_delete),
                    color = if (state.deleting) CodiceColors.TextLow else CodiceColors.Coral,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.deleting) {
                Text(stringResource(R.string.notes_cancel), color = CodiceColors.TextLow)
            }
        },
    )
}

@Composable
private fun LoadingContent() {
    // Esqueletos con brillo en lugar de una rueda: muestran la forma de lo que va a llegar.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 4.dp)
            .describedAs(stringResource(R.string.notes_loading_notes), mergeDescendants = true),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) { SkeletonCard() }
    }
}

/**
 * El estudiante no ha fotografiado ningún apunte todavía. Incluye la tarjeta
 * de "Unirme a un curso" — su nuevo hogar, trasladada desde el placeholder
 * que ocupaba esta ruta antes de que existiera esta pantalla real: ambas
 * cosas describen al mismo estudiante que recién empieza.
 */
@Composable
private fun EmptySinApuntesContent(onNavigateToEnrollment: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.notes_you_haven_t_photographed_any_note_yet),
                color = CodiceColors.TextHigh,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.notes_use_the_capture_tab_to_start_your_libr),
                color = CodiceColors.TextMid,
                fontSize = 14.sp,
            )
        }

        JoinCourseCard(onNavigateToEnrollment)
    }
}

/** Hay apuntes, pero ninguno coincide con el filtro activo — mensaje distinto de [EmptySinApuntesContent]. */
@Composable
private fun EmptySinResultadosContent() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.notes_no_notes_match_your_search),
                color = CodiceColors.TextHigh,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.notes_try_clearing_the_filters_or_searching_),
                color = CodiceColors.TextMid,
                fontSize = 14.sp,
            )
        }
    }
}

/** La lista no se pudo cargar: no es lo mismo que no tener apuntes, así que lo dice y deja reintentar. */
@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.notes_couldn_t_load_your_notes),
                color = CodiceColors.TextHigh,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = CodiceColors.TextMid,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(20.dp))
            CtaButton(text = stringResource(R.string.notes_retry), onClick = onRetry)
        }
    }
}

/**
 * "Agregar apunte" en la Biblioteca: lleva a Inicio y abre solo el diálogo de captura (cámara, galería o archivo).
 * Antes aquí había un botón "Unirme", que no tenía nada que ver con los apuntes y confundía.
 */
@Composable
private fun AddNoteButton(onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(accent.primary.copy(alpha = 0.14f))
            .border(1.dp, accent.brush(), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = accent.primary, modifier = Modifier.size(18.dp))
        Text(text = stringResource(R.string.notes_add_note), color = accent.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * La tarjeta de invitación a matricularse — trasladada tal cual desde el
 * placeholder anterior de esta ruta (`StudentNotesPlaceholder`, ya borrado).
 */
@Composable
private fun JoinCourseCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodiceColors.Glass, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(CodiceColors.Lime.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Filled.Groups, contentDescription = null, tint = CodiceColors.Lime)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.notes_don_t_have_a_course_yet), color = CodiceColors.TextHigh, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text = stringResource(R.string.notes_join_with_the_code_your_teacher_gave_y), color = CodiceColors.TextMid, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CodiceColors.Lime),
        ) {
            Text(text = stringResource(R.string.notes_join), color = CodiceColors.Canvas, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * El campo de búsqueda del issue #17: lupa a la izquierda, texto de ayuda,
 * botón de limpiar cuando hay texto, y `ImeAction.Search` que cierra el
 * teclado al buscar (criterio de aceptación: no hace falta tocar nada más).
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(text = stringResource(R.string.notes_search_my_notes), color = CodiceColors.TextLow) },
        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = CodiceColors.TextLow) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Filled.Clear, contentDescription = stringResource(R.string.notes_clear_search), tint = CodiceColors.TextLow)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CodiceColors.Glass,
            unfocusedContainerColor = CodiceColors.Glass,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = CodiceColors.TextHigh,
            unfocusedTextColor = CodiceColors.TextHigh,
            cursorColor = CodiceColors.Lime,
        ),
    )
}

/**
 * La fila de chips del issue #17: "Todos" siempre primero, un chip por
 * módulo (issue: "usando el color del curso" — se usa Lime fijo, ver el KDoc
 * de [ModuleChip] sobre por qué no hay color por curso todavía), y los chips
 * de estado de sincronización + "Sin clasificar". `LazyRow` para que se
 * desplacen horizontalmente sin cortar el último (criterio de aceptación).
 *
 * El chip activo se distingue con una marca de verificación además del
 * color de fondo (mismo criterio de accesibilidad que [SyncStatusPill]).
 */
@Composable
private fun FilterChipsRow(
    filter: NoteFilter,
    moduleOptions: List<ModuleOption>,
    onModuleFilter: (String?) -> Unit,
    onSyncFilter: (SyncStatus?) -> Unit,
    onToggleSinClasificar: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
) {
    val ningunFiltroActivo = filter.moduleId == null && filter.syncStatus == null && !filter.soloSinClasificar

    LazyRow(
        contentPadding = PaddingValues(horizontal = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            LibraryChip(label = stringResource(R.string.notes_all), selected = ningunFiltroActivo, onClick = onClearFilters)
        }
        items(moduleOptions, key = { it.moduleId }) { modulo ->
            val seleccionado = filter.moduleId == modulo.moduleId
            LibraryChip(
                label = modulo.label,
                selected = seleccionado,
                onClick = { onModuleFilter(if (seleccionado) null else modulo.moduleId) },
            )
        }
        item {
            val seleccionado = filter.syncStatus == SyncStatus.SYNCED
            LibraryChip(
                label = stringResource(R.string.notes_synced),
                selected = seleccionado,
                onClick = { onSyncFilter(if (seleccionado) null else SyncStatus.SYNCED) },
            )
        }
        item {
            val seleccionado = filter.syncStatus == SyncStatus.QUEUED
            LibraryChip(
                label = stringResource(R.string.notes_queued),
                selected = seleccionado,
                onClick = { onSyncFilter(if (seleccionado) null else SyncStatus.QUEUED) },
            )
        }
        item {
            LibraryChip(
                label = stringResource(R.string.notes_unclassified),
                selected = filter.soloSinClasificar,
                onClick = { onToggleSinClasificar(!filter.soloSinClasificar) },
            )
        }
    }
}

@Composable
private fun LibraryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) },
        leadingIcon = if (selected) {
            { Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CodiceColors.Glass,
            labelColor = CodiceColors.TextMid,
            iconColor = CodiceColors.Canvas,
            selectedContainerColor = CodiceColors.Lime,
            selectedLabelColor = CodiceColors.Canvas,
        ),
        border = null,
    )
}

/** "7 apuntes" debajo de los chips — criterio de aceptación del issue #17. Oculto mientras carga o cuando la Biblioteca está vacía de verdad (no hay nada que contar todavía). */
@Composable
private fun ResultCountText(notes: NotesStatus) {
    val texto = when (notes) {
        is NotesStatus.Content -> stringResource(if (notes.notes.size == 1) R.string.notes_count_one else R.string.notes_count_other, notes.notes.size)
        is NotesStatus.EmptySinResultados -> stringResource(R.string.notes_count_other, 0)
        is NotesStatus.Loading, is NotesStatus.EmptySinApuntes, is NotesStatus.Error -> null
    }
    if (texto != null) {
        Text(
            text = texto,
            color = CodiceColors.TextLow,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 8.dp),
        )
    }
}

/**
 * La lista del issue #14: un `LazyColumn` con encabezados fijos por sección
 * de fecha (Hoy / Esta semana / Anteriores), calculados desde
 * [Note.createdAt] — es formato de presentación, no una regla de negocio,
 * así que vive aquí y no en `:core:domain` (mismo nivel que `asPercentText()`
 * en `CoverageScreen.kt`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteList(notes: List<Note>, onDelete: (Note) -> Unit) {
    val secciones = agruparPorFecha(notes)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        secciones.forEach { (titulo, apuntes) ->
            stickyHeader {
                // Una etiqueta flotante, no una barra de lado a lado: así el encabezado fijo no tapa el fondo aurora con
                // un bloque oscuro, pero sigue legible cuando las tarjetas pasan por debajo.
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = titulo,
                        color = CodiceColors.TextMid,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(CodiceColors.Canvas.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            itemsIndexed(apuntes, key = { _, note -> note.id }) { index, note ->
                NoteCard(note, onDelete, Modifier.staggeredEntrance(index))
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * La tarjeta de apunte del issue #14: código/título del subtema (o "Sin
 * clasificar" en ámbar si no tiene), etiqueta de módulo, dos líneas de vista
 * previa del texto, y un pie con el número de páginas, la fecha relativa y
 * el estado de sincronización.
 *
 * Sin miniatura de página: `Note` no tiene ninguna imagen asociada todavía
 * (depende de la feature de Cámara, que no existe) — el layout no le reserva
 * espacio, se agrega cuando ese modelo exista.
 *
 * `heightIn(min = 48.dp)` cumple el criterio de aceptación "cada tarjeta es
 * tocable en un área de al menos 48dp de alto".
 */
@Composable
private fun NoteCard(note: Note, onDelete: (Note) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            // Cristal translúcido con borde luminoso: deja pasar el tinte del fondo aurora.
            .glassPanel(18.dp)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (note.subtopicId == null) {
                Text(
                    text = stringResource(R.string.notes_unclassified),
                    color = CodiceColors.Amber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = "${note.subtopicCode} · ${note.subtopicTitle}",
                    color = CodiceColors.TextHigh,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }

            // IconButton reserva 48dp alrededor del icono de 20dp, que es el mínimo tocable que ya exige el KDoc de
            // esta tarjeta. Eso deja la papelera un poco más adentro que la píldora de sincronización de abajo; se
            // prefiere eso a recortar el área tocable o a desalinear el resto del contenido con un padding distinto.
            IconButton(onClick = { onDelete(note) }) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.notes_delete_note),
                    tint = CodiceColors.TextLow,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        note.moduleLabel?.let { etiqueta ->
            Spacer(Modifier.height(6.dp))
            ModuleChip(etiqueta)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = note.confirmedText,
            color = CodiceColors.TextMid,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = CodiceColors.TextLow,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "${note.pageCount}", color = CodiceColors.TextLow, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Text(text = fechaRelativa(note.createdAt), color = CodiceColors.TextLow, fontSize = 12.sp)
            }

            SyncStatusPill(note.syncStatus)
        }
    }
}

/** La etiqueta de módulo — color fijo en Lime por ahora: no existe (todavía) un color por curso en el dominio. */
@Composable
private fun ModuleChip(etiqueta: String) {
    Box(
        modifier = Modifier
            .background(CodiceColors.Lime.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = etiqueta, color = CodiceColors.Lime, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** El punto de color + la etiqueta de texto — nunca solo el color (regla de accesibilidad de `convenciones.md`). */
@Composable
private fun SyncStatusPill(status: SyncStatus) {
    val (color, etiqueta, icono) = when (status) {
        SyncStatus.SYNCED -> Triple(CodiceColors.Blue, stringResource(R.string.notes_synced), Icons.Filled.CloudDone)
        SyncStatus.QUEUED -> Triple(CodiceColors.Yellow, stringResource(R.string.notes_queued), Icons.Filled.CloudQueue)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icono, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text = etiqueta, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Agrupa por "Hoy" / "Esta semana" / "Anteriores" comparando el inicio del
 * día de [Note.createdAt] contra el inicio del día de hoy, con
 * `java.util.Calendar` (no `java.time`: el `minSdk` de este módulo es 24, y
 * `java.time` recién está disponible sin desugaring desde API 26). Solo
 * devuelve las secciones que tienen al menos un apunte, en ese orden fijo.
 */
@Composable
private fun agruparPorFecha(notes: List<Note>): List<Pair<String, List<Note>>> {
    val inicioDeHoy = inicioDelDia(System.currentTimeMillis())
    val unDia = 24L * 60 * 60 * 1000

    val hoy = mutableListOf<Note>()
    val estaSemana = mutableListOf<Note>()
    val anteriores = mutableListOf<Note>()

    for (note in notes) {
        val diasDeDiferencia = (inicioDeHoy - inicioDelDia(note.createdAt)) / unDia
        when {
            diasDeDiferencia <= 0 -> hoy += note
            diasDeDiferencia in 1..6 -> estaSemana += note
            else -> anteriores += note
        }
    }

    return buildList {
        if (hoy.isNotEmpty()) add(stringResource(R.string.notes_today) to hoy)
        if (estaSemana.isNotEmpty()) add(stringResource(R.string.notes_this_week) to estaSemana)
        if (anteriores.isNotEmpty()) add(stringResource(R.string.notes_earlier) to anteriores)
    }
}

private fun inicioDelDia(epochMillis: Long): Long {
    val calendario = Calendar.getInstance()
    calendario.timeInMillis = epochMillis
    calendario.set(Calendar.HOUR_OF_DAY, 0)
    calendario.set(Calendar.MINUTE, 0)
    calendario.set(Calendar.SECOND, 0)
    calendario.set(Calendar.MILLISECOND, 0)
    return calendario.timeInMillis
}

/** "Hoy", "Ayer" o "Hace N días" — texto simple, sin librería de fechas relativas de por medio. */
@Composable
private fun fechaRelativa(epochMillis: Long): String {
    val dias = (inicioDelDia(System.currentTimeMillis()) - inicioDelDia(epochMillis)) / (24L * 60 * 60 * 1000)
    return when {
        dias <= 0 -> stringResource(R.string.notes_today)
        dias == 1L -> stringResource(R.string.notes_yesterday)
        else -> stringResource(R.string.notes_days_ago, dias)
    }
}

// --- Previews ---

private fun previewNote(
    id: String,
    texto: String,
    subtopicId: String? = "subtema-1",
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    diasAtras: Long = 0,
) = Note(
    id = id,
    subtopicId = subtopicId,
    moduleId = if (subtopicId != null) "modulo-4" else null,
    moduleLabel = if (subtopicId != null) "M4" else null,
    subtopicCode = if (subtopicId != null) "4.4" else null,
    subtopicTitle = if (subtopicId != null) "Servicios REST" else null,
    confirmedText = texto,
    pageCount = 2,
    syncStatus = syncStatus,
    createdAt = System.currentTimeMillis() - diasAtras * 24L * 60 * 60 * 1000,
)

@Preview(name = "Con apuntes", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun LibraryScreenContentPreview() {
    MaterialTheme {
        LibraryScreen(
            state = LibraryUiState(
                notes = NotesStatus.Content(
                    listOf(
                        previewNote("1", "La arquitectura REST se basa en recursos identificados por URIs y verbos HTTP estándar.", syncStatus = SyncStatus.QUEUED),
                        previewNote("2", "Apunte todavía sin clasificar por la auditoría de IA.", subtopicId = null, diasAtras = 3),
                        previewNote("3", "Repaso de autenticación con tokens JWT.", diasAtras = 10),
                    ),
                ),
                moduleOptions = listOf(ModuleOption("modulo-4", "M4"), ModuleOption("modulo-5", "M5")),
            ),
            onNavigateToEnrollment = {},
        )
    }
}

@Preview(name = "Sin apuntes", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun LibraryScreenEmptySinApuntesPreview() {
    MaterialTheme {
        LibraryScreen(state = LibraryUiState(notes = NotesStatus.EmptySinApuntes), onNavigateToEnrollment = {})
    }
}

@Preview(name = "Sin resultados", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun LibraryScreenEmptySinResultadosPreview() {
    MaterialTheme {
        LibraryScreen(state = LibraryUiState(notes = NotesStatus.EmptySinResultados), onNavigateToEnrollment = {})
    }
}

@Preview(name = "Error al cargar", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun LibraryScreenErrorPreview() {
    MaterialTheme {
        LibraryScreen(
            state = LibraryUiState(
                notes = NotesStatus.Error("No se pudo conectar. Revisa tu conexión e intenta de nuevo"),
            ),
            onNavigateToEnrollment = {},
        )
    }
}

@Preview(name = "Cargando", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun LibraryScreenLoadingPreview() {
    MaterialTheme {
        LibraryScreen(state = LibraryUiState(notes = NotesStatus.Loading), onNavigateToEnrollment = {})
    }
}
