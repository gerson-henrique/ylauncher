package com.ykatchou.ylauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ykatchou.ylauncher.ui.theme.Y

/** A proverb: Portuguese, its authentic Chinese source, and the author (romanized + 中文). */
data class Proverbio(val pt: String, val zh: String, val autor: String, val autorZh: String)

private fun grupo(autor: String, autorZh: String, vararg itens: Pair<String, String>): List<Proverbio> =
    itens.map { Proverbio(it.first, it.second, autor, autorZh) }

/**
 * The kung-fu corner of the home: a Chinese proverb, drawn at random. Curated and offline — a home
 * widget must render instantly with no network, and dedicated proverb APIs misattribute freely.
 * Every entry is genuinely Chinese in origin, so each carries its real 中文 and a real author; folk
 * sayings are credited to the tradition (谚语). The Portuguese is written to read naturally and land
 * like a proverb, not as a word-for-word gloss of the classical Chinese.
 */
val PROVERBIOS: List<Proverbio> =
    grupo(
        "Lao Tzu", "老子",
        "Uma jornada de mil léguas começa com um único passo." to "千里之行，始于足下",
        "Conhecer os outros é sabedoria; conhecer a si mesmo é iluminação." to "知人者智，自知者明",
        "A maior virtude é como a água: serve a tudo sem disputar nada." to "上善若水",
        "Vencer os outros exige força; vencer a si mesmo é o verdadeiro poder." to "胜人者有力，自胜者强",
        "Rico é quem sabe se contentar." to "知足者富",
        "Os grandes talentos amadurecem tarde." to "大器晚成",
        "Toda tarefa difícil começou fácil um dia." to "天下难事，必作于易",
        "Governar um grande país é como fritar um peixinho: não mexa demais." to "治大国若烹小鲜",
        "Quem sabe não fica falando; quem fica falando não sabe." to "知者不言，言者不知",
        "A verdadeira habilidade parece desajeitada." to "大巧若拙",
        "Na desgraça mora a sorte; na sorte se esconde a desgraça." to "祸兮福所倚，福兮祸所伏",
        "O suave vence o rígido; o fraco vence o forte." to "柔弱胜刚强",
        "Cuide do fim como cuidou do começo, e nada vai dar errado." to "慎终如始，则无败事",
        "Sem forçar nada, nada fica por fazer." to "无为而无不为",
        "Enfrente o difícil enquanto ainda é fácil." to "图难于其易",
        "Quem se contenta não passa vergonha." to "知足不辱",
    ) + grupo(
        "Confúcio", "孔子",
        "Aprender sem pensar é perda de tempo; pensar sem aprender é perigoso." to "学而不思则罔，思而不学则殆",
        "Não faça aos outros o que não quer para você." to "己所不欲，勿施于人",
        "Onde há três pessoas, sempre há algo a aprender com uma delas." to "三人行，必有我师焉",
        "Revisite o velho e você descobrirá o novo." to "温故而知新",
        "Quem quer fazer um bom trabalho começa afiando as ferramentas." to "工欲善其事，必先利其器",
        "A pressa atrapalha quem quer chegar." to "欲速则不达",
        "Rápido para agir, cuidadoso para falar." to "敏于事而慎于言",
        "Aprender e praticar na hora certa — que prazer!" to "学而时习之，不亦说乎",
        "Errar e não corrigir: isso sim é um erro." to "过而不改，是谓过矣",
        "Não se preocupe se não te conhecem; preocupe-se em não conhecer os outros." to "不患人之不己知，患不知人也",
        "O sábio cobra de si mesmo; o mesquinho cobra dos outros." to "君子求诸己，小人求诸人",
        "Ao encontrar alguém melhor, procure se igualar a ele." to "见贤思齐焉",
        "Dá pra tirar o general de um exército, mas não a determinação de um homem." to "三军可夺帅也，匹夫不可夺志也",
        "Curioso e estudioso, sem vergonha de perguntar a quem sabe menos." to "敏而好学，不耻下问",
        "Não segurar as pequenas impaciências estraga os grandes planos." to "小不忍则乱大谋",
        "Só no rigor do inverno se vê que o pinheiro é o último a murchar." to "岁寒，然后知松柏之后凋也",
    ) + grupo(
        "Sun Tzu", "孙子",
        "Conheça a si mesmo e ao inimigo, e não temerá cem batalhas." to "知己知彼，百战不殆",
        "Vencer sem lutar é a maior das vitórias." to "不战而屈人之兵，善之善者也",
        "Na guerra, a rapidez vale ouro." to "兵贵神速",
        "A guerra é a arte do engano." to "兵者，诡道也",
        "A melhor estratégia é desmontar os planos do inimigo." to "上兵伐谋",
        "Enfrente com o método, vença com a surpresa." to "以正合，以奇胜",
        "Imóvel como quem espera, veloz como a lebre em fuga." to "静如处子，动如脱兔",
        "O bom guerreiro dita o jogo, não dança conforme a música do outro." to "善战者，致人而不致于人",
        "Ataque onde não há defesa, apareça onde não te esperam." to "攻其无备，出其不意",
    ) + grupo(
        "Mêncio", "孟子",
        "A dificuldade faz crescer; o conforto demais mata aos poucos." to "生于忧患，死于安乐",
        "A hora certa vale menos que o bom terreno; o terreno vale menos que a união das pessoas." to "天时不如地利，地利不如人和",
        "Quem age certo ganha aliados; quem age errado acaba sozinho." to "得道多助，失道寡助",
        "Cuide dos seus mais velhos, e estenda esse cuidado aos dos outros." to "老吾老，以及人之老",
        "Que a riqueza não te corrompa nem a pobreza te dobre." to "富贵不能淫，贫贱不能移",
    ) + grupo(
        "Zhuangzi", "庄子",
        "A vida tem limite; o conhecimento, não." to "吾生也有涯，而知也无涯",
        "Não se explica o mar para o sapo que só conhece o poço." to "井蛙不可以语于海",
        "Melhor se perder livre no rio do que secar juntos na poça." to "相濡以沫，不如相忘于江湖",
    ) + grupo(
        "Xunzi", "荀子",
        "Sem juntar passo a passo, não se cruza mil léguas." to "不积跬步，无以至千里",
        "Com persistência, até o metal e a pedra se esculpem." to "锲而不舍，金石可镂",
        "O azul nasce do índigo, mas fica mais azul que ele." to "青，取之于蓝，而青于蓝",
        "Nunca se deve parar de aprender." to "学不可以已",
    ) + grupo(
        "I Ching", "易经",
        "O céu é incansável; assim o sábio se fortalece sem parar." to "天行健，君子以自强不息",
        "Como a terra que tudo carrega, o sábio sustenta o mundo com sua virtude." to "地势坤，君子以厚德载物",
        "No limite, mude; ao mudar, você encontra a saída." to "穷则变，变则通",
    ) + grupo(
        "Zhuge Liang", "诸葛亮",
        "Sem simplicidade não se enxerga o próprio rumo; sem calma não se chega longe." to "非淡泊无以明志，非宁静无以致远",
        "Dar tudo de si até o último suspiro." to "鞠躬尽瘁，死而后已",
    ) + grupo(
        "provérbio chinês", "谚语",
        "Enquanto se vive, se aprende." to "活到老，学到老",
        "Melhor ensinar a pescar do que dar o peixe." to "授人以鱼，不如授人以渔",
        "Perdeu o cavalo? Quem sabe não é sorte disfarçada." to "塞翁失马，焉知非福",
        "Cada instante vale ouro." to "一寸光阴一寸金",
        "Nada é impossível para quem se dedica de verdade." to "世上无难事，只怕有心人",
        "Quem não entra na toca do tigre não pega o filhote." to "不入虎穴，焉得虎子",
        "Gota a gota, a água fura a pedra." to "水滴石穿",
        "Ver uma vez vale mais que ouvir cem." to "百闻不如一见",
        "Jade sem lapidação não vira joia." to "玉不琢，不成器",
        "Pense três vezes antes de agir." to "三思而后行",
        "Não esquecer o passado é a lição para o futuro." to "前事不忘，后事之师",
        "A estrada mostra a força do cavalo; o tempo revela o coração das pessoas." to "路遥知马力，日久见人心",
        "Quem tem vontade sempre acaba conseguindo." to "有志者事竟成",
        "O fracasso é a mãe do sucesso." to "失败乃成功之母",
        "A prática leva à perfeição." to "熟能生巧",
        "O começo é sempre a parte mais difícil." to "万事开头难",
        "Com esforço e paciência, uma barra de ferro vira agulha." to "只要功夫深，铁杵磨成针",
        "Estudar é como remar contra a corrente: quem não avança, recua." to "学如逆水行舟，不进则退",
        "Quem anda com o vermelho se tinge; quem anda com a tinta se mancha." to "近朱者赤，近墨者黑",
        "Bom remédio é amargo na boca, mas é o que cura." to "良药苦口",
        "Conselho sincero incomoda os ouvidos." to "忠言逆耳",
        "A arrogância leva o exército à derrota." to "骄兵必败",
        "A soberba traz prejuízo; a humildade, ganho." to "满招损，谦受益",
        "Quem se contenta vive sempre feliz." to "知足常乐",
        "O pessegueiro não chama ninguém, mas o caminho até ele se forma sozinho." to "桃李不言，下自成蹊",
        "Diante da sinceridade total, até a pedra se abre." to "精诚所至，金石为开",
        "Na hora tranquila, já pense no perigo." to "居安思危",
        "Afiar o machado não atrasa quem vai cortar lenha." to "磨刀不误砍柴工",
        "Com muita gente juntando lenha, a fogueira sobe alto." to "众人拾柴火焰高",
        "Uma faísca é capaz de incendiar toda a campina." to "星星之火，可以燎原",
        "Cada tropeço traz um pouco mais de juízo." to "吃一堑，长一智",
        "Consertar o cercado depois de perder a ovelha ainda vale a pena." to "亡羊补牢，未为迟也",
        "Uma gota de bondade se retribui com uma fonte inteira." to "滴水之恩，当涌泉相报",
        "Enquanto houver montanha verde, não vai faltar lenha." to "留得青山在，不怕没柴烧",
        "Quem está no jogo se perde; quem assiste enxerga claro." to "当局者迷，旁观者清",
        "Ouvir todos os lados esclarece; acreditar num só cega." to "兼听则明，偏信则暗",
        "Leia dez mil livros e percorra dez mil léguas." to "读万卷书，行万里路",
        "O tempo não espera por ninguém." to "岁月不待人",
        "Evite o problema antes que ele aconteça." to "防患于未然",
        "O céu recompensa quem se esforça." to "天道酬勤",
        "Firme-se ajudando os outros a se firmarem; vença ajudando os outros a vencer." to "己欲立而立人，己欲达而达人",
    )

/** A proverb at random — re-rolled each time the home enters composition (app launch / return). */
fun proverbioAleatorio(): Proverbio = PROVERBIOS.random()

@Composable
fun ProverbWidget(modifier: Modifier = Modifier) {
    val p = remember { proverbioAleatorio() }
    // Same vertical scrim as the left column (glass colour, fading only at the extremes) — no card,
    // no border, so it reads as one language with the rest of the home and over any wallpaper.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Y.scrim)
            .padding(horizontal = Y.space.md, vertical = Y.space.md),
    ) {
        // Portuguese, in white, bold and centred — the line you read.
        Text(
            text = p.pt,
            style = Y.type.body.copy(lineHeight = 22.sp),
            color = Y.text,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // The Chinese source, centred and unbold, in the same dim as the clock's weekday.
        Text(
            text = p.zh,
            style = Y.type.bodySm.copy(letterSpacing = 1.sp),
            color = Y.textDim,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        // The author, centred: 中文 · romanização.
        Text(
            text = "${p.autorZh} · ${p.autor}",
            style = Y.type.caption,
            color = Y.textFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}
