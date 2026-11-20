package una.codice.core.domain.model

/**
 * El estado de cobertura de un subtema para un estudiante concreto.
 *
 * Corresponde exactamente al `CHECK` de la tabla `coverage` del modelo de
 * datos (`CODICE_2.HTM#ddl`): `NO_NOTES`, `PARTIAL`, `COVERED`, `REINFORCED`.
 * Es un `enum` y no un `String` por el mismo motivo que [UserRole]: la validez
 * del valor pasa a ser una garantía del compilador, no una regla que haya que
 * repetir en cada capa que lo toque.
 *
 * No hay que confundirlo con el veredicto de la auditoría de IA (llamada 3),
 * que solo devuelve `cubierto | parcial | ausente`. Ese veredicto es la
 * ENTRADA que produce (o no) un cambio de estado aquí; [REINFORCED] en
 * particular no lo pone la auditoría, sino que se alcanza más adelante,
 * cuando el estudiante aprueba el quiz de repaso de ese subtema.
 */
enum class CoverageStatus {
    /** Sin ningún apunte confirmado para este subtema todavía. Estado inicial. */
    NO_NOTES,

    /** Hay apunte, pero la auditoría encontró que cubre solo parte de los objetivos. */
    PARTIAL,

    /** La auditoría confirmó que el apunte cubre los objetivos del subtema. */
    COVERED,

    /** Estuvo cubierto y además el estudiante ya aprobó su repaso espaciado. */
    REINFORCED,
}

/**
 * Si este estado cuenta como "cubierto" para efectos de un porcentaje de
 * cobertura.
 *
 * Centralizado aquí porque [ModuleCoverage] y [CoverageMap] necesitan el
 * mismo criterio (COVERED o REINFORCED) para calcular su porcentaje, y
 * repetir el `||` en dos sitios es exactamente el tipo de duplicación que
 * termina desincronizada cuando alguien cambia solo una de las dos copias.
 */
val CoverageStatus.countsAsCovered: Boolean
    get() = this == CoverageStatus.COVERED || this == CoverageStatus.REINFORCED
