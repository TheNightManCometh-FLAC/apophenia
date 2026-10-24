package com.betterappmafia.apophenia.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Better App Mafia mark on a cold start, as Independent Video does it: on true black (the logo's edges are
 * feathered), 78% of the screen's short side, fading and growing in from 94% over 520 ms, held until 1.7 s, then
 * faded out over 380 ms to show the app. A tap skips it. Android 12+ draws its own system splash (the launcher
 * icon on black, from Theme.Apophenia) before this.
 */
@Composable
fun BamSplash(done: () -> Unit) {
    val context = LocalContext.current
    val logo = remember { runCatching { context.assets.open("brand/bam_logo.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() }
    val shown = remember { Animatable(0f) }
    val cover = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var ending by remember { mutableStateOf(false) }
    fun end() {
        if (ending) return
        ending = true
        scope.launch { cover.animateTo(0f, tween(380)); done() }
    }
    LaunchedEffect(Unit) {
        if (logo == null) { done(); return@LaunchedEffect }
        launch { shown.animateTo(1f, tween(520, easing = LinearOutSlowInEasing)) }
        delay(1700)
        end()
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = cover.value }.background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { end() },
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) Image(
            logo, contentDescription = "Better App Mafia",
            modifier = Modifier.size(minOf(maxWidth, maxHeight) * 0.78f).graphicsLayer {
                alpha = shown.value
                val s = 0.94f + 0.06f * shown.value
                scaleX = s; scaleY = s
            },
        )
    }
}
