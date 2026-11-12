# noedgetoedge: cómo está hecho y por qué

Documento técnico para quien vaya a mantener o modificar el módulo (personas o IAs). El uso
diario y los casos raros están en `README.md`; aquí está el razonamiento.

## 1. El problema

Desde Android 15 (API 35), una app con `targetSdk >= 35` es edge-to-edge a la fuerza:

- El sistema deja de apartar el contenido de las barras de estado y de navegación.
- `android:statusBarColor` y `window.statusBarColor` dejan de tener efecto (la barra es
  transparente). `navigationBarColor` solo sobrevive con navegación de 3 botones.
- El recorte de pantalla (notch) pasa a modo "siempre dibujar debajo".
- Con `adjustResize` el sistema ya no encoge la ventana para el teclado; solo entrega insets.

La salida oficial, `android:windowOptOutEdgeToEdgeEnforcement`, solo funciona con `targetSdk 35`.
Con `targetSdk >= 36` está desactivada. El proyecto donde nació este módulo usa `targetSdk 37`,
así que no había salida oficial.

Requisitos del dueño del proyecto: no bajar `targetSdk`, que aplique a toda la app sin repetir
código por pantalla, que sea reutilizable en otras apps, y que se comporte como antes de
edge-to-edge.

## 2. De dónde sale la solución

Todo se dedujo leyendo el código fuente de la plataforma (API 37, en
`<SDK>/sources/android-37.0`). Las piezas relevantes:

- `com/android/internal/policy/PhoneWindow.java`
  - `isEdgeToEdgeEnforced(...)`: decide si la ventana queda forzada.
  - Con forzado activo: `mDecorFitsSystemWindows = false`, `mStatusBarColor = TRANSPARENT`,
    `setStatusBarColor()` y `setDecorFitsSystemWindows()` retornan sin hacer nada.
  - `sDefaultContentInsetsApplier`: lo que hacía el sistema cuando NO era edge-to-edge:

    ```java
    Insets insetsToApply = insets.getSystemWindowInsets();
    return new Pair<>(insetsToApply, insets.inset(insetsToApply).consumeSystemWindowInsets());
    ```

    Ese resultado se aplicaba como padding a `mContentRoot`, la vista hija directa del
    `DecorView` (el `LinearLayout` de `screen_simple.xml`), y los insets se consumían.
- `android/view/InsetsState.java`, `calculateInsets(...)`: define qué incluye
  `getSystemWindowInsets()` (los "compat insets"): barras del sistema + notch, **más el teclado
  solo si la ventana es `adjustResize`** (ya resuelto por `ViewRootImpl` cuando el modo es
  automático), **menos la barra de estado si hay `FLAG_FULLSCREEN`**.
- `com/android/internal/policy/DecorView.java`
  - Dibujaba el fondo de las barras con vistas hijas del `DecorView` ("color views").
  - `updateColorViewTranslations()`: con `adjustPan`, `ViewRootImpl` desplaza toda la ventana;
    el `DecorView` contrarresta ese desplazamiento con `translationY` para que el fondo de la
    barra de estado no se mueva.
  - `calculateBarColor(...)`: con forzado y 3 botones, el sistema pinta
    `navigationBarColor` al 80 % de opacidad.
- `data/res/values/colors.xml`: `navigation_bar_default = #00808080` es el valor centinela de
  "el tema no definió color"; en ese caso las apps antiguas usaban negro.

Conclusión: no hace falta inventar un comportamiento. Basta con volver a ejecutar, desde la app,
exactamente lo que el sistema dejó de ejecutar: mismo cálculo, misma vista.

## 3. Arquitectura

Módulo de librería Android (`com.android.library`), tres archivos de código:

```
noedgetoedge/
  build.gradle.kts
  src/main/AndroidManifest.xml               <provider> de arranque
  src/main/java/com/macruware/noedgetoedge/
    NoEdgeToEdgeInitializer.kt               ContentProvider vacío que arranca el módulo
    NoEdgeToEdge.kt                          toda la lógica + interfaces KeepEdgeToEdge
                                             y RemoveEdgeToEdge
```

Flujo en tiempo de ejecución:

1. Arranca el proceso. Android crea los `ContentProvider` antes de `Application.onCreate()`.
   `NoEdgeToEdgeInitializer.onCreate()` llama a `NoEdgeToEdge.install(app)`.
2. `install` no hace nada si `SDK_INT < 35` o `targetSdk < 35`. Si no, registra
   `ActivityHook` (`Application.ActivityLifecycleCallbacks`).
3. `ActivityHook.onActivityCreated`: si es `FragmentActivity`, registra `DialogHook`
   (`FragmentLifecycleCallbacks`, recursivo).
4. `ActivityHook.onActivityPostCreated`: si `shouldRemoveFrom(activity)` (ver 4.11),
   `applyTo(window)`.
5. `DialogHook.onFragmentStarted`: si es un `DialogFragment` y `shouldRemoveFrom(fragment)`,
   `applyTo` sobre la ventana de su diálogo.
6. `applyTo(window)`:
   - sale si la ventana es flotante (`window.isFloating`);
   - localiza la vista raíz de contenido (`contentRootOf`);
   - sale si ya hay una `SystemBarsView` en el `DecorView` (idempotencia);
   - añade una `SystemBarsView` al `DecorView`;
   - instala `FitSystemWindows` en la vista raíz como listener de insets, callback de animación
     de insets y listener de attach;
   - pide un nuevo reparto de insets.

## 4. Decisiones, una por una

### 4.1 Arranque con ContentProvider
- Qué: `<provider>` en el manifest del módulo, que se fusiona con el de la app.
- Por qué: cero código en la app. Es el mismo truco que usan `androidx.startup` y Firebase.
- `android:authorities="${applicationId}.noedgetoedge-init"`: la autoridad debe ser única en el
  dispositivo; con el `applicationId` dos apps distintas pueden llevar el módulo.
- `android:multiprocess="true"`: para que también se instale en procesos secundarios de la app.
  No probado en dispositivo.

### 4.2 Hook en `onActivityPostCreated`, no en `onActivityCreated`
- `onActivityCreated` se ejecuta dentro de `super.onCreate()`, antes de `setContentView`. Tocar
  ahí el `DecorView` (o llamar a `findViewById`) crea la ventana antes de tiempo; un
  `requestWindowFeature()` posterior lanza excepción, y en AppCompat se crea el sub-decor antes
  de que la Activity configure nada.
- `onActivityPostCreated` (API 29+) se ejecuta al terminar todo el `onCreate` de la Activity.
  Como el módulo solo actúa en API 35+, siempre existe.

### 4.3 Sobre qué vista se aplica
- `contentRootOf`: sube desde `android.R.id.content` hasta la vista cuyo padre es el
  `DecorView`. Es `mContentRoot`, la misma que usaba el sistema.
- Por qué no `android.R.id.content`: en AppCompat ese id pasa a un `ContentFrameLayout`
  interno, y la ActionBar queda fuera. Con la vista raíz, ActionBar y contenido se apartan
  juntos.
- Por qué no el propio `DecorView`: su `onApplyWindowInsets` hace trabajo interno; un listener
  ahí lo sustituiría.

### 4.4 El cálculo: `insets.systemWindowInsets` (obsoleto a propósito)
- Es literalmente la fórmula del sistema. Da gratis: teclado solo con `adjustResize` (incluido
  el modo automático), `FLAG_FULLSCREEN`, modo inmersivo y notch.
- Reimplementarlo con `getInsets(Type...)` no puede saber qué modo de teclado resolvió
  `ViewRootImpl` cuando el manifest no lo declara.
- El listener devuelve `WindowInsets.CONSUMED`: las vistas hijas no reciben insets, igual que
  antes. Por eso un listener o `fitsSystemWindows` en los layouts de la app queda sin efecto y
  no hay doble padding.
- Se respeta la misma excepción que el sistema: si la ventana tiene
  `SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN` o `SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION`, no se aplica
  padding y los insets pasan sin consumir.

### 4.5 Fondo de las barras: `SystemBarsView`
- Una `View` del tamaño del `DecorView`, hermana del contenido, que solo pinta rectángulos en
  los huecos: arriba `statusBarColor`, abajo `navigationBarColor`.
- Hueco que no corresponde a una barra = notch: se pinta negro (antes esa zona quedaba fuera
  de la ventana).
- Abajo se pinta `min(hueco, barra de navegación)`: si el hueco es del teclado, no se pinta.
- Colores: `android:statusBarColor` / `android:navigationBarColor` leídos del tema de la
  ventana; el centinela `#00808080` se convierte en negro.
- Primera versión descartada: un `Drawable` como fondo de la vista raíz. Con `adjustPan` se
  desplazaba con el contenido y la barra de estado quedaba sin fondo. Por eso es una vista
  aparte con un `ViewTreeObserver.OnDrawListener` que lee el desplazamiento de la ventana
  (`parent.getLocationInWindow`, que resta `ViewRootImpl.mCurScrollY`) y lo contrarresta con
  `translationY`. Es lo mismo que hace `DecorView.updateColorViewTranslations()`.
- No recibe toques (una `View` sin listeners no los consume) y está marcada como no importante
  para accesibilidad.

### 4.6 Barra de navegación con 3 botones
- `syncSystemNavigationBar` llama a `window.navigationBarColor = color` cuando el color es
  opaco. Así el sistema elige el contraste de los botones como antes; su franja al 80 % queda
  encima de la del módulo al 100 %, y el resultado es el color exacto.
- Verificado solo contra el código fuente, no en dispositivo.

### 4.7 Colores por código
- `setStatusBarColor` / `setNavigationBarColor` guardan el color en un
  `WeakHashMap<Window, ColorOverrides>` (solo enteros, no retiene nada) y actualizan la
  `SystemBarsView` si ya existe. También llaman al setter real de la ventana, que sigue
  funcionando en Android 14 o inferior.
- Se pueden llamar antes de que el módulo se aplique (por ejemplo en `onCreate`); el color
  queda pendiente y se usa al aplicar.

### 4.8 Sin fugas de memoria
- No hay mapas con vistas como valor. La `SystemBarsView` se localiza recorriendo los hijos del
  `DecorView`. Una versión anterior usaba `WeakHashMap<View, ...>` cuyo valor acababa
  referenciando la clave: eso impide que se libere y filtraría cada Activity.

### 4.9 Diálogos
- Los diálogos flotantes no son edge-to-edge: se ignoran (`window.isFloating`).
- `DialogFragment` de pantalla completa y `BottomSheetDialogFragment` tienen ventana propia no
  flotante: se cubren con `FragmentLifecycleCallbacks.onFragmentStarted`, cuando el diálogo ya
  se mostró y su `DecorView` existe.
- Un `Dialog` creado a mano no se puede detectar desde fuera: para eso `applyTo` es público.
- Dependencia `androidx.fragment:fragment:1.3.6`: es la versión que ya trae appcompat 1.6.1,
  para no subirle la versión a ninguna app.

### 4.10 Animación suave del teclado (añadido después)
- El comportamiento antiguo era un salto. El dueño pidió que el contenido acompañe al teclado.
- `FitSystemWindows` es además un `WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP)`:
  - `onPrepare`: si la animación es del teclado, incrementa `imeAnimations`.
  - `onApplyWindowInsets`: al empezar la animación el sistema entrega ya los insets finales;
    si `imeAnimations > 0` no se aplican (ese sería el salto).
  - `onProgress`: aplica el padding con los insets de ese fotograma. El sistema los calcula con
    el mismo modo de teclado (`InsetsController` usa `mLegacySoftInputMode`), así que con
    `adjustPan` el teclado no entra en el cálculo y nada cambia.
  - `onEnd`: al terminar la última animación, aplica `root.rootWindowInsets` (estado final).
  - `onViewDetachedFromWindow`: pone `imeAnimations = 0` por si `onEnd` no llega.
- Coste: un layout por fotograma mientras dura la animación.
- Medido en dispositivo: la altura del contenido pasó por unos 40 valores intermedios al abrir
  y 15 al cerrar, en vez de uno solo.

### 4.11 Dos modos y selección de pantallas (añadido después)

El dueño pidió primero poder excluir una pantalla, y después lo contrario: que la app pueda
quedarse edge-to-edge y el módulo actúe solo en pantallas elegidas. Se resolvió con un único
punto de decisión, `shouldRemoveFrom(screen)`, en vez de dos mecanismos separados.

API pública:

| Elemento | Para qué |
| --- | --- |
| `NoEdgeToEdge.removeFromAllScreens` (`Boolean`, por defecto `true`) | Modo. `true` = quitar en todas (modo 1). `false` = quitar solo en las marcadas (modo 2). |
| `interface KeepEdgeToEdge` | Modo 1: esta Activity o DialogFragment propia conserva edge-to-edge. |
| `NoEdgeToEdge.exclude(vararg nombres)` | Igual, por nombre de clase, para pantallas de librerías. |
| `interface RemoveEdgeToEdge` | Modo 2: quitar edge-to-edge solo en esta Activity o DialogFragment propia. |
| `NoEdgeToEdge.include(vararg nombres)` | Igual, por nombre de clase, para pantallas de librerías. |

Regla exacta:

```kotlin
private fun shouldRemoveFrom(screen: Any): Boolean {
    val name = screen.javaClass.name
    if (screen is KeepEdgeToEdge || name in excluded) return false   // excluir siempre gana
    return removeFromAllScreens || screen is RemoveEdgeToEdge || name in included
}
```

Decisiones:

- **El modo es una propiedad, no un parámetro de `install`.** `install` lo llama el
  `ContentProvider` antes de `Application.onCreate()`, cuando la app aún no ha podido opinar.
  Por eso `install` registra siempre los hooks y el modo se consulta en cada pantalla, en el
  momento de crearla. La app lo asigna en `Application.onCreate()`, que corre antes que
  cualquier Activity.
- **`@Volatile`** porque podría asignarse desde otro hilo; los hooks leen en el hilo principal.
- **Por defecto `true`** para que el caso original (quitarlo en todo) siga sin necesitar código.
- **Excluir gana a incluir.** Si una clase queda marcada de las dos formas, lo conservador es no
  tocarla.
- **Nombres de clase como `String`** en `exclude`/`include` para no obligar a que el módulo
  tenga la clase de la librería en su classpath.
- **Se decide en los hooks, no en `applyTo`.** `applyTo(window)` sigue siendo una llamada manual
  explícita que siempre actúa, sea cual sea el modo.
- **El marcado es por clase y por ventana.** Marcar una Activity no marca sus `DialogFragment`
  (cada uno se evalúa por su propia clase). Un Fragment normal no tiene ventana: marcarlo no
  hace nada. No es posible quitar edge-to-edge a un Fragment o a un destino de Compose dentro
  de una Activity que lo conserva.
- **En el modo 2, en Android 14 o inferior, el módulo sigue sin actuar.** Una pantalla marcada
  solo es edge-to-edge ahí si ella misma llama a `enableEdgeToEdge()`; no debe llamarlo.

## 5. Lo que se cambió en la app de ejemplo

- `settings.gradle.kts`: `include(":noedgetoedge")`.
- `app/build.gradle.kts`: `implementation(project(":noedgetoedge"))`.
- `MainActivity.kt`: se quitaron `enableEdgeToEdge()` y el
  `ViewCompat.setOnApplyWindowInsetsListener` que genera el asistente de Android Studio.

## 6. Verificación realizada

Dispositivo: Galaxy S23 Ultra, Android 17 (API 37), navegación por gestos, modo oscuro. Se usaron
Activities de prueba en `app/src/debug`, ya borradas, lanzadas con `adb shell am start` y
revisadas con capturas de `adb exec-out screencap`.

Comprobado: pantalla normal; `adjustResize`; `adjustPan` (fondo de barra fijo); modo automático
con vista de scroll; `FLAG_FULLSCREEN`; modo inmersivo; colores por código; `DialogFragment` de
pantalla completa con teclado; `BottomSheetDialogFragment`; `AlertDialog`; Activity con ActionBar
nativa (`Theme.Material`); horizontal invertido con notch; animación del teclado (por registro de
alturas); `KeepEdgeToEdge`; modo 2 (`removeFromAllScreens = false`) con una Activity sin marcar
(queda edge-to-edge), con `RemoveEdgeToEdge` (se quita), incluida por nombre con `include(...)`
(se quita) y con `RemoveEdgeToEdge` + `KeepEdgeToEdge` a la vez (queda edge-to-edge); y que el
modo por defecto sigue quitándolo tras reiniciar el proceso. `:noedgetoedge:lintDebug` con 0 errores; `assembleDebug` y
`assembleRelease` (R8) correctos.

No comprobado: navegación de 3 botones, Android 15 y 16, Compose, tablets, plegables, pantalla
dividida, DeX, picture-in-picture, varios procesos, `exclude(...)` e `include(...)` con una
Activity de librería real (solo con clases de prueba propias), `DialogFragment` marcado con
`RemoveEdgeToEdge` en el modo 2, el modo asignado desde una clase `Application` real (en la
prueba se asignó al inicio del `onCreate` de la Activity), horizontal con el notch a la
izquierda, y los flags antiguos de layout.

## 7. Qué NO hace y por qué

- **No actúa en Android 14 o inferior.** Ahí el sistema no fuerza nada; edge-to-edge solo
  aparece si la app llama a `enableEdgeToEdge()`. Deshacerlo exigiría, después de `onCreate`:
  `WindowCompat.setDecorFitsSystemWindows(window, true)`, restaurar `statusBarColor` y
  `navigationBarColor` desde el tema y restaurar el color de los iconos
  (`windowLightStatusBar`). Riesgos: pisaría colores que la Activity haya puesto a propósito en
  `onCreate`, y `onActivityPostCreated` no existe antes de API 29 (habría que usar
  `onActivityStarted`). No se implementó porque no había dispositivo ni emulador con Android 14
  o inferior para probarlo.
- **No cubre ventanas que no sean de Activity o `DialogFragment`** (diálogos de Compose,
  `WindowManager.addView`, `Presentation`): no hay un punto global público donde engancharse.

## 8. Puntos frágiles, para quien lo modifique

| Dependencia | Dónde | Si cambia |
| --- | --- | --- |
| `WindowInsets.getSystemWindowInsets()` | `FitSystemWindows.fit` | Padding incorrecto. |
| Vista de contenido hija directa del `DecorView` | `contentRootOf` | El módulo no hace nada. |
| `getLocationInWindow` refleja el pan de la ventana | `SystemBarsView` | Fondo de barra se mueve con `adjustPan`. |
| Centinela `#00808080` | `navigationBarColorOf` | Barra de navegación transparente en vez de negra. |
| Insets de animación calculados con el modo de teclado | `FitSystemWindows.onProgress` | Animación con valores erróneos. |
| `setNavigationBarColor` sigue activo con forzado | `syncSystemNavigationBar` | Contraste de los 3 botones. |

Reglas al modificar:

- No guardar vistas, ventanas ni Activities en campos estáticos o como valores de mapas.
- No tocar el `DecorView` antes de que termine el `onCreate` de la Activity.
- No poner el listener en otra vista que la raíz de contenido.
- Volver a probar en dispositivo al menos: `adjustResize`, `adjustPan`, modo inmersivo,
  `DialogFragment` de pantalla completa y bottom sheet.
- Todo el código que usa API 30+ debe quedar detrás de la comprobación `SDK_INT >= 35`
  (`minSdk` del módulo es 21).
