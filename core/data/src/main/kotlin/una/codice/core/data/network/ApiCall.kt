package una.codice.core.data.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import una.codice.core.domain.error.DomainError
import una.codice.core.domain.result.DomainResult

/**
 * Punto único donde una respuesta de Retrofit se traduce a [DomainResult].
 *
 * Sin esto, cada repositorio tendría que repetir el mismo `try/catch` de tres
 * ramas (HTTP, sin conexión, cualquier otra cosa) — y bastaría con que alguien
 * lo escribiera distinto en un repositorio para que un mismo tipo de fallo
 * (p. ej. un timeout) se mostrara distinto según la pantalla.
 *
 * ### Por qué [onHttpError] es un parámetro y no un mapeo fijo aquí
 *
 * Un mismo código HTTP significa cosas distintas según el endpoint: un `409`
 * en login no existe, pero en registro significa "correo repetido"; un `401`
 * en login es "contraseña incorrecta", pero en cualquier otra llamada
 * significa "la sesión expiró". Cada método del repositorio conoce su propio
 * significado y lo pasa aquí; si no reconoce el código, cae al `Unknown` por
 * defecto de la rama `HttpException`.
 */
suspend fun <T> apiCall(
    onHttpError: (code: Int) -> DomainError? = { null },
    block: suspend () -> T,
): DomainResult<T> = apiCallDetailed(
    onHttpError = { code, _ -> onHttpError(code) },
    block = block,
)

suspend fun <T> apiCallDetailed(
    onHttpError: (code: Int, body: String?) -> DomainError? = { _, _ -> null },
    block: suspend () -> T,
): DomainResult<T> = try {
    DomainResult.Success(block())
} catch (error: CancellationException) {
    // Nunca hay que atrapar la cancelación de una corrutina: si la pantalla
    // que pidió esto ya no existe (el usuario navegó a otro lado), dejar que
    // la cancelación siga su curso es lo correcto. Atraparla aquí la
    // convertiría en un DomainResult.Failure fantasma que nadie va a leer.
    throw error
} catch (error: HttpException) {
    val errorBody = error.response()?.errorBody()?.string()
    DomainResult.Failure(onHttpError(error.code(), errorBody) ?: DomainError.Unknown())
} catch (error: IOException) {
    // Sin internet, DNS caído, timeout, Mockoon apagado: todo lo que impide
    // siquiera completar la llamada (a diferencia de HttpException, que sí
    // llegó al servidor y este respondió con un error).
    DomainResult.Failure(DomainError.Network())
} catch (error: Exception) {
    DomainResult.Failure(DomainError.Unknown())
}
