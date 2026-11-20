# Mock API de Códice (Mockoon)

Este entorno simula el backend de Auth y Biblioteca mientras no existe la API
real. `AuthRepositoryImpl` y `NoteRepositoryImpl` (en `:core:data`) le hablan
por HTTP a través de Retrofit — ver `NetworkModule.kt`.

## Cómo levantarlo

1. Instalar [Mockoon Desktop](https://mockoon.com/download/) (Windows/Mac/Linux, gratis).
2. `File > Open environment` y seleccionar `codice-mock-environment.json`.
3. Presionar el botón de play (▶) del entorno. Debe quedar corriendo en el
   puerto **3000** (`http://localhost:3000`).
4. Dejarlo corriendo mientras se prueba la app en el **emulador** de Android
   Studio — por defecto la app apunta a `http://10.0.2.2:3000/`, que es como
   el emulador ve el `localhost` de esta misma PC (ver el KDoc de
   `NetworkModule.kt`).

### Probar en un dispositivo físico

`10.0.2.2` no existe fuera del emulador, así que hay que decirle a la app
otra URL — **sin tocar `NetworkModule.kt`**, para no romperle el default a
quien sigue probando en el emulador. La URL sale de `BuildConfig.MOCK_BASE_URL`,
que a su vez se lee de `local.properties` (nunca se sube a git, es distinto
por máquina). Dos formas, de más simple a más flexible:

**a) `adb reverse` (no depende de estar en la misma red WiFi)**
1. Conectar el teléfono por USB con depuración USB activada y autorizada
   (`adb devices` debe listarlo).
2. `adb reverse tcp:3000 tcp:3000` — el `localhost:3000` del teléfono pasa a
   ser el `localhost:3000` de la PC.
3. Agregar en `local.properties`: `mockoon.baseUrl=http://localhost:3000/`
4. Sincronizar Gradle y volver a instalar la app.

**b) IP de la PC en la red local (WiFi, sin cable)**
1. PC y teléfono deben estar en la **misma red WiFi**.
2. Averiguar la IP de la PC (`ipconfig` en Windows, buscar la IPv4 de la
   red WiFi).
3. Agregar en `local.properties`: `mockoon.baseUrl=http://192.168.x.x:3000/`
4. Revisar que el Firewall de Windows no bloquee conexiones entrantes al
   puerto 3000.
5. Sincronizar Gradle y volver a instalar la app.

En ambos casos, quien no toque `local.properties` sigue usando `10.0.2.2`
sin darse cuenta de nada.

## Rutas incluidas

| Método | Ruta | Qué simula |
|---|---|---|
| `POST` | `/api/v1/auth/login` | `200` con una sesión si el correo/clave coinciden con `ana@una.ac.cr` o `profe@una.ac.cr` (clave `codice2026` para ambas); `401` en cualquier otro caso |
| `POST` | `/auth/register` | `409` si el correo ya es una de las dos cuentas de ejemplo; `200` con una sesión nueva en cualquier otro caso |
| `GET` | `/notes?studentId=` | `200` con dos apuntes de ejemplo; `500` si se manda `studentId=forzar-error`, para probar el camino de error de la Biblioteca |

## Editar o agregar rutas

Cada ruta en Mockoon puede tener varias "respuestas" con reglas (`rules`) que
deciden cuál se dispara según el body/query de la petición — así es como
`/auth/login` distingue una cuenta válida de una inválida sin escribir
código. Para agregar un caso nuevo (p. ej. un tercer usuario, o un endpoint
de Cobertura si algún día se decide moverla de Room a Retrofit también):

1. Abrir el entorno en Mockoon Desktop.
2. Agregar la ruta o la respuesta nueva con la UI.
3. `Environment > Export current environment` para regenerar este JSON, y
   commitear el archivo actualizado — así el resto del equipo levanta
   exactamente el mismo mock.

## Por qué Coverage y Enrollment no están aquí

Esos dos repositorios (`CoverageRepositoryImpl`, `EnrollmentRepositoryImpl`)
ya tienen persistencia real con Room, DAOs y pruebas instrumentadas propias
(`CoverageDaoTest`, etc.). Reemplazarlos por Mockoon habría sido un paso
atrás — Auth y Biblioteca sí lo necesitaban porque antes eran datos falsos en
memoria sin persistencia real de ningún tipo.
