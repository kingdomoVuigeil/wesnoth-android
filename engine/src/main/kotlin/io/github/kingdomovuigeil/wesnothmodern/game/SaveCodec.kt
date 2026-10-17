/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.game

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Versioned data, no JVM object deserialization. Includes RNG state for exact continuation. */
object SaveCodec {
    fun encode(state: GameState): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { out ->
            out.writeUTF("WESNOTH-MODERN"); out.writeInt(2)
            out.writeUTF(state.scenario); out.writeBoolean(state.vsAi)
            out.writeInt(state.currentSide); out.writeInt(state.turn); out.writeInt(state.winner ?: 0)
            out.writeLong(state.rngState); out.writeInt(state.nextUnitId); out.writeInt(state.combatNumber)
            out.writeUTF(state.selected.orEmpty())
            out.writeInt(state.terrain.size)
            state.terrain.forEach { (h, t) -> out.writeInt(h.q); out.writeInt(h.r); out.writeUTF(t.name) }
            out.writeInt(state.sides.size)
            state.sides.forEach { out.writeInt(it.id); out.writeUTF(it.name); out.writeInt(it.gold); out.writeInt(it.baseIncome) }
            out.writeInt(state.villages.size)
            state.villages.forEach { (h, owner) -> out.writeInt(h.q); out.writeInt(h.r); out.writeInt(owner) }
            out.writeInt(state.units.size)
            state.units.forEach {
                out.writeUTF(it.id); out.writeUTF(it.type.id); out.writeInt(it.side)
                out.writeInt(it.hex.q); out.writeInt(it.hex.r); out.writeInt(it.hp); out.writeInt(it.moves)
                out.writeInt(it.attacksLeft); out.writeInt(it.xp); out.writeBoolean(it.leader)
                out.writeBoolean(it.slowed); out.writeBoolean(it.poisoned); out.writeBoolean(it.acted)
            }
            out.writeInt(state.log.size); state.log.forEach(out::writeUTF)
        }
        buffer.toByteArray()
    }
    fun decode(bytes: ByteArray): GameState {
        require(bytes.size in 1..1_000_000) { "Invalid save size" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readUTF() == "WESNOTH-MODERN" && input.readInt() == 2) { "Unsupported save version" }
            val scenario = input.readUTF(); val ai = input.readBoolean()
            val side = input.readInt(); val turn = input.readInt(); val winner = input.readInt().takeIf { it != 0 }
            val rng = input.readLong(); val next = input.readInt(); val combat = input.readInt()
            val selected = input.readUTF().takeIf { it.isNotEmpty() }
            fun count(max: Int): Int = input.readInt().also { require(it in 0..max) { "Corrupt save count" } }
            val terrain = buildMap { repeat(count(4096)) { put(Hex(input.readInt(), input.readInt()), Terrain.valueOf(input.readUTF())) } }
            val sides = List(count(8)) { Side(input.readInt(), input.readUTF(), input.readInt(), input.readInt()) }
            val villages = buildMap { repeat(count(4096)) { put(Hex(input.readInt(), input.readInt()), input.readInt()) } }
            val units = List(count(500)) {
                val id = input.readUTF(); val type = requireNotNull(UnitCatalog.all[input.readUTF()]) { "Unknown unit type" }
                BattleUnit(id, type, input.readInt(), Hex(input.readInt(), input.readInt()), input.readInt(), input.readInt(),
                    input.readInt(), input.readInt(), input.readBoolean(), input.readBoolean(), input.readBoolean(), input.readBoolean())
            }
            val log = List(count(30)) { input.readUTF() }
            val sideIds = sides.map { it.id }.toSet()
            require(sides.isNotEmpty() && side in sideIds && turn in 1..31 && next > 0)
            require(units.map { it.id }.distinct().size == units.size && units.map { it.hex }.distinct().size == units.size)
            require(units.all { it.hex in terrain && it.side in sideIds && it.hp in 1..it.type.maxHp && it.moves >= 0 && it.attacksLeft in 0..1 })
            require(villages.all { (h, owner) -> terrain[h] == Terrain.VILLAGE && owner in sideIds })
            require(winner == null || winner in sideIds)
            require(input.available() == 0) { "Trailing save data" }
            GameState(terrain, units, sides, side, turn, selected?.takeIf { id -> units.any { it.id == id } }, villages,
                winner, scenario, ai, rng, next, log, combatNumber = combat)
        }
    }
}
