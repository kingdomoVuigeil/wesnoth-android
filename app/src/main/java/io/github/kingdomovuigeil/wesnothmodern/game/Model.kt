/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.game

import kotlin.math.abs

data class Hex(val q: Int, val r: Int) {
    fun neighbors() = DIRECTIONS.map { Hex(q + it.q, r + it.r) }
    fun distance(other: Hex): Int = (abs(q-other.q) + abs(q+r-other.q-other.r) + abs(r-other.r)) / 2
    companion object { private val DIRECTIONS = listOf(Hex(1,0), Hex(1,-1), Hex(0,-1), Hex(-1,0), Hex(-1,1), Hex(0,1)) }
}

enum class Terrain(val moveCost: Int, val defense: Int) {
    GRASS(1, 40), FOREST(2, 60), HILLS(2, 50), WATER(99, 20), VILLAGE(1, 60), CASTLE(1, 60), KEEP(1, 60)
}
enum class Alignment { LAWFUL, NEUTRAL, CHAOTIC }
enum class DamageType { BLADE, PIERCE, IMPACT, FIRE, COLD, ARCANE }
data class Attack(val name: String, val damage: Int, val strikes: Int, val type: DamageType, val ranged: Boolean = false)
data class UnitType(val id: String, val name: String, val maxHp: Int, val maxMoves: Int, val cost: Int, val alignment: Alignment, val attacks: List<Attack>)
data class Unit(
    val id: String, val type: UnitType, val side: Int, val hex: Hex,
    val hp: Int = type.maxHp, val moves: Int = type.maxMoves, val attacksLeft: Int = 1,
    val experience: Int = 0, val level: Int = 1
)
data class Side(val id: Int, val name: String, val gold: Int, val income: Int = 2)
data class GameState(
    val terrain: Map<Hex, Terrain>, val units: List<Unit>, val sides: List<Side>,
    val currentSide: Int = 1, val turn: Int = 1, val selected: String? = null,
    val message: String = "Select a unit", val winner: Int? = null
)

object UnitCatalog {
    val fighter = UnitType("elvish_fighter", "Elvish Fighter", 33, 5, 14, Alignment.NEUTRAL,
        listOf(Attack("Sword", 5, 4, DamageType.BLADE), Attack("Bow", 3, 3, DamageType.PIERCE, true)))
    val grunt = UnitType("orcish_grunt", "Orcish Grunt", 38, 5, 12, Alignment.CHAOTIC,
        listOf(Attack("Sword", 9, 2, DamageType.BLADE)))
}
