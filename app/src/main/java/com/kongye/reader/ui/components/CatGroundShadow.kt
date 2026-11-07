package com.kongye.reader.ui.components

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kongye.reader.data.Shop

/** 在动物自己的坐标中单独画阴影，脚下饰品随后绘制，身体最后绘制。 */
internal fun DrawScope.drawCatGroundShadow(id: String, dark: Boolean, sleeping: Boolean = false) {
    when (id) {
        Shop.MINCAT -> if (sleeping) drawMinimalSleepShadow(dark) else drawMinimalFocusShadow(dark)
        Shop.GOLDMIN -> if (sleeping) drawGoldMinSleepShadow(dark) else drawGoldMinFocusShadow(dark)
        Shop.GOLDCAT -> if (sleeping) drawGoldSleepShadow(dark) else drawGoldFocusShadow(dark)
        else -> if (sleeping) drawImportedSleepShadow(dark) else drawImportedFocusShadow(dark)
    }
}
