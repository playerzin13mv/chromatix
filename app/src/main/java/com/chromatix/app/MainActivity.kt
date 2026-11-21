package com.chromatix.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Cfg.init(this); Store.init(this)
        setContent { App() }
    }
    override fun onStop() { super.onStop(); Engine.stop() }
}

val Violet = Color(0xFF7C5CFF)
val Cyan = Color(0xFF27E0C8)
val Dark = Color(0xFF2E2658)
val Muted = Color(0xFF9A93C4)
private val Scheme = darkColorScheme(
    primary = Violet, secondary = Cyan, background = Color(0xFF14102A), surface = Color(0xFF1F1940),
    surfaceVariant = Dark, onPrimary = Color.White, onBackground = Color.White, onSurface = Color.White,
)

fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_LONG).show()

@Composable
fun Ico(id: Int, size: Dp = 22.dp, tint: Color = LocalContentColor.current) = Icon(painterResource(id), null, Modifier.size(size), tint)

@Composable
fun App() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var expP by remember { mutableStateOf<Proj?>(null) }
    var pend by remember { mutableStateOf<Proj?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u ->
        val p = pend
        if (u != null && p != null) try {
            ctx.contentResolver.openOutputStream(u)?.use { it.write(Sf2.build(p)) }
            toast(ctx, "Exportado!")
        } catch (e: Exception) { toast(ctx, "Erro: ${e.message}") }
    }
    fun save(q: Proj) {
        try { toast(ctx, "Salvo em " + Out.save(ctx, Sf2.fileName(q), Sf2.build(q))) }
        catch (e: Exception) { toast(ctx, "Erro: ${e.message}") }
    }
    val wperm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) expP?.let { save(it) } else toast(ctx, "Sem permissão para salvar")
        expP = null
    }
    val export: (Proj) -> Unit = { p ->
        val q = if (p.pcm.any { it != null }) p else Store.full(p)
        if (!q.done) toast(ctx, "Faltam: ${q.missing()}") else expP = q
    }
    MaterialTheme(colorScheme = Scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(R.drawable.ic_folder to "Projetos", R.drawable.ic_piano to "Editor", R.drawable.ic_sliders to "Config")
                        .forEachIndexed { i, (ic, l) -> NavigationRailItem(tab == i, { tab = i }, { Ico(ic) }, label = { Text(l, fontSize = 10.sp) }) }
                }
                Box(Modifier.weight(1f).padding(8.dp)) {
                    when (tab) {
                        0 -> Projects({ Store.open(it); tab = 1 }, export)
                        1 -> Editor(export)
                        else -> Settings()
                    }
                }
            }
        }
        expP?.let { q ->
            AlertDialog(onDismissRequest = { expP = null }, title = { Text("Exportar “${q.name}”") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("O arquivo vai para a pasta ${Out.DIR} do celular.", fontSize = 13.sp)
                        Btn2("SoundFont (.sf2)", R.drawable.ic_export) {
                            if (Build.VERSION.SDK_INT >= 29 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) { save(q); expP = null }
                            else wperm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        Btn2("Escolher outro local…", R.drawable.ic_folder) { pend = q; saver.launch(Sf2.fileName(q)); expP = null }
                        Text("Para .dwp (FL Studio): abra o .sf2 no DirectWave e salve como .dwp.", fontSize = 12.sp, color = Muted)
                    }
                },
                confirmButton = {}, dismissButton = { TextButton({ expP = null }) { Text("Fechar") } })
        }
    }
}

@Composable
fun Projects(open: (Proj) -> Unit, export: (Proj) -> Unit) {
    var dlg by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Proj?>(null) }
    LaunchedEffect(Unit) { Store.refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button({ dlg = true }) { Ico(R.drawable.ic_add, 18.dp, Color.White); Spacer(Modifier.width(6.dp)); Text("Novo projeto") }
        if (Store.list.isEmpty()) Text("Nenhum projeto ainda. Toque em “Novo projeto”.", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Store.list, key = { it.id }) { p ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text(Store.info(p) + " · raiz ${nt(p.root)}", fontSize = 12.sp, color = Muted)
                        }
                        TextButton({ open(p) }) { Text("Abrir") }
                        IconButton({ Store.duplicate(p) }) { Ico(R.drawable.ic_copy) }
                        IconButton({ export(p) }) { Ico(R.drawable.ic_export) }
                        IconButton({ del = p }) { Ico(R.drawable.ic_trash) }
                    }
                }
            }
        }
    }
    if (dlg) {
        var t by remember { mutableStateOf("Meu Chromatic") }
        AlertDialog(onDismissRequest = { dlg = false },
            confirmButton = { TextButton({ val p = Store.create(t.ifBlank { "Chromatic" }); dlg = false; open(p) }) { Text("Criar") } },
            dismissButton = { TextButton({ dlg = false }) { Text("Cancelar") } },
            title = { Text("Novo projeto") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) })
    }
    del?.let { p ->
        AlertDialog(onDismissRequest = { del = null },
            confirmButton = { TextButton({ Store.delete(p); del = null }) { Text("Excluir") } },
            dismissButton = { TextButton({ del = null }) { Text("Cancelar") } },
            title = { Text("Excluir “${p.name}”?") })
    }
}

@Composable
fun Editor(export: (Proj) -> Unit) {
    val p = Store.cur
    val ctx = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val rec = remember { Rec() }
    var sel by remember(p?.id, p?.mode) {
        mutableIntStateOf(when (p?.mode) { 1 -> 4; 2 -> (p?.keys?.firstOrNull()?.let { 6 + it } ?: 66); else -> 0 })
    }
    var tgt by remember { mutableIntStateOf(0) }
    var recing by remember { mutableStateOf(false) }
    var ren by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var cut by remember { mutableStateOf<CutSession?>(null) }
    var imp by remember { mutableStateOf<Pair<ShortArray, Int>?>(null) }
    var start by remember { mutableIntStateOf(12 * Cfg.o) }

    fun got(slot: Int, d: ShortArray, r: Int) {
        val was = Store.cur?.done == true
        Store.setPcm(slot, Audio.norm(d), r)
        val c = Store.cur ?: return
        if (c.vow) {
            if (slot == 5) toast(ctx, "U adicionado! Agora o teclado repete A E I O U")
            else if (c.done && !was) toast(ctx, "Vogais completas! Elas se repetem no teclado todo (o U é opcional)")
            else (0..3).firstOrNull { c.pcm[it] == null }?.let { sel = it }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) Thread {
            try { val (d, r) = Decoder.decode(ctx, u); main.post { imp = Pair(d, r) } }
            catch (e: Exception) { main.post { toast(ctx, "Não consegui ler: ${e.message}") } }
        }.start()
    }
    val startRec = {
        tgt = sel
        try { rec.start(); recing = true } catch (e: Exception) { toast(ctx, "Microfone indisponível: ${e.message}") }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRec() else toast(ctx, "Sem permissão do microfone")
    }
    if (p == null) { Text("Abra ou crie um projeto na aba Projetos."); return }
    val c0 = cut
    if (c0 != null) { Cutter(c0) { cut = null }; return }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val title = p.name + when (p.mode) {
                    0 -> " · ${(0..3).count { p.pcm[it] != null }}/4 vogais" + (if (p.hasU) " +U" else "")
                    2 -> " · ${p.keys.size} notas"
                    else -> ""
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.weight(1f).clickable { ren = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(4.dp)); Ico(R.drawable.ic_edit, 14.dp)
                    }
                    Ch("Vogais", p.mode == 0) { Store.edit { it.copy(mode = 0) } }
                    Ch("Único", p.mode == 1) { Store.edit { it.copy(mode = 1) } }
                    Ch("Escala", p.mode == 2) { Store.edit { it.copy(mode = 2) } }
                }
                Wave(p, sel, Modifier.weight(1f).fillMaxWidth())
                if (p.mode == 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in listOf(0, 1, 2, 3, 5)) Pill(letter(i) + if (i == 5 && p.pcm[5] == null) " (opc.)" else "", sel == i, p.pcm[i] != null, Modifier.weight(1f)) { sel = i }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Btn("Importar", R.drawable.ic_import) { tgt = sel; pick.launch("audio/*") }
                    Btn(if (recing) "Parar" else "Gravar", if (recing) R.drawable.ic_stop else R.drawable.ic_mic) {
                        if (recing) { val d = rec.stop(); recing = false; if (d.size > 200) got(tgt, d, 44100) }
                        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRec()
                        else perm.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    Hold("Ouvir", R.drawable.ic_play, { Engine.on(-1, p.rootFor(sel), sel) }, { Engine.off(-1) })
                    if (p.pcm[sel] != null) Btn("Cortar", R.drawable.ic_cut) { cut = CutSession(2, p.pcm[sel]!!, p.rate[sel], listOf(sel)) }
                    Btn("Exportar", R.drawable.ic_export) { export(p) }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)).padding(8.dp)) {
                if (p.mode != 2) Box {
                    Btn2("Nota raiz: ${nt(p.root)} (${p.root})", R.drawable.ic_piano) { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        for (m in 24..96) DropdownMenuItem(text = { Text("${nt(m)} ($m)") }, onClick = { Store.edit { it.copy(root = m) }; menu = false })
                    }
                } else Text("Cada nota toca no tom em que foi cortada. Teclas sem sample usam a nota cortada mais próxima.", fontSize = 12.sp, color = Muted)
                Par("Afinação fina (cents)", p.tune.toFloat(), -99f..99f, "%.0f") { v -> Store.edit { it.copy(tune = v.toInt()) } }
                Par("Ataque (s)", p.a, 0f..2f) { v -> Store.edit { it.copy(a = v) } }
                Par("Decaimento (s)", p.d, 0f..3f) { v -> Store.edit { it.copy(d = v) } }
                Par("Sustain", p.s, 0f..1f) { v -> Store.edit { it.copy(s = v) } }
                Par("Release (s)", p.r, 0f..3f) { v -> Store.edit { it.copy(r = v) } }
                Par("Volume (dB)", p.vol, -24f..0f, "%.0f") { v -> Store.edit { it.copy(vol = v) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Loop", Modifier.weight(1f), fontSize = 12.sp)
                    Switch(p.loop, { v -> Store.edit { it.copy(loop = v) } })
                }
                if (p.loop) {
                    Par("Início do loop", p.ls, 0f..1f) { v -> Store.edit { it.copy(ls = v) } }
                    Par("Fim do loop", p.le, 0f..1f) { v -> Store.edit { it.copy(le = v) } }
                }
            }
        }
        Piano(p, Modifier.fillMaxWidth().fillMaxHeight(.3f)) { m -> if (p.mode == 2) { val s = p.slot(m); if (s >= 0) sel = s } }
    }
    if (ren) {
        var t by remember { mutableStateOf(p.name) }
        AlertDialog(onDismissRequest = { ren = false },
            confirmButton = { TextButton({ Store.edit { it.copy(name = t.ifBlank { "Chromatic" }) }; ren = false }) { Text("OK") } },
            title = { Text("Renomear") }, text = { OutlinedTextField(t, { t = it }, singleLine = true) })
    }
    imp?.let { (d, r) ->
        AlertDialog(onDismissRequest = { imp = null }, title = { Text("O que fazer com este áudio?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("%.1f s".format(d.size / r.toFloat()), fontSize = 12.sp, color = Muted)
                    if (p.mode != 2) Btn2(if (p.mode == 0) "Usar inteiro na vogal ${letter(sel)}" else "Usar inteiro como sample", R.drawable.ic_import) { got(tgt, d, r); imp = null }
                    Btn2("Cortar em vogais A E I O", R.drawable.ic_cut) { cut = CutSession(0, d, r, listOf(0, 1, 2, 3)); imp = null }
                    Btn2("Cortar em vogais A E I O U", R.drawable.ic_cut) { cut = CutSession(0, d, r, listOf(0, 1, 2, 3, 5)); imp = null }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Escala cromática, primeira nota:", Modifier.weight(1f), fontSize = 12.sp)
                        IconButton({ if (start > 0) start-- }) { Ico(R.drawable.ic_remove) }
                        Text(nt(start), fontWeight = FontWeight.Bold)
                        IconButton({ if (start < 120) start++ }) { Ico(R.drawable.ic_add) }
                    }
                    Btn2("Cortar escala (1 nota por tecla)", R.drawable.ic_cut) { cut = CutSession(1, d, r, emptyList(), start); imp = null }
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ imp = null }) { Text("Cancelar") } })
    }
}

@Composable
fun Wave(p: Proj, sel: Int, m: Modifier) {
    Box(m.background(Color(0xFF1F1940), RoundedCornerShape(10.dp))) {
        val d = p.pcm.getOrNull(sel)
        Canvas(Modifier.fillMaxSize()) {
            if (d == null) return@Canvas
            val w = size.width.toInt(); val h = size.height
            val k = d.size.toFloat() / w; val st = maxOf(1, (k / 40).toInt())
            for (i in 0 until w) {
                var lo = 0; var hi = 0; var j = (i * k).toInt(); val e = minOf(((i + 1) * k).toInt(), d.size)
                while (j < e) { val v = d[j].toInt(); if (v < lo) lo = v; if (v > hi) hi = v; j += st }
                drawLine(Cyan, Offset(i.toFloat(), h / 2 - hi / 32768f * h / 2), Offset(i.toFloat(), h / 2 - lo / 32768f * h / 2 + 1f), 1f)
            }
            val dim = Color(0xCC14102A)
            drawRect(dim, Offset.Zero, Size(p.ts[sel] * size.width, h))
            drawRect(dim, Offset(p.te[sel] * size.width, 0f), Size(size.width, h))
            if (p.loop) for (f in listOf(p.ls, p.le)) drawRect(Violet, Offset((p.ts[sel] + (p.te[sel] - p.ts[sel]) * f) * size.width, 0f), Size(2f, h))
        }
        if (d == null) Text(
            when (p.mode) { 0 -> "Grave ou importe a vogal ${letter(sel)}"; 2 -> "Importe uma escala cromática e corte as notas"; else -> "Importe ou grave um sample" },
            Modifier.align(Alignment.Center), color = Muted)
    }
}

private val Up = listOf(0xFFF8C8D8, 0xFFFFF0B8, 0xFFC8F0D0, 0xFFC8DCFF, 0xFFE4C8F8).map { Color(it) }   // A E I O U
private val Bk = listOf(0xFF6A2A40, 0xFF6A5A20, 0xFF20603A, 0xFF20406A, 0xFF4A2A6A).map { Color(it) }

@Composable
fun Piano(p: Proj, mod: Modifier, onHit: (Int) -> Unit) {
    val s = 12 * Cfg.o; val e = s + 12 * Cfg.n; val bl = setOf(1, 3, 6, 8, 10)
    val whites = (s..e).filter { it % 12 !in bl }
    BoxWithConstraints(mod) {
        val ww = maxWidth / whites.size
        Row(Modifier.fillMaxSize(), Arrangement.spacedBy(1.dp)) {
            whites.forEach { m -> Key(m, p, false, Modifier.weight(1f).fillMaxHeight(), onHit) }
        }
        (s..e).filter { it % 12 in bl }.forEach { m ->
            Key(m, p, true, Modifier.offset(x = ww * whites.count { it < m } - ww * .3f).width(ww * .6f).fillMaxHeight(.62f), onHit)
        }
    }
}

@Composable
fun Key(m: Int, p: Proj, black: Boolean, mod: Modifier, onHit: (Int) -> Unit) {
    var down by remember { mutableStateOf(false) }
    val sh = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
    val sl = p.slot(m)
    val ok = sl >= 0 && p.pcm[sl] != null
    val v = if (sl == 5) 4 else sl.coerceIn(0, 3)
    val own = p.mode == 2 && p.pcm[6 + m] != null
    val bg = when {
        down -> Cyan
        !ok -> if (black) Color(0xFF222222) else Color(0xFF8A8A8A)
        p.mode == 0 -> if (black) Bk[v] else Up[v]
        own -> if (black) Color(0xFF4A3A8A) else Color(0xFFD8CCFF)
        else -> if (black) Color(0xFF111111) else Color(0xFFF2F0FF)
    }
    val lab = when { p.mode == 0 -> letter(sl).toString(); m % 12 == 0 -> nt(m); else -> "" }
    Box(mod.background(bg, sh)
        .then(if (m == p.root && p.mode != 2) Modifier.border(2.dp, Violet, sh) else Modifier)
        .pointerInput(m) {
            detectTapGestures(onPress = {
                down = true; onHit(m); Engine.on(m, m, Store.cur?.slot(m) ?: 4)
                try { tryAwaitRelease() } finally { down = false; Engine.off(m) }
            })
        }, Alignment.BottomCenter) {
        if (lab.isNotEmpty()) Text(lab, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (black) Color.White else Color(0xFF333333))
    }
}

@Composable
fun Par(l: String, v: Float, r: ClosedFloatingPointRange<Float>, f: String = "%.2f", on: (Float) -> Unit) {
    Column {
        Row { Text(l, Modifier.weight(1f), fontSize = 12.sp); Text(f.format(v), fontSize = 12.sp, color = Cyan) }
        Slider(v, on, Modifier.height(32.dp), valueRange = r)
    }
}

@Composable
fun Ch(t: String, sel: Boolean, on: () -> Unit) =
    Button(on, colors = ButtonDefaults.buttonColors(containerColor = if (sel) Violet else Dark), contentPadding = PaddingValues(horizontal = 10.dp)) { Text(t, fontSize = 12.sp) }

@Composable
fun RowScope.Btn(t: String, ic: Int, on: () -> Unit) =
    Button(on, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Dark), contentPadding = PaddingValues(4.dp)) {
        Ico(ic, 16.dp, Color.White); Spacer(Modifier.width(3.dp)); Text(t, fontSize = 11.sp, maxLines = 1)
    }

@Composable
fun Btn2(t: String, ic: Int, on: () -> Unit) =
    Button(on, colors = ButtonDefaults.buttonColors(containerColor = Dark)) {
        Ico(ic, 18.dp, Color.White); Spacer(Modifier.width(6.dp)); Text(t, fontSize = 13.sp)
    }

@Composable
fun RowScope.Hold(t: String, ic: Int, down: () -> Unit, up: () -> Unit) {
    val d by rememberUpdatedState(down); val u by rememberUpdatedState(up)
    Row(Modifier.weight(1f).height(40.dp).background(Dark, RoundedCornerShape(20.dp))
        .pointerInput(Unit) { detectTapGestures(onPress = { d(); try { tryAwaitRelease() } finally { u() } }) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Ico(ic, 16.dp, Color.White); Spacer(Modifier.width(3.dp)); Text(t, fontSize = 11.sp)
    }
}

@Composable
fun Settings() {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Opt("Oitava inicial") { (1..5).forEach { i -> Ch("$i", Cfg.o == i) { Cfg.o = i; Cfg.save() } } }
        Opt("Nº de oitavas") { (1..4).forEach { i -> Ch("$i", Cfg.n == i) { Cfg.n = i; Cfg.save() } } }
        Opt("Nome das notas") {
            Ch("FL Studio (C5 = 60)", Cfg.fl) { Cfg.fl = true; Cfg.save() }
            Ch("Padrão (C4 = 60)", !Cfg.fl) { Cfg.fl = false; Cfg.save() }
        }
        Par("Volume do piano", Cfg.vol, .1f..1f) { Cfg.vol = it; Cfg.save() }
        Par("Reforço de graves no piano (dB por oitava abaixo de G3)", Cfg.bass, 0f..12f, "%.0f") { Cfg.bass = it; Cfg.save() }
        Par("Release padrão (s)", Cfg.rel, .01f..2f) { Cfg.rel = it; Cfg.save() }
        Text("Os arquivos exportados ficam em ${Out.DIR}.", fontSize = 12.sp, color = Muted)
        Text("Chromatix 0.3 · Kotlin + Jetpack Compose", fontSize = 12.sp, color = Muted)
    }
}

@Composable
fun Opt(l: String, c: @Composable RowScope.() -> Unit) {
    Column { Text(l, fontSize = 12.sp); Row(Modifier.horizontalScroll(rememberScrollState()), Arrangement.spacedBy(6.dp), content = c) }
}
