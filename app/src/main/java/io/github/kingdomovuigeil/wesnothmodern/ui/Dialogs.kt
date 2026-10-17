/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kingdomovuigeil.wesnothmodern.game.*
import org.json.JSONObject

@Composable fun NewBattleDialog(onDismiss: () -> Unit, onStart: (String, Boolean) -> Unit) {
    var selected by remember { mutableStateOf("crossing") }
    var ai by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose your battlefield") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(ai, { ai = true }, label = { Text("Vs computer") })
                    FilterChip(!ai, { ai = false }, label = { Text("Pass & play") })
                }
                scenarios.forEach { scenario ->
                    Surface(Modifier.fillMaxWidth().clickable { selected = scenario.id }.testTag("scenario-${scenario.id}"),
                        shape = RoundedCornerShape(14.dp), color = if (selected == scenario.id) PanelRaised else Panel,
                        border = BorderStroke(1.dp, if (selected == scenario.id) Gold else Muted.copy(alpha = .2f))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(scenario.title, color = if (selected == scenario.id) Gold else Parchment, fontWeight = FontWeight.SemiBold)
                            Text(scenario.subtitle, color = Muted, fontSize = 11.sp)
                            if (selected == scenario.id) Text(scenario.description, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { onStart(selected, ai) }, Modifier.testTag("start-battle")) { Text("Start battle") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable fun RecruitDialog(state: GameState, engine: GameEngine, art: Art, onDismiss: () -> Unit, onRecruit: (String, Hex) -> Unit) {
    val spaces = engine.recruitHexes(state).sortedWith(compareBy<Hex> { it.distance(state.units.firstOrNull { u -> u.leader && u.side == state.currentSide }?.hex ?: it) }.thenBy { it.q }.thenBy { it.r })
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Recruit your warband") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${state.side(state.currentSide).gold} gold available", color = Gold, fontWeight = FontWeight.Bold)
                Text(if (spaces.isEmpty()) "Your leader must stand on the keep with an empty connected castle hex." else
                    "Recruits appear on an empty castle hex. They can act next turn.", color = Muted, fontSize = 12.sp)
                UnitCatalog.recruits(state.currentSide).forEach { type ->
                    Surface(shape = RoundedCornerShape(14.dp), color = PanelRaised) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Sprite(type.sprite, null, art, Modifier.size(64.dp), side = state.currentSide)
                                Column(Modifier.weight(1f)) {
                                    Text(type.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("${type.maxHp} HP · ${type.maxMoves} MP", color = Muted, fontSize = 11.sp)
                                    Text(if (type.healing > 0) "Heals +${type.healing} · Slows enemies" else if (type == UnitCatalog.archer) "Ranged specialist · 70% forest cover" else "Versatile frontline fighter", fontSize = 10.sp, color = Muted)
                                }
                            }
                            SmallButton("Recruit · ${type.cost} gold", { onRecruit(type.id, spaces.first()) },
                                Modifier.fillMaxWidth().testTag("recruit-${type.id}"), enabled = spaces.isNotEmpty() && state.side(state.currentSide).gold >= type.cost, primary = true)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable fun AttackDialog(state: GameState, engine: GameEngine, art: Art, attacker: BattleUnit, defender: BattleUnit,
                             onDismiss: () -> Unit, onAttack: (Int) -> Unit) {
    var index by remember(attacker.id, defender.id) { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose your attack") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Sprite(attacker.type.sprite, null, art, side = attacker.side); Text("${attacker.hp} HP", color = if (attacker.side == 1) Blue else Red, fontSize = 12.sp)
                    }
                    Text("VS", color = Muted, fontWeight = FontWeight.Bold)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Sprite(defender.type.sprite, null, art, side = defender.side); Text("${defender.hp} HP", color = if (defender.side == 1) Blue else Red, fontSize = 12.sp)
                    }
                }
                Text("${attacker.type.name} against ${defender.type.name}", fontSize = 12.sp)
                attacker.type.attacks.forEachIndexed { i, _ ->
                    val f = engine.forecast(state, attacker, defender, i)
                    Surface(Modifier.fillMaxWidth().clickable { index = i }.testTag("weapon-$i"), shape = RoundedCornerShape(12.dp),
                        color = if (index == i) PanelRaised else Panel,
                        border = BorderStroke(1.dp, if (index == i) Gold else Muted.copy(alpha = .2f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(f.weapon.name, color = if (index == i) Gold else Parchment, fontWeight = FontWeight.SemiBold)
                                Text("${f.damage} × ${f.weapon.strikes}", color = Gold, fontWeight = FontWeight.Bold)
                            }
                            Text("${if (f.weapon.ranged) "Ranged" else "Melee"} · ${f.chance}% hit chance · ${f.weapon.type.name.lowercase()}", fontSize = 11.sp, color = Muted)
                            Text(if (f.retaliation == null) "No retaliation" else
                                "Retaliation: ${f.returnDamage} × ${f.retaliation!!.strikes} · ${f.returnChance}% hit chance",
                                fontSize = 11.sp, color = if (f.retaliation == null) Mint else Red)
                            if (f.weapon.special == Special.SLOW) Text("A hit slows the enemy's damage and movement.", fontSize = 10.sp, color = Mint)
                        }
                    }
                }
                Text("Each strike rolls separately. Combat ends when a unit falls. Attacking ends this unit's movement.", fontSize = 11.sp, color = Muted)
            }
        }, confirmButton = { TextButton(onClick = { onAttack(index) }, Modifier.testTag("confirm-attack")) { Text("Attack") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable fun BattleMenuDialog(busy: Boolean, hasManualSave: Boolean, onDismiss: () -> Unit,
                                 onSave: () -> Unit, onLoad: () -> Unit, onHome: () -> Unit,
                                 onRestart: () -> Unit, onHelp: () -> Unit, onCredits: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Battle menu") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Progress is saved automatically after each action.", color = Muted, fontSize = 12.sp)
            SmallButton("Save battle", onSave, Modifier.fillMaxWidth(), enabled = !busy)
            SmallButton("Load saved battle", onLoad, Modifier.fillMaxWidth(), enabled = !busy && hasManualSave)
            SmallButton("How to play", onHelp, Modifier.fillMaxWidth())
            SmallButton("Credits & licenses", onCredits, Modifier.fillMaxWidth())
            SmallButton("Restart battle", onRestart, Modifier.fillMaxWidth(), enabled = !busy)
            SmallButton("Main menu", onHome, Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Resume") } })
}

@Composable fun HelpDialog(onDismiss: () -> Unit) {
    val sections = listOf(
        "Move with confidence" to "Tap a blue unit. Gold hexes show its movement range. Tap a destination to preview the path, then Confirm move. Undo reverses your last move until you attack, recruit or end the turn.",
        "Choose your battles" to "Move next to an enemy and tap it. Compare weapons, damage, hit chance and retaliation before confirming. A bow attacks an adjacent unit too; it does not shoot across the map.",
        "Read the terrain" to "Forest gives elves 60–70% cover. Cover is the chance to dodge a strike. Water costs more movement and gives poor cover. Entering an enemy's zone of control stops movement.",
        "Hold the villages" to "Capture a village by entering it. Capturing ends movement. Each owned village gives 2 gold per turn and supports one level of upkeep. Villages heal 8 HP at the start of your turn.",
        "Grow your army" to "Keep your leader on the keep and tap Recruit. A connected castle hex must be empty. New recruits act next turn. XP from combat advances eligible units and restores their health.",
        "Use your shaman" to "A shaman heals adjacent allies by 4 HP each turn. Entangle can slow an enemy. The captain's leadership strengthens adjacent lower-level allies.",
        "Time your attack" to "Lawful units deal more damage in daylight. Chaotic orcs deal more at night. Neutral elves are unaffected. The six-part cycle changes once per full round.",
        "Explore the map" to "Drag to pan. Pinch or use + and − to zoom. Fit shows the whole battlefield. Next unit cycles through allies that can still act. Portrait, landscape and unfolded screens are supported.",
        "Win the skirmish" to "Defeat the enemy leader and protect yours. The attacker loses if the battle lasts beyond 30 rounds. Select Vs computer or Pass & play when starting a battle.")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Field guide") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            sections.forEach { (title, body) -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Gold, fontWeight = FontWeight.SemiBold); Text(body, fontSize = 13.sp, lineHeight = 19.sp)
            } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Ready") } })
}

@Composable fun CreditsDialog(context: Context, onDismiss: () -> Unit) {
    var showLicense by remember { mutableStateOf(false) }
    var showArtLicense by remember { mutableStateOf(false) }
    val attribution = remember {
        runCatching {
            val assets = JSONObject(context.assets.open("asset-provenance.json").bufferedReader().use { it.readText() }).getJSONArray("assets")
            (0 until assets.length()).map { assets.getJSONObject(it).getString("author") }.distinct().joinToString("\n")
        }.getOrDefault("The Battle for Wesnoth art contributors")
    }
    val license = remember { context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() } }
    val artLicense = remember { context.assets.open("CC-BY-SA-4.0.txt").bufferedReader().use { it.readText() } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Credits & licenses") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Wesnoth Modern 0.2.0", color = Gold, fontWeight = FontWeight.SemiBold)
            Text("An unofficial Android reimplementation by Domokos Kovacs and contributors.\n\nOriginal art and unit data: The Battle for Wesnoth project, version 1.18.6.", fontSize = 13.sp)
            Text("Art contributors", fontWeight = FontWeight.SemiBold)
            Text(attribution, fontSize = 11.sp, color = Muted)
            Text("Original files are bundled unchanged. Exact sources, authors and licenses are recorded in asset-provenance.json, included in the source archive.", fontSize = 12.sp)
            Text("Art licenses: GPL-2.0-or-later and CC-BY-SA-4.0. The fighter attack frames by doofus-01 are CC-BY-SA-4.0; attribution for every asset is retained in the manifest.", fontSize = 12.sp)
            Text("GNU GPL version 2 or later. This is free software, supplied WITHOUT ANY WARRANTY. You may redistribute and modify it under the license terms. Source: github.com/kingdomoVuigeil/wesnoth-android", fontSize = 12.sp)
            TextButton(onClick = { showLicense = !showLicense }) { Text(if (showLicense) "Hide GNU license" else "Read GNU license") }
            if (showLicense) Text(license, fontSize = 10.sp, lineHeight = 15.sp)
            TextButton(onClick = { showArtLicense = !showArtLicense }) { Text(if (showArtLicense) "Hide art license" else "Read CC BY-SA 4.0") }
            if (showArtLicense) Text(artLicense, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
