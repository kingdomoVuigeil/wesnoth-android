/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.game

data class Scenario(val id: String, val title: String, val subtitle: String, val description: String)
val scenarios = listOf(
    Scenario("crossing", "Greenford Crossing", "River valley · 12 × 9", "Secure the river villages, recruit a warband, and defeat the orc leader within 30 turns."),
    Scenario("woodland", "The Whispering Wood", "Forest skirmish · 11 × 8", "Use the forest for cover. Keep a shaman near your fighters and strike before nightfall."),
    Scenario("patrol", "First Patrol", "Quick battle · 7 × 6", "A smaller training battle with an enemy nearby. Learn movement, cover and attack previews.")
)
fun newGame(scenario: String = "crossing", vsAi: Boolean = true, seed: Long = 20261017L): GameState {
    val small = scenario == "patrol"
    val columns = if (small) 7 else if (scenario == "woodland") 11 else 12
    val rows = if (small) 6 else if (scenario == "woodland") 8 else 9
    val terrain = linkedMapOf<Hex, Terrain>()
    for (q in 0 until columns) for (row in 0 until rows) {
        terrain[Hex.at(q, row)] = when {
            !small && scenario == "crossing" && q in 5..6 -> if (row == 3 || row == 6) Terrain.BRIDGE else Terrain.WATER
            row == rows / 2 -> Terrain.ROAD
            (q * 17 + row * 11) % (if (scenario == "woodland") 5 else 9) < 2 -> Terrain.FOREST
            (q * 3 + row * 7) % 19 == 2 -> Terrain.HILLS
            else -> Terrain.GRASS
        }
    }
    val blue = Hex.at(1, rows / 2); val red = Hex.at(columns - 2, rows / 2 - 1)
    for (keep in listOf(blue, red)) {
        keep.neighbors().filter { it in terrain }.forEach { terrain[it] = Terrain.CASTLE }
        terrain[keep] = Terrain.KEEP
    }
    val villages = if (small) listOf(Hex.at(3, 1), Hex.at(3, 4)) else
        listOf(Hex.at(3, 1), Hex.at(3, rows - 2), Hex.at(columns - 4, 1), Hex.at(columns - 4, rows - 2), Hex.at(4, 4), Hex.at(columns - 4, 4))
    villages.forEach { terrain[it] = Terrain.VILLAGE }
    val units = listOf(
        BattleUnit("blue-leader", UnitCatalog.captain, 1, blue, leader = true),
        BattleUnit("blue-fighter", UnitCatalog.fighter, 1, Hex.at(2, rows / 2)),
        BattleUnit("blue-archer", UnitCatalog.archer, 1, Hex.at(1, rows / 2 + 1)),
        BattleUnit("blue-shaman", UnitCatalog.shaman, 1, Hex.at(2, rows / 2 + 1)),
        BattleUnit("red-leader", UnitCatalog.warrior, 2, red, leader = true),
        BattleUnit("red-grunt", UnitCatalog.grunt, 2, Hex.at(columns - 3, rows / 2)),
        BattleUnit("red-archer", UnitCatalog.orcArcher, 2, Hex.at(columns - 2, rows / 2 + 1))
    )
    return GameState(terrain, units, listOf(Side(1, "Woodland Alliance", 50), Side(2, "Ashen Raiders", 38)),
        scenario = scenario, vsAi = vsAi, rngState = seed)
}
fun demoGame() = newGame()
