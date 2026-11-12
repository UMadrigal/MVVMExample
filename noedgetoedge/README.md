# noedgetoedge

Quita el edge-to-edge que Android 15+ impone a las apps con `targetSdk` 35 o superior, sin bajar
el `targetSdk`. Es un módulo de librería local: se añade como dependencia y no hay que escribir
nada en la app, porque se instala solo al arrancar.

Por defecto lo quita en toda la app. También puede hacer lo contrario: dejar la app con
edge-to-edge y quitarlo solo en las pantallas que elijas (ver "Los dos modos").

## Usarlo en otra app

1. Copia la carpeta `noedgetoedge/` a la raíz del proyecto.
2. En `settings.gradle.kts` añade:

   ```kotlin
   include(":noedgetoedge")
   ```

3. En `app/build.gradle.kts`, dentro de `dependencies`, añade:

   ```kotlin
   implementation(project(":noedgetoedge"))
   ```

4. Sincroniza Gradle. Listo.

## Qué hace y qué no

| Situación | Resultado |
| --- | --- |
| Android 15+ con `targetSdk` 35+ | Aparta el contenido de las barras y pinta su fondo. |
| Android 14 o inferior | No hace nada: ahí el sistema no fuerza edge-to-edge. |
| `targetSdk` 34 o inferior | No hace nada: el sistema tampoco lo fuerza. |

Cubre solo: todas las Activity (también las de librerías), todo lo que vive dentro de ellas
(Fragments, vistas, Compose), los `DialogFragment` de pantalla completa y los
`BottomSheetDialogFragment`.

## Los dos modos

El módulo trabaja en uno de dos modos. Se elige una sola vez para toda la app.

### Modo 1 (por defecto): sin edge-to-edge en toda la app

No hay que escribir nada. Para dejar una pantalla concreta con edge-to-edge:

```kotlin
// Pantalla tuya (Activity o DialogFragment)
class MapaActivity : AppCompatActivity(), KeepEdgeToEdge

// Pantalla de una librería: en Application.onCreate()
NoEdgeToEdge.exclude("com.libreria.SuActivity")
```

### Modo 2: edge-to-edge en toda la app, y quitarlo solo en algunas pantallas

1. Crea una clase `Application` (si no la tienes) y declárala en el manifest con
   `android:name=".MiApp"`.
2. Cambia el modo en su `onCreate()`:

   ```kotlin
   class MiApp : Application() {
       override fun onCreate() {
           super.onCreate()
           NoEdgeToEdge.removeFromAllScreens = false
       }
   }
   ```

3. Marca las pantallas a las que quieres quitarle edge-to-edge:

   ```kotlin
   // Pantalla tuya (Activity o DialogFragment)
   class FormularioActivity : AppCompatActivity(), RemoveEdgeToEdge

   // Pantalla de una librería: en Application.onCreate()
   NoEdgeToEdge.include("com.libreria.SuActivity")
   ```

En este modo el resto de la app es edge-to-edge normal: cada pantalla sin marcar maneja sus
insets como cualquier app moderna.

### Resumen

| Quiero... | Modo | Qué uso |
| --- | --- | --- |
| Quitarlo en todo | 1 | Nada |
| Quitarlo en todo menos en una pantalla mía | 1 | `KeepEdgeToEdge` |
| Quitarlo en todo menos en una pantalla de librería | 1 | `NoEdgeToEdge.exclude(...)` |
| Dejarlo en todo menos en una pantalla mía | 2 | `RemoveEdgeToEdge` |
| Dejarlo en todo menos en una pantalla de librería | 2 | `NoEdgeToEdge.include(...)` |

Si una pantalla queda marcada de las dos formas, gana la exclusión: se queda con edge-to-edge.

### Casos raros de los modos

- **Puse `removeFromAllScreens = false` y nada cambió.**
  - Qué se ve: toda la app sigue sin edge-to-edge.
  - Solución: la línea no se está ejecutando. Comprueba que tu clase `Application` está en el
    manifest (`android:name`) y que la asignación está en su `onCreate()`.
- **Cambié el modo dentro de una Activity.**
  - Qué se ve: las pantallas ya abiertas no cambian; solo las que se creen después. El resultado
    depende del orden en que se abran, y tras una rotación puede cambiar.
  - Solución: asígnalo solo en `Application.onCreate()`.
- **Implementé `RemoveEdgeToEdge` pero sigo en el modo 1.**
  - Qué se ve: nada distinto; en el modo 1 ya se quita en todas.
  - Solución: ninguna necesaria. La interfaz solo importa en el modo 2.
- **Implementé `KeepEdgeToEdge` en el modo 2.**
  - Qué se ve: nada distinto; en el modo 2 las pantallas sin marcar ya son edge-to-edge.
- **Modo 2: marqué una Activity pero sus `DialogFragment` de pantalla completa o bottom sheets
  siguen siendo edge-to-edge.**
  - Qué se ve: la Activity tiene franjas y el diálogo se mete detrás de las barras.
  - Solución: el marcado es por clase. Implementa `RemoveEdgeToEdge` también en el diálogo.
- **Modo 2: marqué un Fragment normal (no diálogo).**
  - Qué se ve: nada; la interfaz se ignora. Un Fragment no tiene ventana propia, comparte la de
    su Activity.
  - Solución: marca la Activity que lo contiene. No se puede quitar edge-to-edge a un Fragment
    suelto dentro de una Activity que sí lo tiene.
- **Modo 2 con una sola Activity y navegación por Fragments o Compose.**
  - Qué se ve: es todo o nada para esa Activity; no hay forma de quitarlo solo en un destino.
  - Solución: en ese destino aplica los insets a mano, o muévelo a su propia Activity.
- **Modo 2: una pantalla marcada llama a `enableEdgeToEdge()`.**
  - Qué se ve: en Android 15+ casi nada; en Android 14 o inferior esa pantalla queda
    edge-to-edge aunque esté marcada, porque ahí el módulo no actúa.
  - Solución: no llames a `enableEdgeToEdge()` en las pantallas marcadas. En las no marcadas sí
    puedes (y debes, si quieres edge-to-edge también en Android 14 o inferior).
- **Modo 2: al pasar de una pantalla edge-to-edge a una marcada, la barra de estado cambia de
  aspecto.**
  - Qué se ve: en una pantalla el contenido pasa por detrás de la barra y en la siguiente hay una
    franja de color. Es el efecto esperado de mezclar los dos estilos.
  - Solución: iguala `android:statusBarColor` al color de fondo de tus pantallas para que el
    cambio no se note.
- **Nombre mal escrito en `include(...)` o `exclude(...)`.**
  - Qué se ve: no pasa nada y no hay ningún error; la pantalla se comporta según el modo.
  - Solución: usa el nombre completo con paquete. Para una clase tuya,
    `MiActivity::class.java.name` evita erratas.

## Estado de las pruebas

Probado en un Galaxy S23 Ultra con Android 17, navegación por gestos y modo oscuro: pantalla
normal, teclado (`adjustResize`, `adjustPan`, automático), `FLAG_FULLSCREEN`, modo inmersivo,
diálogos, bottom sheet, ActionBar nativa, horizontal, cambio de colores por código, animación del
teclado, exclusión de una pantalla con `KeepEdgeToEdge`, y el modo 2 completo
(`removeFromAllScreens = false` con pantalla sin marcar, con `RemoveEdgeToEdge`, con
`include(...)` y con las dos interfaces a la vez).

Sin probar en dispositivo: navegación de 3 botones, Android 15 y 16, Compose, tablets,
plegables, pantalla dividida, Samsung DeX, picture-in-picture, transiciones con elementos
compartidos y apps con varios procesos. Si algo se ve raro, empieza sospechando de estos casos.

## Limitaciones

- **No es una API oficial.** Imita lo que hacía el sistema; no lo reactiva. Google no ofrece
  ninguna forma soportada de quitar edge-to-edge con `targetSdk` 36 o superior.
- **Solo ve ventanas de Activity y de `DialogFragment`.** No ve un `Dialog` creado a mano, los
  `Dialog` y `ModalBottomSheet` de Compose, ventanas añadidas con `WindowManager.addView`
  (overlays, burbujas), `Presentation` en una segunda pantalla ni un `InputMethodService`.
- **El marcado es por clase y por ventana, no por momento.** Una pantalla se incluye o excluye
  entera; no se puede activar y desactivar el módulo mientras está abierta, ni aplicarlo a un
  Fragment o a un destino de Compose sueltos dentro de una Activity.
- **El modo 2 necesita una clase `Application`.** El modo 1 no necesita ningún código.
- **No corrige `enableEdgeToEdge()` en Android 14 o inferior.** Ahí el módulo no actúa, así que
  esa llamada sigue activando edge-to-edge.
- **El fondo de las barras es un color liso.** No admite degradados, imágenes ni que el contenido
  se vea por detrás con transparencia.
- **Los colores se leen una vez,** al crear la Activity. Si el tema cambia sin recrearla, no se
  actualizan solos.
- **No pinta `android:navigationBarDividerColor`.** La línea divisoria sobre la barra de
  navegación no aparece.
- **El color de la rayita de gestos lo decide el sistema.** No se puede elegir.
- **La franja del notch siempre es negra.** No es configurable.
- **La animación del teclado recalcula el layout en cada fotograma.** En pantallas muy pesadas
  (listas enormes sin reciclar, layouts muy anidados) puede notarse algún tirón al abrir o cerrar
  el teclado.
- **Con `adjustPan` la animación es la del sistema,** no la del módulo.
- **Añade `androidx.fragment` a la app.** En apps con AppCompat ya estaba; en una app solo de
  Compose es una dependencia nueva (pequeña).
- **Hay que llamarlo desde el hilo principal.** `applyTo` y los `set...Color` tocan vistas.
- **Cobertura de pruebas limitada.** Un solo teléfono y una sola versión de Android; ver "Estado
  de las pruebas".

## Qué NO hacer

Cada punto dice qué se ve si lo haces y cómo arreglarlo.

- **Llamar a `enableEdgeToEdge()`.** El asistente de Android Studio lo pone en cada Activity nueva
  junto con un `ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main))`.
  - Qué se ve: en Android 15+ casi nada, salvo que los iconos de la barra de estado pueden cambiar
    de color y perder contraste. En Android 14 o inferior el fondo de tu layout se mete detrás de
    las barras: la app se ve distinta según el teléfono.
  - Solución: borra las dos cosas.
- **Llamar a `WindowCompat.setDecorFitsSystemWindows(window, false)`.**
  - Qué se ve: en Android 14 o inferior, el contenido queda detrás de las barras. En Android 15+
    puede seguir habiendo hueco para las barras aunque estén ocultas en modo inmersivo.
  - Solución: quita la llamada.
- **Usar `window.statusBarColor = ...` o `window.navigationBarColor = ...`.**
  - Qué se ve: en Android 15+ el color no cambia; en Android 14 o inferior sí. Otra vez la app se
    ve distinta según el teléfono.
  - Solución: `NoEdgeToEdge.setStatusBarColor(window, color)` y
    `NoEdgeToEdge.setNavigationBarColor(window, color)`. Funcionan en todas las versiones.
- **Llamar a `NoEdgeToEdge.applyTo(window)` en una Activity.**
  - Qué se ve: nada si lo llamas tarde (ya estaba aplicado). Si lo llamas en `onCreate` antes de
    `setContentView`, un `requestWindowFeature()` posterior lanza
    `AndroidRuntimeException: requestFeature() must be called before adding content`.
  - Solución: no lo llames; en Activities es automático.
- **Vaciar el `DecorView`** (`(window.decorView as ViewGroup).removeAllViews()` o parecido).
  - Qué se ve: las barras pierden su color y muestran el `windowBackground`; además desaparece
    todo el contenido de la pantalla.
  - Solución: no manipules los hijos del `DecorView`.
- **Poner un `setOnApplyWindowInsetsListener` en la vista padre de `android.R.id.content`.**
  - Qué se ve: el contenido vuelve a meterse detrás de las barras en esa pantalla, y las franjas
    de color quedan con un tamaño viejo o desaparecen.
  - Solución: pon tu listener en tu propio layout, no en esa vista.
- **Subir la versión de `androidx.fragment` dentro del módulo.**
  - Qué se ve: nada en pantalla, pero la app pasa a usar una versión de fragment más nueva sin
    que lo hayas decidido, lo que puede cambiar comportamientos o pedir un `compileSdk` mayor.
  - Solución: déjala en 1.3.6 e ignora el aviso de lint.

## Colores de las barras

Salen del tema, como antes de edge-to-edge:

```xml
<item name="android:statusBarColor">@color/mi_color</item>
<item name="android:windowLightStatusBar">false</item> <!-- true = iconos oscuros -->
<item name="android:navigationBarColor">@color/mi_color</item>
<item name="android:windowLightNavigationBar">false</item>
```

- **El tema Material3 no define `android:statusBarColor`.**
  - Qué se ve: barra de estado morada (modo claro) o lila (modo oscuro), que es `colorPrimary`.
  - Solución: define el atributo en `themes.xml`.
- **Fondo de barra y color de iconos mal combinados.**
  - Qué se ve: la hora y los iconos de la barra de estado casi no se distinguen (blanco sobre
    claro u oscuro sobre oscuro).
  - Solución: `android:windowLightStatusBar` en `true` para fondo claro, `false` para oscuro.
- **El tema no define `android:navigationBarColor`.**
  - Qué se ve: franja negra abajo, detrás de la rayita de gestos o de los 3 botones.
  - Solución: define el atributo.
- **Color totalmente transparente (`#00000000`).**
  - Qué se ve: detrás de la barra aparece el `windowBackground` del tema (normalmente blanco o
    casi negro), no tu layout.
  - Solución: es el comportamiento esperado. Si quieres otro color, ponlo opaco.
- **Color semitransparente.**
  - Qué se ve: un tono mezclado con el `windowBackground`, más apagado de lo que esperabas. Tu
    contenido no se transparenta.
  - Solución: usa un color opaco con el tono final que quieras.
- **Una Activity necesita otro color.**
  - Qué se ve: si no haces nada, usa el mismo color que el resto.
  - Solución: dale su propio tema en el manifest, o llama a los `NoEdgeToEdge.set...Color` en su
    `onCreate`.
- **Cambio de modo claro/oscuro con `android:configChanges="uiMode"`.**
  - Qué se ve: el contenido cambia de modo pero las barras conservan el color anterior.
  - Solución: en `onConfigurationChanged`, llama a los `NoEdgeToEdge.set...Color`. Sin ese
    `configChanges` la Activity se recrea y no pasa nada.

## Teclado

- **`adjustResize`.**
  - Qué se ve: el contenido se encoge acompañando al teclado mientras sube, y lo de abajo queda
    justo encima. Al cerrarlo, crece acompañándolo. Correcto.
- **`adjustPan`.**
  - Qué se ve: la pantalla sube entera, la parte de arriba sale de la vista y el fondo de la barra
    de estado se queda en su sitio. Correcto.
- **Sin `windowSoftInputMode`.**
  - Qué se ve: se comporta como `adjustResize` si la pantalla tiene una vista con scroll y como
    `adjustPan` si no. Dos pantallas de la misma app pueden comportarse distinto.
  - Solución: si quieres que sea siempre igual, declara el modo en el manifest.
- **`adjustNothing`.**
  - Qué se ve: el teclado tapa el campo de texto y lo que haya abajo.
  - Solución: usa `adjustResize`. Así era también antes de edge-to-edge.
- **Pantalla pesada con `adjustResize`.**
  - Qué se ve: tirones mientras el teclado sube o baja, porque el layout se recalcula en cada
    fotograma.
  - Solución: aligera el layout de esa pantalla (menos anidamiento, `RecyclerView` en vez de
    listas completas).
- **Código propio que escucha `WindowInsetsAnimation` en una vista hija.**
  - Qué se ve: su callback nunca se llama; el módulo corta la animación en la vista raíz.
  - Solución: no hace falta animar nada a mano; el contenido ya acompaña al teclado.
- **`Modifier.imePadding()` o `safeDrawingPadding()` en Compose.**
  - Qué se ve: no añaden espacio. Con `adjustResize` todo se ve bien igualmente; con
    `adjustNothing` el teclado tapa el contenido aunque tengas `imePadding()`.
  - Solución: usa `adjustResize` en el manifest. Los modificadores pueden quedarse o quitarse.
- **Teclado en horizontal.**
  - Qué se ve: el teclado ocupa toda la pantalla con su propio campo de texto. Lo hace el sistema,
    no el módulo.

## Layouts y código de insets que ya tenías

- **`android:fitsSystemWindows="true"` en un layout.**
  - Qué se ve: nada distinto; el atributo deja de tener efecto.
  - Solución: puedes borrarlo.
- **Un `setOnApplyWindowInsetsListener` propio que solo aplicaba padding.**
  - Qué se ve: nada distinto; el listener nunca se ejecuta.
  - Solución: bórralo.
- **Un listener propio que hacía algo más** (saber si el teclado está abierto, mover un botón).
  - Qué se ve: esa lógica deja de funcionar: el botón no se mueve, el estado no se actualiza.
  - Solución: lee `ViewCompat.getRootWindowInsets(view)`, que sigue dando los valores reales.
- **`CoordinatorLayout`, `DrawerLayout` o `AppBarLayout` que dibujaban detrás de la barra de
  estado.**
  - Qué se ve: la toolbar o el cajón lateral empiezan debajo de la barra de estado en vez de
    pasar por detrás.
  - Solución: ninguna; es justo lo que significa quitar edge-to-edge.
- **Una imagen de cabecera pensada para llegar hasta arriba del todo.**
  - Qué se ve: la imagen empieza debajo de la barra de estado, con la franja de color encima.
  - Solución: iguala `android:statusBarColor` al tono de la imagen, o trata esa pantalla como
    "pantalla especial".

## Pantallas especiales

- **Vídeo, foto o juego a pantalla completa.**
  - Qué se ve: si no haces nada, las barras siguen ahí con su franja.
  - Solución: `WindowInsetsControllerCompat(window, view).hide(WindowInsetsCompat.Type.systemBars())`.
    El contenido llega hasta los bordes.
- **Modo inmersivo o `FLAG_FULLSCREEN` en vertical.**
  - Qué se ve: una franja negra arriba, a la altura de la cámara frontal.
  - Solución: ninguna; es el notch y antes también quedaba negro.
- **Horizontal.**
  - Qué se ve: una franja negra en el lado donde queda la cámara, de arriba abajo.
  - Solución: ninguna; mismo motivo.
- **Una pantalla debe dibujar detrás de las barras sin ocultarlas** (un mapa, un splash propio).
  - Qué se ve: si no haces nada, el contenido queda entre las dos franjas.
  - Solución: implementa `KeepEdgeToEdge` en esa Activity o `DialogFragment`. El módulo la deja
    intacta: vuelve a ser edge-to-edge y sus insets se manejan a mano, como en cualquier app
    moderna.

    ```kotlin
    class MapaActivity : AppCompatActivity(), KeepEdgeToEdge
    ```
- **Excluí una pantalla y ahora su contenido queda debajo de la hora o de la rayita de gestos.**
  - Qué se ve: texto o botones tapados por las barras del sistema.
  - Solución: es lo esperado al excluirla. Aplica tú los insets en esa pantalla
    (`ViewCompat.setOnApplyWindowInsetsListener`, o `safeDrawingPadding()` en Compose).
- **Excluí una Activity pero sus `DialogFragment` de pantalla completa siguen con franjas.**
  - Qué se ve: la Activity es edge-to-edge y el diálogo no.
  - Solución: la exclusión es por clase. Implementa `KeepEdgeToEdge` también en el diálogo.
- **Activity con tema de diálogo o ventana flotante.**
  - Qué se ve: se muestra como siempre, centrada y sin franjas. Se ignora a propósito.
- **Pantalla dividida, ventana flotante, plegable, DeX.**
  - Qué se ve: debería verse igual pero con las barras que correspondan a esa ventana. Sin probar.
  - Solución: si falla, revisa la clase `FitSystemWindows` en `NoEdgeToEdge.kt`.

## Diálogos y otras ventanas

- **`AlertDialog`, `Toast`, `Snackbar`, `PopupWindow`, menús.**
  - Qué se ve: igual que siempre. No les afecta.
- **`DialogFragment` de pantalla completa.**
  - Qué se ve: como una Activity: franja de estado arriba, de navegación abajo. Automático.
- **`BottomSheetDialogFragment`.**
  - Qué se ve: la hoja termina encima de la barra de navegación, no detrás; la barra de estado
    queda oscurecida por el fondo del diálogo. Automático.
- **`Dialog` de pantalla completa creado a mano, sin `DialogFragment`.**
  - Qué se ve: su contenido se mete detrás de la barra de estado y de la de navegación.
  - Solución: es el único caso manual. Llamarlo dos veces no hace daño.

    ```kotlin
    dialog.show()
    NoEdgeToEdge.applyTo(dialog.window!!)
    ```
- **`Dialog` y `ModalBottomSheet` de Compose.**
  - Qué se ve: sin probar. Si falla, su contenido quedará detrás de la barra de navegación.
  - Solución: maneja sus insets dentro del propio composable (`windowInsets` del componente o
    `Modifier.navigationBarsPadding()`).
- **Overlays con `WindowManager.addView`.**
  - Qué se ve: el módulo no los toca; se dibujan donde tú los pongas.

## Librerías de terceros

- **Activities de librerías** (anuncios, selector de fotos, escáner, login).
  - Qué se ve: también quedan entre las franjas de color, con los colores del tema de esa
    Activity. Si la librería contaba con dibujar detrás de las barras, puede verse una franja de
    un color que no pega con su diseño.
  - Solución: normalmente ninguna hace falta; es lo que pasaba con un `targetSdk` antiguo.
- **Quiero excluir una Activity concreta.**
  - Qué se ve: sin excluirla, sigue con franjas.
  - Solución: como no puedes modificar su código, exclúyela por nombre de clase en
    `Application.onCreate()`:

    ```kotlin
    NoEdgeToEdge.exclude("com.libreria.SuActivity")
    ```
- **Llamé a `NoEdgeToEdge.exclude(...)` y no hizo nada.**
  - Qué se ve: la pantalla sigue con franjas.
  - Solución: el nombre debe ser el completo, con paquete, y la llamada debe ocurrir antes de
    abrir la pantalla. Una pantalla ya abierta no cambia hasta que se vuelve a crear.

## Problemas de compilación o de arranque

- **`minSdk` del módulo mayor que el de la app.**
  - Qué se ve: error de Gradle `Manifest merger failed : uses-sdk:minSdkVersion ... cannot be
    smaller than version ... declared in library`.
  - Solución: baja el `minSdk` en `noedgetoedge/build.gradle.kts`.
- **`compileSdk` del módulo no instalado o menor de 30.**
  - Qué se ve: Gradle pide descargar una plataforma que no tienes, o errores `Unresolved
    reference` en `WindowInsets.Type` y `WindowInsets.CONSUMED`.
  - Solución: pon el mismo `compileSdk` que la app.
- **AGP 8 o anterior.**
  - Qué se ve: `Unresolved reference: NoEdgeToEdgeInitializer` o la app arranca con
    `ClassNotFoundException` porque los `.kt` no se compilaron.
  - Solución: añade el plugin `org.jetbrains.kotlin.android` al módulo. AGP 9 ya trae Kotlin.
- **Olvidaste el `include` o el `implementation`.**
  - Qué se ve: sin `include`, error `Project with path ':noedgetoedge' could not be found`. Sin
    `implementation`, compila pero la app sigue edge-to-edge.
  - Solución: añade la línea que falte.
- **Renombrar el paquete a medias.**
  - Qué se ve: errores de compilación, o `ClassNotFoundException` del provider al arrancar.
  - Solución: cambia a la vez el `namespace`, el `package` de los dos `.kt` y las carpetas.
- **Alguien quitó el `<provider>`** (por ejemplo con `tools:node="remove"`).
  - Qué se ve: compila y arranca, pero la app vuelve a ser edge-to-edge en todas las pantallas.
  - Solución: llama a `NoEdgeToEdge.install(this)` en `Application.onCreate()`. Llamarlo además
    del arranque automático no hace daño.
- **Dos apps tuyas distintas, ambas con el módulo, instaladas en el mismo teléfono.**
  - Qué se ve: nada; conviven sin problema. Android exige que la `authority` de cada
    `ContentProvider` sea única en todo el teléfono, y la del módulo se forma con el
    `applicationId` de cada app. Si fuera un texto fijo, la segunda app no se podría instalar
    (`INSTALL_FAILED_CONFLICTING_PROVIDER`).
- **R8 / ProGuard.**
  - Qué se ve: nada; no hace falta ninguna regla.
- **Layout Inspector o tests de UI.**
  - Qué se ve: una vista `SystemBarsView` que no es tuya, del tamaño de la pantalla.
  - Solución: ignórala. No recibe toques ni foco de accesibilidad.

## Qué podría fallar en el futuro

- **Cambia `WindowInsets.getSystemWindowInsets()`.** El módulo usa ese método obsoleto porque es
  el cálculo que el sistema aplica a las apps antiguas.
  - Qué se vería: huecos de tamaño incorrecto, o el teclado tapando el contenido.
  - Dónde mirar: la clase `FitSystemWindows` en `NoEdgeToEdge.kt`.
- **Cambia la estructura del `DecorView`** (por un fabricante o una versión nueva).
  - Qué se vería: la app vuelve a ser edge-to-edge, sin crashear, porque el módulo no encuentra
    la vista y no hace nada.
  - Dónde mirar: `contentRootOf` en `NoEdgeToEdge.kt`.
- **Navegación de 3 botones.** El sistema dibuja además su propia franja.
  - Qué se vería si falla: la barra de navegación con un tono algo distinto al del tema, o los
    botones con poco contraste.
  - Dónde mirar: `syncSystemNavigationBar` en `NoEdgeToEdge.kt`.
- **Google cambia las reglas otra vez** (por ejemplo, forzando el contenido detrás de las barras
  de otra manera).
  - Qué se vería: imposible saberlo de antemano. Vuelve a probar la app al subir `targetSdk` o
    cuando salga una versión nueva de Android.

## Licencia

MIT. Ver el archivo `LICENSE` de esta carpeta: cualquiera puede usar, copiar y modificar el
módulo, también en apps comerciales, siempre que conserve el aviso de copyright.
