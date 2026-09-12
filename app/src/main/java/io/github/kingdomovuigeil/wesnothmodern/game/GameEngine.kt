/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.game

import java.util.PriorityQueue
import kotlin.random.Random

class GameEngine(private val random: Random = Random.Default) {
    fun select(state: GameState, hex: Hex): GameState {
        val unit = state.units.find { it.hex == hex && it.side == state.currentSide }
        return state.copy(selected = unit?.id, message = unit?.let { "${it.type.name}: ${it.hp}/${it.type.maxHp} HP" } ?: "Select your unit")
    }

    fun reachable(state: GameState, unitId: String): Set<Hex> {
        val unit = state.units.first { it.id == unitId }
        val occupied = state.units.filter { it.id != unitId }.map { it.hex }.toSet()
        val costs = mutableMapOf(unit.hex to 0)
        val queue = PriorityQueue(compareBy<Pair<Hex, Int>> { it.second }).apply { add(unit.hex to 0) }
        while (queue.isNotEmpty()) {
            val (at, cost) = queue.remove()
            if (cost != costs[at]) continue
            at.neighbors().filter { it in state.terrain && it !in occupied }.forEach { next ->
                val nextCost = cost + (state.terrain[next]?.moveCost ?: 99)
                if (nextCost <= unit.moves && nextCost < (costs[next] ?: Int.MAX_VALUE)) {
                    costs[next] = nextCost; queue.add(next to nextCost)
                }
            }
        }
        return costs.keys - unit.hex
    }

    fun move(state: GameState, destination: Hex): GameState {
        val id = state.selected ?: return state
        val unit = state.units.first { it.id == id }
        if (destination !in reachable(state, id)) return state.copy(message = "That hex is out of reach")
        val spent = shortestCost(state, unit, destination)
        return state.copy(units = state.units.map { if (it.id == id) it.copy(hex = destination, moves = it.moves-spent) else it }, message = "${unit.type.name} moved")
    }

    fun attack(state: GameState, targetHex: Hex, attackIndex: Int = 0): GameState {
        val attacker = state.units.find { it.id == state.selected } ?: return state
        val defender = state.units.find { it.hex == targetHex && it.side != attacker.side } ?: return state
        if (attacker.hex.distance(defender.hex) != 1 || attacker.attacksLeft == 0) return state.copy(message = "Cannot attack that unit")
        val weapon = attacker.type.attacks.getOrNull(attackIndex) ?: attacker.type.attacks.first()
        val defense = state.terrain[defender.hex]?.defense ?: 40
        var hp = defender.hp
        repeat(weapon.strikes) { if (random.nextInt(100) >= defense) hp -= weapon.damage }
        val survivors = state.units.mapNotNull {
            when (it.id) { attacker.id -> it.copy(attacksLeft = 0, moves = 0); defender.id -> if (hp > 0) it.copy(hp = hp) else null; else -> it }
        }
        val won = if (survivors.none { it.side != attacker.side }) attacker.side else null
        return state.copy(units = survivors, selected = attacker.id, message = if (hp <= 0) "${defender.type.name} defeated" else "${defender.type.name}: $hp HP", winner = won)
    }

    fun endTurn(state: GameState): GameState {
        val next = state.sides.firstOrNull { it.id > state.currentSide }?.id ?: state.sides.first().id
        val turn = if (next == state.sides.first().id) state.turn + 1 else state.turn
        val units = state.units.map { if (it.side == next) it.copy(moves = it.type.maxMoves, attacksLeft = 1) else it }
        return state.copy(currentSide = next, turn = turn, selected = null, units = units, message = "${state.sides.first { it.id == next }.name}'s turn")
    }

    private fun shortestCost(state: GameState, unit: Unit, destination: Hex): Int {
        val occupied = state.units.filter { it.id != unit.id }.map { it.hex }.toSet()
        var frontier = mapOf(unit.hex to 0)
        val visited = mutableMapOf(unit.hex to 0)
        while (frontier.isNotEmpty()) {
            val (at, cost) = frontier.minBy { it.value }
            if (at == destination) return cost
            frontier = frontier - at
            at.neighbors().filter { it in state.terrain && it !in occupied }.forEach { next ->
                val value = cost + state.terrain.getValue(next).moveCost
                if (value < (visited[next] ?: Int.MAX_VALUE)) { visited[next] = value; frontier = frontier + (next to value) }
            }
        }
        return unit.moves
    }
}

fun demoGame(): GameState {
    val terrain = buildMap {
        for (q in 0..7) for (r in 0..5) put(Hex(q,r), when {
            q == 3 && r in 1..3 -> Terrain.FOREST
            q == 5 && r == 3 -> Terrain.HILLS
            (q == 1 && r == 2) || (q == 6 && r == 3) -> Terrain.VILLAGE
            else -> Terrain.GRASS
        })
    }
    return GameState(terrain, listOf(Unit("elf-1", UnitCatalog.fighter, 1, Hex(1,2)), Unit("orc-1", UnitCatalog.grunt, 2, Hex(6,3))), listOf(Side(1,"Alliance",40), Side(2,"Orcs",40)))
}
