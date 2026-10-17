/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.game

/** Bounded, deterministic tactical AI. Returned states let the UI show one action at a time. */
class BattleAi(private val engine: GameEngine = GameEngine()) {
    fun playTurn(initial: GameState): List<GameState> {
        if (initial.winner != null || initial.currentSide != 2) return emptyList()
        var state = initial
        val steps = mutableListOf<GameState>()
        repeat(2) { n ->
            val hex = engine.recruitHexes(state).sortedWith(compareBy<Hex> { it.q }.thenBy { it.r }).firstOrNull()
            val choice = UnitCatalog.recruits(2).getOrNull((state.turn + n) % 2)
            if (hex != null && choice != null && state.side(2).gold >= choice.cost && state.units.count { it.side == 2 } < 11) {
                state = engine.recruit(state, choice.id, hex); steps += state
            }
        }
        val order = state.units.filter { it.side == 2 && it.moves > 0 }.sortedBy { if (it.leader) 1 else 0 }.map { it.id }
        for (id in order) {
            if (state.winner != null) break
            var unit = state.units.find { it.id == id } ?: continue
            val enemies = state.units.filter { it.side != unit.side }
            val plans = engine.movement(state, id)
            fun score(hex: Hex): Double {
                val distance = enemies.minOfOrNull { it.hex.distance(hex) } ?: 0
                val cover = unit.type.defense(state.terrain.getValue(hex)) / 15.0
                val adjacent = enemies.count { it.hex.distance(hex) == 1 }
                val village = if (state.terrain[hex] == Terrain.VILLAGE && state.villages[hex] != 2) 7.0 else 0.0
                val healing = if (unit.hp < unit.type.maxHp * .6 && state.terrain[hex] == Terrain.VILLAGE) 12.0 else 0.0
                val stayLeader = if (unit.leader && state.turn <= 3 && hex == unit.hex) 15.0 else 0.0
                val exposure = if (unit.leader || unit.hp < unit.type.maxHp / 3) adjacent * 5.0 else 0.0
                val attack = if (unit.attacksLeft > 0 && adjacent > 0) 9.0 else 0.0
                return -distance * 2.0 + cover + village + healing + stayLeader + attack - exposure
            }
            val destination = (plans.keys + unit.hex).sortedWith(compareBy<Hex> { it.q }.thenBy { it.r }).maxByOrNull(::score)
            if (destination != null && destination != unit.hex) {
                state = engine.move(state, id, destination); steps += state
            }
            unit = state.units.find { it.id == id } ?: continue
            data class Choice(val target: BattleUnit, val weapon: Int, val score: Double)
            val options = state.units.filter { engine.canAttack(state, unit, it) }.flatMap { target ->
                unit.type.attacks.indices.map { index ->
                    val f = engine.forecast(state, unit, target, index)
                    val dealt = f.damage * f.weapon.strikes * f.chance / 100.0
                    val received = f.returnDamage * (f.retaliation?.strikes ?: 0) * f.returnChance / 100.0
                    Choice(target, index, dealt - received * .65 + if (dealt >= target.hp) 15.0 else 0.0)
                }
            }
            options.maxByOrNull { it.score }?.let {
                state = engine.attack(state, id, it.target.id, it.weapon); steps += state
            }
        }
        if (state.winner == null) steps += engine.endTurn(state)
        return steps
    }
}
