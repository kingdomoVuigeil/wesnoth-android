/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class GameEngineTest {
    private val engine = GameEngine()
    private fun field(units: List<BattleUnit>, turn: Int = 1): GameState = GameState(
        buildMap { for (q in 0..8) for (r in 0..7) put(Hex(q, r), Terrain.GRASS) }, units,
        listOf(Side(1, "Elves", 50), Side(2, "Orcs", 50)), turn = turn, vsAi = false)
    private fun blue(hex: Hex = Hex(1, 2), type: UnitType = UnitCatalog.fighter, leader: Boolean = true) = BattleUnit("blue", type, 1, hex, leader = leader)
    private fun red(hex: Hex = Hex(2, 2), type: UnitType = UnitCatalog.grunt, leader: Boolean = true) = BattleUnit("red", type, 2, hex, leader = leader)

    @Test fun hexCoordinatesRoundTripAtDifferentZooms() {
        for (q in -8..12) for (r in -8..12) for (radius in listOf(11f, 36f, 112f)) {
            val x = radius * 1.5f * q; val y = radius * sqrt(3f) * (r + q / 2f)
            assertEquals(Hex(q, r), Hex.fromPixel(x, y, radius))
            assertEquals(Hex.at(q, Hex(q, r).row), Hex(q, r))
        }
        assertEquals(6, Hex(0, 0).neighbors().distinct().size)
    }
    @Test fun cubeRoundingCorrectsIndependentAxialRounding() {
        assertEquals(Hex(1, 0), Hex.round(.55f, .30f))
        assertEquals(1, Hex(0, 0).distance(Hex(1, -1)))
    }
    @Test fun woodlandMovementAndWaterCostsAreApplied() {
        val base = field(listOf(blue(), red(Hex(8, 7))))
        val terrain = base.terrain + (Hex(2, 2) to Terrain.FOREST) + (Hex(1, 3) to Terrain.WATER)
        val state = base.copy(terrain = terrain)
        assertEquals(1, engine.movement(state, "blue").getValue(Hex(2, 2)).cost)
        assertEquals(3, engine.movement(state, "blue").getValue(Hex(1, 3)).cost)
    }
    @Test fun reachableAndExecutedMoveUseExactlyTheSamePathCost() {
        val state = field(listOf(blue(), red(Hex(6, 5))))
        engine.movement(state, "blue").forEach { (hex, plan) ->
            val moved = engine.move(state, "blue", hex).units.first { it.id == "blue" }
            assertEquals(hex, moved.hex)
            assertEquals(if (plan.stops) 0 else 5 - plan.cost, moved.moves)
        }
    }
    @Test fun enemyZoneStopsMovementAndOccupiedHexIsNeverDestination() {
        val state = field(listOf(blue(Hex(0, 2)), red(Hex(3, 2))))
        val plans = engine.movement(state, "blue")
        assertTrue(plans.getValue(Hex(2, 2)).stops)
        assertFalse(Hex(3, 2) in plans)
        val result = engine.move(state, "blue", Hex(2, 2))
        assertEquals(0, result.units.first { it.id == "blue" }.moves)
    }
    @Test fun friendlyUnitsCanBeTraversedWithoutBeingOverwritten() {
        val state = field(listOf(blue(Hex(0, 0)), blue(Hex(1, 0)).copy(id = "friend"), red(Hex(8, 7))))
        val plans = engine.movement(state, "blue")
        assertFalse(Hex(1, 0) in plans)
        assertTrue(Hex(2, 0) in plans)
    }
    @Test fun villageCaptureEndsMovementAndUndoRestoresOwnership() {
        val initial = field(listOf(blue(), red(Hex(8, 7)))).copy(terrain = field(emptyList()).terrain + (Hex(2, 2) to Terrain.VILLAGE))
        val moved = engine.move(initial, "blue", Hex(2, 2))
        assertEquals(1, moved.villages[Hex(2, 2)])
        assertEquals(0, moved.selectedUnit!!.moves)
        val restored = engine.undo(moved)
        assertEquals(initial.units, restored.units)
        assertEquals(initial.villages, restored.villages)
    }
    @Test fun invalidAndWrongSideCommandsDoNotMutateState() {
        val state = field(listOf(blue(), red()))
        assertEquals(state, engine.move(state, "red", Hex(4, 4)))
        assertEquals(state, engine.attack(state, "red", "blue"))
        assertEquals(state, engine.attack(state, "blue", "red", 99))
        assertEquals(state, engine.recruit(state, "grunt", Hex(4, 4)))
    }
    @Test fun meleeCombatAlternatesRetaliationAndExhaustsAttacker() {
        val state = field(listOf(blue(), red()))
        val result = engine.attack(state, "blue", "red")
        assertEquals(listOf("blue", "red", "blue", "red", "blue", "blue"), result.combat!!.strikes.map { it.actor })
        result.units.find { it.id == "blue" }?.let { assertEquals(0, it.moves); assertEquals(0, it.attacksLeft) }
        assertNull(result.undo)
    }
    @Test fun rangedAttackAgainstGruntHasNoRetaliation() {
        val state = field(listOf(blue(type = UnitCatalog.archer), red()))
        val forecast = engine.forecast(state, state.units[0], state.units[1], 1)
        assertNull(forecast.retaliation)
        assertEquals(4, engine.attack(state, "blue", "red", 1).combat!!.strikes.size)
    }
    @Test fun timeOfDayAndSlowModifyDamage() {
        val a = blue(); val d = red()
        val day = field(listOf(a, d), 2); val night = day.copy(turn = 5)
        assertEquals(7, engine.damage(day, d, a, d.type.attacks[0]))
        assertEquals(11, engine.damage(night, d, a, d.type.attacks[0]))
        assertEquals(6, engine.damage(night, d.copy(slowed = true), a, d.type.attacks[0]))
    }
    @Test fun entangleSlowsTheTargetAndOnlyMatchingRangeRetaliates() {
        val state = field(listOf(blue(type = UnitCatalog.shaman), red())).copy(rngState = 1)
        val result = engine.attack(state, "blue", "red", 1)
        assertTrue(result.units.first { it.id == "red" }.slowed)
        val orcTurn = engine.endTurn(result)
        assertEquals(3, orcTurn.units.first { it.id == "red" }.moves)
        assertFalse(engine.endTurn(orcTurn).units.first { it.id == "red" }.slowed)
    }
    @Test fun defenseDependsOnUnitAndTerrain() {
        val state = field(listOf(blue(), red(type = UnitCatalog.archer))).copy(terrain = field(emptyList()).terrain + (Hex(2, 2) to Terrain.FOREST))
        assertEquals(30, engine.chance(state, state.units[1], state.units[0].type.attacks[0], true))
    }
    @Test fun leadershipBoostsNearbyLowerLevelUnit() {
        val attacker = blue(leader = false)
        val captain = blue(Hex(1, 1), UnitCatalog.captain).copy(id = "captain")
        val state = field(listOf(attacker, captain, red()))
        assertEquals(6, engine.damage(state, attacker, state.units.last(), attacker.type.attacks[0]))
    }
    @Test fun recruitmentRequiresKeepConnectedCastleGoldAndFreeHex() {
        val initial = newGame(vsAi = false)
        val available = engine.recruitHexes(initial)
        assertTrue(available.isNotEmpty())
        val recruited = engine.recruit(initial, "fighter", available.first())
        assertEquals(initial.units.size + 1, recruited.units.size)
        assertEquals(36, recruited.side(1).gold)
        assertEquals(0, recruited.selectedUnit!!.moves)
        assertEquals(0, recruited.selectedUnit!!.attacksLeft)
        assertEquals(recruited, engine.recruit(recruited, "fighter", available.first()))
        assertEquals(initial.copy(sides = initial.sides.map { it.copy(gold = 0) }),
            engine.recruit(initial.copy(sides = initial.sides.map { it.copy(gold = 0) }), "fighter", available.first()))
    }
    @Test fun incomeIncludesVillagesAndLevelBasedUpkeep() {
        val state = field(listOf(blue(leader = false), red())).copy(villages = mapOf(Hex(4, 4) to 1))
        assertEquals(4, state.income(1))
        assertEquals(2, state.copy(villages = emptyMap()).income(2))
    }
    @Test fun villageAndShamanHealingAreAppliedAtStartOfOwnTurn() {
        val a = blue().copy(hp = 10, acted = true)
        val state = field(listOf(a, red(Hex(8, 7)))).copy(currentSide = 2, terrain = field(emptyList()).terrain + (a.hex to Terrain.VILLAGE))
        assertEquals(18, engine.endTurn(state).units.first { it.id == "blue" }.hp)
        val shaman = blue(Hex(1, 1), UnitCatalog.shaman).copy(id = "healer")
        val noVillage = state.copy(terrain = field(emptyList()).terrain, units = state.units + shaman)
        assertEquals(14, engine.endTurn(noVillage).units.first { it.id == "blue" }.hp)
    }
    @Test fun killingLeaderEndsGameAndBlocksFurtherActions() {
        val state = field(listOf(blue(type = UnitCatalog.archer), red().copy(hp = 1))).copy(rngState = 1)
        val won = engine.attack(state, "blue", "red", 1)
        assertEquals(1, won.winner)
        assertEquals(won, engine.endTurn(won))
        assertEquals(won, engine.move(won, "blue", Hex(3, 3)))
    }
    @Test fun advancementRestoresHealthAndChangesSprite() {
        val state = field(listOf(blue(type = UnitCatalog.archer).copy(xp = 43, hp = 12), red().copy(hp = 1))).copy(rngState = 1)
        val promoted = engine.attack(state, "blue", "red", 1).units.first { it.id == "blue" }
        assertEquals(UnitCatalog.ranger, promoted.type)
        assertEquals(UnitCatalog.ranger.maxHp, promoted.hp)
    }
    @Test fun savesPreserveRandomnessAndRejectCorruptData() {
        val initial = field(listOf(blue(), red()))
        val loaded = SaveCodec.decode(SaveCodec.encode(initial))
        assertEquals(initial, loaded)
        assertEquals(engine.attack(initial, "blue", "red"), engine.attack(loaded, "blue", "red"))
        assertThrows(IllegalArgumentException::class.java) { SaveCodec.decode(byteArrayOf()) }
        assertThrows(Exception::class.java) { SaveCodec.decode(SaveCodec.encode(initial).take(20).toByteArray()) }
    }
    @Test fun aiReturnsControlAndNeverOverlapsUnitsOrOverspends() {
        var state = newGame("patrol")
        repeat(8) {
            if (state.winner == null) {
                state = engine.endTurn(state)
                val steps = BattleAi().playTurn(state)
                assertTrue(steps.size < 40)
                steps.forEach { s ->
                    assertEquals(s.units.size, s.units.map { it.hex }.distinct().size)
                    assertTrue(s.sides.all { it.gold >= 0 })
                    assertTrue(s.units.all { it.hex in s.terrain && it.hp > 0 })
                }
                state = steps.last()
                assertTrue(state.currentSide == 1 || state.winner != null)
            }
        }
    }
    @Test fun aiAndCombatAreReproducibleFromTheSameSave() {
        val state = engine.endTurn(newGame("patrol"))
        assertEquals(BattleAi().playTurn(state), BattleAi().playTurn(SaveCodec.decode(SaveCodec.encode(state))))
    }
}
