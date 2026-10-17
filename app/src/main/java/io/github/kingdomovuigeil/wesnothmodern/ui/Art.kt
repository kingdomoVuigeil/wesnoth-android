/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class Art(context: Context) {
    val images: Map<String, Bitmap> = context.assets.list("art").orEmpty().filter { it.endsWith(".png") }.associate {
        it.removeSuffix(".png") to context.assets.open("art/$it").use { stream -> requireNotNull(BitmapFactory.decodeStream(stream)) }
    }
    operator fun get(name: String): Bitmap? = images[name]
    private val teamImages = mutableMapOf<String, Bitmap>()
    /** Recolor the magenta team markings at runtime; distributed PNGs stay untouched. */
    fun forSide(name: String, side: Int): Bitmap? {
        val source = images[name] ?: return null
        return teamImages.getOrPut("$name/$side") {
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            val hsv = FloatArray(3)
            for (i in pixels.indices) {
                val color = pixels[i]
                if (android.graphics.Color.alpha(color) == 0) continue
                android.graphics.Color.colorToHSV(color, hsv)
                if (hsv[0] in 285f..345f && hsv[1] > .4f) {
                    hsv[0] = if (side == 1) 204f else 5f
                    hsv[1] = (hsv[1] * .82f).coerceIn(.45f, 1f)
                    pixels[i] = android.graphics.Color.HSVToColor(android.graphics.Color.alpha(color), hsv)
                }
            }
            Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
        }
    }
}
@Composable fun rememberArt(): Art {
    val context = LocalContext.current
    return remember { Art(context) }
}
@Composable fun Sprite(name: String, description: String?, art: Art, modifier: Modifier = Modifier.size(72.dp), side: Int = 1) {
    val image = art.forSide(name, side)?.let { remember(it) { it.asImageBitmap() } }
    if (image != null) Image(image, description, modifier, filterQuality = FilterQuality.None)
}
