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
 * sayings are credited to the tradition (谚语), never to an invented name.
 */
val PROVERBIOS: List<Proverbio> =
    grupo(
        "Lao Tzu", "老子",
        "Uma jornada de mil li começa sob os pés." to "千里之行，始于足下",
        "Conhecer os outros é sabedoria; conhecer-se é iluminação." to "知人者智，自知者明",
        "A bondade suprema é como a água." to "上善若水",
        "Vencer os outros exige força; vencer a si mesmo é poder." to "胜人者有力，自胜者强",
        "Quem se contenta é rico." to "知足者富",
        "Os grandes talentos amadurecem tarde." to "大器晚成",
        "As coisas difíceis do mundo começam pelas fáceis." to "天下难事，必作于易",
        "Governar um grande reino é como fritar um peixinho." to "治大国若烹小鲜",
        "Quem sabe não fala; quem fala não sabe." to "知者不言，言者不知",
        "A grande destreza parece desajeitada." to "大巧若拙",
        "Na desgraça apoia-se a sorte; na sorte oculta-se a desgraça." to "祸兮福所倚，福兮祸所伏",
        "O flexível e o fraco vencem o duro e o forte." to "柔弱胜刚强",
        "Cuida do fim como do começo, e nada fracassará." to "慎终如始，则无败事",
        "No não-forçar, nada fica por fazer." to "无为而无不为",
        "Enfrenta o difícil pelo seu lado fácil." to "图难于其易",
        "Quem se contenta não se humilha." to "知足不辱",
    ) + grupo(
        "Confúcio", "孔子",
        "Aprender sem pensar é vão; pensar sem aprender é perigoso." to "学而不思则罔，思而不学则殆",
        "Não faças aos outros o que não queres para ti." to "己所不欲，勿施于人",
        "Entre três que caminham, há sempre um mestre para mim." to "三人行，必有我师焉",
        "Revê o velho e conhecerás o novo." to "温故而知新",
        "Para fazer bem o trabalho, afia primeiro a ferramenta." to "工欲善其事，必先利其器",
        "A pressa não alcança o fim." to "欲速则不达",
        "Ágil no agir, prudente no falar." to "敏于事而慎于言",
        "Aprender e praticar a seu tempo — que alegria!" to "学而时习之，不亦说乎",
        "Errar e não corrigir: isso sim é erro." to "过而不改，是谓过矣",
        "Não te aflijas por não te conhecerem; sim por não conheceres os outros." to "不患人之不己知，患不知人也",
        "O nobre cobra de si; o mesquinho, dos outros." to "君子求诸己，小人求诸人",
        "Ao ver um sábio, deseja igualá-lo." to "见贤思齐焉",
        "Pode-se tomar o general de um exército, mas não a vontade de um homem." to "三军可夺帅也，匹夫不可夺志也",
        "Ávido por aprender, sem vergonha de perguntar aos humildes." to "敏而好学，不耻下问",
        "Não suportar o pequeno arruína o grande plano." to "小不忍则乱大谋",
        "Só no frio do inverno se vê que o pinho é o último a murchar." to "岁寒，然后知松柏之后凋也",
    ) + grupo(
        "Sun Tzu", "孙子",
        "Conhece-te e conhece o inimigo: cem batalhas sem perigo." to "知己知彼，百战不殆",
        "Vencer sem lutar é a suprema excelência." to "不战而屈人之兵，善之善者也",
        "Na guerra, a rapidez é preciosa." to "兵贵神速",
        "A guerra é a via do engano." to "兵者，诡道也",
        "A melhor guerra derrota os planos do inimigo." to "上兵伐谋",
        "Enfrenta com o regular, vence com o inesperado." to "以正合，以奇胜",
        "Quieto como uma donzela, veloz como a lebre à solta." to "静如处子，动如脱兔",
        "O bom guerreiro impõe o ritmo, não o sofre." to "善战者，致人而不致于人",
        "Ataca onde não se preparam, surge onde não te esperam." to "攻其无备，出其不意",
    ) + grupo(
        "Mêncio", "孟子",
        "Nasce-se na adversidade, morre-se no conforto." to "生于忧患，死于安乐",
        "A ocasião cede ao terreno; o terreno, à concórdia entre as pessoas." to "天时不如地利，地利不如人和",
        "Quem segue o caminho tem muitos aliados." to "得道多助，失道寡助",
        "Honra teus velhos, e estende-o aos velhos alheios." to "老吾老，以及人之老",
        "A riqueza não o corrompe, a pobreza não o abala." to "富贵不能淫，贫贱不能移",
    ) + grupo(
        "Zhuangzi", "庄子",
        "A vida tem limite; o saber, não." to "吾生也有涯，而知也无涯",
        "Não se fala do mar à rã do poço." to "井蛙不可以语于海",
        "Melhor esquecerem-se livres nos rios que molharem-se juntos na seca." to "相濡以沫，不如相忘于江湖",
    ) + grupo(
        "Xunzi", "荀子",
        "Sem juntar meios-passos, não se chega a mil li." to "不积跬步，无以至千里",
        "Com persistência, esculpem-se metal e pedra." to "锲而不舍，金石可镂",
        "O azul nasce do índigo, mas supera-o." to "青，取之于蓝，而青于蓝",
        "O aprendizado nunca deve cessar." to "学不可以已",
    ) + grupo(
        "I Ching", "易经",
        "O céu move-se firme; o nobre fortalece-se sem cessar." to "天行健，君子以自强不息",
        "A terra acolhe; o nobre sustenta tudo com virtude." to "地势坤，君子以厚德载物",
        "No limite, muda; mudando, encontra o caminho." to "穷则变，变则通",
    ) + grupo(
        "Zhuge Liang", "诸葛亮",
        "Sem desapego não se clareia a vontade; sem calma não se alcança o longe." to "非淡泊无以明志，非宁静无以致远",
        "Dar-se por inteiro até o último fôlego." to "鞠躬尽瘁，死而后已",
    ) + grupo(
        "provérbio chinês", "谚语",
        "Vive até velho, aprende até velho." to "活到老，学到老",
        "Melhor ensinar a pescar que dar o peixe." to "授人以鱼，不如授人以渔",
        "O velho perdeu o cavalo — quem sabe não é sorte?" to "塞翁失马，焉知非福",
        "Um palmo de tempo vale um palmo de ouro." to "一寸光阴一寸金",
        "Nada no mundo é difícil para quem se dedica." to "世上无难事，只怕有心人",
        "Sem entrar na toca do tigre, não se pega o filhote." to "不入虎穴，焉得虎子",
        "Gota a gota, a água fura a pedra." to "水滴石穿",
        "Ver uma vez vale mais que ouvir cem." to "百闻不如一见",
        "Jade não lapidado não vira joia." to "玉不琢，不成器",
        "Pensa três vezes antes de agir." to "三思而后行",
        "Não esquecer o passado guia o futuro." to "前事不忘，后事之师",
        "A estrada revela a força do cavalo; o tempo, o coração das pessoas." to "路遥知马力，日久见人心",
        "Quem tem vontade acaba vencendo." to "有志者事竟成",
        "O fracasso é a mãe do sucesso." to "失败乃成功之母",
        "A prática gera a maestria." to "熟能生巧",
        "Todo começo é difícil." to "万事开头难",
        "Com afinco, a barra de ferro vira agulha." to "只要功夫深，铁杵磨成针",
        "Estudar é remar contra a corrente: não avançar é recuar." to "学如逆水行舟，不进则退",
        "Perto do rubro ficas rubro; perto da tinta, negro." to "近朱者赤，近墨者黑",
        "Bom remédio é amargo na boca." to "良药苦口",
        "O conselho sincero fere o ouvido." to "忠言逆耳",
        "Exército soberbo fatalmente perde." to "骄兵必败",
        "A soberba traz perda; a humildade, ganho." to "满招损，谦受益",
        "Quem se contenta vive sempre feliz." to "知足常乐",
        "O pessegueiro cala, mas trilhas se abrem até ele." to "桃李不言，下自成蹊",
        "Diante da sinceridade total, metal e pedra se abrem." to "精诚所至，金石为开",
        "Na paz, pensa no perigo." to "居安思危",
        "Afiar a lâmina não atrasa o corte da lenha." to "磨刀不误砍柴工",
        "Com muitos a juntar lenha, a chama sobe alto." to "众人拾柴火焰高",
        "Uma faísca pode incendiar toda a pradaria." to "星星之火，可以燎原",
        "Cada tropeço traz mais juízo." to "吃一堑，长一智",
        "Consertar o curral após perder a ovelha ainda não é tarde." to "亡羊补牢，未为迟也",
        "Uma gota de bondade retribui-se com uma fonte." to "滴水之恩，当涌泉相报",
        "Havendo a montanha verde, não faltará lenha." to "留得青山在，不怕没柴烧",
        "Quem joga se perde; quem assiste vê claro." to "当局者迷，旁观者清",
        "Ouvir todos os lados esclarece; crer num só cega." to "兼听则明，偏信则暗",
        "Lê dez mil livros, percorre dez mil li." to "读万卷书，行万里路",
        "O tempo não espera por ninguém." to "岁月不待人",
        "Previne o mal antes que ele surja." to "防患于未然",
        "O céu recompensa o esforço." to "天道酬勤",
        "Querendo firmar-te, firma os outros; querendo chegar, faze-os chegar." to "己欲立而立人，己欲达而达人",
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
            style = Y.type.body.copy(shadow = Y.textShadow, lineHeight = 22.sp),
            color = Y.text,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // The Chinese source, centred and unbold, in the same dim as the clock's weekday.
        Text(
            text = p.zh,
            style = Y.type.bodySm.copy(shadow = Y.textShadow, letterSpacing = 1.sp),
            color = Y.textDim,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        // The author, centred: 中文 · romanização.
        Text(
            text = "${p.autorZh} · ${p.autor}",
            style = Y.type.caption.copy(shadow = Y.textShadow),
            color = Y.textFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}
