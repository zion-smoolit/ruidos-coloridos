package com.rennan.ruidos

import androidx.compose.ui.graphics.Color

enum class NoiseColor(
    val label: String,
    val beta: Float,
    val light: Color,
    val deep: Color,
    val formula: String,
    val slope: String,
    val feel: String,
    val explain: String,
) {
    WHITE(
        "Branco", 0f, Color(0xFFE8EAF6), Color(0xFF7986CB),
        "S(f) = constante", "plano · 0 dB por oitava", "chiado de TV fora do ar",
        "Todas as frequências têm a mesma potência. Cada amostra é um sorteio independente: x[n] entre −1 e 1. Como o ouvido é mais sensível aos agudos, o branco soa brilhante.",
    ),
    PINK(
        "Rosa", -1f, Color(0xFFF8BBD0), Color(0xFFD81B60),
        "S(f) ∝ 1/f", "−3 dB por oitava", "chuva forte, cachoeira",
        "A potência cai 3 dB a cada oitava, então cada oitava carrega a mesma energia. Por isso soa natural e equilibrado. É feito somando filtros de 1ª ordem sobre o ruído branco (método de Paul Kellet).",
    ),
    BROWN(
        "Marrom", -2f, Color(0xFFE0B27A), Color(0xFF8D5524),
        "S(f) ∝ 1/f²", "−6 dB por oitava", "trovão distante, vento grave",
        "É a integral do branco: y[n] = y[n−1] + ε·x[n], um passeio aleatório (movimento browniano). Dobrar a frequência divide a potência por 4. Resultado: grave, profundo e envolvente.",
    ),
    BLUE(
        "Azul", 1f, Color(0xFF90CAF9), Color(0xFF1565C0),
        "S(f) ∝ f", "+3 dB por oitava", "jato de ar, spray",
        "O espelho do rosa: derivada do ruído rosa, y[n] = p[n] − p[n−1]. A potência sobe 3 dB por oitava, realçando os agudos.",
    ),
    VIOLET(
        "Violeta", 2f, Color(0xFFD1C4E9), Color(0xFF7C4DFF),
        "S(f) ∝ f²", "+6 dB por oitava", "chiado agudo e fino",
        "Derivada do branco: y[n] = x[n] − x[n−1]. A potência cresce com o quadrado da frequência, quase só agudos. Prefira volume baixo.",
    ),
}
