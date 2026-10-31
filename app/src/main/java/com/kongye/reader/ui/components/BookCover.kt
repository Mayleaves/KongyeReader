package com.kongye.reader.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Book
import com.kongye.reader.data.Library
import com.kongye.reader.ui.theme.K
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** EPUB 自带封面的解码缓存（缩小到 360px 宽以内） */
object Covers {
    private val cache = LruCache<String, ImageBitmap>(48)

    fun load(id: String): ImageBitmap? {
        cache.get(id)?.let { return it }
        val f = Library.coverFile(id)
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 360) s *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        return bmp.asImageBitmap().also { cache.put(id, it) }
    }
}

/** 书封：EPUB 用自带封面；TXT 生成一张黑白灰的字封，右下角趴一只小猫 */
@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier, titleSize: TextUnit = 14.sp) {
    val bmp by produceState<ImageBitmap?>(null, book.id, book.hasCover) {
        if (book.hasCover) value = withContext(Dispatchers.IO) { Covers.load(book.id) }
    }
    Box(modifier.aspectRatio(0.72f).clip(SmoothShape(12.dp))) {
        val img = bmp
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else GeneratedCover(book, titleSize)
        Box(Modifier.fillMaxHeight().width(5.dp).background(Color.Black.copy(alpha = 0.08f)))
    }
}

@Composable
private fun GeneratedCover(book: Book, titleSize: TextUnit) {
    val c = K.c
    val styles = if (!c.dark) listOf(
        Triple(Color(0xFF1F1F1F), Color.White, Color.White),
        Triple(Color(0xFFEDEDED), Color(0xFF1F1F1F), Color(0xFF1F1F1F)),
        Triple(Color(0xFFDADADA), Color(0xFF1F1F1F), Color(0xFF1F1F1F)),
        Triple(Color(0xFFF6F0E3), Color(0xFF2E2921), Color(0xFF2E2921)),
    ) else listOf(
        Triple(Color(0xFF2C2C2E), Color.White, Color.White),
        Triple(Color(0xFF3A3A3C), Color.White, Color.White),
        Triple(Color(0xFF1C1C1E), Color(0xFFE5E5E5), Color(0xFFE5E5E5)),
        Triple(Color(0xFF48484A), Color.White, Color.White),
    )
    val (bg, tc, cat) = styles[(book.id.hashCode() and 0x7fffffff) % styles.size]
    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .padding(start = 13.dp, end = 10.dp, top = 12.dp, bottom = 9.dp),
    ) {
        T(book.title, titleSize, tc, FontWeight.Bold, maxLines = 4, lineHeight = titleSize * 1.3f)
        T(book.format.uppercase(), 9.sp, tc.copy(alpha = 0.5f), FontWeight.Bold, Modifier.align(Alignment.BottomStart), spacing = 1.sp)
        CatMascot(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 6.dp, y = 8.dp),
            size = 38.dp, color = cat, eye = bg, shadow = false, pose = CatPose.LOAF, sleep = true,
        )
    }
}
