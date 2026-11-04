package com.rennan.ruidos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

private val Bg = Color(0xFF0C0C12)
private val Panel = Color(0xFF17171F)

class MainActivity : ComponentActivity() {
    private val askNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startPlayback() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RuidosTheme { Screen(onToggle = { toggle() }) } }
    }

    private fun toggle() {
        if (NoiseEngine.playing) {
            NoiseEngine.stop()
            return
        }
        val needAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needAsk) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) else startPlayback()
    }

    private fun startPlayback() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, PlaybackService::class.java).setAction(PlaybackService.ACTION_PLAY)
        )
    }
}

@Composable
fun RuidosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB39DDB),
            background = Bg,
            surface = Panel,
            onBackground = Color(0xFFE6E1E9),
            onSurface = Color(0xFFE6E1E9),
        ),
        content = content,
    )
}

@Composable
fun Screen(onToggle: () -> Unit) {
    val sel = NoiseEngine.selected
    val playing = NoiseEngine.playing
    val c1 by animateColorAsState(sel.light, tween(700), label = "c1")
    val c2 by animateColorAsState(sel.deep, tween(700), label = "c2")
    var timerOpen by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(c2.copy(alpha = 0.45f), Color.Transparent), endY = 1400f)
            )
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 220.dp)
        ) {
            Reveal(shown, 0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ruídos Coloridos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Gerados em tempo real · 100% offline", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                    }
                    AnimatedVisibility(NoiseEngine.timerLeft > 0, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        Text(
                            fmt(NoiseEngine.timerLeft), color = c1, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(c1.copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Reveal(shown, 1) { SpectrumCard(sel, playing, c1, c2) }
            Spacer(Modifier.height(16.dp))
            Reveal(shown, 2) { NoiseChips() }
            Spacer(Modifier.height(16.dp))
            Reveal(shown, 3) {
                Panel(Modifier) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Volume · ${sel.label}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${(NoiseEngine.levels[sel.ordinal] * 100).toInt()}%", color = c1, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = NoiseEngine.levels[sel.ordinal],
                        onValueChange = { NoiseEngine.setLevel(sel.ordinal, it) },
                        colors = SliderDefaults.colors(
                            thumbColor = c1, activeTrackColor = c1,
                            inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                        )
                    )
                    Text("Dica: ajuste vários ruídos para misturar.", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Reveal(shown, 4) {
                Panel(Modifier.animateContentSize()) {
                    AnimatedContent(
                        targetState = sel,
                        transitionSpec = {
                            (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 4 }) togetherWith fadeOut(tween(200))
                        },
                        label = "explain"
                    ) { s ->
                        Column {
                            Text("Como soa: ${s.feel}", color = s.light, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(s.explain, color = Color.White.copy(alpha = 0.8f), lineHeight = 22.sp)
                        }
                    }
                }
            }
        }

        // ---- botões flutuantes ----
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(
                timerOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.8f) + slideInVertically { it / 2 },
                exit = fadeOut() + scaleOut(targetScale = 0.8f) + slideOutVertically { it / 2 }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { m ->
                        Pill("$m min", c1) {
                            NoiseEngine.startTimer(m)
                            if (!playing) onToggle()
                            timerOpen = false
                        }
                    }
                    if (NoiseEngine.timerLeft > 0) {
                        Pill("Cancelar", Color(0xFFFF8A80)) {
                            NoiseEngine.cancelTimer()
                            timerOpen = false
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SmallFloatingActionButton(
                    onClick = { timerOpen = !timerOpen },
                    containerColor = Panel, contentColor = c1
                ) { Glyph(G.TIMER, Modifier.size(22.dp)) }
                PlayFab(playing, c1, onToggle)
            }
        }
    }
}

@Composable
fun Reveal(visible: Boolean, index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(500, delayMillis = index * 90)) +
            slideInVertically(tween(500, delayMillis = index * 90)) { it / 4 }
    ) { content() }
}

@Composable
fun Panel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Panel)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
fun SpectrumCard(sel: NoiseColor, playing: Boolean, c1: Color, c2: Color) {
    val beta by animateFloatAsState(sel.beta, spring(dampingRatio = 0.6f, stiffness = 90f), label = "beta")
    val ripple by animateFloatAsState(if (playing) 1f else 0f, tween(600), label = "ripple")
    val phase by rememberInfiniteTransition(label = "phase").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "ph"
    )
    Panel(Modifier) {
        AnimatedContent(
            targetState = sel,
            transitionSpec = {
                (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }) togetherWith fadeOut(tween(200))
            },
            label = "formula"
        ) { s ->
            Column {
                Text(s.formula, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = s.light)
                Text(s.slope, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val w = size.width
            val h = size.height
            val dbRange = 40f
            fun yAt(db: Float) = h / 2f - (db / dbRange) * (h / 2f)
            fun xAtF(f: Float) = w * (ln(f / 20f) / ln(1000f))
            val grid = Color.White.copy(alpha = 0.08f)
            drawLine(grid, Offset(0f, h / 2f), Offset(w, h / 2f), 1.dp.toPx())
            for (f in listOf(100f, 1000f, 10000f)) {
                drawLine(grid, Offset(xAtF(f), 0f), Offset(xAtF(f), h), 1.dp.toPx())
            }
            val path = Path()
            val n = 120
            for (i in 0..n) {
                val t = i / n.toFloat()
                val f = 20f * 1000f.pow(t)
                var db = 10f * beta * log10(f / 1000f)
                db += sin(t * 18f + phase) * 1.6f * ripple
                db = db.coerceIn(-dbRange, dbRange)
                val x = t * w
                val y = yAt(db)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(c1.copy(alpha = 0.22f), Color.Transparent)))
            val line = Brush.horizontalGradient(listOf(c2, c1))
            drawPath(path, line, alpha = 0.25f, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, line, style = Stroke(3.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (t in listOf("20 Hz", "1 kHz", "20 kHz")) {
                Text(t, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun NoiseChips() {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NoiseColor.entries.forEach { nc ->
            val selected = NoiseEngine.selected == nc
            val scale by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = 0.55f), label = "chip")
            val level = NoiseEngine.levels[nc.ordinal]
            val shape = RoundedCornerShape(20.dp)
            Column(
                Modifier
                    .scale(scale)
                    .clip(shape)
                    .background(if (selected) nc.deep.copy(alpha = 0.35f) else Panel)
                    .border(1.5.dp, if (selected) nc.light else Color.White.copy(alpha = 0.06f), shape)
                    .clickable { NoiseEngine.selected = nc }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(nc.light, nc.deep)))
                        .alpha(0.4f + 0.6f * level)
                )
                Spacer(Modifier.height(8.dp))
                Text(nc.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(36.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
                    Box(Modifier.fillMaxWidth(level).fillMaxHeight().background(nc.light))
                }
            }
        }
    }
}

@Composable
fun PlayFab(playing: Boolean, c1: Color, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "pulse")
    val ring by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing)),
        label = "ring"
    )
    Box(contentAlignment = Alignment.Center) {
        if (playing) {
            Canvas(Modifier.size(112.dp)) {
                val r = size.minDimension / 2f * (0.6f + 0.4f * ring)
                drawCircle(c1.copy(alpha = 0.35f * (1f - ring)), radius = r)
            }
        }
        LargeFloatingActionButton(
            onClick = onClick,
            containerColor = c1,
            contentColor = Color(0xFF14101F),
            shape = CircleShape
        ) {
            AnimatedContent(
                targetState = playing,
                transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
                label = "icon"
            ) { p -> Glyph(if (p) G.PAUSE else G.PLAY, Modifier.size(36.dp)) }
        }
    }
}

@Composable
fun Pill(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, color = color, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Panel)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

enum class G { PLAY, PAUSE, TIMER }

@Composable
fun Glyph(g: G, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (g) {
            G.PLAY -> {
                val p = Path().apply {
                    moveTo(w * 0.22f, h * 0.12f)
                    lineTo(w * 0.88f, h * 0.5f)
                    lineTo(w * 0.22f, h * 0.88f)
                    close()
                }
                drawPath(p, color)
            }
            G.PAUSE -> {
                drawRoundRect(color, Offset(w * 0.18f, h * 0.12f), Size(w * 0.24f, h * 0.76f), CornerRadius(w * 0.06f))
                drawRoundRect(color, Offset(w * 0.58f, h * 0.12f), Size(w * 0.24f, h * 0.76f), CornerRadius(w * 0.06f))
            }
            G.TIMER -> {
                val sw = w * 0.09f
                drawCircle(color, radius = w * 0.4f, style = Stroke(sw))
                drawLine(color, Offset(w / 2f, h / 2f), Offset(w / 2f, h * 0.28f), sw, StrokeCap.Round)
                drawLine(color, Offset(w / 2f, h / 2f), Offset(w * 0.66f, h * 0.58f), sw, StrokeCap.Round)
            }
        }
    }
}

private fun fmt(s: Int) = "%02d:%02d".format(s / 60, s % 60)
