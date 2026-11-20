package una.codice

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Clase Application de Codice: el primer objeto que Android crea al abrir la
 * app, antes que cualquier Activity.
 *
 * ### Que hace la anotacion @HiltAndroidApp
 *
 * Le dice a Hilt que genere aqui el "contenedor" raiz de dependencias, del que
 * cuelgan todos los demas. Sin esta anotacion, ningun @Inject del proyecto
 * funciona.
 *
 * Hilt es la libreria de inyeccion de dependencias: en vez de que cada clase
 * construya lo que necesita (`val repo = AuthRepositoryImpl(...)`), lo declara
 * en su constructor y Hilt se lo entrega ya armado. Eso es lo que permite que
 * en los tests se pase un fake en su lugar sin tocar el codigo de produccion.
 *
 * Hay que declararla en el AndroidManifest con android:name=".CodiceApplication".
 */
@HiltAndroidApp
class CodiceApplication : Application()
