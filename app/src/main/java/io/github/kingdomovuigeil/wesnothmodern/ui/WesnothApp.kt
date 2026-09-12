/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.kingdomovuigeil.wesnothmodern.game.*
import kotlin.math.*

private val Ink = Color(0xFFE9E2CE)
private val Navy = Color(0xFF111A22)

@Composable fun WesnothApp() {
    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFFD9A441), surface=Navy, onSurface=Ink)) {
        var game by remember { mutableStateOf(demoGame()) }
        val engine = remember { GameEngine() }
        Surface(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameBoard(game, Modifier.weight(1f).fillMaxHeight()) { hex ->
                    val tapped = game.units.find { it.hex == hex }
                    game = when {
                        tapped?.side == game.currentSide -> engine.select(game, hex)
                        tapped != null && game.selected != null -> engine.attack(game, hex)
                        game.selected != null -> engine.move(game, hex)
                        else -> engine.select(game, hex)
                    }
                }
                Column(Modifier.width(240.dp).fillMaxHeight().background(Color(0xFF1B2935)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("WESNOTH", style=MaterialTheme.typography.headlineMedium, color=Color(0xFFD9A441))
                    Text("Turn ${game.turn}  •  Side ${game.currentSide}")
                    HorizontalDivider()
                    Text(game.message, modifier=Modifier.weight(1f))
                    if (game.winner != null) Text("Side ${game.winner} wins!", color=Color(0xFFFFD166))
                    Button(onClick={ game = engine.endTurn(game) }, modifier=Modifier.fillMaxWidth()) { Text("End turn") }
                    OutlinedButton(onClick={ game = demoGame() }, modifier=Modifier.fillMaxWidth()) { Text("Restart") }
                    Text("GPL-2.0-or-later • Prototype 0.1", style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable private fun GameBoard(state: GameState, modifier: Modifier, onHex: (Hex)->Unit) {
    val reachable = state.selected?.let { GameEngine().reachable(state,it) }.orEmpty()
    Canvas(modifier.background(Color(0xFF0D141A)).pointerInput(state) {
        detectTapGestures { p ->
            val size = 42f; val x = p.x - 54f; val y = p.y - 54f
            val q = ((sqrt(3f)/3f*x - y/3f)/size).roundToInt()
            val r = ((2f/3f*y)/size).roundToInt()
            onHex(Hex(q,r))
        }
    }) {
        val radius = 42f
        state.terrain.forEach { (hex, terrain) ->
            val center = Offset(54f + radius*sqrt(3f)*(hex.q+hex.r/2f), 54f + radius*1.5f*hex.r)
            val path = Path().apply { repeat(6) { i -> val a=(60*i-30)*PI/180; val px=center.x+radius*cos(a).toFloat(); val py=center.y+radius*sin(a).toFloat(); if(i==0) moveTo(px,py) else lineTo(px,py) }; close() }
            drawPath(path, when(terrain){ Terrain.FOREST->Color(0xFF285943); Terrain.HILLS->Color(0xFF796044); Terrain.VILLAGE->Color(0xFF9C7A3C); else->Color(0xFF4E7245) })
            drawPath(path, if(hex in reachable) Color(0x88FFD166) else Color(0x55304040), style=androidx.compose.ui.graphics.drawscope.Stroke(if(hex in reachable) 5f else 2f))
            state.units.find { it.hex == hex }?.let { unit ->
                drawCircle(if(unit.side==1) Color(0xFF4D9DE0) else Color(0xFFE15554), radius*0.54f, center)
                if(unit.id==state.selected) drawCircle(Color.White, radius*0.61f, center, style=androidx.compose.ui.graphics.drawscope.Stroke(4f))
                drawContext.canvas.nativeCanvas.apply {
                    val paint=android.graphics.Paint().apply{color=android.graphics.Color.WHITE;textAlign=android.graphics.Paint.Align.CENTER;textSize=20f;isFakeBoldText=true}
                    drawText(if(unit.side==1) "ELF" else "ORC",center.x,center.y+7f,paint)
                }
            }
        }
    }
}
