package una.codice.core.domain.model

/**
 * La cobertura de UN subtema para el estudiante actual, con el mínimo de
 * datos de su módulo para poder agruparlo sin una segunda consulta.
 *
 * Es la forma "plana" en la que [una.codice.core.domain.repository.CoverageRepository]
 * entrega los datos: una fila por subtema, tal como sale de un solo `JOIN`
 * entre `module`, `subtopic` y `coverage` (ver el ejemplo de consulta agregada
 * en `CODICE_2.HTM#ddl`, que hace exactamente ese cruce para el profesor). El
 * agrupamiento por módulo y el cálculo del porcentaje pasan a
 * [una.codice.core.domain.usecase.coverage.GetCoverageMapUseCase] a propósito:
 * así esa cuenta se prueba con JUnit puro en vez de depender de que el `GROUP
 * BY` de Room esté bien escrito.
 *
 * @property subtopicId `uuid` del subtema.
 * @property subtopicCode Código del programa, p. ej. `"4.4"`.
 * @property subtopicTitle Título del subtema para mostrar en la lista.
 * @property moduleNumber Número del módulo al que pertenece, p. ej. `4`.
 * @property moduleTitle Título del módulo, p. ej. `"Programación distribuida"`.
 * @property status Estado actual de cobertura para el estudiante.
 */
data class SubtopicCoverage(
    val subtopicId: String,
    val subtopicCode: String,
    val subtopicTitle: String,
    val moduleNumber: Int,
    val moduleTitle: String,
    val status: CoverageStatus,
)
