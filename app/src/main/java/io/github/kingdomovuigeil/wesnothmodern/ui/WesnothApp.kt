/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kingdomovuigeil.wesnothmodern.game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun WesnothApp(initialGame: GameState? = null) {
    val context = LocalContext.current
    val art = rememberArt()
    val store = remember { BattleStore(context) }
    val engine = remember { GameEngine() }
    val scope = rememberCoroutineScope()
    var game by remember { mutableStateOf(initialGame ?: store.load() ?: newGame()) }
    var inBattle by rememberSaveable { mutableStateOf(initialGame != null) }
    var hasSave by remember { mutableStateOf(store.load() != null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var inspected by remember { mutableStateOf<Hex?>(null) }
    var destination by remember { mutableStateOf<Hex?>(null) }
    var attackTarget by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var combatBusy by remember { mutableStateOf(false) }
    val blocked = aiBusy || combatBusy || (game.vsAi && game.currentSide == 2)

    // Serialize autosaves in order, including after app rotation and during AI turns.
    LaunchedEffect(Unit) {
        snapshotFlow { inBattle to game }.collect { (active, state) ->
            if (active) { withContext(Dispatchers.IO) { store.save(state) }; hasSave = true }
        }
    }
    LaunchedEffect(notice) { if (notice != null) { delay(3000); notice = null } }
    LaunchedEffect(game.combatNumber) {
        combatBusy = false
        if (game.combat != null) {
            combatBusy = true
            delay((game.combat!!.strikes.size * 170L + 400).coerceAtLeast(700))
            combatBusy = false
        }
    }
    LaunchedEffect(inBattle, game.currentSide, game.vsAi, game.winner) {
        if (inBattle && game.vsAi && game.currentSide == 2 && game.winner == null) {
            aiBusy = true
            try {
                val states = withContext(Dispatchers.Default) { BattleAi(engine).playTurn(game) }
                for (state in states) {
                    game = state; inspected = null; destination = null
                    delay(if (state.combat != null) state.combat!!.strikes.size * 170L + 500 else 480)
                }
            } finally { aiBusy = false }
        }
    }
    BackHandler(inBattle) { dialog = "menu" }

    fun openHex(hex: Hex) {
        if (blocked || game.winner != null) return
        val unit = game.at(hex)
        val selected = game.selectedUnit
        inspected = hex
        when {
            unit?.side == game.currentSide -> { game = engine.select(game, hex); destination = null }
            unit != null && selected != null && engine.canAttack(game, selected, unit) -> {
                attackTarget = unit.id; destination = null
            }
            unit == null && selected != null && hex in engine.reachable(game, selected.id) -> destination = hex
            else -> { destination = null; if (unit == null && selected != null) notice = "Outside this unit's movement range" }
        }
    }
    fun nextUnit() {
        val ready = game.units.filter { it.side == game.currentSide && it.ready }
        if (ready.isEmpty()) { notice = "All units have acted"; return }
        val next = ready[(ready.indexOfFirst { it.id == game.selected } + 1) % ready.size]
        game = engine.select(game, next.hex); inspected = next.hex; destination = null
    }
    fun endTurn() { game = engine.endTurn(game); inspected = null; destination = null; dialog = null }

    FrontierTheme {
        Surface(color = Night, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (!inBattle) HomeScreen(art, hasSave, onContinue = {
                    store.load()?.let { game = it; inBattle = true; inspected = null; destination = null }
                }, onNew = { dialog = "new" }, onHelp = { dialog = "help" }, onCredits = { dialog = "credits" })
                else BattleScreen(game, engine, art, inspected, destination, notice, blocked,
                    onHex = ::openHex, onMenu = { dialog = "menu" }, onNext = ::nextUnit,
                    onRecruit = { dialog = "recruit" }, onUndo = { game = engine.undo(game); destination = null; inspected = game.selectedUnit?.hex },
                    onEnd = { if (game.units.any { it.side == game.currentSide && it.ready }) dialog = "end" else endTurn() },
                    onMove = { game.selected?.let { id -> destination?.let { game = engine.move(game, id, it) } }; destination = null; inspected = game.selectedUnit?.hex },
                    onCancel = { destination = null })

                if (inBattle && game.winner != null && !combatBusy && dialog == null) {
                    AlertDialog(onDismissRequest = {}, title = { Text(if (game.winner == 1) "Victory at the frontier" else "The battle is lost") },
                        text = { Text("${game.side(game.winner!!).name} won in ${game.turn} rounds.\n\n${game.message}") },
                        confirmButton = { TextButton(onClick = { inBattle = false }) { Text("Return to menu") } },
                        dismissButton = { TextButton(onClick = { dialog = "new" }) { Text("New battle") } })
                }
            }
        }
        when (dialog) {
            "new" -> NewBattleDialog(onDismiss = { dialog = null }) { scenario, ai ->
                game = newGame(scenario, ai, System.currentTimeMillis() and 0xffffffffL)
                inBattle = true; inspected = null; destination = null; attackTarget = null; dialog = null
            }
            "help" -> HelpDialog { dialog = null }
            "credits" -> CreditsDialog(context) { dialog = null }
            "recruit" -> RecruitDialog(game, engine, art, onDismiss = { dialog = null }) { type, hex ->
                game = engine.recruit(game, type, hex); inspected = game.selectedUnit?.hex; destination = null; dialog = null
            }
            "end" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("End your turn?") },
                text = { Text("${game.units.count { it.side == game.currentSide && it.ready }} units can still move or attack. The other side will act next.") },
                confirmButton = { TextButton(onClick = ::endTurn) { Text("End turn now") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Keep playing") } })
            "restart" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Restart this battle?") },
                text = { Text("Your current battle will be replaced. Your manual save is kept.") },
                confirmButton = { TextButton(onClick = { game = newGame(game.scenario, game.vsAi); inspected = null; destination = null; dialog = null }) { Text("Restart battle") } },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } })
            "menu" -> BattleMenuDialog(blocked, store.load("manual") != null, onDismiss = { dialog = null },
                onSave = { scope.launch { val ok = withContext(Dispatchers.IO) { store.save(game, "manual") }; notice = if (ok) "Battle saved" else "Save failed" }; dialog = null },
                onLoad = { store.load("manual")?.let { game = it; inspected = null; destination = null; notice = "Saved battle loaded" }; dialog = null },
                onHome = { inBattle = false; dialog = null }, onRestart = { dialog = "restart" },
                onHelp = { dialog = "help" }, onCredits = { dialog = "credits" })
        }
        val defender = game.units.find { it.id == attackTarget }
        val attacker = game.selectedUnit
        if (attackTarget != null && attacker != null && defender != null && !blocked) {
            AttackDialog(game, engine, art, attacker, defender, onDismiss = { attackTarget = null }) { index ->
                game = engine.attack(game, attacker.id, defender.id, index); attackTarget = null; inspected = game.selectedUnit?.hex
            }
        }
    }
}

@Composable private fun HomeScreen(art: Art, hasSave: Boolean, onContinue: () -> Unit, onNew: () -> Unit,
                                    onHelp: () -> Unit, onCredits: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Panel, Night)))) {
        val wide = maxWidth > 650.dp
        val hero: @Composable () -> Unit = {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("THE BATTLE FOR WESNOTH", fontSize = 11.sp, color = Gold, letterSpacing = 3.sp)
                Text("The frontier\nis yours.", style = MaterialTheme.typography.headlineLarge, fontSize = if (wide) 42.sp else 36.sp)
                Text("Raise a warband. Hold the villages.\nOutthink the enemy.", color = Muted, fontSize = 15.sp)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(if (wide) 92.dp else 72.dp)) {
                    Sprite("elf_archer", null, art, Modifier.size(84.dp))
                    Sprite("elf_captain", null, art, Modifier.size(104.dp))
                    Sprite("elf_shaman", null, art, Modifier.size(84.dp))
                }
            }
        }
        val menu: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("WESNOTH  /  FRONTIERS", style = MaterialTheme.typography.titleMedium, color = Parchment)
                Text("Native Android · Offline skirmishes", color = Muted, fontSize = 12.sp)
                if (hasSave) SmallButton("Continue battle", onContinue, Modifier.fillMaxWidth(), primary = true)
                SmallButton("New battle", onNew, Modifier.fillMaxWidth(), primary = !hasSave)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("How to play", onHelp, Modifier.weight(1f))
                    SmallButton("Credits", onCredits, Modifier.weight(1f))
                }
                Text("0.2.0  ·  Unofficial community prototype", fontSize = 11.sp, color = Muted)
            }
        }
        if (wide) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { hero() }
            Box(Modifier.width(344.dp).verticalScroll(rememberScrollState())) { menu() }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { hero(); menu() }
    }
}

@Composable private fun BattleScreen(state: GameState, engine: GameEngine, art: Art, inspected: Hex?, destination: Hex?,
                                     notice: String?, blocked: Boolean, onHex: (Hex) -> Unit, onMenu: () -> Unit,
                                     onNext: () -> Unit, onRecruit: () -> Unit, onUndo: () -> Unit,
                                     onEnd: () -> Unit, onMove: () -> Unit, onCancel: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val short = maxHeight < 440.dp
        val compactControls = short || maxWidth < 440.dp
        val unit = inspected?.let(state::at) ?: state.selectedUnit
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallButton("Menu", onMenu, Modifier.testTag("battle-menu"))
                Column(Modifier.weight(1f)) {
                    Text("Round ${state.turn} / 30 · ${state.time.label}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    Text(if (blocked && state.currentSide == 2 && state.vsAi) "Raiders are thinking…" else state.side(state.currentSide).name,
                        fontSize = 11.sp, color = if (state.currentSide == 1) Blue else Red, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${state.side(state.currentSide).gold} gold", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val net = state.income(state.currentSide)
                    Text("${state.villageCount(state.currentSide)} villages · ${if (net >= 0) "+" else ""}$net / turn", color = Muted, fontSize = 10.sp)
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    BattleBoard(state, engine, art, destination, blocked, Modifier.fillMaxSize(), onHex)
                    if (blocked) Surface(color = Night.copy(alpha = .86f), shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.TopCenter).padding(10.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Gold)
                            Text(if (state.currentSide == 2 && state.vsAi) "Enemy turn" else "Combat", fontSize = 12.sp)
                        }
                    }
                }
                if (wide) Column(Modifier.width(236.dp).fillMaxHeight().background(Panel).padding(16.dp)
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(scenarios.first { it.id == state.scenario }.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text("Defeat the red leader. Protect your captain.", fontSize = 12.sp, color = Muted)
                    HorizontalDivider(color = Muted.copy(alpha = .2f))
                    UnitPanel(state, unit, inspected, art, compact = false)
                }
            }
            if (!wide && !short) Surface(color = Panel) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) { UnitPanel(state, unit, inspected, art, compact = true) }
            }
            if (destination != null && state.selectedUnit != null && !blocked) {
                val plan = engine.movement(state, state.selectedUnit!!.id)[destination]
                if (plan != null) Row(Modifier.fillMaxWidth().background(PanelRaised).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${state.terrain.getValue(destination).label} · ${plan.cost} MP${if (plan.stops) " · stops movement" else ""}",
                        Modifier.weight(1f), color = Parchment, fontSize = 12.sp)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    SmallButton("Confirm move", onMove, Modifier.testTag("confirm-move"), primary = true)
                }
            } else Text(notice ?: if (unit != null && short) "${unit.type.name}  ${unit.hp}/${unit.type.maxHp} HP · ${unit.moves} MP" else
                if (state.selected == null) "Tap a ${if (state.currentSide == 1) "blue" else "red"} unit to see where it can move." else state.message,
                Modifier.fillMaxWidth().background(Panel).padding(horizontal = 14.dp, vertical = 5.dp),
                color = if (notice != null) Gold else Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().background(Night).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("Undo", onUndo, enabled = !blocked && state.undo != null && state.winner == null)
                SmallButton(if (compactControls) "Next" else "Next unit", onNext, enabled = !blocked && state.winner == null)
                Spacer(Modifier.weight(1f))
                SmallButton("Recruit", onRecruit, Modifier.testTag("recruit"), enabled = !blocked && state.winner == null)
                SmallButton("End turn", onEnd, Modifier.testTag("end-turn"), enabled = !blocked && state.winner == null, primary = true)
            }
        }
    }
}

@Composable private fun UnitPanel(state: GameState, unit: BattleUnit?, inspected: Hex?, art: Art, compact: Boolean) {
    if (unit == null) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(inspected?.let { state.terrain[it]?.label } ?: "Your next move", style = MaterialTheme.typography.titleMedium)
            Text(if (inspected != null) "Move a unit here to take advantage of the terrain." else
                "Select a unit, tap a highlighted hex, then confirm. Tap an adjacent enemy to preview combat.", fontSize = 12.sp, color = Muted)
            if (!compact) Text("Blue rings: your units\nRed rings: enemies\nGold hexes: movement range\nVillages: income and healing", fontSize = 12.sp, lineHeight = 21.sp, color = Muted)
        }
        return
    }
    val terrain = state.terrain.getValue(unit.hex)
    val detail: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(unit.type.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (unit.leader) Text("LEADER", color = Gold, fontSize = 9.sp)
            }
            LinearProgressIndicator(progress = { unit.hp.toFloat() / unit.type.maxHp }, Modifier.fillMaxWidth().height(5.dp),
                color = if (unit.hp > unit.type.maxHp / 2) Mint else Red, trackColor = Night)
            Text("${unit.hp}/${unit.type.maxHp} HP   ·   ${unit.moves} MP   ·   ${unit.type.defense(terrain)}% cover", fontSize = 11.sp, color = Muted)
            Text("${terrain.label} · Level ${unit.level} · XP ${unit.xp}/${unit.type.xpNeeded}", fontSize = 10.sp, color = Muted)
        }
    }
    if (compact) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sprite(unit.type.sprite, null, art, Modifier.size(64.dp), side = unit.side); Box(Modifier.weight(1f)) { detail() }
    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Sprite(unit.type.sprite, unit.type.name, art, Modifier.size(104.dp).align(Alignment.CenterHorizontally), side = unit.side); detail()
        unit.type.attacks.forEach { attack ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(attack.name, fontSize = 13.sp); Text(if (attack.ranged) "Ranged · ${attack.type.name.lowercase()}" else "Melee · ${attack.type.name.lowercase()}", fontSize = 10.sp, color = Muted) }
                Text("${attack.damage} × ${attack.strikes}", color = Gold, fontWeight = FontWeight.SemiBold)
            }
        }
        if (unit.type.healing > 0) Text("Heals adjacent allies +${unit.type.healing} HP each turn.", fontSize = 11.sp, color = Mint)
        if (unit.type.leadership) Text("Leadership strengthens nearby lower-level allies.", fontSize = 11.sp, color = Gold)
        if (unit.slowed) Text("Slowed: half damage and movement.", fontSize = 11.sp, color = Red)
        Text(if (unit.side != state.currentSide) "Enemy unit. Move next to it to attack." else if (unit.attacksLeft == 0)
            "This unit has acted. Ready next turn." else "Tap a gold hex to plan a move. Tap an adjacent enemy to choose a weapon.", fontSize = 11.sp, color = Muted)
    }
}
