/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.game

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Hex(val q: Int, val r: Int) {
    val row: Int get() = r + (q - (q and 1)) / 2
    fun neighbors() = directions.map { Hex(q + it.q, r + it.r) }
    fun distance(other: Hex) = (abs(q - other.q) + abs(q + r - other.q - other.r) + abs(r - other.r)) / 2
    companion object {
        val directions = listOf(Hex(1, 0), Hex(1, -1), Hex(0, -1), Hex(-1, 0), Hex(-1, 1), Hex(0, 1))
        fun at(column: Int, row: Int) = Hex(column, row - (column - (column and 1)) / 2)
        fun round(q: Float, r: Float): Hex {
            var x = q.roundToInt(); var z = r.roundToInt(); val y = (-q - r).roundToInt()
            val dx = abs(x - q); val dz = abs(z - r); val dy = abs(y + q + r)
            if (dx > dy && dx > dz) x = -y - z else if (dz > dy) z = -x - y
            return Hex(x, z)
        }
        fun fromPixel(x: Float, y: Float, radius: Float): Hex =
            round((2f / 3f * x) / radius, (-x / 3f + sqrt(3f) / 3f * y) / radius)
    }
}

enum class Terrain(val label: String, val moveCost: Int, val defense: Int) {
    GRASS("Grassland", 1, 40), FOREST("Forest", 2, 50), HILLS("Hills", 2, 50),
    WATER("Shallow water", 3, 20), VILLAGE("Village", 1, 60),
    CASTLE("Castle", 1, 60), KEEP("Keep", 1, 60), ROAD("Road", 1, 40), BRIDGE("Bridge", 1, 40);
    val isCastle: Boolean get() = this == CASTLE || this == KEEP
}
enum class Alignment { LAWFUL, NEUTRAL, CHAOTIC }
enum class DamageType { BLADE, PIERCE, IMPACT, FIRE, COLD, ARCANE }
enum class Special { NONE, SLOW, MAGICAL, MARKSMAN, FIRSTSTRIKE }
enum class TimeOfDay(val label: String, val lawfulBonus: Int) {
    DAWN("Dawn", 0), MORNING("Morning", 25), AFTERNOON("Afternoon", 25),
    DUSK("Dusk", 0), FIRST_WATCH("First watch", -25), SECOND_WATCH("Second watch", -25);
    companion object { fun atTurn(turn: Int) = entries[(turn - 1).coerceAtLeast(0) % entries.size] }
}
data class Attack(val name: String, val damage: Int, val strikes: Int, val type: DamageType,
                  val ranged: Boolean = false, val special: Special = Special.NONE)
data class UnitType(
    val id: String, val name: String, val sprite: String, val maxHp: Int, val maxMoves: Int,
    val cost: Int, val alignment: Alignment, val attacks: List<Attack>, val level: Int = 1,
    val xpNeeded: Int = 40, val advancesTo: String? = null, val woodland: Boolean = false,
    val forestDefense: Int = 50, val healing: Int = 0, val leadership: Boolean = false,
    val resistances: Map<DamageType, Int> = emptyMap()
) {
    fun defense(terrain: Terrain): Int = if (terrain == Terrain.FOREST) forestDefense else terrain.defense
    fun movement(terrain: Terrain): Int = if (woodland && terrain == Terrain.FOREST) 1 else terrain.moveCost
}
data class BattleUnit(
    val id: String, val type: UnitType, val side: Int, val hex: Hex,
    val hp: Int = type.maxHp, val moves: Int = type.maxMoves, val attacksLeft: Int = 1,
    val xp: Int = 0, val leader: Boolean = false, val slowed: Boolean = false,
    val poisoned: Boolean = false, val acted: Boolean = false
) {
    val level get() = type.level
    val ready get() = moves > 0 || attacksLeft > 0
}
data class Side(val id: Int, val name: String, val gold: Int, val baseIncome: Int = 2)
data class MovePlan(val path: List<Hex>, val cost: Int, val stops: Boolean = false)
data class UndoMove(val unit: BattleUnit, val villages: Map<Hex, Int>, val log: List<String>)
data class Strike(val actor: String, val target: String, val damage: Int, val hit: Boolean)
data class CombatReport(val number: Int, val attacker: BattleUnit, val defender: BattleUnit,
                        val weapon: Attack, val retaliation: Attack?, val strikes: List<Strike>)
data class AttackForecast(val weapon: Attack, val damage: Int, val chance: Int,
                          val retaliation: Attack?, val returnDamage: Int, val returnChance: Int)
data class GameState(
    val terrain: Map<Hex, Terrain>, val units: List<BattleUnit>, val sides: List<Side>,
    val currentSide: Int = 1, val turn: Int = 1, val selected: String? = null,
    val villages: Map<Hex, Int> = emptyMap(), val winner: Int? = null,
    val scenario: String = "crossing", val vsAi: Boolean = true,
    val rngState: Long = 20261017L, val nextUnitId: Int = 10,
    val log: List<String> = listOf("Capture villages and defeat the enemy leader."),
    val undo: UndoMove? = null, val combat: CombatReport? = null, val combatNumber: Int = 0
) {
    val time get() = TimeOfDay.atTurn(turn)
    val selectedUnit get() = units.find { it.id == selected }
    val message get() = log.lastOrNull().orEmpty()
    fun at(hex: Hex) = units.find { it.hex == hex }
    fun side(id: Int) = sides.first { it.id == id }
    fun villageCount(id: Int) = villages.count { it.value == id }
    fun income(id: Int): Int {
        val villages = villageCount(id)
        val upkeep = units.filter { it.side == id && !it.leader }.sumOf { it.level }
        return side(id).baseIncome + 2 * villages - (upkeep - villages).coerceAtLeast(0)
    }
    fun note(text: String) = copy(log = (log + text).takeLast(30))
}

/** Numeric unit profiles adapted from Wesnoth 1.18.6 data/core/units (GPL-2.0-or-later).
 * Original project and individual art authors are credited in asset-provenance.json. */
object UnitCatalog {
    val fighter = UnitType("fighter", "Elvish Fighter", "elf_fighter", 33, 5, 14, Alignment.NEUTRAL,
        listOf(Attack("Sword", 5, 4, DamageType.BLADE), Attack("Bow", 3, 3, DamageType.PIERCE, true)),
        xpNeeded = 37, advancesTo = "hero", woodland = true, forestDefense = 60)
    val archer = UnitType("archer", "Elvish Archer", "elf_archer", 29, 6, 17, Alignment.NEUTRAL,
        listOf(Attack("Sword", 5, 2, DamageType.BLADE), Attack("Bow", 5, 4, DamageType.PIERCE, true)),
        xpNeeded = 44, advancesTo = "ranger", woodland = true, forestDefense = 70)
    val shaman = UnitType("shaman", "Elvish Shaman", "elf_shaman", 26, 5, 15, Alignment.NEUTRAL,
        listOf(Attack("Staff", 3, 2, DamageType.IMPACT), Attack("Entangle", 3, 2, DamageType.IMPACT, true, Special.SLOW)),
        xpNeeded = 32, advancesTo = "druid", woodland = true, forestDefense = 70, healing = 4)
    val captain = UnitType("captain", "Elvish Captain", "elf_captain", 47, 5, 32, Alignment.NEUTRAL,
        listOf(Attack("Sword", 7, 4, DamageType.BLADE), Attack("Bow", 5, 3, DamageType.PIERCE, true)),
        level = 2, xpNeeded = 100, woodland = true, forestDefense = 60, leadership = true)
    val hero = UnitType("hero", "Elvish Hero", "elf_hero", 51, 5, 32, Alignment.NEUTRAL,
        listOf(Attack("Sword", 8, 4, DamageType.BLADE), Attack("Bow", 6, 3, DamageType.PIERCE, true)),
        level = 2, xpNeeded = 100, woodland = true, forestDefense = 60)
    val ranger = UnitType("ranger", "Elvish Ranger", "elf_ranger", 42, 6, 31, Alignment.NEUTRAL,
        listOf(Attack("Sword", 7, 3, DamageType.BLADE), Attack("Bow", 7, 4, DamageType.PIERCE, true)),
        level = 2, xpNeeded = 100, woodland = true, forestDefense = 70)
    val druid = UnitType("druid", "Elvish Druid", "elf_druid", 36, 5, 34, Alignment.NEUTRAL,
        listOf(Attack("Staff", 4, 2, DamageType.IMPACT), Attack("Entangle", 4, 2, DamageType.IMPACT, true, Special.SLOW),
            Attack("Faerie fire", 6, 3, DamageType.ARCANE, true, Special.MAGICAL)),
        level = 2, xpNeeded = 100, woodland = true, forestDefense = 70, healing = 8)
    val grunt = UnitType("grunt", "Orcish Grunt", "orc_grunt", 38, 5, 12, Alignment.CHAOTIC,
        listOf(Attack("Sword", 9, 2, DamageType.BLADE)), xpNeeded = 42, advancesTo = "warrior")
    val orcArcher = UnitType("orc_archer", "Orcish Archer", "orc_archer", 32, 5, 14, Alignment.CHAOTIC,
        listOf(Attack("Dagger", 3, 2, DamageType.BLADE), Attack("Bow", 5, 3, DamageType.PIERCE, true),
            Attack("Fire arrow", 7, 2, DamageType.FIRE, true)), xpNeeded = 30, advancesTo = "crossbow")
    val warrior = UnitType("warrior", "Orcish Warrior", "orc_warrior", 58, 5, 28, Alignment.CHAOTIC,
        listOf(Attack("Greatsword", 10, 3, DamageType.BLADE)), level = 2, xpNeeded = 100)
    val crossbow = UnitType("crossbow", "Orcish Crossbowman", "orc_crossbow", 43, 5, 26, Alignment.CHAOTIC,
        listOf(Attack("Dagger", 4, 3, DamageType.BLADE), Attack("Crossbow", 10, 3, DamageType.PIERCE, true)),
        level = 2, xpNeeded = 100)
    val all = listOf(fighter, archer, shaman, captain, hero, ranger, druid, grunt, orcArcher, warrior, crossbow).associateBy { it.id }
    fun recruits(side: Int) = if (side == 1) listOf(fighter, archer, shaman) else listOf(grunt, orcArcher)
}
