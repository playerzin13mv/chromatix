package com.chromatix.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

/** kind: 0 = cortar em vogais, 1 = cortar escala cromática (1 nota por tecla), 2 = recortar um sample existente. */
class CutSession(val kind: Int, val pcm: ShortArray, val rate: Int, val targets: List<Int>, val startNote: Int = 0)

/** Estado do editor de corte: zoom/rolagem da onda e a seleção [a, b] em amostras. */
class CutState(val pcm: ShortArray, val rate: Int) {
    var w by mutableFloatStateOf(1f)      // largura da onda em px
    var vs by mutableFloatStateOf(0f)     // primeira amostra visível
    var spp by mutableFloatStateOf(1f)    // amostras por pixel (menor = mais zoom)
    var a by mutableFloatStateOf(0f)
    var b by mutableFloatStateOf(0f)
    var ph by mutableFloatStateOf(-1f)    // playhead
    val peak = (pcm.maxOfOrNull { abs(it.toInt()) } ?: 1).coerceAtLeast(1)

    private fun maxSpp() = maxOf(pcm.size / w, .25f)
    fun clamp() { spp = spp.coerceIn(.25f, maxSpp()); vs = vs.coerceIn(0f, maxOf(0f, pcm.size - w * spp)) }
    fun fit() { spp = maxSpp(); vs = 0f }
    fun zoom(f: Float, cx: Float) { val s = vs + cx * spp; spp = (spp / f).coerceIn(.25f, maxSpp()); vs = s - cx * spp; clamp() }
    fun xOf(s: Float) = (s - vs) / spp
    fun sOf(x: Float) = (vs + x * spp).coerceIn(0f, pcm.size.toFloat())
    /** Dá zoom para a seleção ocupar a tela. */
    fun show() { val len = maxOf(b - a, 256f); spp = (len * 1.5f) / w; vs = a - len * .25f; clamp() }

    /** Acha o próximo som (depois do silêncio) a partir de 'from' e seleciona. */
    fun detect(from: Int): Boolean {
        val th = (peak * .06f).toInt().coerceAtLeast(60)
        var i = from.coerceAtLeast(0)
        while (i < pcm.size && abs(pcm[i].toInt()) < th) i++
        if (i >= pcm.size) return false
        val gap = (rate * .12).toInt(); var quiet = 0; var j = i
        while (j < pcm.size) { if (abs(pcm[j].toInt()) < th) { quiet++; if (quiet > gap) break } else quiet = 0; j++ }
        a = maxOf(0, i - (rate * .01).toInt()).toFloat()
        b = minOf(pcm.size, j - quiet + (rate * .03).toInt()).toFloat()
        if (b <= a + 16) b = minOf(pcm.size.toFloat(), a + 2000f)
        show(); return true
    }
}

@Composable
fun Pill(t: String, sel: Boolean, done: Boolean, mod: Modifier = Modifier, on: () -> Unit) {
    Row(mod.height(34.dp).background(if (sel) Violet else Dark, RoundedCornerShape(17.dp)).clickable { on() }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(t, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (done) { Spacer(Modifier.width(3.dp)); Ico(R.drawable.ic_check, 14.dp, Color.White) }
    }
}

@Composable
fun Cutter(s: CutSession, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val W = remember { CutState(s.pcm, s.rate) }
    var idx by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val cur = Store.cur

    fun note() = s.startNote + idx
    fun target() = if (s.kind == 1) 6 + note() else s.targets[idx.coerceIn(0, s.targets.size - 1)]

    LaunchedEffect(W.w) {
        if (!ready && W.w > 1f) {
            ready = true; W.fit()
            if (!W.detect(0)) { W.a = 0f; W.b = minOf(s.pcm.size, s.rate).toFloat(); W.show() }
        }
    }
    LaunchedEffect(playing) {
        if (playing) {
            val a = W.a.toInt(); val n = (W.b - W.a).toInt()
            while (true) { delay(30); val h = Preview.head(); W.ph = a + h.toFloat(); if (h >= n) break }
            W.ph = -1f; playing = false
        }
    }
    DisposableEffect(Unit) { onDispose { Preview.stop() } }

    fun apply() {
        val a = W.a.toInt(); val b = W.b.toInt()
        if (b - a < 64) { toast(ctx, "Selecione um trecho"); return }
        Preview.stop(); playing = false; W.ph = -1f
        val t = target()
        Store.setPcm(t, Audio.norm(s.pcm.copyOfRange(a, b)), s.rate)
        if (s.kind == 0 && Store.cur?.mode != 0) Store.edit { it.copy(mode = 0) }
        if (s.kind == 1 && Store.cur?.mode != 2) Store.edit { it.copy(mode = 2) }
        when (s.kind) {
            0 -> {
                val c = Store.cur
                val n = s.targets.indices.firstOrNull { it > idx && c?.pcm?.get(s.targets[it]) == null }
                    ?: s.targets.indices.firstOrNull { c?.pcm?.get(s.targets[it]) == null }
                if (n != null) idx = n else toast(ctx, "Tudo cortado! Toque em Concluir.")
            }
            1 -> if (note() < 127) idx++
            else -> { onClose(); return }
        }
        W.detect(b)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(when (s.kind) { 0 -> "Cortar vogal ${letter(target())}"; 1 -> "Cortar nota ${nt(note())}"; else -> "Recortar sample" },
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (s.kind == 0) s.targets.forEachIndexed { i, t -> Pill(letter(t).toString(), idx == i, cur?.pcm?.get(t) != null) { idx = i } }
            if (s.kind == 1) {
                IconButton({ if (idx > 0) idx-- }) { Ico(R.drawable.ic_prev) }
                IconButton({ if (note() < 127) idx++ }) { Ico(R.drawable.ic_next) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))) {
            Canvas(Modifier.fillMaxSize().onSizeChanged { W.w = it.width.toFloat() }.pointerInput(Unit) {
                val tol = 28.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val mode = if (abs(down.position.x - W.xOf(W.a)) < tol) 1 else if (abs(down.position.x - W.xOf(W.b)) < tol) 2 else 0
                    var moved = 0f; var multi = false
                    do {
                        val ev = awaitPointerEvent()
                        val pts = ev.changes.filter { it.pressed }
                        if (pts.size >= 2) {
                            multi = true
                            val p0 = pts[0]; val p1 = pts[1]
                            val d1 = abs(p0.position.x - p1.position.x); val d0 = abs(p0.previousPosition.x - p1.previousPosition.x)
                            if (d0 > 8f && d1 > 8f) W.zoom(d1 / d0, (p0.position.x + p1.position.x) / 2)
                            val dx = ((p0.position.x + p1.position.x) - (p0.previousPosition.x + p1.previousPosition.x)) / 2
                            W.vs -= dx * W.spp; W.clamp()
                        } else if (pts.size == 1 && !multi) {
                            val c = pts[0]; val dx = c.position.x - c.previousPosition.x; moved += abs(dx)
                            when (mode) {
                                1 -> W.a = minOf(W.sOf(c.position.x), W.b - 16f)
                                2 -> W.b = maxOf(W.sOf(c.position.x), W.a + 16f)
                                else -> { W.vs -= dx * W.spp; W.clamp() }
                            }
                        }
                        ev.changes.forEach { it.consume() }
                    } while (ev.changes.any { it.pressed })
                    if (!multi && mode == 0 && moved < 12f) { // toque simples: move a ponta mais próxima
                        val sp = W.sOf(down.position.x)
                        if (abs(sp - W.a) < abs(sp - W.b)) W.a = minOf(sp, W.b - 16f) else W.b = maxOf(sp, W.a + 16f)
                    }
                }
            }) {
                val h = size.height; val n = W.pcm.size; val d = W.pcm
                val st = maxOf(1, (W.spp / 40).toInt())
                for (x in 0 until size.width.toInt()) {
                    val s0 = W.vs + x * W.spp
                    if (s0 >= n) break
                    var j = s0.toInt(); val e = minOf(n, maxOf(j + 1, (W.vs + (x + 1) * W.spp).toInt()))
                    var lo = 0; var hi = 0
                    while (j < e) { val v = d[j].toInt(); if (v < lo) lo = v; if (v > hi) hi = v; j += st }
                    drawLine(Cyan, Offset(x.toFloat(), h / 2 - hi / 32768f * h / 2), Offset(x.toFloat(), h / 2 - lo / 32768f * h / 2 + 1f), 1f)
                }
                val xa = W.xOf(W.a); val xb = W.xOf(W.b); val wd = size.width
                val ca = xa.coerceIn(0f, wd); val cb = xb.coerceIn(0f, wd)
                drawRect(Color(0xAA14102A), Offset.Zero, Size(ca, h))
                drawRect(Color(0xAA14102A), Offset(cb, 0f), Size(wd - cb, h))
                drawRect(Color(0x337C5CFF), Offset(ca, 0f), Size(cb - ca, h))
                for (x in listOf(xa, xb)) { drawRect(Violet, Offset(x - 2f, 0f), Size(4f, h)); drawCircle(Violet, 13f, Offset(x, h / 2)) }
                if (W.ph >= 0) drawRect(Color.White, Offset(W.xOf(W.ph), 0f), Size(2f, h))
            }
        }
        Text("%.2f s → %.2f s  (%.2f s)  ·  zoom %.1fx".format(W.a / s.rate, W.b / s.rate, (W.b - W.a) / s.rate, maxOf(s.pcm.size / W.w, .25f) / W.spp),
            fontSize = 12.sp, color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton({ W.zoom(1 / 1.6f, W.w / 2) }) { Ico(R.drawable.ic_zoom_out) }
            IconButton({ W.zoom(1.6f, W.w / 2) }) { Ico(R.drawable.ic_zoom_in) }
            IconButton({ W.fit() }) { Ico(R.drawable.ic_fit) }
            IconButton({ if (!W.detect(W.b.toInt())) toast(ctx, "Não achei mais nenhum som") }) { Ico(R.drawable.ic_next) }
            Spacer(Modifier.weight(1f))
            Button({
                if (playing) { Preview.stop(); playing = false; W.ph = -1f }
                else { Preview.play(s.pcm, W.a.toInt(), W.b.toInt(), s.rate); playing = true }
            }, colors = ButtonDefaults.buttonColors(containerColor = Dark)) {
                Ico(if (playing) R.drawable.ic_stop else R.drawable.ic_play, 18.dp, Color.White); Spacer(Modifier.width(4.dp)); Text("Ouvir", fontSize = 13.sp)
            }
            Button({ apply() }) { Ico(R.drawable.ic_check, 18.dp, Color.White); Spacer(Modifier.width(4.dp)); Text("Aplicar", fontSize = 13.sp) }
            Button({ onClose() }, colors = ButtonDefaults.buttonColors(containerColor = Dark)) { Text("Concluir", fontSize = 13.sp) }
        }
    }
}
