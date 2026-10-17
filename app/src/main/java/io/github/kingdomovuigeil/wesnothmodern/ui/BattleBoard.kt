/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kingdomovuigeil.wesnothmodern.game.*
import kotlin.math.*

@Composable fun BattleBoard(state: GameState, engine: GameEngine, art: Art, destination: Hex?, blocked: Boolean,
                            modifier: Modifier = Modifier, onHex: (Hex) -> Unit) {
    val density = LocalDensity.current
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(state.scenario) { mutableFloatStateOf(1f) }
    var pan by remember(state.scenario) { mutableStateOf(Offset.Zero) }
    val hexes = state.terrain.keys.toList()
    fun raw(h: Hex) = Offset(1.5f * h.q, sqrt(3f) * (h.r + h.q / 2f))
    val rawPoints = remember(state.terrain) { hexes.map(::raw) }
    val middle = Offset((rawPoints.minOf { it.x } + rawPoints.maxOf { it.x }) / 2f,
        (rawPoints.minOf { it.y } + rawPoints.maxOf { it.y }) / 2f)
    val mapWidth = rawPoints.maxOf { it.x } - rawPoints.minOf { it.x } + 2.6f
    val mapHeight = rawPoints.maxOf { it.y } - rawPoints.minOf { it.y } + 3f
    val fit = min(dimensions.width / mapWidth, dimensions.height / mapHeight).coerceAtLeast(1f)
    val base = max(fit, with(density) { 20.dp.toPx() })
    val radius = base * zoom
    val origin = Offset(dimensions.width / 2f, dimensions.height / 2f) - middle * radius + pan
    fun project(h: Hex) = origin + raw(h) * radius
    LaunchedEffect(state.selected, dimensions) {
        if (dimensions.width > 0 && dimensions.height > 0) state.selectedUnit?.let { selected ->
            val point = project(selected.hex)
            if (point.x < radius || point.x > dimensions.width - radius || point.y < radius || point.y > dimensions.height - radius)
                pan += Offset(dimensions.width / 2f, dimensions.height / 2f) - point
        }
    }
    val plans = remember(state.terrain, state.units, state.currentSide, state.selected, state.winner) {
        state.selected?.let { engine.movement(state, it) }.orEmpty()
    }
    val route = destination?.let { plans[it]?.path }.orEmpty()
    val animation = remember { Animatable(1f) }
    LaunchedEffect(state.combatNumber) {
        if (state.combat != null) { animation.snapTo(0f); animation.animateTo(1f, tween(state.combat!!.strikes.size * 170 + 400, easing = LinearEasing)) }
    }
    val positions = state.units.associate { unit -> key(unit.id) {
        val at by animateOffsetAsState(Offset(unit.hex.q.toFloat(), unit.hex.r.toFloat()), tween(380), label = "unit-${unit.id}")
        unit.id to at
    } }
    val health = state.units.associate { unit -> key("hp-${unit.id}") {
        val hp by animateFloatAsState(unit.hp.toFloat(), tween(500), label = "hp-${unit.id}")
        unit.id to hp
    } }

    Box(modifier.clipToBounds().background(Night).onSizeChanged { dimensions = it }) {
        Canvas(Modifier.fillMaxSize().testTag("battlefield")
            .pointerInput(state.scenario, dimensions) {
                detectTransformGestures { centroid, delta, scale, _ ->
                    val previous = zoom
                    zoom = (zoom * scale).coerceIn(.55f, 3.5f)
                    val ratio = zoom / previous
                    val center = Offset(size.width / 2f, size.height / 2f)
                    pan = (pan + delta - (centroid - center - pan) * (ratio - 1f)).let {
                        Offset(it.x.coerceIn(-mapWidth * base * zoom / 2, mapWidth * base * zoom / 2),
                            it.y.coerceIn(-mapHeight * base * zoom / 2, mapHeight * base * zoom / 2))
                    }
                }
            }.pointerInput(state, radius, origin, blocked) {
                detectTapGestures(onDoubleTap = { zoom = if (zoom > 1.4f) 1f else 2f; pan = Offset.Zero }, onTap = { p ->
                    val local = p - origin
                    val hex = Hex.fromPixel(local.x, local.y, radius)
                    if (!blocked && hex in state.terrain) onHex(hex)
                })
            }) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF30423B), Color(0xFF142523)), center, size.maxDimension * .75f))
            val canvas = drawContext.canvas.nativeCanvas
            val imagePaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
            fun bitmap(name: String, center: Offset, width: Float, height: Float = width, alpha: Int = 255, flip: Boolean = false, side: Int? = null) {
                val bmp = (if (side == null) art[name] else art.forSide(name, side)) ?: return
                imagePaint.alpha = alpha
                canvas.save()
                if (flip) canvas.scale(-1f, 1f, center.x, center.y)
                canvas.drawBitmap(bmp, null, RectF(center.x - width / 2, center.y - height / 2, center.x + width / 2, center.y + height / 2), imagePaint)
                canvas.restore()
            }
            fun polygon(at: Offset, r: Float = radius): Path = Path().apply {
                for (i in 0..5) {
                    val angle = i * PI / 3
                    val x = at.x + r * cos(angle).toFloat(); val y = at.y + r * sin(angle).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            // Base textures first, then tall overlays sorted by depth.
            for ((hex, terrain) in state.terrain) {
                val c = project(hex)
                val variant = ((hex.q * 7 + hex.r * 3).absoluteValue % 3)
                val texture = when (terrain) {
                    Terrain.WATER -> "water"; Terrain.HILLS -> "hills"; Terrain.CASTLE -> "castle"
                    Terrain.KEEP -> "keep"; Terrain.VILLAGE -> if ((hex.q + hex.r) % 2 == 0) "village" else "village_elf"
                    else -> if (variant == 0) "grass" else "grass${variant + 1}"
                }
                bitmap(texture, c, radius * 2, radius * sqrt(3f))
                drawPath(polygon(c), Night.copy(alpha = .16f))
                if (terrain == Terrain.ROAD || terrain == Terrain.BRIDGE) {
                    if (terrain == Terrain.BRIDGE) {
                        drawRoundRect(Color(0xFF907550), c - Offset(radius * .86f, radius * .28f),
                            Size(radius * 1.72f, radius * .56f), CornerRadius(radius * .08f))
                        for (i in -3..3) drawLine(Color(0xFF4D4635), c + Offset(i * radius * .23f, -radius * .25f), c + Offset(i * radius * .23f, radius * .25f), radius * .035f)
                    } else {
                        for (n in hex.neighbors().filter { state.terrain[it] == Terrain.ROAD || state.terrain[it] == Terrain.BRIDGE || state.terrain[it]?.isCastle == true })
                            drawLine(Color(0xFFB19C70).copy(alpha = .45f), c, (project(n) + c) / 2f, radius * .32f, StrokeCap.Round)
                    }
                }
                drawPath(polygon(c), Color.Black.copy(alpha = .16f), style = Stroke(1f))
            }
            for (hex in hexes.filter { state.terrain[it] == Terrain.FOREST }.sortedBy { project(it).y }) {
                bitmap(if (hex.q % 2 == 0) "forest" else "pine", project(hex) - Offset(0f, radius * .16f), radius * 4f)
            }
            for ((hex, owner) in state.villages) {
                val c = project(hex) + Offset(radius * .55f, -radius * .2f)
                drawLine(Parchment.copy(alpha = .8f), c, c + Offset(0f, -radius * .62f), radius * .04f)
                val flag = Path().apply { moveTo(c.x, c.y - radius * .62f); lineTo(c.x + radius * .43f, c.y - radius * .48f); lineTo(c.x, c.y - radius * .32f); close() }
                drawPath(flag, if (owner == 1) Blue else Red)
            }
            if (!blocked && state.winner == null) {
                for (hex in plans.keys) {
                    drawPath(polygon(project(hex), radius * .95f), Gold.copy(alpha = .13f))
                    drawPath(polygon(project(hex), radius * .95f), Gold.copy(alpha = .5f), style = Stroke(with(density) { 1.dp.toPx() }))
                }
                state.selectedUnit?.let { selected ->
                    state.units.filter { engine.canAttack(state, selected, it) }.forEach {
                        drawPath(polygon(project(it.hex), radius * .94f), Red.copy(alpha = .8f), style = Stroke(with(density) { 2.dp.toPx() }))
                    }
                }
            }
            if (route.isNotEmpty()) {
                route.zipWithNext().forEach { (a, b) -> drawLine(Gold, project(a), project(b), radius * .09f, StrokeCap.Round) }
                route.drop(1).forEachIndexed { index, h ->
                    val c = project(h)
                    drawCircle(Night.copy(alpha = .9f), radius * .2f, c)
                    textPaint.color = Gold.toArgb(); textPaint.textSize = radius * .26f
                    canvas.drawText("${index + 1}", c.x, c.y + radius * .09f, textPaint)
                }
                drawPath(polygon(project(route.last()), radius * .98f), Parchment, style = Stroke(radius * .05f))
            }
            val report = state.combat
            val combatTime = if (report != null) animation.value * (report.strikes.size * 170 + 400) - 150 else -1f
            val strikeIndex = (combatTime / 170).toInt()
            val activeStrike = report?.strikes?.getOrNull(strikeIndex)?.takeIf { combatTime >= 0 && animation.value < 1 }
            val strikeFraction = ((combatTime % 170) / 170).coerceIn(0f, 1f)
            val ghosts = if (report != null && animation.value < 1) listOf(report.attacker, report.defender).filter { old -> state.units.none { it.id == old.id } } else emptyList()
            val visual = (state.units + ghosts).sortedBy { project(it.hex).y }
            for (unit in visual) {
                val position = positions[unit.id] ?: Offset(unit.hex.q.toFloat(), unit.hex.r.toFloat())
                val moving = abs(position.x - unit.hex.q) + abs(position.y - unit.hex.r) > .015f
                var c = origin + Offset(1.5f * position.x, sqrt(3f) * (position.y + position.x / 2f)) * radius
                val selected = unit.id == state.selected
                val team = if (unit.side == 1) Blue else Red
                val shadow = c + Offset(0f, radius * .42f)
                drawOval(Color.Black.copy(alpha = .25f), shadow - Offset(radius * .5f, radius * .14f), Size(radius, radius * .28f))
                drawOval(team.copy(alpha = .32f), shadow - Offset(radius * .58f, radius * .22f), Size(radius * 1.16f, radius * .44f), style = Stroke(radius * .08f))
                if (selected) drawPath(polygon(c, radius * .9f), Gold, style = Stroke(radius * .055f))
                var sprite = unit.type.sprite
                if (moving && sprite == "elf_fighter") sprite += "_run${((abs(position.x + position.y) * 16).toInt() % 10) + 1}"
                if (activeStrike?.actor == unit.id) {
                    val target = visual.firstOrNull { it.id == activeStrike.target }
                    if (target != null && report?.weapon?.ranged == false) c += (project(target.hex) - c) * (sin(strikeFraction * PI).toFloat() * .24f)
                    val count = if (unit.type.sprite.startsWith("orc")) 2 else 4
                    val actionSprite = unit.type.sprite + "_attack${(strikeFraction * count).toInt().coerceAtMost(count - 1) + 1}"
                    if (art[actionSprite] != null) sprite = actionSprite
                }
                val exhausted = unit.side == state.currentSide && unit.moves == 0 && unit.attacksLeft == 0
                bitmap(sprite, c - Offset(0f, radius * .18f), radius * 2.7f,
                    alpha = if (unit in ghosts) ((1f - animation.value) * 255).toInt() else if (exhausted) 170 else 255, flip = unit.side == 2, side = unit.side)
                val hp = health[unit.id] ?: unit.hp.toFloat()
                val left = c + Offset(-radius * .52f, radius * .69f)
                drawRoundRect(Night, left - Offset(radius * .04f, radius * .035f), Size(radius * 1.12f, radius * .16f), CornerRadius(2f))
                drawRoundRect(if (hp > unit.type.maxHp * .45f) Mint else Red, left,
                    Size(radius * 1.04f * (hp / unit.type.maxHp).coerceIn(0f, 1f), radius * .09f), CornerRadius(2f))
                if (unit.leader) {
                    val p = c + Offset(radius * .55f, -radius * .64f)
                    val crown = Path().apply { moveTo(p.x - radius * .18f, p.y); lineTo(p.x - radius * .2f, p.y - radius * .2f); lineTo(p.x - radius * .06f, p.y - radius * .1f); lineTo(p.x, p.y - radius * .3f); lineTo(p.x + radius * .06f, p.y - radius * .1f); lineTo(p.x + radius * .2f, p.y - radius * .2f); lineTo(p.x + radius * .18f, p.y); close() }
                    drawPath(crown, Gold)
                }
                if (unit.slowed) drawCircle(Color(0xFFC39CED), radius * .11f, c + Offset(-radius * .6f, -radius * .4f))
            }
            if (activeStrike != null && report != null) {
                val source = visual.find { it.id == activeStrike.actor }; val target = visual.find { it.id == activeStrike.target }
                if (source != null && target != null) {
                    val a = project(source.hex); val b = project(target.hex)
                    val weapon = if (source.id == report.attacker.id) report.weapon else report.retaliation
                    if (weapon?.ranged == true) {
                        val end = a + (b - a) * strikeFraction
                        drawLine(Gold, end - (b - a) * .18f, end, radius * .045f, StrokeCap.Round)
                    }
                    if (activeStrike.hit) drawCircle(Red.copy(alpha = (1 - strikeFraction) * .45f), radius * .6f, b)
                    textPaint.color = if (activeStrike.hit) Red.toArgb() else Parchment.toArgb()
                    textPaint.textSize = radius * .65f
                    textPaint.setShadowLayer(3f, 0f, 2f, android.graphics.Color.BLACK)
                    canvas.drawText(if (activeStrike.hit) "−${activeStrike.damage}" else "MISS", b.x, b.y - radius * (.8f + strikeFraction * .7f), textPaint)
                    textPaint.clearShadowLayer()
                }
            }
        }
        // Semantic hit regions share the exact camera projection with the visual board.
        // Pointer input remains on the canvas, so square accessibility bounds cannot steal edge taps.
        for (hex in hexes) {
            val c = project(hex); val edge = radius * 1.4f
            val label = state.at(hex)?.let { "${it.type.name}, ${if (it.side == 1) "blue" else "red"}, ${it.hp} health, ${it.moves} movement" }
                ?: "${state.terrain.getValue(hex).label}, column ${hex.q + 1}, row ${hex.row + 1}"
            Box(Modifier.offset { IntOffset((c.x - edge / 2).roundToInt(), (c.y - edge / 2).roundToInt()) }
                .size(with(density) { edge.toDp() })
                .testTag("hex-${hex.q}-${hex.r}").semantics {
                    contentDescription = label
                    onClick("Select hex") { if (!blocked) onHex(hex); true }
                })
        }
        Surface(Modifier.align(Alignment.TopEnd).padding(10.dp), shape = RoundedCornerShape(12.dp), color = Night.copy(alpha = .88f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(.55f) }, Modifier.width(44.dp)) { Text("−", fontSize = 22.sp) }
                TextButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(3.5f) }, Modifier.width(44.dp)) { Text("+", fontSize = 22.sp) }
                TextButton(onClick = { zoom = (fit / base).coerceAtLeast(.55f); pan = Offset.Zero }, Modifier.testTag("fit-map")) { Text("Fit", fontSize = 12.sp) }
            }
        }
        Text("Drag to pan · Pinch to zoom", Modifier.align(Alignment.BottomStart).padding(10.dp)
            .background(Night.copy(alpha = .8f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp), color = Muted, fontSize = 9.sp)
    }
}
