// Copiado de Mochy Music (github.com/realidkroo/Mochy-Music, GPL-3.0): app/src/main/kotlin/com/mochyy/mochymusic/ui/utils/AppMotion.kt
// customize's animation and element blur settings, mirrored here by mainactivity so draw code and nav lambdas can read them

package com.example.lector.ui.mochy

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale

// the speeds the animation slider stops at, slowest to fastest
val AnimationSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

// Shiori: el estado vive en com.example.lector.ui.MovimientoApp (Personalizacion.kt), que ya está conectado al
// Recomposer de la ventana; AppMotion lo lee y escribe a través de él en vez de crear un segundo MotionDurationScale.
object AppMotion {
    /** false: nothing animates, every compose animation jumps straight to its end */
    var enabled: Boolean
        get() = com.example.lector.ui.MovimientoApp.activado
        set(value) = com.example.lector.ui.MovimientoApp.elegirActivado(value)

    /** how much faster than android's own animation speed things move, 1 is just android's */
    var speed: Float
        get() = com.example.lector.ui.MovimientoApp.velocidad
        set(value) = com.example.lector.ui.MovimientoApp.elegirVelocidad(value)

    /** false: lyric lines, title morphs, menus and page wipes stay sharp, glass blur isn't touched */
    var elementBlur by mutableStateOf(true)

    /** handed to the window's recomposer, so every animation in the app reads it each frame */
    val durationScale: MotionDurationScale get() = com.example.lector.ui.MovimientoApp.escala

    // keeps android's animator duration scale in step, the way compose's own scale did before we replaced it
    // (Shiori: MovimientoApp.iniciar ya registra este observador; aquí solo se le delega)
    fun followSystemScale(context: Context): ContentObserver {
        com.example.lector.ui.MovimientoApp.iniciar(context)
        return object : ContentObserver(Handler(Looper.getMainLooper())) {}
    }
}

/** how a speed reads on screen: 1x, 1.25x, 0.5x */
fun animationSpeedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
    return "${text}x"
}

/** [transition], or no transition at all while animations are switched off */
fun <S> AnimatedContentTransitionScope<S>.motionEnter(
    transition: AnimatedContentTransitionScope<S>.() -> EnterTransition?,
): EnterTransition? = if (AppMotion.enabled) transition() else EnterTransition.None

/** [transition], or no transition at all while animations are switched off */
fun <S> AnimatedContentTransitionScope<S>.motionExit(
    transition: AnimatedContentTransitionScope<S>.() -> ExitTransition?,
): ExitTransition? = if (AppMotion.enabled) transition() else ExitTransition.None

/** The NavHost's own defaults, which can't be null: [transition], or none while animations are off */
fun <S> AnimatedContentTransitionScope<S>.defaultMotionEnter(
    transition: AnimatedContentTransitionScope<S>.() -> EnterTransition,
): EnterTransition = if (AppMotion.enabled) transition() else EnterTransition.None

/** The NavHost's own defaults, which can't be null: [transition], or none while animations are off */
fun <S> AnimatedContentTransitionScope<S>.defaultMotionExit(
    transition: AnimatedContentTransitionScope<S>.() -> ExitTransition,
): ExitTransition = if (AppMotion.enabled) transition() else ExitTransition.None
