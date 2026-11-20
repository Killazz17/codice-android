package una.codice.presentation.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import una.codice.core.designsystem.component.GlassCard
import una.codice.core.designsystem.component.GradientText
import una.codice.core.designsystem.motion.rememberCountUp
import una.codice.core.designsystem.motion.staggeredEntrance
import una.codice.core.designsystem.theme.CodiceColors
import una.codice.core.designsystem.theme.LocalAccent
import androidx.compose.ui.res.stringResource
import una.codice.R

/*
 * Las piezas visuales del Perfil. Son sin estado: reciben lo que muestran y no saben de ViewModels ni de la red. Lo
 * único que guardan es estado de presentación (qué día de repaso está elegido).
 */

/**
 * Las cifras de "Mi actividad" en una cuadrícula tipo bento: la cifra del medio es la protagonista (una tarjeta alta
 * con borde neón y número enorme) y las otras dos se apilan a su izquierda. Con una cantidad de cifras distinta de
 * tres se reparten en fila, todas iguales.
 */
@Composable
fun BentoStats(stats: List<ProfileStat>, modifier: Modifier = Modifier) {
    if (stats.size != 3) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.forEachIndexed { index, stat ->
                StatTile(stat, Modifier.weight(1f).heightIn(min = 96.dp).staggeredEntrance(index + 1), hero = false)
            }
        }
        return
    }

    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stats[0], Modifier.weight(1f).fillMaxWidth().heightIn(min = 84.dp).staggeredEntrance(1), hero = false)
            StatTile(stats[2], Modifier.weight(1f).fillMaxWidth().heightIn(min = 84.dp).staggeredEntrance(2), hero = false)
        }
        StatTile(stats[1], Modifier.weight(1.15f).staggeredEntrance(3), hero = true)
    }
}

/** Una cifra con su etiqueta. La protagonista ([hero]) lleva el borde neón, un resplandor detrás y el número enorme. */
@Composable
private fun StatTile(stat: ProfileStat, modifier: Modifier, hero: Boolean) {
    val accent = LocalAccent.current
    // La cifra sube contando desde 0; un "–" (cifra que no se pudo pedir) se muestra tal cual.
    val number = stat.value.toIntOrNull()
    val shown = if (number != null) rememberCountUp(number).toString() else stat.value

    // Sin relleno en la tarjeta: el resplandor se dibuja sobre la tarjeta entera (si no, se vería el borde del contenido
    // como un cuadrado) y el relleno se aplica después, dentro del propio Column.
    GlassCard(modifier = modifier, contentPadding = PaddingValues(0.dp), glow = hero) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (hero) {
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(accent.primary.copy(alpha = 0.22f), Color.Transparent),
                                center = Offset(size.width * 0.25f, size.height * 0.45f),
                                radius = size.maxDimension * 0.75f,
                            )
                        )
                    }
                }
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            GradientText(
                text = shown,
                fontSize = if (hero) 64.sp else 36.sp,
                fontWeight = FontWeight.Black,
                animated = hero,
            )
            Text(
                // "1 CURSOS" no: con un solo elemento se usa la forma singular.
                text = stringResource(if (stat.value == "1" && stat.singularRes != null) stat.singularRes else stat.labelRes).uppercase(),
                color = CodiceColors.TextMid,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

/**
 * Los intervalos de repaso como botones: se encogen al presionarlos y rebotan al soltarlos (un resorte flojo que pasa
 * de largo), y el elegido sube, se rellena con el degradado del rol y explica qué significa ese intervalo.
 */
@Composable
fun IntervalDays(days: List<Int>, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // El espacio de arriba deja sitio a que el botón elegido suba sin cortarse.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEachIndexed { index, count ->
                IntervalChip(
                    days = count,
                    selected = index == selected,
                    onClick = { selected = index },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) { it / 3 }) togetherWith
                    fadeOut(tween(120))
            },
            label = "interval-caption",
        ) { index ->
            Text(
                text = intervalCaption(days, index),
                color = CodiceColors.TextMid,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun IntervalChip(days: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Presionado se encoge; al soltar vuelve a 1 con un resorte flojo y se pasa un poco: ese es el rebote.
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.32f, stiffness = 420f),
        label = "interval-press",
    )
    val transition = updateTransition(targetState = selected, label = "interval-select")
    val lift by transition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.45f, stiffness = 380f) },
        label = "interval-lift",
    ) { isSelected -> if (isSelected) (-6).dp else 0.dp }
    val fill by transition.animateFloat(transitionSpec = { tween(240) }, label = "interval-fill") { isSelected ->
        if (isSelected) 1f else 0f
    }
    val textColor by transition.animateColor(transitionSpec = { tween(240) }, label = "interval-text") { isSelected ->
        if (isSelected) CodiceColors.Canvas else accent.primary
    }

    Column(
        modifier = modifier
            .offset { IntOffset(0, lift.roundToPx()) }
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .drawBehind { drawRect(accent.brush(), alpha = fill) }
            .border(1.dp, accent.primary.copy(alpha = 0.22f + 0.5f * fill), shape)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "$days", color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(text = stringResource(if (days == 1) R.string.app_day else R.string.app_days), color = textColor.copy(alpha = 0.85f), fontSize = 11.sp)
    }
}

/** Qué significa el intervalo [index]: cuándo toca ese repaso y qué pasa si se acierta o se falla. */
@Composable
private fun intervalCaption(days: List<Int>, index: Int): String {
    val count = days[index]
    if (index == 0) return stringResource(R.string.app_interval_first, count, unit(count))
    val previous = days[index - 1]
    return stringResource(R.string.app_interval_next, previous, unit(previous), count, unit(count))
}

@Composable
private fun unit(count: Int) = stringResource(if (count == 1) R.string.app_day else R.string.app_days)
