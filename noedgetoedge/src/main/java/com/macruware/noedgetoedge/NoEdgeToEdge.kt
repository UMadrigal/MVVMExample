package com.macruware.noedgetoedge

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import java.util.WeakHashMap
import kotlin.math.min

/**
 * Quita el edge-to-edge que Android impone a las apps con targetSdk 35 o superior, sin bajar el
 * targetSdk y sin tocar ninguna Activity, Fragment ni layout.
 *
 * Cómo lo hace: antes de edge-to-edge, el sistema apartaba el contenido de las barras con
 * `insets.getSystemWindowInsets()` sobre la vista raíz de la ventana, y pintaba detrás de las
 * barras los colores `android:statusBarColor` y `android:navigationBarColor` del tema. Android 15+
 * dejó de hacer ambas cosas; este módulo las vuelve a hacer con la misma fórmula y sobre la misma
 * vista. Por eso el teclado (adjustResize / adjustPan), FLAG_FULLSCREEN, el modo inmersivo y el
 * notch se comportan igual que antes.
 *
 * Se instala solo (ver [NoEdgeToEdgeInitializer]) y cubre todas las Activity de la app y de sus
 * librerías, todo lo que vive dentro de ellas (Fragments, Compose, vistas) y los DialogFragment
 * de pantalla completa. En Android 14 o inferior no hace nada: ahí el sistema no fuerza nada.
 */
object NoEdgeToEdge {

    /** Primer nivel de API (Android 15) en el que el sistema fuerza edge-to-edge. */
    private const val ENFORCED_SINCE = 35

    /** Valor de `@android:color/navigation_bar_default`: el tema no definió color de navegación. */
    private const val NAVIGATION_BAR_UNSPECIFIED = 0x00808080

    @Suppress("DEPRECATION")
    private const val LEGACY_LAYOUT_FLAGS =
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

    private var installed = false

    /**
     * Modo del módulo.
     *
     * - `true` (por defecto): quita edge-to-edge en todas las pantallas, salvo las marcadas con
     *   [KeepEdgeToEdge] o pasadas a [exclude].
     * - `false`: la app sigue siendo edge-to-edge y solo se quita en las pantallas marcadas con
     *   [RemoveEdgeToEdge] o pasadas a [include].
     *
     * Para cambiarlo, asígnalo en `Application.onCreate()`. Una pantalla ya abierta no cambia
     * hasta que se vuelve a crear.
     */
    @JvmStatic
    @Volatile
    var removeFromAllScreens = true

    /** Nombres de clase de Activities y DialogFragments que deben seguir siendo edge-to-edge. */
    private val excluded = HashSet<String>()

    /** Nombres de clase a las que quitar edge-to-edge cuando [removeFromAllScreens] es `false`. */
    private val included = HashSet<String>()

    /** Colores puestos por código con [setStatusBarColor] / [setNavigationBarColor]. */
    private val colorOverrides = WeakHashMap<Window, ColorOverrides>()

    /** Lo llama [NoEdgeToEdgeInitializer] al arrancar el proceso. Repetirlo no hace nada. */
    @JvmStatic
    fun install(app: Application) {
        if (installed || !isEnforced(app)) return
        installed = true
        app.registerActivityLifecycleCallbacks(ActivityHook)
    }

    /**
     * Deja fuera del módulo las Activities o DialogFragments con esos nombres de clase completos
     * (por ejemplo `"com.libreria.SuActivity"`): seguirán siendo edge-to-edge. Pensado para
     * pantallas de librerías; en las propias es más cómodo implementar [KeepEdgeToEdge].
     * Hay que llamarlo antes de que la pantalla se abra, normalmente en `Application.onCreate()`.
     */
    @JvmStatic
    fun exclude(vararg classNames: String) {
        excluded.addAll(classNames)
    }

    /**
     * Lo contrario de [exclude], para cuando [removeFromAllScreens] es `false`: quita edge-to-edge
     * solo en las Activities o DialogFragments con esos nombres de clase completos. Pensado para
     * pantallas de librerías; en las propias es más cómodo implementar [RemoveEdgeToEdge].
     * Hay que llamarlo antes de que la pantalla se abra, normalmente en `Application.onCreate()`.
     */
    @JvmStatic
    fun include(vararg classNames: String) {
        included.addAll(classNames)
    }

    /** Si a esta Activity o DialogFragment hay que quitarle edge-to-edge. Excluir siempre gana. */
    private fun shouldRemoveFrom(screen: Any): Boolean {
        val name = screen.javaClass.name
        if (screen is KeepEdgeToEdge || name in excluded) return false
        return removeFromAllScreens || screen is RemoveEdgeToEdge || name in included
    }

    /**
     * Solo para ventanas que el módulo no puede ver por sí mismo: un `Dialog` de pantalla completa
     * creado a mano (sin DialogFragment). Llamar después de `dialog.show()` o de `setContentView`.
     */
    @JvmStatic
    fun applyTo(window: Window) {
        if (Build.VERSION.SDK_INT < ENFORCED_SINCE || !isEnforced(window.context)) return
        val decor = window.decorView as? ViewGroup ?: return
        // Las ventanas flotantes (diálogos normales) nunca son edge-to-edge.
        if (window.isFloating) return
        val root = contentRootOf(window, decor) ?: return
        if (barsIn(decor) != null) return

        val bars = SystemBarsView(decor.context, statusBarColorOf(window), navigationBarColorOf(window))
        decor.addView(bars, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        syncSystemNavigationBar(window, bars.navigationBarColor)
        val fit = FitSystemWindows(root, bars)
        root.setOnApplyWindowInsetsListener(fit)
        root.setWindowInsetsAnimationCallback(fit)
        root.addOnAttachStateChangeListener(fit)
        root.requestApplyInsets()
    }

    /** Sustituto de `window.statusBarColor = color`, que Android 15+ ignora. */
    @JvmStatic
    fun setStatusBarColor(window: Window, color: Int) {
        @Suppress("DEPRECATION")
        window.statusBarColor = color
        colorOverrides.getOrPut(window) { ColorOverrides() }.statusBar = color
        barsOf(window)?.statusBarColor = color
    }

    /** Sustituto de `window.navigationBarColor = color`, que Android 15+ ignora con gestos. */
    @JvmStatic
    fun setNavigationBarColor(window: Window, color: Int) {
        @Suppress("DEPRECATION")
        window.navigationBarColor = color
        colorOverrides.getOrPut(window) { ColorOverrides() }.navigationBar = color
        barsOf(window)?.navigationBarColor = color
    }

    private fun isEnforced(context: Context): Boolean =
        Build.VERSION.SDK_INT >= ENFORCED_SINCE &&
            context.applicationInfo.targetSdkVersion >= ENFORCED_SINCE

    /** La vista hija directa del DecorView que contiene todo el contenido de la ventana. */
    private fun contentRootOf(window: Window, decor: ViewGroup): View? {
        var view: View = window.findViewById(android.R.id.content) ?: return null
        while (true) {
            val parent = view.parent
            if (parent === decor) return view
            view = parent as? View ?: return null
        }
    }

    private fun barsOf(window: Window): SystemBarsView? =
        (window.peekDecorView() as? ViewGroup)?.let(::barsIn)

    private fun barsIn(decor: ViewGroup): SystemBarsView? {
        for (i in decor.childCount - 1 downTo 0) {
            (decor.getChildAt(i) as? SystemBarsView)?.let { return it }
        }
        return null
    }

    private fun statusBarColorOf(window: Window): Int =
        colorOverrides[window]?.statusBar
            ?: themeColor(window.context, android.R.attr.statusBarColor)

    private fun navigationBarColorOf(window: Window): Int {
        colorOverrides[window]?.navigationBar?.let { return it }
        val color = themeColor(window.context, android.R.attr.navigationBarColor)
        // Sin color definido en el tema, las apps anteriores a edge-to-edge la tenían negra.
        return if (color == NAVIGATION_BAR_UNSPECIFIED) Color.BLACK else color
    }

    private fun themeColor(context: Context, attr: Int): Int {
        val attrs = context.theme.obtainStyledAttributes(intArrayOf(attr))
        try {
            return attrs.getColor(0, Color.BLACK)
        } finally {
            attrs.recycle()
        }
    }

    /**
     * Con navegación de 3 botones el sistema todavía dibuja su propia franja usando
     * `navigationBarColor`. Dárselo mantiene el color de los botones igual que antes.
     */
    private fun syncSystemNavigationBar(window: Window, color: Int) {
        if (Color.alpha(color) == 0) return
        @Suppress("DEPRECATION")
        window.navigationBarColor = color
    }

    private class ColorOverrides {
        var statusBar: Int? = null
        var navigationBar: Int? = null
    }

    private object ActivityHook : Application.ActivityLifecycleCallbacks {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is FragmentActivity) {
                activity.supportFragmentManager
                    .registerFragmentLifecycleCallbacks(DialogHook, true)
            }
        }

        // Después del onCreate() completo de la Activity: la ventana ya tiene su contenido y sus
        // requestWindowFeature(), así que tocar el DecorView aquí no rompe nada.
        override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (shouldRemoveFrom(activity)) applyTo(activity.window)
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    /** DialogFragment de pantalla completa y BottomSheetDialogFragment: tienen ventana propia. */
    private object DialogHook : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            if (f is DialogFragment && shouldRemoveFrom(f)) f.dialog?.window?.let(::applyTo)
        }
    }

    /**
     * Lo mismo que hacía el sistema con `decorFitsSystemWindows = true`, con una mejora: mientras
     * el teclado se abre o se cierra, el hueco crece o se encoge fotograma a fotograma en vez de
     * saltar de golpe.
     */
    @RequiresApi(ENFORCED_SINCE)
    private class FitSystemWindows(
        private val root: View,
        private val bars: SystemBarsView,
    ) : WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP),
        View.OnApplyWindowInsetsListener,
        View.OnAttachStateChangeListener {

        /** Animaciones de teclado en curso. Mientras haya alguna, manda [onProgress]. */
        private var imeAnimations = 0

        override fun onApplyWindowInsets(view: View, insets: WindowInsets): WindowInsets {
            // Al empezar una animación de teclado el sistema entrega ya los insets finales.
            // Aplicarlos aquí sería el salto que se quiere evitar: se aplican en onProgress.
            if (imeAnimations == 0) fit(insets)
            return if (wantsLegacyLayoutBehindBars()) insets else WindowInsets.CONSUMED
        }

        override fun onPrepare(animation: WindowInsetsAnimation) {
            if (animation.isIme()) imeAnimations++
        }

        override fun onProgress(
            insets: WindowInsets,
            runningAnimations: MutableList<WindowInsetsAnimation>,
        ): WindowInsets {
            if (imeAnimations > 0) fit(insets)
            return insets
        }

        override fun onEnd(animation: WindowInsetsAnimation) {
            if (!animation.isIme() || imeAnimations == 0) return
            imeAnimations--
            if (imeAnimations == 0) root.rootWindowInsets?.let(::fit)
        }

        override fun onViewAttachedToWindow(view: View) = Unit

        override fun onViewDetachedFromWindow(view: View) {
            // Por si la ventana se va a mitad de una animación y nunca llega onEnd.
            imeAnimations = 0
        }

        private fun WindowInsetsAnimation.isIme(): Boolean =
            typeMask and WindowInsets.Type.ime() != 0

        /** La pantalla pidió dibujar detrás de las barras con los flags antiguos. */
        @Suppress("DEPRECATION")
        private fun wantsLegacyLayoutBehindBars(): Boolean =
            root.windowSystemUiVisibility and LEGACY_LAYOUT_FLAGS != 0

        private fun fit(insets: WindowInsets) {
            if (wantsLegacyLayoutBehindBars()) {
                root.setPadding(0, 0, 0, 0)
                bars.clear()
                return
            }
            // Barras visibles + notch, más el teclado si la ventana es adjustResize, menos la
            // barra de estado si hay FLAG_FULLSCREEN. Es el cálculo "legacy" del propio sistema.
            @Suppress("DEPRECATION")
            val fit = insets.systemWindowInsets
            root.setPadding(fit.left, fit.top, fit.right, fit.bottom)
            bars.update(
                fit = fit,
                statusBar = insets.getInsets(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.captionBar()
                ),
                navigationBar = insets.getInsets(WindowInsets.Type.navigationBars()),
            )
        }
    }

    /**
     * Pinta el fondo de las barras del sistema en el hueco que deja el padding. Es una vista
     * hermana del contenido dentro del DecorView, igual que las que usaba el propio sistema.
     */
    private class SystemBarsView(
        context: Context,
        statusBarColor: Int,
        navigationBarColor: Int,
    ) : View(context) {

        var statusBarColor = statusBarColor
            set(value) {
                field = value
                invalidate()
            }

        var navigationBarColor = navigationBarColor
            set(value) {
                field = value
                invalidate()
            }

        private val paint = Paint()
        private val location = IntArray(2)
        private var fitLeft = 0
        private var fitTop = 0
        private var fitRight = 0
        private var fitBottom = 0
        private var statusBarTop = 0
        private var navigationLeft = 0
        private var navigationRight = 0
        private var navigationBottom = 0

        // Con adjustPan el sistema desplaza la ventana entera para dejar ver el campo de texto.
        // El fondo de las barras debe quedarse en su sitio, como antes.
        private val keepInPlaceWhilePanning = ViewTreeObserver.OnDrawListener {
            (parent as? View)?.getLocationInWindow(location)
            val pan = -location[1]
            translationY = if (pan > 0) pan.toFloat() else 0f
        }

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        fun update(fit: Insets, statusBar: Insets, navigationBar: Insets) {
            fitLeft = fit.left
            fitTop = fit.top
            fitRight = fit.right
            fitBottom = fit.bottom
            statusBarTop = statusBar.top
            navigationLeft = navigationBar.left
            navigationRight = navigationBar.right
            navigationBottom = navigationBar.bottom
            invalidate()
        }

        fun clear() {
            fitLeft = 0
            fitTop = 0
            fitRight = 0
            fitBottom = 0
            invalidate()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            viewTreeObserver.addOnDrawListener(keepInPlaceWhilePanning)
        }

        override fun onDetachedFromWindow() {
            viewTreeObserver.removeOnDrawListener(keepInPlaceWhilePanning)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            // Un hueco que no es de una barra es el notch: antes quedaba en negro.
            if (fitTop > 0) {
                fill(canvas, 0, 0, width, fitTop,
                    if (statusBarTop > 0) statusBarColor else Color.BLACK)
            }
            // Abajo solo la barra de navegación: si el hueco es del teclado, no se pinta.
            val barBottom = min(fitBottom, navigationBottom)
            if (barBottom > 0) {
                fill(canvas, 0, height - barBottom, width, height, navigationBarColor)
            }
            if (fitLeft > 0) {
                fill(canvas, 0, 0, fitLeft, height,
                    if (navigationLeft > 0) navigationBarColor else Color.BLACK)
            }
            if (fitRight > 0) {
                fill(canvas, width - fitRight, 0, width, height,
                    if (navigationRight > 0) navigationBarColor else Color.BLACK)
            }
        }

        private fun fill(canvas: Canvas, l: Int, t: Int, r: Int, b: Int, color: Int) {
            if (Color.alpha(color) == 0) return
            paint.color = color
            canvas.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), paint)
        }
    }
}

/**
 * Interruptor por pantalla: una Activity o un DialogFragment que implemente esta interfaz queda
 * fuera del módulo y sigue siendo edge-to-edge (tendrá que manejar sus insets ella misma).
 *
 * ```
 * class VideoActivity : AppCompatActivity(), KeepEdgeToEdge
 * ```
 */
interface KeepEdgeToEdge

/**
 * Lo contrario de [KeepEdgeToEdge], para cuando `NoEdgeToEdge.removeFromAllScreens` es `false`:
 * la app es edge-to-edge y solo se le quita a la Activity o DialogFragment que implemente esta
 * interfaz.
 *
 * ```
 * class FormularioActivity : AppCompatActivity(), RemoveEdgeToEdge
 * ```
 */
interface RemoveEdgeToEdge
