package com.kongye.reader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.kongye.reader.KongyeReaderApp
import com.kongye.reader.data.Shop
import com.kongye.reader.data.ShopAnimal
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.sin

/** Unbounded frame time, read only by Canvas; no periodic clock reset or per-frame recomposition. */
@Composable
internal fun animalPartTime(id: String): State<Float> {
    val time = remember(id) { mutableFloatStateOf(0f) }
    LaunchedEffect(id) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
    }
    return time
}

/** A pivot in the animal's original SVG coordinate system. */
internal data class AnimalPart(
    val name: String, val x: Float, val y: Float, val angle: Float,
    val period: Float, val kind: String, val attachments: Set<String>,
) {
    fun rotation(seconds: Float): Float {
        val phase = (seconds / period) % 1f
        return when (kind) {
            "blink" -> 0f
            "twitch", "flutter" -> {
                // Long rests between two gentle flicks; start/end at zero velocity.
                if (phase < 0.65f) 0f else {
                    val t = (phase - 0.65f) / 0.35f
                    val envelope = sin(PI * t).toFloat()
                    angle * sin(4 * PI * t).toFloat() * envelope * envelope
                }
            }
            else -> angle * sin(2 * PI * phase).toFloat()
        }
    }
    fun height(seconds: Float): Float {
        if (kind != "blink") return 1f
        val phase = (seconds / period) % 1f
        if (phase < 0.94f) return 1f
        return 1f - 0.94f * sin(PI * (phase - 0.94f) / 0.06f).toFloat()
    }
}

private data class LayerSpec(val res: String, val part: AnimalPart?)
private object AnimalRecipes {
    val all: Map<String, List<LayerSpec>> by lazy {
        val raw = KongyeReaderApp.app.assets.open("animal_motion.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        root.keys().asSequence().associateWith { id ->
            val recipe = root.getJSONObject(id)
            val ms = recipe.getJSONArray("motions")
            val parts = (0 until ms.length()).map { i ->
                val m = ms.getJSONObject(i)
                val p = m.getJSONArray("pivot")
                val attached = m.getJSONArray("attach")
                AnimalPart(m.getString("name"), p.getDouble(0).toFloat(), p.getDouble(1).toFloat(),
                    m.getDouble("angle").toFloat(), m.getDouble("period").toFloat(), m.getString("kind"),
                    (0 until attached.length()).map { attached.getString(it) }.toSet())
            }.associateBy { it.name }
            val ls = recipe.getJSONArray("layers")
            (0 until ls.length()).map { i ->
                val layer = ls.getJSONObject(i)
                LayerSpec(layer.getString("res"), parts[layer.getString("motion")])
            }
        }
    }
}

internal class AnimalLayer(val painter: Painter, val part: AnimalPart?)

@Composable
internal fun animalLayers(id: String): List<AnimalLayer> {
    val specs = remember(id) { AnimalRecipes.all[id].orEmpty() }
    return specs.mapNotNull { layer ->
        val res = remember(layer.res) { Shop.drawable(layer.res) }
        if (res == 0) null else AnimalLayer(painterResource(res), layer.part)
    }
}

/** Coordinates stay in the same space for both body layers and attached accessories. */
internal fun DrawScope.withAnimalPart(
    animal: ShopAnimal, part: AnimalPart?, seconds: Float,
    k: Float, ox: Float, oy: Float, block: DrawScope.() -> Unit,
) {
    if (part == null) { block(); return }
    val pivot = Offset(ox + (part.x - animal.vb[0]) * k, oy + (part.y - animal.vb[1]) * k)
    withTransform({
        rotate(part.rotation(seconds), pivot)
        scale(1f, part.height(seconds), pivot)
    }, block)
}

/** Shared renderer also used by device checks of the original/rest pose and each moving part. */
internal fun DrawScope.drawAnimalLayers(animal: ShopAnimal, layers: List<AnimalLayer>, seconds: Float, k: Float, ox: Float, oy: Float) {
    for (layer in layers) withAnimalPart(animal, layer.part, seconds, k, ox, oy) {
        withTransform({ translate(ox, oy) }) {
            with(layer.painter) { draw(androidx.compose.ui.geometry.Size(animal.vb[2] * k, animal.vb[3] * k)) }
        }
    }
}
