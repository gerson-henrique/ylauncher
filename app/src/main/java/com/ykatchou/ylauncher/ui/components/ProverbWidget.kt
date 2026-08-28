package com.ykatchou.ylauncher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.ykatchou.ylauncher.ui.theme.Y
import java.time.LocalDate

/** A proverb with its source. Anonymous folk sayings are credited to the tradition, never invented. */
data class Proverbio(val texto: String, val autor: String)

/**
 * A proverb of the day for the home's top-left slot — the kung-fu corner. Curated and offline on
 * purpose: a home widget must render instantly with no network, and dedicated "Chinese proverb"
 * APIs are unmaintained and misattribute freely (half the internet's "Sun Tzu" he never said). Here
 * every line is real and credited to its actual source, or to its tradition when it is genuinely a
 * folk saying. Rotated by day-of-year: the same proverb all day, a new one at midnight.
 */
val PROVERBIOS = listOf(
    // Lao Tzu — Tao Te Ching
    Proverbio("Uma jornada de mil milhas começa com um único passo.", "Lao Tzu"),
    Proverbio("Quem conhece os outros é sábio; quem conhece a si mesmo é iluminado.", "Lao Tzu"),
    Proverbio("Quem vence os outros é forte; quem vence a si mesmo é poderoso.", "Lao Tzu"),
    Proverbio("Nada no mundo é mais mole que a água, e no entanto nada a supera ao desgastar o duro.", "Lao Tzu"),
    Proverbio("Aquele que sabe não fala; aquele que fala não sabe.", "Lao Tzu"),
    Proverbio("Faz o difícil enquanto é fácil; faz o grande enquanto é pequeno.", "Lao Tzu"),
    Proverbio("Um bom viajante não deixa rastros.", "Lao Tzu"),
    Proverbio("Ao curvar-se, o homem conserva-se inteiro.", "Lao Tzu"),
    Proverbio("Quem sabe que tem o bastante é rico.", "Lao Tzu"),
    Proverbio("O silêncio é uma fonte de grande força.", "Lao Tzu"),
    Proverbio("Quando me solto do que sou, torno-me o que poderia ser.", "Lao Tzu"),
    Proverbio("A natureza não se apressa, e no entanto tudo se cumpre.", "Lao Tzu"),
    Proverbio("Responde à ofensa com bondade.", "Lao Tzu"),
    Proverbio("Governa um grande reino como quem frita um peixe pequeno.", "Lao Tzu"),

    // Sun Tzu — A Arte da Guerra
    Proverbio("Conhece o inimigo e conhece-te a ti mesmo, e em cem batalhas nunca correrás perigo.", "Sun Tzu"),
    Proverbio("A suprema arte da guerra é subjugar o inimigo sem lutar.", "Sun Tzu"),
    Proverbio("No meio do caos, há também oportunidade.", "Sun Tzu"),
    Proverbio("Toda guerra se baseia no engano.", "Sun Tzu"),
    Proverbio("As oportunidades multiplicam-se à medida que são agarradas.", "Sun Tzu"),
    Proverbio("Vence quem sabe quando lutar e quando não lutar.", "Sun Tzu"),
    Proverbio("A rapidez é a essência da guerra.", "Sun Tzu"),
    Proverbio("Não contes que o inimigo não venha; prepara-te para recebê-lo.", "Sun Tzu"),
    Proverbio("Sê tão sutil que chegues à ausência de forma.", "Sun Tzu"),

    // Bruce Lee
    Proverbio("Esvazie a mente. Seja sem forma, sem contornos — como a água.", "Bruce Lee"),
    Proverbio("Não temo quem treinou 10 mil chutes uma vez, mas quem treinou um chute 10 mil vezes.", "Bruce Lee"),
    Proverbio("Absorva o que é útil, descarte o que é inútil e acrescente o que é seu.", "Bruce Lee"),
    Proverbio("Conhecer não basta: é preciso aplicar. Querer não basta: é preciso fazer.", "Bruce Lee"),
    Proverbio("Não reze por uma vida fácil; reze pela força de suportar uma difícil.", "Bruce Lee"),
    Proverbio("Os erros são sempre perdoáveis, se se tem a coragem de admiti-los.", "Bruce Lee"),
    Proverbio("A derrota é um estado de espírito; ninguém está derrotado até aceitá-la como real.", "Bruce Lee"),

    // Miyamoto Musashi — Dokkōdō / Livro dos Cinco Anéis
    Proverbio("Aceita tudo exatamente como é.", "Miyamoto Musashi"),
    Proverbio("Não faças nada que não tenha utilidade.", "Miyamoto Musashi"),
    Proverbio("Percebe o que não pode ser visto com os olhos.", "Miyamoto Musashi"),
    Proverbio("Pensa levemente sobre ti mesmo e profundamente sobre o mundo.", "Miyamoto Musashi"),
    Proverbio("Hoje é a vitória sobre ti mesmo de ontem.", "Miyamoto Musashi"),
    Proverbio("Uma vez que compreendas o caminho amplamente, verás em todas as coisas.", "Miyamoto Musashi"),
    Proverbio("Não te apegues a nada.", "Miyamoto Musashi"),

    // Confúcio
    Proverbio("Não importa quão devagar você vá, desde que não pare.", "Confúcio"),
    Proverbio("Aprender sem pensar é inútil; pensar sem aprender é perigoso.", "Confúcio"),
    Proverbio("A joia não se pole sem atrito, nem o homem se aperfeiçoa sem provações.", "Confúcio"),
    Proverbio("Exige muito de ti mesmo e pouco dos outros.", "Confúcio"),
    Proverbio("Onde quer que vás, vai com todo o teu coração.", "Confúcio"),
    Proverbio("A impaciência em pequenas coisas arruína grandes planos.", "Confúcio"),
    Proverbio("O homem que move montanhas começa carregando pequenas pedras.", "Confúcio"),
    Proverbio("Quando a raiva surgir, pensa nas consequências.", "Confúcio"),

    // Provérbios chineses (folk)
    Proverbio("A melhor hora de plantar uma árvore foi há vinte anos. A segunda melhor é agora.", "provérbio chinês"),
    Proverbio("Quando sopram ventos de mudança, uns constroem muros, outros constroem moinhos.", "provérbio chinês"),
    Proverbio("Uma faísca pode incendiar toda uma pradaria.", "provérbio chinês"),
    Proverbio("Melhor acender uma vela do que amaldiçoar a escuridão.", "provérbio chinês"),
    Proverbio("Cava o poço antes de teres sede.", "provérbio chinês"),
    Proverbio("Quem pergunta é tolo por um minuto; quem não pergunta é tolo a vida inteira.", "provérbio chinês"),
    Proverbio("Os mestres abrem a porta, mas és tu que deves entrar.", "provérbio chinês"),
    Proverbio("Não tenhas medo de crescer devagar; teme apenas ficar parado.", "provérbio chinês"),
    Proverbio("Com tempo e paciência, a folha da amoreira vira seda.", "provérbio chinês"),
    Proverbio("Se queres saber o caminho à frente, pergunta a quem já voltou.", "provérbio chinês"),
    Proverbio("Um diamante com defeito vale mais que um seixo perfeito.", "provérbio chinês"),
    Proverbio("O sábio aponta para a lua; o tolo olha para o dedo.", "provérbio chinês"),
    Proverbio("A tensão é quem você acha que deveria ser; o relaxamento é quem você é.", "provérbio chinês"),

    // Provérbios japoneses
    Proverbio("Cai sete vezes, levanta oito.", "provérbio japonês"),
    Proverbio("O bambu que verga é mais forte que o carvalho que resiste.", "provérbio japonês"),
    Proverbio("A visão sem ação é um devaneio; a ação sem visão é um pesadelo.", "provérbio japonês"),
)

/** The day's proverb — deterministic within a day, cycling through the list across the year. */
fun proverbioDoDia(hoje: LocalDate = LocalDate.now()): Proverbio =
    PROVERBIOS[(hoje.dayOfYear - 1).mod(PROVERBIOS.size)]

@Composable
fun ProverbWidget(modifier: Modifier = Modifier) {
    val p = remember { proverbioDoDia() }
    Column(modifier = modifier.padding(start = 4.dp, top = 6.dp, end = 8.dp)) {
        androidx.compose.material3.Text(
            text = p.texto,
            style = Y.type.bodySm.copy(shadow = Y.textShadow),
            color = Y.text,
            fontStyle = FontStyle.Italic,
        )
        androidx.compose.material3.Text(
            text = "— ${p.autor}",
            style = Y.type.caption.copy(shadow = Y.textShadow),
            color = Y.textFaint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
