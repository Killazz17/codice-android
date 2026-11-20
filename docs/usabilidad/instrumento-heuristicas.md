# Instrumento de evaluación heurística — Códice

Adaptación de las 10 heurísticas de usabilidad de Jakob Nielsen al proyecto Códice, siguiendo el
procedimiento de la cátedra **IS-016-2026 — Procedimiento para la Administración del Proceso de
Pruebas de Usabilidad por Inspección con Heurísticas** (fuente: `Tema5_CalidadPruebasUsabilidadInspeccion2026.pdf`).

## Cómo se usa este instrumento

- Se aplica **por Sprint**, sobre las pantallas/flujos nuevos o modificados de ese Sprint — no
  pantalla por pantalla de toda la app cada vez.
- Cada evaluador responde cada pregunta con **Sí / No / N/A**, documentando evidencia (captura de
  pantalla o cita del comportamiento observado).
- Toda respuesta "No" se convierte en un hallazgo con: heurística incumplida, severidad (1-4),
  evidencia y recomendación.
- Escala de severidad (igual que el material de la cátedra):

  | Severidad | Significado |
  |---|---|
  | 1 | Cosmético — no hay que arreglarlo salvo que sobre tiempo |
  | 2 | Menor — baja prioridad |
  | 3 | Importante — prioridad alta |
  | 4 | Crítico — debe arreglarse antes de entregar |

Este archivo es el **instrumento reutilizable**: las preguntas no cambian de un Sprint a otro; lo
que cambia es qué pantallas se evalúan contra ellas y qué informe se produce (ver
`informes/`).

---

## H1 — Visibilidad del estado del sistema
El sistema debe mantener informado al usuario sobre lo que está ocurriendo.

1. ¿Toda acción que dispara una llamada asíncrona (login, registro, unirse a un curso, cerrar
   sesión) muestra un indicador mientras está en curso?
2. ¿El estado `Loading` de cada `UiState` (`AuthUiState.isLoading`, `NotesStatus.Loading`,
   `CoverageUiState.Loading`, `JoinUiState.isLoading`) tiene una representación visual explícita,
   no solo un valor interno?
3. ¿Después de una acción exitosa (crear cuenta, unirse a un curso, cerrar sesión) el usuario recibe
   alguna confirmación, aunque sea implícita (cambio de pantalla)?
4. ¿La barra de navegación inferior (`CodiceNavigationBar`) deja claro en todo momento en qué tab
   está parado el usuario?

## H2 — Coincidencia entre el sistema y el mundo real
El sistema debe usar lenguaje y conceptos familiares para sus usuarios, no jerga técnica.

1. ¿Los mensajes de `DomainError` (`InvalidCredentials`, `Network`, `Unknown`, etc.) están escritos
   en español llano, sin códigos ni términos de programación?
2. ¿Las etiquetas de los campos (`correo institucional`, `contraseña`, `nombre completo`) usan
   palabras que un estudiante reconoce, no nombres de columnas de base de datos?
3. ¿Los íconos de la barra de navegación (Capturar, Apuntes, Mapa, Repaso, Perfil) representan
   acciones que un estudiante entiende sin explicación?

## H3 — Control y libertad del usuario
El usuario debe poder deshacer acciones y salir de estados no deseados sin procesos largos.

1. ¿Unirse a un curso (`JoinCourseScreen`) pide confirmación antes de enviar la solicitud
   definitiva, con opción de cancelar?
2. ¿El escáner QR (`QrScannerOverlay`) se puede cerrar sin completar el escaneo?
3. ¿"Cerrar sesión" es una acción de un solo toque sin posibilidad de deshacerla por error (¿debería
   pedir confirmación, dado que es un cierre de sesión y no una acción trivial)?
4. ¿Existe una forma de volver atrás desde el registro al login sin perder los datos ya escritos?

## H4 — Consistencia y estándares
Los mismos términos, íconos y acciones deben verse igual en toda la interfaz.

1. ¿Los colores de estado (Lime = éxito/activo, Coral = error/salida, Amber = alerta/pendiente) se
   usan siempre con el mismo significado en todas las pantallas (`CodiceColors` centralizado)?
2. ¿El mismo tipo de acción (por ejemplo, un botón principal) tiene la misma forma y tratamiento
   visual en Login, Registro, Biblioteca y Cobertura?
3. ¿Los mensajes de campo obligatorio/inválido siguen el mismo patrón de redacción en todos los
   formularios (Login, Registro, código de invitación)?

## H5 — Prevención de errores
Es mejor evitar que ocurra un error que mostrar un buen mensaje después de que ocurrió.

1. ¿`ValidateEmailUseCase`/`ValidatePasswordUseCase` bloquean formatos inválidos antes de intentar
   enviarlos al repositorio?
2. ¿El botón de envío se deshabilita mientras una operación ya está en curso, para evitar envíos
   duplicados (`if (current.isLoading) return` en `AuthViewModel.onSubmit`)?
3. ¿El código de invitación (`ResolveInviteCodeUseCase`) valida el formato antes de intentar
   resolverlo contra un grupo real?
4. ¿Existe alguna acción destructiva o irreversible en la app (cerrar sesión, eliminar algo) sin
   ningún tipo de confirmación previa?

## H6 — Reconocer mejor que recordar
Minimizar la carga de memoria mostrando opciones e instrucciones visibles.

1. ¿El estado de sincronización de cada apunte (`Sincronizado`/`En cola`) se ve directamente en la
   tarjeta, sin que el estudiante deba recordarlo o consultarlo aparte?
2. ¿Los chips de filtro de Biblioteca muestran cuál está activo sin que el usuario deba recordarlo?
3. ¿El botón de mostrar/ocultar contraseña usa un ícono reconocible (ojo abierto/cerrado) en vez de
   exigir que el usuario recuerde un atajo?

## H7 — Flexibilidad y eficiencia de uso
El sistema debe servir a novatos y expertos, con atajos para estos últimos.

1. ¿Existe alguna forma más rápida de unirse a un curso para quien ya sabe su código, aparte del
   flujo completo con escáner?
2. ¿El buscador de Biblioteca permite llegar directo a un apunte sin navegar por las secciones de
   fecha?
3. ¿Hay algo en la app que obligue a un usuario experimentado a repetir pasos ya conocidos
   (re-tipear el mismo dato en dos pantallas, por ejemplo)?

## H8 — Estética y diseño minimalista
Las pantallas no deben tener información irrelevante que compita con lo importante.

1. ¿Cada tarjeta de apunte (`NoteCard`) muestra solo la información necesaria (código, módulo,
   extracto, estado), sin saturar con datos secundarios?
2. ¿La pantalla de Perfil evita mostrar información sin terminar como si fuera funcional (el texto
   "Próximamente" es honesto sobre qué falta, no aparenta estar completo)?
3. ¿Los colores de acento (Lime, Coral, Amber) se usan con moderación, resaltando solo lo que
   importa, sin competir entre sí en una misma pantalla?

## H9 — Ayudar a reconocer, diagnosticar y recuperarse de errores
Los mensajes de error deben ser claros, precisos y sugerir una solución.

1. ¿Todos los mensajes de `DomainError` evitan códigos técnicos (`Error 500`, excepciones de Kotlin)
   y explican en español qué pasó?
2. ¿El mensaje de credenciales inválidas se ubica junto al campo que el usuario debería revisar, o
   podría hacer pensar que el problema es solo la contraseña cuando también podría ser el correo?
3. ¿La pantalla de Cobertura ofrece un botón "Reintentar" ante un error de red, en vez de dejar al
   usuario sin salida?
4. ¿El estado vacío de Biblioteca distingue "todavía no fotografiaste nada" de "tu búsqueda no
   encontró resultados", con un mensaje distinto para cada causa?

## H10 — Ayuda y documentación
Debe existir ayuda accesible, fácil de buscar y enfocada en tareas del usuario.

1. ¿Existe algún ícono o acceso de ayuda visible desde las pantallas principales?
2. ¿El estudiante tiene alguna forma de entender qué significa cada estado de cobertura
   (`NO_NOTES`/`PARTIAL`/`COVERED`/`REINFORCED`) sin que se lo expliquen fuera de la app?
3. ¿Hay alguna guía o mensaje de bienvenida la primera vez que un estudiante entra sin cursos
   matriculados?
