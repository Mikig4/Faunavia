package it.faunavia.app

import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class ModelFailure(message: String) : IllegalArgumentException(message)
internal data class GlbClip(val name: String, val duration: Float)
internal data class GlbInfo(val triangles: Long, val clips: List<GlbClip>, val nodes: Int, val textureBytes: Long)

/** Bounded, self-contained glTF 2 subset. No URLs, compressed meshes, sparse accessors or extension animations. */
internal object GlbInspection {
    const val MAX_BYTES = 20 * 1024 * 1024
    private const val MAX_JSON = 1024 * 1024

    fun inspect(bytes: ByteArray): GlbInfo {
        try { return parse(bytes) }
        catch (failure: ModelFailure) { throw failure }
        catch (_: Exception) { throw ModelFailure("GLB non valido o incompleto. Il modello precedente è conservato.") }
    }

    private fun check(value: Boolean, message: String) { if (!value) throw ModelFailure(message) }
    private fun parse(bytes: ByteArray): GlbInfo {
        check(bytes.size in 28..MAX_BYTES, "GLB vuoto o superiore a 20 MB.")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(buffer.int == 0x46546c67 && buffer.int == 2 && buffer.int == bytes.size, "Serve un GLB completo in formato glTF 2.")
        val jsonSize = buffer.int
        check(jsonSize in 4..MAX_JSON && jsonSize % 4 == 0 && jsonSize <= buffer.remaining() - 4, "Intestazione GLB non valida.")
        check(buffer.int == 0x4e4f534a, "Il GLB non contiene la descrizione JSON.")
        val json = ByteArray(jsonSize).also(buffer::get)
        val root = JSONObject(json.toString(Charsets.UTF_8))
        check(buffer.remaining() >= 8, "Il GLB deve contenere geometria incorporata.")
        val binSize = buffer.int
        check(buffer.int == 0x004e4942 && binSize >= 4 && binSize % 4 == 0 && binSize == buffer.remaining(), "Blocco geometria incompleto o inatteso.")
        val binStart = buffer.position()
        check(root.getJSONObject("asset").getString("version") == "2.0", "Versione glTF non supportata.")
        check(root.getJSONObject("asset").optString("minVersion", "2.0") == "2.0", "Versione minima glTF non supportata.")
        check(root.optJSONArray("extensionsRequired")?.length().let { it == null || it == 0 }, "Questo GLB richiede estensioni non supportate.")
        // Reject unsupported extension payloads even when declared optional, rather than silently losing animation or geometry.
        val extensions = root.optJSONArray("extensionsUsed") ?: JSONArray()
        val supported = setOf("KHR_materials_unlit", "KHR_materials_pbrSpecularGlossiness", "KHR_texture_transform", "KHR_materials_emissive_strength")
        check((0 until extensions.length()).all { extensions.getString(it) in supported }, "Estensioni GLB non supportate: esporta un GLB standard con texture incorporate.")
        val buffers = root.getJSONArray("buffers")
        check(buffers.length() == 1 && !buffers.getJSONObject(0).has("uri"), "Risorse esterne non ammesse: incorpora tutte le risorse nel GLB.")
        val binaryLength = buffers.getJSONObject(0).getInt("byteLength")
        check(binaryLength > 0 && binSize - binaryLength in 0..3, "Dimensione del buffer incoerente.")
        val views = root.getJSONArray("bufferViews")
        check(views.length() in 1..512, "Troppe risorse geometriche per il budget mobile.")
        for (i in 0 until views.length()) {
            val view = views.getJSONObject(i)
            val offset = view.optLong("byteOffset", 0)
            val length = view.getLong("byteLength")
            val stride = view.optInt("byteStride", 0)
            check(view.getInt("buffer") == 0 && offset >= 0 && length > 0 && offset + length <= binaryLength,
                "Buffer geometrico fuori dai limiti.")
            check(stride == 0 || (stride in 4..252 && stride % 4 == 0), "Passo geometrico non supportato.")
        }
        val accessors = root.getJSONArray("accessors")
        check(accessors.length() in 1..512, "Troppi accessori per il budget mobile.")
        val componentSizes = mapOf(5120 to 1, 5121 to 1, 5122 to 2, 5123 to 2, 5125 to 4, 5126 to 4)
        val components = mapOf("SCALAR" to 1, "VEC2" to 2, "VEC3" to 3, "VEC4" to 4, "MAT4" to 16)
        var totalComponents = 0L
        for (i in 0 until accessors.length()) {
            val a = accessors.getJSONObject(i)
            check(!a.has("sparse"), "Accessori sparse non supportati: esporta geometria completa.")
            val size = componentSizes[a.getInt("componentType")] ?: throw ModelFailure("Tipo geometrico non supportato.")
            val element = size * (components[a.getString("type")] ?: throw ModelFailure("Formato geometrico non supportato."))
            val count = a.getLong("count")
            totalComponents += count * (element / size)
            check(totalComponents <= 2_000_000, "La geometria supera il budget di memoria mobile.")
            val v = views.getJSONObject(a.getInt("bufferView"))
            val offset = a.optLong("byteOffset", 0)
            val stride = v.optInt("byteStride", element)
            check(count in 1..180_000 && offset >= 0 && offset % size == 0L && stride >= element &&
                offset + (count - 1) * stride + element <= v.getLong("byteLength"), "Accessore geometrico fuori dai limiti.")
            for (name in listOf("min", "max")) a.optJSONArray(name)?.let { list ->
                check(list.length() == components.getValue(a.getString("type")) && (0 until list.length()).all { list.getDouble(it).isFinite() },
                    "Coordinate del modello non valide.")
            }
            if (a.getInt("componentType") == 5126) {
                val start = binStart + v.optInt("byteOffset", 0) + offset.toInt()
                for (n in 0 until count.toInt()) for (component in 0 until element / 4) {
                    check(buffer.getFloat(start + n * stride + component * 4).isFinite(), "Coordinate o animazioni non finite.")
                }
            }
        }
        val materials = root.optJSONArray("materials") ?: JSONArray()
        check(materials.length() <= 32, "Il modello supera 32 materiali.")
        val meshes = root.getJSONArray("meshes")
        check(meshes.length() in 1..128, "Il modello supera il budget di mesh.")
        var triangles = 0L
        val meshTriangles = LongArray(meshes.length())
        for (m in 0 until meshes.length()) {
            val primitives = meshes.getJSONObject(m).getJSONArray("primitives")
            check(primitives.length() in 1..32, "Troppe primitive nel modello.")
            for (p in 0 until primitives.length()) {
                val primitive = primitives.getJSONObject(p)
                check(primitive.optInt("mode", 4) == 4, "Sono supportate solo mesh triangolari.")
                val attributes = primitive.getJSONObject("attributes")
                attributes.keys().asSequence().forEach { accessors.getJSONObject(attributes.getInt(it)) }
                val positions = accessors.getJSONObject(attributes.getInt("POSITION"))
                check(positions.getString("type") == "VEC3" && positions.getInt("componentType") == 5126 &&
                    positions.has("min") && positions.has("max"), "Posizioni o limiti geometrici non validi.")
                val count = if (primitive.has("indices")) {
                    val indices = accessors.getJSONObject(primitive.getInt("indices"))
                    check(indices.getString("type") == "SCALAR" && indices.getInt("componentType") in listOf(5121, 5123, 5125), "Indici geometrici non validi.")
                    val view = views.getJSONObject(indices.getInt("bufferView"))
                    check(!view.has("byteStride"), "Indici interlacciati non supportati.")
                    val start = binStart + view.optInt("byteOffset", 0) + indices.optInt("byteOffset", 0)
                    val size = componentSizes.getValue(indices.getInt("componentType"))
                    for (index in 0 until indices.getInt("count")) {
                        val at = start + index * size
                        val value = when (size) { 1 -> buffer.get(at).toLong() and 255; 2 -> buffer.getShort(at).toLong() and 65535; else -> buffer.getInt(at).toLong() and 0xffffffffL }
                        check(value < positions.getLong("count"), "Indice fuori dalla mesh.")
                    }
                    indices.getLong("count")
                } else positions.getLong("count")
                check(count % 3 == 0L, "Numero di indici non triangolare.")
                triangles += count / 3
                meshTriangles[m] += count / 3
                if (primitive.has("material")) materials.getJSONObject(primitive.getInt("material"))
                primitive.optJSONArray("targets")?.let { targets ->
                    check(targets.length() <= 8, "Troppi morph target.")
                    for (t in 0 until targets.length()) targets.getJSONObject(t).let { target ->
                        target.keys().asSequence().forEach { accessors.getJSONObject(target.getInt(it)) }
                    }
                }
            }
        }
        check(triangles in 1..60_000, "Il modello supera 60.000 triangoli.")
        val nodes = root.getJSONArray("nodes")
        check(nodes.length() in 1..128, "Il modello supera 128 nodi.")
        val parents = IntArray(nodes.length())
        var instantiatedTriangles = 0L
        for (i in 0 until nodes.length()) {
            val node = nodes.getJSONObject(i)
            if (node.has("mesh")) {
                meshes.getJSONObject(node.getInt("mesh"))
                instantiatedTriangles += meshTriangles[node.getInt("mesh")]
            }
            for (field in listOf("matrix", "translation", "rotation", "scale")) node.optJSONArray(field)?.let { values ->
                val length = when (field) { "matrix" -> 16; "rotation" -> 4; else -> 3 }
                check(values.length() == length && (0 until length).all { values.getDouble(it).isFinite() }, "Trasformazione non valida.")
            }
            node.optJSONArray("children")?.let { children -> for (c in 0 until children.length()) {
                val child = children.getInt(c)
                check(child in 0 until nodes.length() && child != i && ++parents[child] == 1, "Gerarchia dei nodi non valida.")
            } }
        }
        check(instantiatedTriangles in 1..120_000, "Troppe istanze geometriche per il budget mobile.")
        val visiting = IntArray(nodes.length())
        fun visit(index: Int) {
            check(visiting[index] != 1, "La gerarchia contiene un ciclo.")
            if (visiting[index] == 2) return
            visiting[index] = 1
            nodes.getJSONObject(index).optJSONArray("children")?.let { for (c in 0 until it.length()) visit(it.getInt(c)) }
            visiting[index] = 2
        }
        for (i in 0 until nodes.length()) visit(i)
        val scenes = root.getJSONArray("scenes")
        check(scenes.length() in 1..16, "Scene non valide.")
        scenes.getJSONObject(root.optInt("scene", 0))
        for (i in 0 until scenes.length()) scenes.getJSONObject(i).optJSONArray("nodes")?.let { roots ->
            for (n in 0 until roots.length()) check(roots.getInt(n) in 0 until nodes.length() && parents[roots.getInt(n)] == 0, "Radice di scena non valida.")
        }
        val skins = root.optJSONArray("skins") ?: JSONArray()
        check(skins.length() <= 16, "Troppe armature.")
        for (i in 0 until skins.length()) {
            val skin = skins.getJSONObject(i)
            val joints = skin.getJSONArray("joints")
            check(joints.length() in 1..128, "Armatura troppo grande.")
            for (j in 0 until joints.length()) nodes.getJSONObject(joints.getInt(j))
            if (skin.has("inverseBindMatrices")) {
                val inverse = accessors.getJSONObject(skin.getInt("inverseBindMatrices"))
                check(inverse.getString("type") == "MAT4" && inverse.getInt("componentType") == 5126 && inverse.getInt("count") >= joints.length(), "Matrici di armatura non valide.")
            }
        }
        for (i in 0 until nodes.length()) if (nodes.getJSONObject(i).has("skin")) skins.getJSONObject(nodes.getJSONObject(i).getInt("skin"))
        var textureBytes = 0L
        val images = root.optJSONArray("images") ?: JSONArray()
        check(images.length() <= 16, "Troppe texture.")
        for (i in 0 until images.length()) {
            val image = images.getJSONObject(i)
            check(!image.has("uri") && image.optString("mimeType") in listOf("image/png", "image/jpeg"), "Texture esterne o non supportate.")
            val view = views.getJSONObject(image.getInt("bufferView"))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, binStart + view.optInt("byteOffset", 0), view.getInt("byteLength"), bounds)
            check(bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048, "Texture non leggibile o superiore a 2048 pixel.")
            textureBytes += bounds.outWidth.toLong() * bounds.outHeight * 4
        }
        check(textureBytes <= 64L * 1024 * 1024, "Le texture superano il budget di 64 MB.")
        val animations = root.optJSONArray("animations") ?: JSONArray()
        check(animations.length() <= 32, "Troppe animazioni.")
        val clips = (0 until animations.length()).map { index ->
            val animation = animations.getJSONObject(index)
            val samplers = animation.getJSONArray("samplers")
            val channels = animation.getJSONArray("channels")
            check(samplers.length() in 1..128 && channels.length() in 1..128, "Clip vuota o troppo grande.")
            var duration = 0f
            for (s in 0 until samplers.length()) {
                val sampler = samplers.getJSONObject(s)
                check(sampler.optString("interpolation", "LINEAR") in listOf("LINEAR", "STEP", "CUBICSPLINE"), "Interpolazione non supportata.")
                val input = accessors.getJSONObject(sampler.getInt("input"))
                accessors.getJSONObject(sampler.getInt("output"))
                check(input.getString("type") == "SCALAR" && input.getInt("componentType") == 5126 && input.getInt("count") <= 10_000, "Tempi di animazione non validi.")
                val view = views.getJSONObject(input.getInt("bufferView"))
                val start = binStart + view.optInt("byteOffset", 0) + input.optInt("byteOffset", 0)
                val stride = view.optInt("byteStride", 4)
                var previous = -1f
                for (t in 0 until input.getInt("count")) {
                    val time = buffer.getFloat(start + t * stride)
                    check(time >= 0 && time > previous && time <= 3600, "Tempi di animazione non validi.")
                    previous = time
                }
                duration = maxOf(duration, previous)
            }
            for (c in 0 until channels.length()) {
                val channel = channels.getJSONObject(c)
                samplers.getJSONObject(channel.getInt("sampler"))
                val target = channel.getJSONObject("target")
                val node = nodes.getJSONObject(target.getInt("node"))
                check(target.getString("path") in listOf("translation", "rotation", "scale", "weights") && !target.has("extensions"), "Canale di animazione non supportato.")
                val sampler = samplers.getJSONObject(channel.getInt("sampler"))
                val input = accessors.getJSONObject(sampler.getInt("input"))
                val output = accessors.getJSONObject(sampler.getInt("output"))
                val path = target.getString("path")
                check(!node.has("matrix") || path == "weights", "Nodo animato con matrice non supportato.")
                var multiplier = if (sampler.optString("interpolation", "LINEAR") == "CUBICSPLINE") 3 else 1
                val expectedType = when (path) { "rotation" -> "VEC4"; "weights" -> "SCALAR"; else -> "VEC3" }
                if (path == "weights") {
                    val primitives = meshes.getJSONObject(node.getInt("mesh")).getJSONArray("primitives")
                    val weights = primitives.getJSONObject(0).getJSONArray("targets").length()
                    check(weights > 0 && (0 until primitives.length()).all { primitives.getJSONObject(it).getJSONArray("targets").length() == weights }, "Morph target incoerenti.")
                    multiplier *= weights
                }
                check(output.getString("type") == expectedType && output.getInt("componentType") == 5126 &&
                    output.getLong("count") == input.getLong("count") * multiplier, "Dati della clip incoerenti.")
            }
            GlbClip(animation.optString("name", "Clip ${index + 1}").take(100).ifBlank { "Clip ${index + 1}" }, duration)
        }
        return GlbInfo(triangles, clips, nodes.length(), textureBytes)
    }
}
