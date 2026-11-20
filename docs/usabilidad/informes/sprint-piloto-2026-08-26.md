# Informe de evaluación heurística — Sprint piloto

**Proyecto:** Códice (Grupo Sierra) | **Fecha:** 2026-08-26 | **Evaluador:** Sebastián B. (con
apoyo de Claude Code, por inspección de código + verificación en emulador donde fue posible)
**Instrumento:** `../instrumento-heuristicas.md` | **Procedimiento:** IS-016-2026

## Alcance

Primera aplicación del instrumento, cubriendo **todas las pantallas navegables de `master` al
momento de escribir esto**, no solo un Sprint puntual — para dejar una línea base completa antes de
empezar a evaluar por Sprint hacia adelante.

| Pantalla | Estado |
|---|---|
| Login (`AuthScreen`) | Evaluada |
| Registro (`RegisterScreen`) | Evaluada |
| Biblioteca (`LibraryScreen`) | Evaluada |
| Unirse a un curso (`JoinCourseScreen`) | Evaluada |
| Mapa de cobertura (`CoverageScreen`) | Evaluada |
| Perfil (`StudentProfilePlaceholder` + logout) | Evaluada (parcial — la mayoría del contenido no existe todavía) |
| Barra de navegación (`CodiceNavigationBar`) | Evaluada (transversal) |
| Capturar / Repaso / Panel del profesor | **N/A** — son placeholders puros ("Próximamente"), sin contenido interactivo que inspeccionar |

---

## Login (`feature/auth/.../AuthScreen.kt`, `AuthViewModel.kt`)

| # | Pregunta | Resp. | Heurística | Severidad | Evidencia | Recomendación |
|---|---|---|---|---|---|---|
| H1.1 | ¿Toda acción asíncrona muestra un indicador? | **Sí** | — | — | El botón "Entrar" reemplaza su texto por `CircularProgressIndicator` mientras `state.isLoading` (`AuthScreen.kt:246-254`) | — |
| H5.2 | ¿Se evita el doble envío? | **Sí** | — | — | `AuthViewModel.onSubmit()`: `if (current.isLoading) return` | — |
| H5.1 | ¿Se valida el formato antes de enviar? | **Sí** | — | — | `ValidateEmailUseCase`/`ValidatePasswordUseCase` corren antes de tocar el repositorio | — |
| H9.1 | ¿Los mensajes de error son legibles, no técnicos? | **Sí** | — | — | `DomainError.InvalidCredentials.message = "Correo o contraseña incorrectos"` — sin códigos | — |
| H9.2 | ¿El mensaje de error se ubica en el campo correcto? | **No** | H9 | 2 — Menor | `AuthViewModel.showError()`: cualquier error que no sea `Validation` (incluido `InvalidCredentials`) se asigna siempre al campo `PASSWORD` (línea 96), aunque la causa real pueda ser el correo. El usuario podría corregir la contraseña sin necesidad, sin revisar el correo. | Mostrar el mensaje de credenciales inválidas debajo de ambos campos, o en un banner general en vez de atribuirlo a uno solo. |
| H2.2 | ¿Las etiquetas usan lenguaje familiar? | **Sí** | — | — | "correo institucional", "contraseña" — sin jerga | — |

## Registro (`RegisterScreen.kt`, `RegisterViewModel.kt`)

| # | Pregunta | Resp. | Heurística | Severidad | Evidencia | Recomendación |
|---|---|---|---|---|---|---|
| H1.1 / H5.2 | Indicador de carga + anti-doble-envío | **Sí** | — | — | Mismo patrón que Login (`isLoading`, `CircularProgressIndicator`) | — |
| H9.1 | ¿Errores por campo, incluida confirmación de contraseña? | **Sí** | — | — | `fieldErrors[PASSWORD_CONFIRMATION]` marca específicamente esa caja cuando no coincide (`ValidatePasswordUseCase.kt:67-71`) | — |
| H3.4 | ¿Volver de Registro a Login conserva lo ya escrito? | **No** *(por inspección de código, no verificado en emulador esta sesión)* | H3 | 2 — Menor | `onNavigateToLogin = { navController.popBackStack() }` en `CodiceNavHost.kt` destruye el `RegisterViewModel` de esa entrada del back stack; si el usuario vuelve a Registro, arranca en blanco. | Si se detecta como problema real al probarlo, usar `SavedStateHandle` en `RegisterViewModel` para sobrevivir a la recreación, o evaluar si perder los datos es aceptable (formulario corto). |
| H4.3 | ¿Mismo patrón de redacción de errores que Login? | **Sí** | — | — | Mismo formato "El X es obligatorio" / "X inválido" en ambos casos de uso de validación | — |

## Biblioteca (`LibraryScreen.kt`, `LibraryViewModel.kt`)

| # | Pregunta | Resp. | Heurística | Severidad | Evidencia | Recomendación |
|---|---|---|---|---|---|---|
| H9.4 | ¿Distingue "sin apuntes" de "sin resultados"? | **Sí** | — | — | `NotesStatus.EmptySinApuntes` vs `EmptySinResultados`, con mensajes distintos (`LibraryScreen.kt`) | — |
| H6.1 | ¿El estado de sincronización se ve sin recordarlo? | **Sí** | — | — | `SyncStatusPill` en cada `NoteCard`, color + ícono + texto | — |
| H6.2 | ¿Se ve cuál chip de filtro está activo? | **Sí** | — | — | `LibraryChip` con check + fondo Lime cuando `selected` | — |
| H1.1 | ¿El buscador muestra algo mientras filtra? | **No** | H1 | 1 — Cosmético | El filtrado es instantáneo (todo en memoria vía `NoteRepositoryImpl`), así que hoy no hace falta indicador — pero si en el futuro `NoteRepository` habla con una API real, el buscador quedaría sin ninguna señal de "buscando…" durante el `debounce(300)` + la espera de red. | Dejar un `TODO` explícito: agregar un indicador de carga al buscador cuando exista el backend real. |
| H8.1 | ¿La tarjeta de apunte evita saturar de información? | **Sí** | — | — | `NoteCard` muestra solo código/título, módulo, extracto a 2 líneas, páginas, fecha y estado — nada más | — |

## Unirse a un curso (`JoinCourseScreen.kt`, `JoinCourseViewModel.kt`)

| # | Pregunta | Resp. | Heurística | Severidad | Evidencia | Recomendación |
|---|---|---|---|---|---|---|
| H3.1 | ¿Pide confirmación antes de enviar la solicitud? | **Sí** | — | — | `ConfirmationDialog` (líneas 175-181, 438-463) con botón "Cancelar" antes de `onConfirmJoin` | — |
| H3.2 | ¿El escáner QR se puede cerrar sin completar? | **Sí** | — | — | `QrScannerOverlay(onDismiss = viewModel::onDismissScanner)` | — |
| H7.1 | ¿Hay una vía rápida para quien ya sabe su código? | **Sí** | — | — | `CodeInputSection`/`PinInput` — no obliga a pasar por el escáner QR | — |
| H5.3 | ¿Se valida el formato del código antes de resolverlo? | **Sí** | — | — | `ResolveInviteCodeUseCase` valida el patrón `^[A-Z0-9]{6}-[A-Z0-9]{2}-[A-Z0-9]{4}$` antes de buscar el grupo | — |

## Mapa de cobertura (`CoverageScreen.kt`, `CoverageViewModel.kt`)

| # | Pregunta | Resp. | Heurística | Severidad | Evidencia | Recomendación |
|---|---|---|---|---|---|---|
| H9.3 | ¿Ofrece "Reintentar" ante un error? | **Sí** | — | — | `ErrorContent(message, onRetry)` con botón "Reintentar" (`CoverageScreen.kt`) | — |
| H1.1 | ¿Muestra indicador mientras carga? | **Sí** | — | — | `CoverageUiState.Loading` → `CircularProgressIndicator` | — |
| H9.4-equiv | ¿Distingue "sin temario" de un error real? | **Sí** | — | — | `CoverageUiState.Empty` ("no hay temario publicado") separado de `Error` — mismo criterio que Biblioteca | — |
| H10.2 | ¿Se explica qué significa cada estado de cobertura? | **No** | H10 | 2 — Menor | Los 4 estados (`NO_NOTES`/`PARTIAL`/`COVERED`/`REINFORCED`) se distinguen por color+etiqueta de texto en la pantalla, pero no hay ninguna ayuda que explique, por ejemplo, la diferencia entre "Cubierto" y "Reforzado" la primera vez que el estudiante lo ve. | Agregar un tooltip o una pantalla de ayuda breve la primera vez que se abre el Mapa de cobertura. |

## Perfil (`Placeholders.kt` → `StudentProfilePlaceholder`, `ProfileViewModel`)

| # | Pregunta | Resp. | Heurística | Severidad | Evidencia | Recomendación |
|---|---|---|---|---|---|---|
| H3.3 | ¿"Cerrar sesión" pide confirmación? | **No** | H3 | 3 — Importante | El botón llama `onLogout` directo, sin diálogo — un toque accidental cierra la sesión sin poder deshacerlo. | Agregar un `AlertDialog` de confirmación antes de invocar `logoutUseCase()`, mismo patrón que ya existe en `ConfirmationDialog` de Matrícula. |
| H8.2 | ¿"Próximamente" es honesto sobre qué falta? | **Sí** | — | — | El texto no aparenta funcionalidad que no existe; el único control real (Cerrar sesión) se distingue visualmente del resto | — |
| — | Resto de la pantalla (avatar, cursos matriculados, ajustes) | **N/A** | — | — | No existe todavía — nada que evaluar hasta que se construya | Evaluar de nuevo cuando Perfil tenga su UI real. |

## Barra de navegación inferior (`CodiceNavigationBar.kt`, transversal a todas las pantallas)

| # | Pregunta | Resp. | Heurística | Severidad | Evidencia | Recomendación |
|---|---|---|---|---|---|---|
| H1.4 | ¿Deja claro en qué tab está el usuario? | **Sí** | — | — | El tab activo tiene fondo Lime@14% + texto/ícono Lime; los demás en `TextLow` | — |
| H2.3 | ¿Los íconos son reconocibles sin explicación? | **Sí**, con matiz | H2 | 1 — Cosmético | Cámara/Libro/Mapa/Perfil son estándar; el ícono de "Repaso" (`Icons.Filled.Quiz`) podría no ser obvio para todos los estudiantes la primera vez. | Sin acción necesaria — severidad cosmética; considerar solo si aparece confusión real reportada por usuarios. |
| (accesibilidad, no heurística de Nielsen) | — | — | — | — | Ya corregido en la rama `issue-accessibility-fix`: el doble anuncio de TalkBack en cada tab (ver auditoría de accesibilidad anterior) | Mergear esa rama si no se ha hecho todavía. |

## Capturar / Repaso / Panel del profesor

**N/A** — son placeholders puros (`GraphPlaceholder` con solo título, subtítulo y "Próximamente"),
sin ningún control interactivo. No aplica ninguna de las 10 heurísticas todavía porque no hay nada
que un usuario pueda hacer mal o bien en estas pantallas. Quedan pendientes de evaluar cuando cada
una tenga su implementación real (issues de Cámara, Repaso y Panel del profesor).

---

## Resumen de hallazgos

| Severidad | Cantidad | Hallazgos |
|---|---|---|
| 4 — Crítico | 0 | — |
| 3 — Importante | 1 | Perfil: "Cerrar sesión" sin confirmación |
| 2 — Menor | 3 | Login: error de credenciales siempre en el campo contraseña · Registro: se pierden los datos al volver de Registro a Login · Cobertura: sin ayuda sobre qué significa cada estado |
| 1 — Cosmético | 2 | Biblioteca: buscador sin indicador de carga (hoy no aplica, a futuro sí) · Barra de navegación: ícono de "Repaso" podría no ser obvio |

## Prioridad de arreglo

1. **Cerrar sesión sin confirmación** (severidad 3) — es la única acción realmente irreversible de
   la app hoy; un toque accidental saca al estudiante de su sesión sin aviso.
2. **Ubicación del error de credenciales** y **pérdida de datos al volver a Registro** (severidad 2)
   — pulido de UX, no bloqueante para la entrega actual.
3. El resto (severidad 1-2 restantes) son mejoras de "más adelante", no bloquean ningún criterio de
   aceptación existente.

## Lo que ya está bien (para no perder de vista qué SÍ funciona)

La mayoría de las respuestas de este piloto fueron **Sí**: indicadores de carga consistentes,
prevención de errores real (validación antes de enviar), confirmación antes de una acción
importante en Matrícula, distinción correcta entre "vacío por falta de datos" y "vacío por
filtro" en dos pantallas distintas (Biblioteca y Cobertura), y una paleta de color centralizada que
sostiene la heurística de consistencia en toda la app. El instrumento no solo sirvió para encontrar
fallas — confirmó que varias decisiones de arquitectura (`DomainError`, `UiState` sellado) ya
resuelven usabilidad "gratis" por diseño.
