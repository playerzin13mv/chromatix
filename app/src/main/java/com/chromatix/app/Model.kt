package com.chromatix.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

val NN = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
const val VOW = "AEIO" // sem U, de propósito

fun nt(m: Int) = NN[m % 12] + (m / 12 - (if (Cfg.fl) 0 else 1))
fun <T> List<T>.with(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }

/** Slots 0..3 = vogais A E I O (modo vogais). Slot 4 = sample único. */
data class Proj(
    val id: Long, val name: String, val vow: Boolean = true,
    val root: Int = 60, val tune: Int = 0,
    val a: Float = .005f, val d: Float = .2f, val s: Float = 1f, val r: Float = .25f,
    val vol: Float = 0f, val loop: Boolean = false, val ls: Float = .3f, val le: Float = .9f,
    val mod: Long = 0,
    val rate: List<Int> = List(5) { 44100 },
    val ts: List<Float> = List(5) { 0f },
    val te: List<Float> = List(5) { 1f },
    val pcm: List<ShortArray?> = List(5) { null },
) {
    /** Na vogais, cada tecla usa a vogal (nota % 4): C=A, C#=E, D=I, D#=O, E=A... repete em todo o teclado. */
    fun slot(m: Int) = if (vow) m % 4 else 4
    val slots: IntRange get() = if (vow) 0..3 else 4..4
    val done: Boolean get() = slots.all { pcm[it] != null }
    fun missing() = slots.filter { pcm[it] == null }.joinToString(", ") { if (it < 4) VOW[it].toString() else "sample" }
}

object Cfg {
    private lateinit var sp: SharedPreferences
    var o by mutableStateOf(3)
    var n by mutableStateOf(3)
    var fl by mutableStateOf(true)
    var vol by mutableStateOf(.8f)
    var rel by mutableStateOf(.25f)
    fun init(c: Context) {
        sp = c.getSharedPreferences("cfg", 0)
        o = sp.getInt("o", 3); n = sp.getInt("n", 3); fl = sp.getBoolean("fl", true)
        vol = sp.getFloat("vol", .8f); rel = sp.getFloat("rel", .25f)
    }
    fun save() { sp.edit().putInt("o", o).putInt("n", n).putBoolean("fl", fl).putFloat("vol", vol).putFloat("rel", rel).apply() }
}

object Store {
    private lateinit var dir: File
    private val h = Handler(Looper.getMainLooper())
    var cur by mutableStateOf<Proj?>(null)
    val list = mutableStateListOf<Proj>()
    private val sv = Runnable { cur?.let { writeMeta(it) } }

    fun init(c: Context) { dir = File(c.filesDir, "projects").also { it.mkdirs() }; refresh() }

    fun refresh() {
        val l = dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { try { parse(JSONObject(it.readText())) } catch (e: Exception) { null } }
            .sortedByDescending { it.mod }
        list.clear(); list.addAll(l)
    }

    fun count(p: Proj) = p.slots.count { File(dir, "${p.id}_$it.pcm").exists() }
    fun full(p: Proj) = p.copy(pcm = List(5) { loadPcm(p.id, it) })
    fun open(p: Proj) { cur = full(p) }

    fun create(name: String): Proj {
        val p = Proj(System.currentTimeMillis(), name, r = Cfg.rel)
        writeMeta(p); cur = p; refresh(); return p
    }

    fun edit(f: (Proj) -> Proj) {
        val c = cur ?: return
        cur = f(c)
        h.removeCallbacks(sv); h.postDelayed(sv, 400)
    }

    fun setPcm(slot: Int, d: ShortArray, rate: Int) {
        val c = cur ?: return
        writePcm(c.id, slot, d)
        val n = c.copy(pcm = c.pcm.with(slot, d), rate = c.rate.with(slot, rate), ts = c.ts.with(slot, 0f), te = c.te.with(slot, 1f))
        cur = n; writeMeta(n)
    }

    fun duplicate(p: Proj) {
        val id = System.currentTimeMillis()
        writeMeta(p.copy(id = id, name = p.name + " (cópia)"))
        for (i in 0..4) { val f = File(dir, "${p.id}_$i.pcm"); if (f.exists()) f.copyTo(File(dir, "${id}_$i.pcm")) }
        refresh()
    }

    fun delete(p: Proj) {
        File(dir, "${p.id}.json").delete()
        for (i in 0..4) File(dir, "${p.id}_$i.pcm").delete()
        if (cur?.id == p.id) cur = null
        refresh()
    }

    private fun loadPcm(id: Long, i: Int): ShortArray? {
        val f = File(dir, "${id}_$i.pcm"); if (!f.exists()) return null
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(b.remaining()).also { b.get(it) }
    }

    private fun writePcm(id: Long, i: Int, d: ShortArray) {
        val b = ByteBuffer.allocate(d.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.asShortBuffer().put(d)
        File(dir, "${id}_$i.pcm").writeBytes(b.array())
    }

    private fun writeMeta(p: Proj) {
        val j = JSONObject().put("id", p.id).put("name", p.name).put("vow", p.vow).put("root", p.root).put("tune", p.tune)
            .put("a", p.a.toDouble()).put("d", p.d.toDouble()).put("s", p.s.toDouble()).put("r", p.r.toDouble())
            .put("vol", p.vol.toDouble()).put("loop", p.loop).put("ls", p.ls.toDouble()).put("le", p.le.toDouble())
            .put("mod", System.currentTimeMillis())
            .put("rate", JSONArray(p.rate)).put("ts", JSONArray(p.ts.map { it.toDouble() })).put("te", JSONArray(p.te.map { it.toDouble() }))
        File(dir, "${p.id}.json").writeText(j.toString())
    }

    private fun parse(j: JSONObject): Proj {
        fun f(k: String, d: Double) = j.optDouble(k, d).toFloat()
        fun <T> arr(k: String, d: T, g: (JSONArray, Int) -> T): List<T> =
            List(5) { i -> val a = j.optJSONArray(k); if (a != null && i < a.length()) g(a, i) else d }
        return Proj(
            j.getLong("id"), j.getString("name"), j.optBoolean("vow", true), j.optInt("root", 60), j.optInt("tune"),
            f("a", .005), f("d", .2), f("s", 1.0), f("r", .25), f("vol", 0.0), j.optBoolean("loop"), f("ls", .3), f("le", .9),
            j.optLong("mod"),
            arr("rate", 44100) { a, i -> a.getInt(i) },
            arr("ts", 0f) { a, i -> a.getDouble(i).toFloat() },
            arr("te", 1f) { a, i -> a.getDouble(i).toFloat() },
        )
    }
}
