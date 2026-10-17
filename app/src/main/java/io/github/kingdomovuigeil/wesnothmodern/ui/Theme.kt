/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Night = Color(0xFF101D20)
val Panel = Color(0xFF1B2C2F)
val PanelRaised = Color(0xFF263B3D)
val Parchment = Color(0xFFF1E8D5)
val Muted = Color(0xFFA7B6AF)
val Gold = Color(0xFFE3BD77)
val Blue = Color(0xFF74BCEB)
val Red = Color(0xFFF28B79)
val Mint = Color(0xFF91C5A3)

@Composable fun FrontierTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Gold, onPrimary = Night, secondary = Mint,
            background = Night, surface = Panel, surfaceVariant = PanelRaised, onSurface = Parchment,
            onSurfaceVariant = Muted, outline = Color(0xFF435750), error = Red),
        typography = Typography(
            headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
            headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Serif, fontSize = 25.sp, fontWeight = FontWeight.Bold),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp)),
        content = content)
}

@Composable fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                            enabled: Boolean = true, primary: Boolean = false) {
    if (primary) Button(onClick, modifier, enabled, shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) { Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1) }
    else OutlinedButton(onClick, modifier, enabled, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (enabled) Muted.copy(alpha = .3f) else Muted.copy(alpha = .12f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) { Text(text, maxLines = 1) }
}
