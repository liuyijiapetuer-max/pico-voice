package com.spatialcoach.soundarchitecture.stage

import android.graphics.Bitmap
import com.pico.spatial.core.ecs.resource.TextureResource

/** 纯色贴图。Unlit 材质用，避免再乘一层环境光。 */
object SolidTextures {
    fun color(argb: Int, size: Int = 16): TextureResource {
        val pixels = IntArray(size * size) { argb }
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        return TextureResource.create(bitmap)
    }
}
