# Distribución con Firebase App Distribution

Cómo se genera el APK firmado de Códice y se reparte a los testers. La llave de firma **nunca** está en el repositorio.

## 1. Llave de firma (una sola vez)

El release se firma con una llave propia (`.jks`) que vive fuera del repo, por ejemplo en `%USERPROFILE%\.codice-keys\`.
Se crea con `keytool`:

```bash
keytool -genkeypair -keystore codice-release.jks -alias codice -keyalg RSA -keysize 2048 -validity 10000
```

Guarda una copia de la llave y de su contraseña en un lugar seguro. **Si se pierde, no se puede publicar una actualización
firmada igual**: los testers tendrían que desinstalar la app e instalarla de nuevo.

## 2. Compilar el release

Gradle lee la firma de cuatro variables de entorno (ver `app/build.gradle.kts`). Sin ellas el release queda sin firmar:

| Variable | Qué es |
|---|---|
| `CODICE_KEYSTORE` | Ruta al `.jks` |
| `CODICE_KEYSTORE_PASSWORD` | Contraseña del almacén |
| `CODICE_KEY_ALIAS` | Alias de la llave (`codice`) |
| `CODICE_KEY_PASSWORD` | Contraseña de la llave |

```bash
./gradlew :app:assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`. R8 está activo en el release (reduce y ofusca el código;
las reglas para no tocar los DTO de la red están en `app/src/main/keepRules/rules.keep`).

Comprobar la firma:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 3. Prueba rápida de R8 (smoke test)

R8 puede romper lo que solo se usa por reflexión (JSON, red). Antes de repartir el APK se instala el **release** y se recorre:

1. Iniciar sesión con una cuenta de prueba (llama a la API real).
2. Apuntes: la lista carga desde el servidor.
3. Cursos → abrir un curso: el mapa de cobertura carga.
4. Repaso: aparece una pregunta generada.
5. Perfil: las cifras de actividad cargan.
6. `adb logcat` sin `FATAL`, `JsonDataException`, `NoSuchMethod` ni `ClassNotFound`.

Resultado de la última corrida (9-nov-2026): los seis puntos correctos, sin excepciones en el log. No se probó la cámara ni
la subida de una foto.

## 4. Subir a Firebase App Distribution

1. Firebase console → proyecto → **Release & Monitor → App Distribution → Get started**.
2. Pestaña **Releases** → arrastrar `app-release.apk`.
3. En **Testers & Groups** crear un grupo (por ejemplo `encuesta`) y agregar los correos.
4. Escribir las notas de la versión y pulsar **Distribute**.

Cada tester recibe un correo con el enlace de instalación. Para una versión nueva con la misma llave basta con subir otro
APK con un `versionCode` mayor (`app/build.gradle.kts`).
