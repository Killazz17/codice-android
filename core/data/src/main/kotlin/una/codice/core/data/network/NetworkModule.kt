package una.codice.core.data.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import una.codice.core.data.BuildConfig
import una.codice.core.data.network.auth.AuthApiService
import una.codice.core.data.network.coverage.CoverageApiService
import una.codice.core.data.network.enrollment.EnrollmentApiService
import una.codice.core.data.network.note.NoteApiService
import una.codice.core.data.network.review.ReviewApiService
import una.codice.core.data.network.syllabus.SyllabusApiService
import una.codice.core.data.network.teacher.TeacherApiService

/**
 * Arma el cliente HTTP y el `Retrofit` que hablan con el backend de Códice
 * (Spring Boot, puerto 8080). Todas las requests llevan el token de la sesión
 * gracias a [AuthInterceptor].
 *
 * ### Por qué la URL viene de `BuildConfig` y no está escrita aquí
 *
 * El emulador de Android corre en su propia máquina virtual: `localhost`
 * ahí adentro es el emulador mismo, no la PC donde corre el backend. `10.0.2.2`
 * es la IP fija que el emulador reserva para referirse al host real — por
 * eso es el default (ver `core/data/build.gradle.kts`, `apiBaseUrl`).
 * En un dispositivo físico esto NO sirve: hay que apuntar a la IP de la PC
 * en la red local, o usar `localhost` junto con `adb reverse tcp:8080
 * tcp:8080`. Quien pruebe en físico agrega `api.baseUrl=...` a su
 * `local.properties` (nunca se sube a git) en vez de tocar esta clase — así
 * nadie más ve su URL cambiada por accidente.
 *
 * ### Por qué un solo lugar para la URL
 *
 * Todos los `ApiService` del proyecto comparten la misma instancia de
 * [Retrofit], así que cambiar de puerto (o pasar del backend local al desplegado
 * real el día de mañana) es una sola línea, no una búsqueda por todo
 * `:core:data`.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val API_BASE_URL = BuildConfig.API_BASE_URL

    /**
     * `ignoreUnknownKeys`: el backend puede
     * devolver campos que los DTO de Códice todavía no modelan, sin que eso
     * tumbe el parseo. `coerceInputValues`: si algún campo llega `null`
     * donde el DTO no lo espera, usa el valor por defecto de la propiedad en
     * vez de lanzar — más tolerante mientras el contrato del mock todavía se
     * está afinando en el curso.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        unauthorizedInterceptor: UnauthorizedInterceptor,
    ): OkHttpClient {
        // Una build de release nunca habla con un servidor que no sea HTTPS.
        require(BuildConfig.DEBUG || API_BASE_URL.startsWith("https://")) { "La API de release debe usar HTTPS" }
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(unauthorizedInterceptor)
        // El log de red solo existe en debug, y sin cuerpos: en el login irían la contraseña y el JWT, y en las demás
        // respuestas los apuntes de estudiantes. El header Authorization se tapa por si se sube el nivel.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader(AuthInterceptor.AUTHORIZATION)
                },
            )
        }
        return builder
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)

    @Provides
    @Singleton
    fun provideNoteApiService(retrofit: Retrofit): NoteApiService =
        retrofit.create(NoteApiService::class.java)

    @Provides
    @Singleton
    fun provideEnrollmentApiService(retrofit: Retrofit): EnrollmentApiService =
        retrofit.create(EnrollmentApiService::class.java)

    @Provides
    @Singleton
    fun provideCoverageApiService(retrofit: Retrofit): CoverageApiService =
        retrofit.create(CoverageApiService::class.java)

    @Provides
    @Singleton
    fun provideReviewApiService(retrofit: Retrofit): ReviewApiService =
        retrofit.create(ReviewApiService::class.java)

    @Provides
    @Singleton
    fun provideSyllabusApiService(retrofit: Retrofit): SyllabusApiService =
        retrofit.create(SyllabusApiService::class.java)

    @Provides
    @Singleton
    fun provideTeacherApiService(retrofit: Retrofit): TeacherApiService =
        retrofit.create(TeacherApiService::class.java)
}
