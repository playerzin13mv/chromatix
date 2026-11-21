package com.chromatix.app

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.log10

/**
 * Gera um SoundFont 2.01. Modo vogais: 4 samples (A,E,I,O, +U se dublado) e 128 zonas, uma por tecla,
 * tecla k usa a vogal k%N (N=4 ou 5), repetindo em todo o teclado. Modo único: 1 sample em todas as teclas.
 */
object Sf2 {
    private fun u16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    private fun u32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    private fun str(s: String, l: Int) = ByteArray(l) { i -> if (i < s.length) (s[i].code and 127).toByte() else 0 }
    private fun cat(vararg a: ByteArray): ByteArray { val o = ByteArrayOutputStream(); a.forEach { o.write(it) }; return o.toByteArray() }
    private fun ck(id: String, vararg d: ByteArray): ByteArray {
        val b = cat(*d)
        return cat(str(id, 4), u32(b.size), b, ByteArray(b.size % 2))
    }
    private fun lst(id: String, vararg d: ByteArray) = ck("LIST", str(id, 4), *d)
    private fun tc(s: Float) = maxOf(-12000, Math.round(1200 * ln(maxOf(s, .001f).toDouble()) / ln(2.0)).toInt())
    private fun nm(p: Proj) = p.name.filter { it.code in 32..126 }.take(19).ifBlank { "Chromatic" }

    fun fileName(p: Proj) = nm(p).trim().replace(" ", "_") + ".sf2"

    fun build(p: Proj): ByteArray {
        val sl = p.slots.toList()
        val segs = sl.map { s ->
            val all = p.pcm[s]!!
            val o = (p.ts[s] * all.size).toInt()
            val n = ((p.te[s] - p.ts[s]) * all.size).toInt().coerceAtLeast(2)
            ShortArray(n) { all[minOf(o + it, all.size - 1)] }
        }
        val bb = ByteBuffer.allocate(segs.sumOf { (it.size + 46) * 2 }).order(ByteOrder.LITTLE_ENDIAN)
        segs.forEach { sg -> sg.forEach { bb.putShort(it) }; repeat(46) { bb.putShort(0.toShort()) } }

        val name = nm(p)
        val sus = if (p.s >= 1f) 0 else minOf(1440, Math.round(-200 * log10(maxOf(p.s, .0001f).toDouble())).toInt())
        val zones = when (p.mode) {
            0 -> (0..127).map { Triple(it, it, it % sl.size) }
            2 -> (0..127).map { Triple(it, it, sl.indexOf(p.slot(it))) }
            else -> listOf(Triple(0, 127, 0))
        }
        fun gens(z: Triple<Int, Int, Int>) = listOf(
            43 to (z.first or (z.second shl 8)), 34 to tc(p.a), 36 to tc(p.d), 37 to sus, 38 to tc(p.r),
            48 to Math.round(-p.vol * 10), 52 to p.tune, 54 to (if (p.loop) 1 else 0), 58 to p.rootFor(sl[z.third]), 53 to z.third,
        )
        val gsz = gens(zones[0]).size
        val igen = zones.flatMap { gens(it) }.map { u16(it.first) + u16(it.second and 0xFFFF) }
        val ibag = zones.indices.map { u16(it * gsz) + u16(0) } + (u16(zones.size * gsz) + u16(0))

        var st = 0
        val shdr = segs.mapIndexed { i, sg ->
            val n = sg.size; val s = sl[i]; val a = st; st += n + 46
            val l1 = (p.ls * n).toInt().coerceIn(0, n - 2)
            val l2 = (p.le * n).toInt().coerceIn(l1 + 1, n)
            cat(str(when { s == 4 -> name; s >= 6 -> "Nota_" + NN[(s - 6) % 12] + ((s - 6) / 12 - 1); else -> "Vogal_${letter(s)}" }, 20), u32(a), u32(a + n), u32(a + l1), u32(a + l2),
                u32(p.rate[s]), byteArrayOf(p.rootFor(s).toByte(), 0), u16(0), u16(1))
        }

        val info = lst("INFO", ck("ifil", u16(2), u16(1)), ck("isng", str("EMU8000", 8)), ck("INAM", str(name, 20)))
        val sdta = lst("sdta", ck("smpl", bb.array()))
        val pdta = lst("pdta",
            ck("phdr", str(name, 20), u16(0), u16(0), u16(0), u32(0), u32(0), u32(0), str("EOP", 20), u16(0), u16(0), u16(1), u32(0), u32(0), u32(0)),
            ck("pbag", u16(0), u16(0), u16(1), u16(0)),
            ck("pmod", ByteArray(10)),
            ck("pgen", u16(41), u16(0), ByteArray(4)),
            ck("inst", str(name, 20), u16(0), str("EOI", 20), u16(zones.size)),
            ck("ibag", *ibag.toTypedArray()),
            ck("imod", ByteArray(10)),
            ck("igen", *igen.toTypedArray(), ByteArray(4)),
            ck("shdr", *shdr.toTypedArray(), str("EOS", 20), ByteArray(26)),
        )
        return ck("RIFF", str("sfbk", 4), info, sdta, pdta)
    }
}
