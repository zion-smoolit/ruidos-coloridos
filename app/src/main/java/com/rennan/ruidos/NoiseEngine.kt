package com.rennan.ruidos

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max

/** Gera os ruídos em tempo real (offline) e mantém o estado observável pela UI. */
object NoiseEngine {
    private const val SR = 44100

    // Ganhos que igualam o volume (RMS) de cada cor
    private const val G_WHITE = 0.3462f
    private const val G_PINK = 0.1152f
    private const val G_BROWN = 3.4840f
    private const val G_BLUE = 0.1910f
    private const val G_VIOLET = 0.2450f

    private val main = Handler(Looper.getMainLooper())

    var playing by mutableStateOf(false)
        private set
    var selected by mutableStateOf(NoiseColor.PINK)
    val levels = mutableStateListOf(0f, 0.8f, 0f, 0f, 0f)
    var timerLeft by mutableIntStateOf(0)
        private set
    var onFinished: (() -> Unit)? = null

    private val targets = floatArrayOf(0f, 0.8f, 0f, 0f, 0f)
    @Volatile private var running = false
    @Volatile private var stopRequested = false

    fun setLevel(i: Int, v: Float) {
        levels[i] = v
        targets[i] = v
    }

    @Synchronized
    fun play() {
        stopRequested = false
        playing = true
        if (running) return
        running = true
        Thread({ loop() }, "noise").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        stopRequested = true
        playing = false
        cancelTimer()
    }

    // ---- timer com fade-out nos últimos 15 s ----
    private val tick = object : Runnable {
        override fun run() {
            if (timerLeft <= 0) return
            timerLeft -= 1
            if (timerLeft <= 0) stop() else main.postDelayed(this, 1000)
        }
    }

    fun startTimer(minutes: Int) {
        main.removeCallbacks(tick)
        timerLeft = minutes * 60
        if (minutes > 0) main.postDelayed(tick, 1000)
    }

    fun cancelTimer() {
        main.removeCallbacks(tick)
        timerLeft = 0
    }

    // ---- loop de áudio ----
    private fun loop() {
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, 4096) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()

            val buf = ShortArray(1024)
            var seed = System.nanoTime().toInt() or 1
            var b0 = 0f; var b1 = 0f; var b2 = 0f; var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f
            var brown = 0f
            var prevW = 0f
            var prevP = 0f
            val cur = FloatArray(5)
            var master = 0f

            while (true) {
                if (stopRequested && master < 0.002f) {
                    synchronized(this) { if (stopRequested) running = false }
                    if (!running) break
                }
                val tm = if (stopRequested) 0f else {
                    val t = timerLeft
                    if (t in 1..15) t / 15f else 1f
                }
                for (i in buf.indices) {
                    // xorshift32 -> branco uniforme em [-1, 1)
                    seed = seed xor (seed shl 13)
                    seed = seed xor (seed ushr 17)
                    seed = seed xor (seed shl 5)
                    val w = seed / 2147483648f

                    // rosa (Paul Kellet)
                    b0 = 0.99886f * b0 + w * 0.0555179f
                    b1 = 0.99332f * b1 + w * 0.0750759f
                    b2 = 0.96900f * b2 + w * 0.1538520f
                    b3 = 0.86650f * b3 + w * 0.3104856f
                    b4 = 0.55000f * b4 + w * 0.5329522f
                    b5 = -0.7616f * b5 - w * 0.0168980f
                    val pk = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f
                    b6 = w * 0.115926f

                    brown = (brown + 0.02f * w) / 1.02f   // integral do branco (com vazamento)
                    val blue = pk - prevP                  // derivada do rosa
                    prevP = pk
                    val violet = w - prevW                 // derivada do branco
                    prevW = w

                    for (k in 0..4) cur[k] += (targets[k] - cur[k]) * 0.001f
                    master += (tm - master) * 0.0005f

                    val s = (cur[0] * w * G_WHITE +
                        cur[1] * pk * G_PINK +
                        cur[2] * brown * G_BROWN +
                        cur[3] * blue * G_BLUE +
                        cur[4] * violet * G_VIOLET) * master
                    val c = if (s > 1f) 1f else if (s < -1f) -1f else s
                    buf[i] = (c * 32767f).toInt().toShort()
                }
                track.write(buf, 0, buf.size)
            }
        } catch (e: Exception) {
            // falha de áudio: encerra com segurança
        } finally {
            try { track?.stop() } catch (_: Exception) {}
            track?.release()
            running = false
            main.post {
                if (!running) playing = false
                onFinished?.invoke()
            }
        }
    }
}
