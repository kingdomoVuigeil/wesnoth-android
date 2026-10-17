/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.game

import java.util.PriorityQueue
import kotlin.math.roundToInt

class GameEngine {
    fun select(state: GameState, hex: Hex): GameState =
        if (state.winner != null) state else state.copy(selected = state.at(hex)?.takeIf { it.side == state.currentSide }?.id)

    /** Dijkstra search shared by previews and execution. Friendly units can be traversed. */
    fun movement(state: GameState, id: String): Map<Hex, MovePlan> {
        val unit = state.units.find { it.id == id } ?: return emptyMap()
        if (state.winner != null || unit.side != state.currentSide || unit.moves <= 0) return emptyMap()
        val occupied = state.units.associateBy { it.hex }
        val plans = mutableMapOf(unit.hex to MovePlan(listOf(unit.hex), 0))
        val queue = PriorityQueue(compareBy<Pair<Hex, Int>> { it.second }.thenBy { it.first.q }.thenBy { it.first.r })
        queue.add(unit.hex to 0)
        while (queue.isNotEmpty()) {
            val (at, cost) = queue.remove()
            val old = plans.getValue(at)
            if (old.cost != cost || old.stops) continue
            for (next in at.neighbors()) {
                val terrain = state.terrain[next] ?: continue
                if (occupied[next]?.side?.let { it != unit.side } == true) continue
                val nextCost = cost + unit.type.movement(terrain)
                if (nextCost > unit.moves || nextCost >= (plans[next]?.cost ?: Int.MAX_VALUE)) continue
                val capture = terrain == Terrain.VILLAGE && state.villages[next] != unit.side
                val zone = next.neighbors().any { neighbor -> occupied[neighbor]?.let { it.side != unit.side && it.level > 0 } == true }
                val plan = MovePlan(old.path + next, nextCost, capture || zone)
                plans[next] = plan
                queue.add(next to nextCost)
            }
        }
        return plans.filterKeys { it != unit.hex && occupied[it] == null }
    }
    fun reachable(state: GameState, id: String) = movement(state, id).keys

    fun move(state: GameState, id: String, destination: Hex): GameState {
        val unit = state.units.find { it.id == id } ?: return state
        val plan = movement(state, id)[destination] ?: return state
        val capture = state.terrain[destination] == Terrain.VILLAGE && state.villages[destination] != unit.side
        val moved = unit.copy(hex = destination, moves = if (plan.stops) 0 else unit.moves - plan.cost, acted = true)
        return state.copy(units = state.units.map { if (it.id == id) moved else it }, selected = id,
            villages = if (capture) state.villages + (destination to unit.side) else state.villages,
            undo = UndoMove(unit, state.villages, state.log), combat = null)
            .note(if (capture) "${unit.type.name} captured a village. +2 gold each turn." else "${unit.type.name} moved.")
    }
    fun undo(state: GameState): GameState {
        val undo = state.undo ?: return state
        if (state.winner != null || undo.unit.side != state.currentSide) return state
        return state.copy(units = state.units.map { if (it.id == undo.unit.id) undo.unit else it },
            villages = undo.villages, log = undo.log, selected = undo.unit.id, undo = null, combat = null)
    }

    fun canAttack(state: GameState, attacker: BattleUnit, defender: BattleUnit): Boolean =
        state.winner == null && attacker.side == state.currentSide && attacker.side != defender.side &&
            attacker.attacksLeft > 0 && attacker.hex.distance(defender.hex) == 1

    fun damage(state: GameState, unit: BattleUnit, target: BattleUnit, weapon: Attack): Int {
        val alignment = when (unit.type.alignment) {
            Alignment.NEUTRAL -> 0; Alignment.LAWFUL -> state.time.lawfulBonus; Alignment.CHAOTIC -> -state.time.lawfulBonus
        }
        val leader = state.units.filter { it.side == unit.side && it.type.leadership && it.level > unit.level && it.hex.distance(unit.hex) == 1 }
            .maxOfOrNull { 25 * (it.level - unit.level) } ?: 0
        val resistance = target.type.resistances[weapon.type] ?: 0
        val result = (weapon.damage * (100 + alignment + leader) / 100.0 * (100 - resistance) / 100.0).roundToInt().coerceAtLeast(1)
        return if (unit.slowed) (result + 1) / 2 else result
    }
    fun chance(state: GameState, target: BattleUnit, weapon: Attack, offense: Boolean): Int = when {
        weapon.special == Special.MAGICAL -> 70
        weapon.special == Special.MARKSMAN && offense -> (100 - target.type.defense(state.terrain.getValue(target.hex))).coerceAtLeast(60)
        else -> 100 - target.type.defense(state.terrain.getValue(target.hex))
    }
    private fun retaliation(state: GameState, defender: BattleUnit, attacker: BattleUnit, weapon: Attack): Attack? =
        defender.type.attacks.filter { it.ranged == weapon.ranged }.maxByOrNull {
            damage(state, defender, attacker, it) * it.strikes * chance(state, attacker, it, false)
        }
    fun forecast(state: GameState, attacker: BattleUnit, defender: BattleUnit, index: Int): AttackForecast {
        val weapon = attacker.type.attacks[index]
        val reply = retaliation(state, defender, attacker, weapon)
        return AttackForecast(weapon, damage(state, attacker, defender, weapon), chance(state, defender, weapon, true),
            reply, reply?.let { damage(state, defender, attacker, it) } ?: 0,
            reply?.let { chance(state, attacker, it, false) } ?: 0)
    }

    fun attack(state: GameState, attackerId: String, defenderId: String, index: Int = 0): GameState {
        var attacker = state.units.find { it.id == attackerId } ?: return state
        var defender = state.units.find { it.id == defenderId } ?: return state
        if (!canAttack(state, attacker, defender) || index !in attacker.type.attacks.indices) return state
        val beforeA = attacker; val beforeD = defender
        val weapon = attacker.type.attacks[index]
        val reply = retaliation(state, defender, attacker, weapon)
        val strikes = mutableListOf<Strike>()
        var rng = state.rngState
        fun hit(): Int { rng = (1664525L * rng + 1013904223L) and 0xffffffffL; return (rng % 100).toInt() }
        fun strike(fromAttacker: Boolean) {
            val source = if (fromAttacker) attacker else defender
            val target = if (fromAttacker) defender else attacker
            val attack = if (fromAttacker) weapon else reply ?: return
            val didHit = hit() < chance(state, target, attack, fromAttacker)
            val amount = if (didHit) damage(state, source, target, attack) else 0
            val changed = target.copy(hp = (target.hp - amount).coerceAtLeast(0),
                slowed = target.slowed || (didHit && attack.special == Special.SLOW))
            if (fromAttacker) defender = changed else attacker = changed
            strikes += Strike(source.id, target.id, amount, didHit)
        }
        val defenseFirst = reply?.special == Special.FIRSTSTRIKE && weapon.special != Special.FIRSTSTRIKE
        var a = 0; var d = 0
        while (attacker.hp > 0 && defender.hp > 0 && (a < weapon.strikes || d < (reply?.strikes ?: 0))) {
            val order = if (defenseFirst) listOf(false, true) else listOf(true, false)
            for (offense in order) {
                if (attacker.hp <= 0 || defender.hp <= 0) break
                if (offense && a < weapon.strikes) { strike(true); a++ }
                if (!offense && d < (reply?.strikes ?: 0)) { strike(false); d++ }
            }
        }
        attacker = attacker.copy(moves = 0, attacksLeft = 0, acted = true)
        fun gain(unit: BattleUnit, enemy: BattleUnit): BattleUnit {
            if (unit.hp <= 0) return unit
            val xp = unit.xp + if (enemy.hp <= 0) 8 * enemy.level.coerceAtLeast(1) else enemy.level
            val advance = unit.type.advancesTo?.let { UnitCatalog.all[it] }
            return if (xp >= unit.type.xpNeeded && advance != null)
                unit.copy(type = advance, hp = advance.maxHp, xp = xp - unit.type.xpNeeded, slowed = false, poisoned = false)
            else unit.copy(xp = xp)
        }
        val finalA = gain(attacker, defender); val finalD = gain(defender, attacker)
        val number = state.combatNumber + 1
        val survivors = state.units.mapNotNull { when (it.id) {
            attackerId -> finalA.takeIf { u -> u.hp > 0 }; defenderId -> finalD.takeIf { u -> u.hp > 0 }; else -> it
        } }
        var result = state.copy(units = survivors, selected = attackerId.takeIf { finalA.hp > 0 }, rngState = rng,
            undo = null, combatNumber = number,
            combat = CombatReport(number, beforeA, beforeD, weapon, reply, strikes))
        result = result.note("${beforeA.type.name} used ${weapon.name}: ${strikes.filter { it.actor == attackerId }.sumOf { it.damage }} damage.")
        if (defender.hp <= 0) result = result.note("${beforeD.type.name} defeated.")
        if (attacker.hp <= 0) result = result.note("${beforeA.type.name} defeated.")
        if (finalA.type != beforeA.type) result = result.note("${finalA.type.name} advanced to level ${finalA.level}!")
        if (finalD.type != beforeD.type) result = result.note("${finalD.type.name} advanced to level ${finalD.level}!")
        return victory(result)
    }

    fun recruitHexes(state: GameState): Set<Hex> {
        if (state.winner != null) return emptySet()
        val leader = state.units.find { it.side == state.currentSide && it.leader && state.terrain[it.hex] == Terrain.KEEP }
            ?: return emptySet()
        val visited = mutableSetOf(leader.hex); val todo = ArrayDeque<Hex>().apply { add(leader.hex) }
        while (todo.isNotEmpty()) for (hex in todo.removeFirst().neighbors()) {
            if (state.terrain[hex]?.isCastle == true && visited.add(hex)) todo.add(hex)
        }
        return visited.filter { state.at(it) == null }.toSet()
    }
    fun recruit(state: GameState, typeId: String, hex: Hex): GameState {
        val type = UnitCatalog.recruits(state.currentSide).find { it.id == typeId } ?: return state
        if (hex !in recruitHexes(state) || state.side(state.currentSide).gold < type.cost) return state
        val unit = BattleUnit("recruit-${state.nextUnitId}", type, state.currentSide, hex, moves = 0, attacksLeft = 0, acted = true)
        return state.copy(units = state.units + unit, sides = state.sides.map {
            if (it.id == state.currentSide) it.copy(gold = it.gold - type.cost) else it
        }, nextUnitId = state.nextUnitId + 1, selected = unit.id, undo = null, combat = null)
            .note("${type.name} recruited. Ready next turn.")
    }

    fun endTurn(state: GameState): GameState {
        if (state.winner != null) return state
        val next = state.sides[(state.sides.indexOfFirst { it.id == state.currentSide } + 1) % state.sides.size].id
        val turn = state.turn + if (next == state.sides.first().id) 1 else 0
        val cleansed = state.units.map { if (it.side == state.currentSide) it.copy(slowed = false) else it }
        val units = cleansed.map { unit ->
            if (unit.side != next) unit else {
                val village = state.terrain[unit.hex] == Terrain.VILLAGE
                val healer = cleansed.filter { it.side == next && it.id != unit.id && it.hex.distance(unit.hex) == 1 }
                    .maxOfOrNull { it.type.healing } ?: 0
                val healing = if (village) 8 else healer
                val hp = when {
                    unit.poisoned && healing == 0 -> (unit.hp - 8).coerceAtLeast(1)
                    unit.poisoned -> unit.hp
                    else -> (unit.hp + healing + if (!unit.acted) 2 else 0).coerceAtMost(unit.type.maxHp)
                }
                unit.copy(hp = hp, moves = if (unit.slowed) (unit.type.maxMoves + 1) / 2 else unit.type.maxMoves,
                    attacksLeft = 1, acted = false, poisoned = unit.poisoned && !village && healer < 8)
            }
        }
        val sides = state.sides.map { if (it.id == next) it.copy(gold = (it.gold + state.income(next)).coerceAtLeast(0)) else it }
        val result = state.copy(units = units, sides = sides, currentSide = next, turn = turn, selected = null, undo = null, combat = null)
            .note("${state.side(next).name}'s turn. ${TimeOfDay.atTurn(turn).label}.")
        return if (turn > 30) result.copy(winner = 2).note("Time is up. The raiders held their ground.") else victory(result)
    }
    private fun victory(state: GameState): GameState {
        val leaders = state.units.filter { it.leader }.map { it.side }.toSet()
        if (leaders.size != 1) return state
        return state.copy(winner = leaders.first(), undo = null).note("${state.side(leaders.first()).name} wins! Enemy leader defeated.")
    }
}
