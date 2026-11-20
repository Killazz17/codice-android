package una.codice.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import una.codice.core.designsystem.component.AuroraBackground
import una.codice.core.designsystem.component.CodiceBottomBar
import androidx.navigation.NavDestination.Companion.hierarchy
import una.codice.core.designsystem.component.NavTab
import una.codice.core.designsystem.theme.CodiceAccents
import una.codice.core.designsystem.theme.ProvideAccent
import una.codice.core.domain.model.UserRole
import una.codice.feature.auth.presentation.AuthRoute
import una.codice.feature.auth.presentation.RegisterRoute
import una.codice.feature.coverage.presentation.CoverageRoute
import una.codice.feature.coverage.presentation.teacher.TeacherCoverageRoute
import una.codice.feature.enrollment.presentation.JoinCourseScreen
import una.codice.feature.enrollment.presentation.JoinCourseViewModel
import una.codice.feature.enrollment.presentation.teacher.TeacherCoursesRoute
import una.codice.feature.enrollment.presentation.teacher.TeacherCreateCourseRoute
import una.codice.feature.enrollment.presentation.teacher.TeacherInviteRoute
import una.codice.feature.enrollment.presentation.teacher.TeacherStudentsRoute
import una.codice.feature.notes.presentation.LibraryRoute
import una.codice.feature.notes.presentation.StudentCaptureRoute
import una.codice.feature.review.presentation.ReviewRoute
import una.codice.feature.syllabus.presentation.SyllabusRoute
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import una.codice.R
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School

/**
 * Los 5 tabs de la barra de navegación inferior del estudiante, tal como
 * están en Figma (nodo `tabbar` `24:114`): Inicio, Apuntes, Cursos, Repaso,
 * Perfil. El profesor no tiene su propia barra todavía — no hay diseño de
 * Figma que seguir para esos tabs.
 */
/** Una pestaña antes de resolver su texto: el idioma se conoce al componer, no cuando se crea esta lista. */
private class TabSpec(val route: String, val icon: ImageVector, @StringRes val label: Int)

private val studentNavTabs = listOf(
    TabSpec(Routes.STUDENT_CAPTURE, Icons.Filled.Home, R.string.app_capture),
    TabSpec(Routes.STUDENT_NOTES, Icons.Filled.MenuBook, R.string.app_notes),
    TabSpec(Routes.STUDENT_ENROLLMENT, Icons.Filled.School, R.string.app_courses),
    TabSpec(Routes.STUDENT_REVIEW, Icons.Filled.Quiz, R.string.app_review),
    TabSpec(Routes.STUDENT_PROFILE, Icons.Filled.Person, R.string.app_profile),
)

/**
 * Los 5 tabs de la barra de navegación inferior del profesor, según la captura
 * de pantalla: Cursos, Temario, Alumnos, Cobertura, Perfil.
 */
private val teacherNavTabs = listOf(
    TabSpec(Routes.TEACHER_HOME, Icons.Filled.Apps, R.string.app_courses),
    TabSpec(Routes.TEACHER_SYLLABUS, Icons.Filled.VerticalSplit, R.string.app_syllabus),
    TabSpec(Routes.TEACHER_STUDENTS, Icons.Filled.AccountCircle, R.string.app_students),
    TabSpec(Routes.TEACHER_COVERAGE, Icons.Filled.Layers, R.string.app_coverage),
    TabSpec(Routes.TEACHER_PROFILE, Icons.Filled.Person, R.string.app_profile),
)

/**
 * El único `NavHost` de la app, y el punto donde se decide con qué pantalla
 * arranca — el logotipo, el login, o directo el grafo de un rol.
 *
 * ### Por qué el `when` decide el punto de arranque en vez de siempre montar
 * el mismo `NavHost` y navegar después
 *
 * `startDestination` de un `NavHost` se fija una sola vez, al componerse por
 * primera vez. La forma "obvia" — montar siempre el `NavHost` en `Routes.AUTH`
 * y, si resulta que ya había sesión, navegar fuera apenas se sepa — sí
 * funciona, pero el usuario alcanza a ver el login un instante antes de que
 * lo saquen de ahí: es exactamente el parpadeo que pide evitar el issue #5.
 *
 * Aquí, mientras [SessionState.Loading] dura, **no existe ningún `NavHost`
 * todavía** — solo el logotipo. El `NavHost` se compone por primera vez
 * recién cuando ya se sabe la respuesta, y nace directamente con el
 * `startDestination` correcto. Nunca hay nada que "deshacer".
 */
@Composable
fun CodiceNavHost(sessionState: SessionState, modifier: Modifier = Modifier) {
    when (sessionState) {
        is SessionState.Loading -> SplashScreen()

        is SessionState.LoggedOut -> {
            val navController = rememberNavController()
            CodiceScaffold(navController, modifier) {
                NavHost(navController = navController, startDestination = Routes.AUTH) {
                    authDestination(navController)
                    studentGraph(navController)
                    teacherGraph(navController)
                }
            }
        }

        is SessionState.LoggedIn -> {
            val navController = rememberNavController()
            val startDestination = startDestinationFor(sessionState.role)
            CodiceScaffold(navController, modifier) {
                NavHost(navController = navController, startDestination = startDestination) {
                    authDestination(navController)
                    studentGraph(navController)
                    teacherGraph(navController)
                }
            }
        }
    }
}

/**
 * Envuelve el `NavHost` en un `Scaffold` cuya `bottomBar` es
 * [CodiceNavigationBar] — pero solo cuando la ruta visible es uno de los
 * [studentNavTabs]. En cualquier otra ruta (login, registro, "Unirse a un
 * curso", el grafo de profesor) la barra no se pinta: no es un tab, se llega
 * ahí por navegación normal.
 */
@Composable
private fun CodiceScaffold(
    navController: NavHostController,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route
    // La zona manda en el color: todo el grafo del profesor (incluidas las pantallas que no son pestañas) va en violeta,
    // todo el del estudiante en verde, y lo que no es de ninguno (iniciar sesión y registro) en ámbar.
    val zone = backStackEntry?.destination?.hierarchy
    val accent = when {
        zone?.any { it.route == Routes.TEACHER_GRAPH } == true -> CodiceAccents.Teacher
        zone?.any { it.route == Routes.STUDENT_GRAPH } == true -> CodiceAccents.Student
        else -> CodiceAccents.Gateway
    }
    val tabSpecs = if (studentNavTabs.any { it.route == currentRoute }) {
        studentNavTabs
    } else if (teacherNavTabs.any { it.route == currentRoute }) {
        teacherNavTabs
    } else {
        null
    }
    val tabs = tabSpecs?.map { NavTab(it.route, it.icon, stringResource(it.label)) }

    // El rol manda en el color: el grafo del profesor va en ultravioleta y magenta; el del estudiante (y el login), en
    // lima y turquesa. Al cambiar de rol los colores se funden. Todo lo de adentro lee la paleta con `LocalAccent`.
    ProvideAccent(accent) {
        // El fondo aurora vive aquí, una sola vez, detrás de todas las pantallas; el Scaffold es transparente para
        // dejarlo ver. Una pantalla que pinte su propio fondo sólido lo tapa.
        AuroraBackground(modifier = modifier) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    if (tabs != null) {
                        CodiceBottomBar(
                            tabs = tabs,
                            currentRoute = currentRoute,
                            onTabSelected = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    content()
                }
            }
        }
    }
}

/** A qué grafo entrar según el rol — la única regla de negocio de este archivo, y se usa en dos sitios. */
private fun startDestinationFor(role: UserRole): String =
    if (role == UserRole.TEACHER) Routes.TEACHER_GRAPH else Routes.STUDENT_GRAPH

private fun NavGraphBuilder.authDestination(navController: NavHostController) {
    composable(Routes.AUTH) {
        AuthRoute(
            onAuthenticated = { role -> navController.navigateToRoleGraph(role) },
            // saveState/restoreState (mismo patrón que un tab de la barra de
            // navegación inferior): en vez de un `navigate` liso, esto deja
            // guardada la entrada de REGISTER si el usuario ya había estado
            // ahí, así que si vuelve, retoma su propio RegisterViewModel en
            // vez de crear uno nuevo vacío.
            onNavigateToRegister = {
                navController.navigate(Routes.REGISTER) {
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
    composable(Routes.REGISTER) {
        RegisterRoute(
            onAuthenticated = { role -> navController.navigateToRoleGraph(role) },
            // Hallazgo H3.4 de la evaluación heurística
            // (docs/usabilidad/informes/sprint-piloto-2026-08-26.md): un
            // `popBackStack()` liso destruye por completo la entrada de
            // REGISTER (y con ella, el RegisterViewModel y todo lo que el
            // usuario había escrito) — si volvía a "Registrarse", el
            // formulario aparecía vacío. `popUpTo(..., saveState = true)`
            // guarda esa entrada en vez de destruirla, para que el
            // `restoreState = true` de arriba la recupere intacta.
            onNavigateToLogin = {
                navController.navigate(Routes.AUTH) {
                    popUpTo(Routes.REGISTER) { inclusive = true; saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }
}

/**
 * Entra al grafo del rol recién autenticado, limpiando todo lo que haya
 * antes — tanto si ese "antes" era solo [Routes.AUTH] (vino del login) como
 * si incluía también [Routes.REGISTER] apilado encima (vino del registro):
 * `popUpTo(Routes.AUTH) { inclusive = true }` saca todo lo que esté entre el
 * tope actual del backstack y [Routes.AUTH] inclusive. Así "el botón atrás
 * después de entrar no regresa al login" (ni al registro) vale para los dos
 * caminos con una sola regla.
 */
private fun NavHostController.navigateToRoleGraph(role: UserRole) {
    navigate(startDestinationFor(role)) {
        popUpTo(Routes.AUTH) { inclusive = true }
    }
}

/**
 * Cambia de pestaña igual que la barra inferior: sin apilar copias y sin dejar la pestaña de origen detrás.
 * Lo usan los atajos que llevan a una pestaña desde dentro de otra pantalla.
 */
private fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

/**
 * El grafo del estudiante. Las cinco pestañas son Inicio, Apuntes, Cursos, Repaso y Perfil; el mapa de cobertura ya no es
 * una pestaña, se abre desde "Cursos" al tocar un curso.
 */
private fun NavGraphBuilder.studentGraph(navController: NavHostController) {
    navigation(startDestination = Routes.STUDENT_NOTES, route = Routes.STUDENT_GRAPH) {
        composable(Routes.STUDENT_NOTES) {
            LibraryRoute(
                onNavigateToEnrollment = { navController.goToTab(Routes.STUDENT_ENROLLMENT) },
                // Igual que tocar la pestaña Inicio de la barra inferior.
                onNavigateToCapture = { navController.goToTab(Routes.STUDENT_CAPTURE) }
            )
        }

        // "Cursos": la lista de cursos del estudiante, unirse a uno nuevo y, desde cada curso, su mapa de cobertura.
        composable(Routes.STUDENT_ENROLLMENT) {
            val viewModel: JoinCourseViewModel = hiltViewModel()
            JoinCourseScreen(
                viewModel = viewModel,
                onOpenCourse = { navController.navigate(Routes.STUDENT_COVERAGE) }
            )
        }

        // Las demás pestañas de la barra de navegación inferior.
        // STUDENT_NOTES (tab "Apuntes") ya está montado arriba.
        composable(Routes.STUDENT_CAPTURE) {
            StudentCaptureRoute(onNavigateToEnrollment = { navController.goToTab(Routes.STUDENT_ENROLLMENT) })
        }
        // El mapa de un curso: pantalla de detalle (sin barra inferior) con flecha para volver a "Cursos".
        composable(Routes.STUDENT_COVERAGE) { CoverageRoute(onBack = { navController.popBackStack() }) }
        composable(Routes.STUDENT_REVIEW) { ReviewRoute() }
        composable(Routes.STUDENT_PROFILE) {
            val viewModel: ProfileViewModel = hiltViewModel()
            StudentProfilePlaceholder(onLogout = viewModel::onLogout)
        }
    }
}

/** El grafo del profesor. Mismo criterio que [studentGraph]. */
private fun NavGraphBuilder.teacherGraph(navController: NavHostController) {
    navigation(startDestination = Routes.TEACHER_HOME, route = Routes.TEACHER_GRAPH) {
        composable(Routes.TEACHER_HOME) {
            TeacherCoursesRoute(
                onNavigateToGroup = { groupId ->
                    navController.navigate(Routes.TEACHER_INVITE.replace("{groupId}", groupId))
                },
                onNavigateToCreateCourse = { navController.navigate(Routes.TEACHER_CREATE_COURSE) },
                onNavigateToRequests = { navController.navigate(Routes.TEACHER_STUDENTS) }
            )
        }
        composable(Routes.TEACHER_CREATE_COURSE) {
            TeacherCreateCourseRoute(
                onBack = { navController.popBackStack() },
                onCreated = { groupId ->
                    // Al crearlo se abre su invitación; "volver" desde ahí regresa a Mis cursos, no al formulario.
                    navController.navigate(Routes.TEACHER_INVITE.replace("{groupId}", groupId)) {
                        popUpTo(Routes.TEACHER_CREATE_COURSE) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.TEACHER_SYLLABUS) { SyllabusRoute() }
        composable(Routes.TEACHER_STUDENTS) {
            TeacherStudentsRoute(
                onNavigateToInvite = { groupId ->
                    navController.navigate(Routes.TEACHER_INVITE.replace("{groupId}", groupId))
                }
            )
        }
        composable(Routes.TEACHER_INVITE) {
            TeacherInviteRoute(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TEACHER_COVERAGE) { TeacherCoverageRoute() }
        composable(Routes.TEACHER_PROFILE) {
            val viewModel: ProfileViewModel = hiltViewModel()
            StudentProfilePlaceholder(onLogout = viewModel::onLogout)
        }
    }
}
