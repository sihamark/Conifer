package eu.heha.conifer.ui.bits

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The little scene the empty state and the beginning note are built around: one large emoji with a
 * handful of smaller ones drifting down past it and out of the bottom, behind it all the way — and,
 * now and then, a visitor: one of [scampering] coming down the big emoji itself and bolting off to
 * one side, or one of [circling] wandering in from one side, going round the foot of it and off out
 * the other.
 *
 * Drawn entirely in the platform's emoji font — there is no artwork here, only text that happens to
 * be pictures, which is also why nothing is measured in advance: the glyphs come out whatever size
 * the platform makes them, so the scene only ever positions them relative to itself.
 *
 * Both the big emoji and the falling ones are sized off [height], so a window with less room to
 * spare gets a smaller scene rather than one that spills over its neighbours.
 */
@Composable
internal fun EmojiFallScene(
    emoji: String,
    falling: List<String>,
    height: Dp,
    modifier: Modifier = Modifier,
    scampering: List<String> = emptyList(),
    circling: List<String> = emptyList()
) {
    val transition = rememberInfiniteTransition(label = "emojiFallScene")
    Box(
        contentAlignment = Alignment.Center,
        // Every fall starts above the scene and ends below it, and the sideways swing can reach
        // past its sides; clipping is what keeps all of that off the content around it.
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .clipToBounds()
    ) {
        DRIFTS.forEachIndexed { index, drift ->
            FallingEmoji(
                // Fewer emoji than lanes just means some of them fall twice, which reads as a
                // handful of the same kind of thing rather than a set being enumerated.
                emoji = falling[index % falling.size],
                drift = drift,
                sceneHeight = height,
                transition = transition
            )
        }
        // One animation per visit, hoisted out of the two copies of each animal below: a copy is
        // drawn on either side of the big emoji, and they have to be the same animal at the same
        // point of the same visit, not two that merely agree.
        val visits = if (circling.isEmpty()) {
            emptyList()
        } else {
            ROUNDS.map { round ->
                transition.animateFloat(
                    initialValue = round.startFraction,
                    targetValue = round.startFraction + 1f,
                    animationSpec = infiniteRepeatable(
                        tween(round.cycleMillis, easing = LinearEasing)
                    ),
                    label = "round"
                )
            }
        }
        // The half of each round that passes behind the big emoji, drawn before it so that it does.
        visits.forEachIndexed { index, cycle ->
            CirclingAnimal(
                emoji = circling[index % circling.size],
                round = ROUNDS[index],
                cycle = cycle,
                sceneHeight = height,
                isBehind = true
            )
        }
        // Rooted at its base like the thing it depicts, so the sway bends the top and leaves the
        // bottom where it is.
        val sway by transition.animateFloat(
            initialValue = -SWAY_DEGREES,
            targetValue = SWAY_DEGREES,
            animationSpec = infiniteRepeatable(
                animation = tween(SWAY_DURATION_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sway"
        )
        Text(
            text = emoji,
            // Converted from dp rather than given in sp: this glyph has to keep fitting the scene
            // it was sized against, which a font scale of 2 would otherwise undo.
            fontSize = with(LocalDensity.current) { (height * CENTER_EMOJI_SHARE).toSp() },
            modifier = Modifier.graphicsLayer {
                rotationZ = sway
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
        )
        // And the half that passes in front of it, along with the walk in and the walk out — which
        // happen off to the sides, where the two copies swap without anything to give it away.
        visits.forEachIndexed { index, cycle ->
            CirclingAnimal(
                emoji = circling[index % circling.size],
                round = ROUNDS[index],
                cycle = cycle,
                sceneHeight = height,
                isBehind = false
            )
        }
        if (scampering.isNotEmpty()) {
            // After the big emoji, so the animals pass in front of it rather than behind: the near
            // side of a trunk is the only place something can be seen climbing down one.
            SCAMPERS.forEachIndexed { index, scamper ->
                ScamperingAnimal(
                    emoji = scampering[index % scampering.size],
                    scamper = scamper,
                    sceneHeight = height,
                    transition = transition
                )
            }
        }
    }
}

/**
 * One emoji falling through the scene forever, along the lane [drift] describes.
 *
 * The whole trajectory is written into a single [graphicsLayer], so the frames of the fall cost a
 * layer update each and no recomposition at all.
 */
@Composable
private fun BoxScope.FallingEmoji(
    emoji: String,
    drift: Drift,
    sceneHeight: Dp,
    transition: InfiniteTransition
) {
    // Counted from where this emoji already is when the scene opens to one whole fall further on,
    // and wrapped back into 0..1 below. Written this way rather than as 0..1 with a fast-forwarded
    // start so that the value it *begins* at is mid-fall: wherever animations don't run — a static
    // preview, or a test, where infinite ones are suppressed outright — every animation sits at its
    // initial value, and the scene is then a still of itself rather than an empty box.
    val fall by transition.animateFloat(
        initialValue = drift.startFraction,
        targetValue = drift.startFraction + 1f,
        // Linear: a leaf that eased in and out of every fall would read as being pulled on a
        // string. What unevenness there is comes from the sideways swing.
        animationSpec = infiniteRepeatable(tween(drift.durationMillis, easing = LinearEasing)),
        label = "fall"
    )
    Text(
        text = emoji,
        fontSize = with(LocalDensity.current) { drift.size.toSp() },
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                // One fall, from the top of the lane (0) to the bottom of it (1).
                val progress = fall % 1f
                // Measured from the centre of the scene, which is where the emoji is aligned: half
                // the scene plus its own size in either direction puts both ends of the fall out of
                // sight, so it is never seen appearing or arriving.
                val reach = (sceneHeight / 2 + drift.size).toPx()
                translationY = -reach + 2 * reach * progress
                // The lanes are spread over a band as wide as the scene is tall — not over the pane,
                // which on a desktop window is wide enough to have the emoji falling nowhere near
                // the tree they are supposed to be coming off.
                translationX = (drift.lane - 0.5f) * (sceneHeight * LANE_BAND).toPx() +
                        sin((progress * drift.swayCycles + drift.swayPhase) * 2 * PI).toFloat() *
                        drift.sway.toPx()
                rotationZ = drift.spins * 360f * progress
                // Dissolves into and out of the scene instead of being cut in half by the clip on
                // the way through: the edges of this box are nowhere in particular.
                alpha = minOf(
                    progress / FADE_IN_FRACTION,
                    (1f - progress) / FADE_OUT_FRACTION,
                    1f
                ).coerceAtLeast(0f)
            }
    )
}

/**
 * One animal, dropping in over the top of the scene, coming down the big emoji and then bolting off
 * sideways and out of the bottom — and doing it again a long wait later, forever.
 *
 * The wait is the point of it: the run itself is only [Scamper.runShare] of a cycle that lasts half
 * a minute, so what the scene mostly shows is no animal at all, and one turning up is an event. That
 * also keeps the animals from piling up on each other without any of them having to know about the
 * others — their cycles are different lengths and start at different points, so they drift in and
 * out of each other's company on their own.
 *
 * Like [FallingEmoji], the whole run lives in one [graphicsLayer] and costs no recomposition.
 */
@Composable
private fun BoxScope.ScamperingAnimal(
    emoji: String,
    scamper: Scamper,
    sceneHeight: Dp,
    transition: InfiniteTransition
) {
    val cycle by transition.animateFloat(
        initialValue = scamper.startFraction,
        targetValue = scamper.startFraction + 1f,
        animationSpec = infiniteRepeatable(tween(scamper.cycleMillis, easing = LinearEasing)),
        label = "scamper"
    )
    Text(
        text = emoji,
        // Sized against the scene rather than fixed like the falling emoji: their sizes say how far
        // behind the big emoji they are, but this one is *on* it, and has to stay in proportion to
        // it whether the pane had 200dp to give the scene or 80.
        fontSize = with(LocalDensity.current) { (sceneHeight * scamper.sizeShare).toSp() },
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                val position = cycle % 1f
                if (position > scamper.runShare) {
                    // Between runs, which is nearly all of the time: nothing to show.
                    alpha = 0f
                    return@graphicsLayer
                }
                // One run, from dropping in (0) to gone (1).
                val run = position / scamper.runShare
                val span = (sceneHeight * scamper.sizeShare).toPx()
                // Beside the centre line rather than on it, on the side it will leave by, so the run
                // already reads as being down one flank of the tree before it breaks away.
                val flank = scamper.side * (sceneHeight * FLANK_OFFSET).toPx()
                // Something running doesn't glide: it bobs, and leans into each bob. One expression
                // for the whole run, so the bobbing carries on through the turn at the bottom.
                val hop = sin(run * scamper.hops * 2 * PI).toFloat()
                if (run < scamper.descentShare) {
                    val descent = run / scamper.descentShare
                    translationX = flank
                    // Starts a whole body-length above the top edge, so it is first seen already on
                    // its way down and never seen arriving from nothing.
                    translationY = lerp(
                        -(sceneHeight / 2).toPx() - span,
                        (sceneHeight * TRUNK_BASE).toPx(),
                        descent
                    ) + hop * span * HOP_SHARE
                    rotationZ = scamper.side * DESCENT_TILT + hop * HOP_TILT
                } else {
                    val away = (run - scamper.descentShare) / (1f - scamper.descentShare)
                    // Squared: it leaves the way a startled animal does, slowest at the moment it
                    // turns and fastest as it goes.
                    val bolt = away * away
                    translationX =
                        flank + scamper.side * (sceneHeight * SCATTER_REACH).toPx() * bolt
                    // Down as well as out, and far enough down to be past the bottom edge by the end
                    // — the clip is what takes it off the scene, so no pane is wide enough for the
                    // dash sideways to end anywhere visible.
                    translationY = lerp(
                        (sceneHeight * TRUNK_BASE).toPx(),
                        (sceneHeight / 2).toPx() + span,
                        bolt
                    ) + hop * span * HOP_SHARE
                    rotationZ =
                        lerp(scamper.side * DESCENT_TILT, scamper.side * SCATTER_TILT, bolt) +
                                hop * HOP_TILT
                }
            }
    )
}

/**
 * One animal wandering in from a side, going round the foot of the big emoji and away out the other
 * side — and, a long wait later, doing it again from the other direction of travel, forever.
 *
 * Half of that loop is on the far side of the big emoji, which is what makes it read as going *round*
 * the thing rather than across it. A layer can't reorder itself against its siblings, so the animal
 * is instead drawn twice, once on either side of the big emoji, and each copy shows only its own half
 * of the loop: this is the [isBehind] one or the other one. Both read the same [cycle], so they are
 * one animal, and they hand over at the two points where the loop is widest — far from the glyph, so
 * there is nothing to see at the moment of the swap.
 *
 * The loop is an ellipse flattened almost to a line, the way a circle at one's feet looks from
 * standing height, and the far half of it is drawn a little smaller. Neither the walk in nor the walk
 * out can be relied on to leave the scene — the pane is as wide as it likes, so a step to the side
 * ends wherever it ends — so both fade instead, the way the falling emoji do.
 */
@Composable
private fun BoxScope.CirclingAnimal(
    emoji: String,
    round: Round,
    cycle: State<Float>,
    sceneHeight: Dp,
    isBehind: Boolean
) {
    Text(
        text = emoji,
        fontSize = with(LocalDensity.current) { (sceneHeight * round.sizeShare).toSp() },
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                val position = cycle.value % 1f
                if (position > round.runShare) {
                    // Between visits, which is nearly all of the time: nothing to show.
                    alpha = 0f
                    return@graphicsLayer
                }
                // One visit, from the first step in (0) to gone (1).
                val run = position / round.runShare
                val span = (sceneHeight * round.sizeShare).toPx()
                // The ground it walks on: the foot of the big emoji, near enough.
                val ground = (sceneHeight * GROUND_LINE).toPx()
                val radius = (sceneHeight * ORBIT_RADIUS).toPx()
                val edge = (sceneHeight * EDGE_REACH).toPx()
                // How far away this moment of the loop is: 1 at the back of it, -1 at the front, 0 at
                // either side and for the whole of the walk in and the walk out.
                var depth = 0f
                val orbitEnd = round.approachShare + round.orbitShare
                when {
                    run < round.approachShare -> {
                        val approach = run / round.approachShare
                        translationX =
                            lerp(round.entrySide * edge, round.entrySide * radius, approach)
                        translationY = ground
                        alpha = (approach / ROUND_FADE_SHARE).coerceAtMost(1f)
                    }

                    run < orbitEnd -> {
                        val orbit = (run - round.approachShare) / round.orbitShare
                        // Half-turns, so the loop ends on the side away from the one it came in on.
                        val angle = orbit * round.turns * 2 * PI
                        depth = sin(angle).toFloat()
                        translationX = round.entrySide * radius * cos(angle).toFloat()
                        translationY = ground - depth * (sceneHeight * ORBIT_DEPTH).toPx()
                    }

                    else -> {
                        val exit = (run - orbitEnd) / (1f - orbitEnd)
                        translationX =
                            lerp(-round.entrySide * radius, -round.entrySide * edge, exit)
                        translationY = ground
                        alpha = ((1f - exit) / ROUND_FADE_SHARE).coerceAtMost(1f)
                    }
                }
                if ((depth > 0f) != isBehind) {
                    // This stretch belongs to the other copy.
                    alpha = 0f
                    return@graphicsLayer
                }
                // Nothing small walks smoothly, and the lean sells the bob as effort.
                val hop = sin(run * round.hops * 2 * PI).toFloat()
                translationY += hop * span * HOP_SHARE
                rotationZ = hop * HOP_TILT
                val scale = 1f - depth * DEPTH_SCALE
                scaleX = scale
                scaleY = scale
            }
    )
}

/** How one of the falling emoji makes its way down the scene. */
private data class Drift(
    /** Where across the scene it falls: 0 is the left edge, 1 the right. */
    val lane: Float,
    /** How large it is, and with that how far behind the big emoji it reads as being. */
    val size: Dp,
    /** How long one fall takes. */
    val durationMillis: Int,
    /** How far it swings to either side on the way down. */
    val sway: Dp,
    /** Full turns over one fall; negative turns the other way. */
    val spins: Float,
    /** Swings it makes over one fall. */
    val swayCycles: Float,
    /** How far into its fall it is when the scene opens, so the lanes never move in lockstep. */
    val startFraction: Float,
    /** Where its swing starts, so two lanes of the same width don't mirror each other. */
    val swayPhase: Float
)

/**
 * The lanes, hand-picked rather than drawn from a random source: this way the scene looks the same
 * every time it is opened — and in every preview and screenshot — while still looking like nothing
 * in it was arranged.
 *
 * Six of them is what turned out to read as a few things falling; more starts to look like weather.
 */
private val DRIFTS = listOf(
    Drift(0.14f, 15.dp, 7_400, 10.dp, 0.6f, 1.5f, 0f, 0f),
    Drift(0.34f, 11.dp, 9_100, 14.dp, -0.4f, 1f, 0.55f, 0.3f),
    Drift(0.50f, 13.dp, 8_200, 8.dp, 0.5f, 2f, 0.25f, 0.7f),
    Drift(0.66f, 10.dp, 10_200, 16.dp, -0.7f, 1.5f, 0.8f, 0.15f),
    Drift(0.86f, 14.dp, 7_900, 11.dp, 0.3f, 1f, 0.4f, 0.9f),
    Drift(0.24f, 9.dp, 11_000, 13.dp, -0.5f, 2.5f, 0.65f, 0.5f)
)

/** How one of the animals makes its run, and how long the scene goes without it in between. */
private data class Scamper(
    /** How large it is, as a share of the scene's height. */
    val sizeShare: Float,
    /** How long one run and the wait after it take together. */
    val cycleMillis: Int,
    /** The share of that cycle spent on the scene; the rest of it is the wait. */
    val runShare: Float,
    /** The share of the run spent coming down before it breaks away sideways. */
    val descentShare: Float,
    /** Which way it goes: -1 off to the left, 1 off to the right. */
    val side: Float,
    /** How many times it bobs over one run. */
    val hops: Float,
    /**
     * How far into its cycle it is when the scene opens. Only the first of these is inside its own
     * [runShare], for the same reason the falling emoji start mid-fall: where animations don't run —
     * a preview, or a test — every animation sits at its initial value, and a still of this scene
     * should have exactly one animal on the tree rather than three or none.
     */
    val startFraction: Float
)

/**
 * The runs, hand-picked like [DRIFTS] so the scene behaves the same way every time it is opened.
 *
 * Three of them against cycles this long comes out at roughly one animal in view a third of the
 * time, which is often enough to be noticed and rare enough to stay a surprise.
 */
private val SCAMPERS = listOf(
    Scamper(0.14f, 19_000, 0.13f, 0.68f, 1f, 6f, 0.05f),
    Scamper(0.12f, 26_500, 0.10f, 0.72f, -1f, 7f, 0.42f),
    Scamper(0.13f, 31_000, 0.11f, 0.65f, -1f, 5f, 0.74f)
)

/** How far off the centre line an animal runs, and where it turns, as shares of the scene's height. */
private const val FLANK_OFFSET = 0.06f
private const val TRUNK_BASE = 0.2f

/** How far out to the side the dash away carries it, as a share of the scene's height. */
private const val SCATTER_REACH = 0.5f

/** How far it leans while coming down, how far while bolting away, and how far each bob adds. */
private const val DESCENT_TILT = 12f
private const val SCATTER_TILT = 28f
private const val HOP_TILT = 6f

/** How far each bob lifts it, as a share of its own size. */
private const val HOP_SHARE = 0.1f

/** One animal's visit to the foot of the big emoji, and how long the scene goes without it after. */
private data class Round(
    /** How large it is, as a share of the scene's height. */
    val sizeShare: Float,
    /** How long one visit and the wait after it take together. */
    val cycleMillis: Int,
    /** The share of that cycle spent on the scene; the rest of it is the wait. */
    val runShare: Float,
    /** The share of the visit spent walking in before the loop starts. */
    val approachShare: Float,
    /** The share of it spent going round; whatever is left over is the walk out. */
    val orbitShare: Float,
    /**
     * How many times round it goes. Half-turns only: a whole number of turns would bring it back to
     * the side it came in on, and leaving the way it arrived is not going round anything.
     */
    val turns: Float,
    /** Which side it comes in from: -1 the left, 1 the right. It leaves by the other one. */
    val entrySide: Float,
    /** How many times it bobs over one visit. */
    val hops: Float,
    /** How far into its cycle it is when the scene opens; see [Scamper.startFraction]. */
    val startFraction: Float
)

/**
 * The visits, hand-picked like [DRIFTS] and [SCAMPERS]. Two is enough: this happens at the foot of
 * the big emoji, where there is only so much room, and two animals circling it at once would look
 * less like a visit than an infestation.
 */
private val ROUNDS = listOf(
    Round(0.13f, 23_000, 0.17f, 0.22f, 0.56f, 1.5f, -1f, 7f, 0.06f),
    Round(0.12f, 34_000, 0.13f, 0.26f, 0.50f, 1.5f, 1f, 9f, 0.55f)
)

/** Where the ground is, how wide the loop is and how deep, as shares of the scene's height. */
private const val GROUND_LINE = 0.17f
private const val ORBIT_RADIUS = 0.16f
private const val ORBIT_DEPTH = 0.05f

/** How far out to the side a visit starts and ends, as a share of the scene's height. */
private const val EDGE_REACH = 0.6f

/** The share of the walk in, and of the walk out, spent fading. */
private const val ROUND_FADE_SHARE = 0.45f

/** How much smaller the far side of a loop is drawn than the near side. */
private const val DEPTH_SCALE = 0.18f

/** How much of the scene's height the big emoji takes up. */
private const val CENTER_EMOJI_SHARE = 0.5f

/** How wide the band of lanes is, as a share of the scene's height. */
private const val LANE_BAND = 1.1f

/** How far the big emoji leans to either side, and how long it takes to lean back. */
private const val SWAY_DEGREES = 2.5f
private const val SWAY_DURATION_MILLIS = 2_600

/** The share of a fall spent fading into the scene, and the share spent fading back out of it. */
private const val FADE_IN_FRACTION = 0.12f
private const val FADE_OUT_FRACTION = 0.2f
