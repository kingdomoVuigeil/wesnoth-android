/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.game

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {
    @Test fun `hex distance and neighbors follow axial geometry`() {
        assertEquals(3, Hex(0,0).distance(Hex(2,1)))
        assertEquals(6, Hex(2,2).neighbors().distinct().size)
    }
    @Test fun `movement honors terrain costs and board bounds`() {
        val state = demoGame()
        val reachable = GameEngine(Random(1)).reachable(state, "elf-1")
        assertTrue(Hex(2,2) in reachable)
        assertFalse(Hex(7,5) in reachable)
        assertFalse(Hex(6,3) in reachable)
    }
    @Test fun `attack spends attack and never heals defender`() {
        val base = demoGame()
        val close = base.copy(units=base.units.map { if(it.id=="elf-1") it.copy(hex=Hex(5,3)) else it }, selected="elf-1")
        val result = GameEngine(Random(4)).attack(close, Hex(6,3))
        assertEquals(0, result.units.first { it.id=="elf-1" }.attacksLeft)
        assertTrue(result.units.none { it.id=="orc-1" } || result.units.first { it.id=="orc-1" }.hp <= UnitCatalog.grunt.maxHp)
    }
    @Test fun `end turn refreshes next side`() {
        val result = GameEngine(Random(1)).endTurn(demoGame())
        assertEquals(2, result.currentSide)
        assertEquals(1, result.turn)
        assertNull(result.selected)
    }
}
