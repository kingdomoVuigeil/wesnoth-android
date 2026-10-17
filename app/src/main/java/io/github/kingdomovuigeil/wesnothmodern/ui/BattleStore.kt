/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.ui

import android.content.Context
import android.util.Base64
import io.github.kingdomovuigeil.wesnothmodern.game.GameState
import io.github.kingdomovuigeil.wesnothmodern.game.SaveCodec

class BattleStore(context: Context) {
    private val preferences = context.getSharedPreferences("battles-v2", Context.MODE_PRIVATE)
    fun load(slot: String = "auto"): GameState? = runCatching {
        preferences.getString(slot, null)?.let { SaveCodec.decode(Base64.decode(it, Base64.NO_WRAP)) }
    }.getOrNull()
    fun save(state: GameState, slot: String = "auto"): Boolean = preferences.edit()
        .putString(slot, Base64.encodeToString(SaveCodec.encode(state), Base64.NO_WRAP)).commit()
}
