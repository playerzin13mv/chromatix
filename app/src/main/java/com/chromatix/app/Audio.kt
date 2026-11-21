package com.chromatix.app

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

class Shorts {
    var a = ShortArray(1 shl 16); var n = 0
    fun add(v: Short) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
}

object Audio {
    fun norm(a: ShortArray): ShortArray {
        var pk = 1
        for (v in a) pk = maxOf(pk, abs(v.toInt()))
        val g = .95f * 32767f / pk
        return ShortArray(a.size) { (a[it] * g).toInt().toShort() }
    }
}

/** Mixer polifônico com AudioTrack de baixa latência. */
object Engine {
    private const val SR = 44100

    private class Voice(
        val id: Int, val d: ShortArray, val o: Int, val len: Int, val step: Double,
        val loop: Boolean, val ls: Double, val le: Double, val g: Float,
        val att: Float, val dec: Float, val sus: Float, val relT: Float,
    ) {
        var pos = 0.0; var env = 0f; var st = 0; var relStep = -1f
        @Volatile var rel = false
        fun render(mix: FloatArray): Boolean {
            for (i in mix.indices) {
                if (rel) {
                    if (relStep < 0) relStep = env / maxOf(1f, relT * SR)
                    env -= relStep; if (env <= 0f) return false
                } else if (st == 0) { env += att; if (env >= 1f) { env = 1f; st = 1 } }
                else if (st == 1) { env -= dec; if (env <= sus) { env = sus; st = 2 } }
                if (loop && pos >= le) pos -= (le - ls)
                if (pos >= len - 1) return false
                val k = pos.toInt(); val f = (pos - k).toFloat()
                val a = d[o + k].toInt(); val b = d[o + k + 1].toInt()
                mix[i] += (a + (b - a) * f) / 32768f * env * g
                pos += step
            }
            return true
        }
    }

    private val vs = ArrayList<Voice>()
    @Volatile private var run = false

    fun start() {
        if (run) return
        run = true
        Thread {
            val min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 2048))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
            t.play()
            val mix = FloatArray(256); val out = ShortArray(256)
            while (run) {
                mix.fill(0f)
                synchronized(vs) { val it = vs.iterator(); while (it.hasNext()) if (!it.next().render(mix)) it.remove() }
                for (i in mix.indices) out[i] = (mix[i] * Cfg.vol * 32767f).coerceIn(-32767f, 32767f).toInt().toShort()
                t.write(out, 0, 256)
            }
            t.stop(); t.release()
        }.start()
    }

    fun stop() { run = false; synchronized(vs) { vs.clear() } }

    /** id identifica a "tecla" (para soltar depois); note define o pitch; slot escolhe o sample/vogal. */
    fun on(id: Int, note: Int, slot: Int) {
        val p = Store.cur ?: return
        val d = p.pcm[slot] ?: return
        start()
        val o = (p.ts[slot] * d.size).toInt()
        val len = ((p.te[slot] - p.ts[slot]) * d.size).toInt()
        if (len < 4) return
        synchronized(vs) {
            if (vs.any { it.id == id && !it.rel }) return
            val le = minOf(p.le.toDouble() * len, len - 2.0)
            val ls = minOf(p.ls.toDouble() * len, le - 2.0)
            val step = 2.0.pow((note - p.root) / 12.0 + p.tune / 1200.0) * p.rate[slot] / SR
            vs.add(Voice(id, d, o, len, step, p.loop && le - ls > 2, ls, le, 10f.pow(p.vol / 20f),
                1f / maxOf(1f, p.a * SR), (1f - p.s) / maxOf(1f, p.d * SR), p.s, p.r))
        }
    }

    fun off(id: Int) { synchronized(vs) { vs.forEach { if (it.id == id) it.rel = true } } }
}

object Decoder {
    fun decode(c: Context, u: Uri): Pair<ShortArray, Int> {
        val ex = MediaExtractor(); ex.setDataSource(c, u, null)
        val ti = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        ex.selectTrack(ti)
        val f = ex.getTrackFormat(ti)
        val cd = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME)!!)
        cd.configure(f, null, null, 0); cd.start()
        var ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT); var rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val out = Shorts(); val bi = MediaCodec.BufferInfo(); var inD = false; var outD = false
        while (!outD) {
            if (!inD) {
                val i = cd.dequeueInputBuffer(10000)
                if (i >= 0) {
                    val b = cd.getInputBuffer(i)!!; val n = ex.readSampleData(b, 0)
                    if (n < 0) { cd.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inD = true }
                    else { cd.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                }
            }
            val o = cd.dequeueOutputBuffer(bi, 10000)
            if (o >= 0) {
                val b = cd.getOutputBuffer(o)!!
                b.position(bi.offset); b.limit(bi.offset + bi.size)
                val sb = b.order(ByteOrder.nativeOrder()).asShortBuffer()
                val a = ShortArray(sb.remaining()); sb.get(a)
                var i = 0
                while (i + ch <= a.size) { var s = 0; for (k in 0 until ch) s += a[i + k]; out.add((s / ch).toShort()); i += ch }
                cd.releaseOutputBuffer(o, false)
                if ((bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outD = true
            } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val nf = cd.outputFormat
                ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT); rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
        }
        cd.stop(); cd.release(); ex.release()
        return Pair(out.a.copyOf(out.n), rate)
    }
}

class Rec {
    private var ar: AudioRecord? = null
    private val out = Shorts()
    @Volatile private var on = false
    private var t: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        out.n = 0
        val min = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        ar = r; r.startRecording(); on = true
        t = Thread { val b = ShortArray(1024); while (on) { val n = r.read(b, 0, b.size); for (i in 0 until n) out.add(b[i]) } }.also { it.start() }
    }

    fun stop(): ShortArray {
        on = false; t?.join(); ar?.stop(); ar?.release(); ar = null
        return out.a.copyOf(out.n)
    }
}
