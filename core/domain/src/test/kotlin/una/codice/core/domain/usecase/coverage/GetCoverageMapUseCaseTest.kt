package una.codice.core.domain.usecase.coverage

import una.codice.core.domain.error.DomainError
import una.codice.core.domain.model.CoverageStatus
import una.codice.core.domain.model.SubtopicCoverage
import una.codice.core.domain.result.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [GetCoverageMapUseCase].
 *
 * El foco es el cálculo del porcentaje de cobertura por módulo y el global
 * (issue #19): que agrupe bien, que cuente [CoverageStatus.COVERED] y
 * [CoverageStatus.REINFORCED] como "cubierto" y los otros dos no, y que el
 * porcentaje global pese por subtema y no por módulo.
 */
class GetCoverageMapUseCaseTest {

    private fun subtema(
        id: String,
        codigo: String,
        moduloNumero: Int,
        moduloTitulo: String,
        estado: CoverageStatus,
    ) = SubtopicCoverage(
        subtopicId = id,
        subtopicCode = codigo,
        subtopicTitle = "Subtema $codigo",
        moduleNumber = moduloNumero,
        moduleTitle = moduloTitulo,
        status = estado,
    )

    @Test
    fun `agrupa los subtemas por modulo`() = runTest {
        val filas = listOf(
            subtema("1", "1.1", 1, "Fundamentos", CoverageStatus.COVERED),
            subtema("2", "1.2", 1, "Fundamentos", CoverageStatus.NO_NOTES),
            subtema("3", "4.4", 4, "Programación distribuida", CoverageStatus.PARTIAL),
        )
        val useCase = GetCoverageMapUseCase(FakeCoverageRepository(rows = filas))

        val mapa = (useCase("est-1") as DomainResult.Success).data

        assertEquals(2, mapa.modules.size)
        assertEquals(2, mapa.modules.first { it.moduleNumber == 1 }.subtopics.size)
        assertEquals(1, mapa.modules.first { it.moduleNumber == 4 }.subtopics.size)
    }

    @Test
    fun `ordena los modulos por numero ascendente aunque lleguen desordenados`() = runTest {
        val filas = listOf(
            subtema("1", "4.4", 4, "Programación distribuida", CoverageStatus.COVERED),
            subtema("2", "1.1", 1, "Fundamentos", CoverageStatus.COVERED),
            subtema("3", "2.1", 2, "GUI", CoverageStatus.COVERED),
        )
        val useCase = GetCoverageMapUseCase(FakeCoverageRepository(rows = filas))

        val mapa = (useCase("est-1") as DomainResult.Success).data

        assertEquals(listOf(1, 2, 4), mapa.modules.map { it.moduleNumber })
    }

    @Test
    fun `covered y reinforced cuentan como cubierto, no_notes y partial no`() = runTest {
        val filas = listOf(
            subtema("1", "1.1", 1, "Fundamentos", CoverageStatus.COVERED),
            subtema("2", "1.2", 1, "Fundamentos", CoverageStatus.REINFORCED),
            subtema("3", "1.3", 1, "Fundamentos", CoverageStatus.PARTIAL),
            subtema("4", "1.4", 1, "Fundamentos", CoverageStatus.NO_NOTES),
        )
        val useCase = GetCoverageMapUseCase(FakeCoverageRepository(rows = filas))

        val mapa = (useCase("est-1") as DomainResult.Success).data

        // 2 de 4 cubiertos -> 50%.
        assertEquals(50.0, mapa.modules.single().coveragePercentage, 0.0001)
    }

    @Test
    fun `un modulo sin subtemas cubiertos da 0 por ciento, no NaN`() = runTest {
        val filas = listOf(
            subtema("1", "1.1", 1, "Fundamentos", CoverageStatus.NO_NOTES),
        )
        val useCase = GetCoverageMapUseCase(FakeCoverageRepository(rows = filas))

        val mapa = (useCase("est-1") as DomainResult.Success).data

        assertEquals(0.0, mapa.modules.single().coveragePercentage, 0.0001)
    }

    @Test
    fun `un modulo totalmente cubierto da 100 por ciento`() = runTest {
        val filas = listOf(
            subtema("1", "1.1", 1, "Fundamentos", CoverageStatus.COVERED),
            subtema("2", "1.2", 1, "Fundamentos", CoverageStatus.REINFORCED),
        )
        val useCase = GetCoverageMapUseCase(FakeCoverageRepository(rows = filas))

        val mapa = (useCase("est-1") as DomainResult.Success).data

        assertEquals(100.0, mapa.modules.single().coveragePercentage, 0.0001)
    }

    /**
     * 1 de 3 no es un porcentaje "redondo" (33.333...%): a propósito, para
     * comprobar que la división no trunca a `Int` en algún punto del camino
     * y que el delta de [assertEquals] alcanza para una comparación de punto
     * flotante honesta, sin que el test dependa de un redondeo previo.
     */
    @Test
    fun `un tercio cubierto da 33_33 por ciento, sin redondear de mas`() = runTest {
        val filas = listOf(
            subtema("1", "1.1", 1, "Fundamentos", CoverageStatus.COVERED),
            subtema("2", "1.2", 1, "Fundamentos", CoverageStatus.NO_NOTES),
            subtema("3", "1.3", 1, "Fundamentos", CoverageStatus.NO_NOTES),
        )
        val useCase = GetCoverageMapUseCase(FakeCoverageRepository(rows = filas))

        val mapa = (useCase("est-1") as DomainResult.Success).data

        assertEquals(100.0 / 3.0, mapa.modules.single().coveragePercentage, 0.0001)
    }

    /**
     * Dos módulos con porcentajes bien distintos en la misma llamada: cada
     * [ModuleCoverage.coveragePercentage][una.codice.core.domain.model.ModuleCoverage.coveragePercentage]
     * debe calcularse solo con SUS PROPIOS subtemas. Los tests anteriores
     * prueban un módulo a la vez; este confirma que agrupar primero no
     * termina mezclando denominadores entre módulos distintos.
     */
    @Test
    fun `cada modulo calcula su propio porcentaje sin mezclarse con el de otro`() = runTest {
        val filas = listOf(
            // Modulo 1: 3 de 4 cubiertos -> 75%.
            subtema("1", "1.1", 1, "Fundamentos", CoverageStatus.COVERED),
            subtema("2", "1.2", 1, "Fundamentos", CoverageStatus.COVERED),
            subtema("3", "1.3", 1, "Fundamentos", CoverageStatus.REINFORCED),
            subtema("4", "1.4", 1, "Fundamentos", CoverageStatus.NO_NOTES),
            // Modulo 4: 1 de 2 cubiertos -> 50%.
            subtema("5", "4.1", 4, "Programación distribuida", CoverageStatus.COVERED),
            subtema("6", "4.2", 4, "Programación distribuida", CoverageStatus.PARTIAL),
        )
        val useCase = GetCoverageMapUseCase(FakeCoverageRepository(rows = filas))

        val mapa = (useCase("est-1") as DomainResult.Success).data

        assertEquals(75.0, mapa.modules.first { it.moduleNumber == 1 }.coveragePercentage, 0.0001)
        assertEquals(50.0, mapa.modules.first { it.moduleNumber == 4 }.coveragePercentage, 0.0001)
    }

    @Test
    fun `el porcentaje global pesa por subtema y no por modulo`() = runTest {
        // Modulo 1: 1 de 1 subtema cubierto (100%).
        // Modulo 2: 0 de 3 subtemas cubiertos (0%).
        // Promedio simple de modulos daria 50%; ponderado por subtema da 25%.
        val filas = listOf(
            subtema("1", "1.1", 1, "Fundamentos", CoverageStatus.COVERED),
            subtema("2", "2.1", 2, "GUI", CoverageStatus.NO_NOTES),
            subtema("3", "2.2", 2, "GUI", CoverageStatus.NO_NOTES),
            subtema("4", "2.3", 2, "GUI", CoverageStatus.PARTIAL),
        )
        val useCase = GetCoverageMapUseCase(FakeCoverageRepository(rows = filas))

        val mapa = (useCase("est-1") as DomainResult.Success).data

        assertEquals(25.0, mapa.globalPercentage, 0.0001)
    }

    @Test
    fun `sin subtemas el mapa queda vacio y el global es 0 por ciento`() = runTest {
        val useCase = GetCoverageMapUseCase(FakeCoverageRepository(rows = emptyList()))

        val mapa = (useCase("est-1") as DomainResult.Success).data

        assertTrue(mapa.modules.isEmpty())
        assertEquals(0.0, mapa.globalPercentage, 0.0001)
    }

    @Test
    fun `propaga el error del repositorio tal cual`() = runTest {
        val useCase = GetCoverageMapUseCase(
            FakeCoverageRepository(failure = DomainError.Network()),
        )

        val resultado = useCase("est-1") as DomainResult.Failure

        assertTrue(resultado.error is DomainError.Network)
    }

    @Test
    fun `el studentId llega tal cual al repositorio`() = runTest {
        val repositorio = FakeCoverageRepository(rows = emptyList())
        val useCase = GetCoverageMapUseCase(repositorio)

        useCase("est-42")

        assertEquals(listOf("est-42"), repositorio.getCalls)
    }
}
