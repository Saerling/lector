package com.example.lector.ui

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import java.lang.ref.WeakReference

/**
 * Vocabulario háptico de la app: un significado por patrón, siempre el mismo (como un sistema de diseño, pero para el
 * tacto). Usa los efectos del sistema, así respeta el ajuste «Vibración al tocar» del móvil y no necesita permisos.
 *
 *  - [tic]       cambio pequeño de selección (chips, pestañas, filtros)
 *  - [alternar]  interruptor encendido/apagado
 *  - [umbral]    un gesto llega a su punto (deslizar para recargar, arrastrar para borrar)
 *  - [confirmar] algo se guardó (añadido a la biblioteca, migrado)
 *  - [rechazar]  error
 */
object Tacto {
    private var vista: WeakReference<View>? = null

    /** La vista raíz de la actividad (MainActivity). */
    fun iniciar(v: View) { vista = WeakReference(v) }

    private fun hacer(constante: Int, alternativa: Int = HapticFeedbackConstants.VIRTUAL_KEY) {
        vista?.get()?.performHapticFeedback(constante.takeIf { it >= 0 } ?: alternativa)
    }

    fun tic() = hacer(HapticFeedbackConstants.CLOCK_TICK)

    fun alternar(encendido: Boolean) = hacer(
        if (Build.VERSION.SDK_INT >= 34) (if (encendido) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF) else -1,
        HapticFeedbackConstants.CLOCK_TICK,
    )

    fun umbral() = hacer(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else -1, HapticFeedbackConstants.LONG_PRESS)

    fun confirmar() = hacer(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else -1, HapticFeedbackConstants.VIRTUAL_KEY)

    fun rechazar() = hacer(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else -1, HapticFeedbackConstants.LONG_PRESS)
}

/**
 * «Quitar animaciones» de Android (Accesibilidad u Opciones de desarrollador). Con él activo, lo que se mueve por
 * adorno (la animación de arranque, la inclinación 3D, las entradas escalonadas, el brillo de los esqueletos) se queda
 * quieto; lo que informa (progreso, cambios de estado) sigue, pero sin desplazamientos grandes.
 */
object MovimientoReducido {
    var activo = false; private set

    fun actualizar() { activo = !ValueAnimator.areAnimatorsEnabled() }
}
