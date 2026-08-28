package com.ykatchou.ylauncher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ykatchou.ylauncher.ui.theme.Y
import androidx.compose.material3.Text

/** A proverb in Portuguese with its authentic Chinese source text. Never fabricated. */
data class Proverbio(val pt: String, val zh: String)

/**
 * The kung-fu corner of the home: a Chinese proverb, drawn at random. Curated and offline — a home
 * widget must render instantly with no network, and dedicated proverb APIs misattribute freely.
 * Every entry is genuinely Chinese in origin (Lao Tzu, Confucius, Sun Tzu, Mencius, Zhuangzi, Xunzi,
 * the I Ching, Zhuge Liang, and classic 谚语/成语) so each carries its real 中文 — no Bruce Lee or
 * Japanese sayings here, which have no Chinese original and would force an invented one.
 */
val PROVERBIOS = listOf(
    // 老子 · Tao Te Ching
    Proverbio("Uma jornada de mil li começa sob os pés.", "千里之行，始于足下"),
    Proverbio("Conhecer os outros é sabedoria; conhecer-se é iluminação.", "知人者智，自知者明"),
    Proverbio("A bondade suprema é como a água.", "上善若水"),
    Proverbio("Vencer os outros exige força; vencer a si mesmo é poder.", "胜人者有力，自胜者强"),
    Proverbio("Quem se contenta é rico.", "知足者富"),
    Proverbio("Os grandes talentos amadurecem tarde.", "大器晚成"),
    Proverbio("As coisas difíceis do mundo começam pelas fáceis.", "天下难事，必作于易"),
    Proverbio("Governar um grande reino é como fritar um peixinho.", "治大国若烹小鲜"),
    Proverbio("Quem sabe não fala; quem fala não sabe.", "知者不言，言者不知"),
    Proverbio("A grande destreza parece desajeitada.", "大巧若拙"),
    Proverbio("Na desgraça apoia-se a sorte; na sorte oculta-se a desgraça.", "祸兮福所倚，福兮祸所伏"),
    Proverbio("O flexível e o fraco vencem o duro e o forte.", "柔弱胜刚强"),
    Proverbio("Cuida do fim como do começo, e nada fracassará.", "慎终如始，则无败事"),
    Proverbio("No não-forçar, nada fica por fazer.", "无为而无不为"),
    Proverbio("Enfrenta o difícil pelo seu lado fácil.", "图难于其易"),
    Proverbio("Quem se contenta não se humilha.", "知足不辱"),

    // 孔子 · Analectos
    Proverbio("Aprender sem pensar é vão; pensar sem aprender é perigoso.", "学而不思则罔，思而不学则殆"),
    Proverbio("Não faças aos outros o que não queres para ti.", "己所不欲，勿施于人"),
    Proverbio("Entre três que caminham, há sempre um mestre para mim.", "三人行，必有我师焉"),
    Proverbio("Revê o velho e conhecerás o novo.", "温故而知新"),
    Proverbio("Para fazer bem o trabalho, afia primeiro a ferramenta.", "工欲善其事，必先利其器"),
    Proverbio("A pressa não alcança o fim.", "欲速则不达"),
    Proverbio("Ágil no agir, prudente no falar.", "敏于事而慎于言"),
    Proverbio("Aprender e praticar a seu tempo — que alegria!", "学而时习之，不亦说乎"),
    Proverbio("Errar e não corrigir: isso sim é erro.", "过而不改，是谓过矣"),
    Proverbio("Não te aflijas por não te conhecerem; sim por não conheceres os outros.", "不患人之不己知，患不知人也"),
    Proverbio("O nobre cobra de si; o mesquinho, dos outros.", "君子求诸己，小人求诸人"),
    Proverbio("Ao ver um sábio, deseja igualá-lo.", "见贤思齐焉"),
    Proverbio("Pode-se tomar o general de um exército, mas não a vontade de um homem.", "三军可夺帅也，匹夫不可夺志也"),
    Proverbio("Ávido por aprender, sem vergonha de perguntar aos humildes.", "敏而好学，不耻下问"),
    Proverbio("Não suportar o pequeno arruína o grande plano.", "小不忍则乱大谋"),
    Proverbio("Só no frio do inverno se vê que o pinho é o último a murchar.", "岁寒，然后知松柏之后凋也"),

    // 孙子 · A Arte da Guerra
    Proverbio("Conhece-te e conhece o inimigo: cem batalhas sem perigo.", "知己知彼，百战不殆"),
    Proverbio("Vencer sem lutar é a suprema excelência.", "不战而屈人之兵，善之善者也"),
    Proverbio("Na guerra, a rapidez é preciosa.", "兵贵神速"),
    Proverbio("A guerra é a via do engano.", "兵者，诡道也"),
    Proverbio("A melhor guerra derrota os planos do inimigo.", "上兵伐谋"),
    Proverbio("Enfrenta com o regular, vence com o inesperado.", "以正合，以奇胜"),
    Proverbio("Quieto como uma donzela, veloz como a lebre à solta.", "静如处子，动如脱兔"),
    Proverbio("O bom guerreiro impõe o ritmo, não o sofre.", "善战者，致人而不致于人"),
    Proverbio("Ataca onde não se preparam, surge onde não te esperam.", "攻其无备，出其不意"),

    // 孟子 · Mêncio
    Proverbio("Nasce-se na adversidade, morre-se no conforto.", "生于忧患，死于安乐"),
    Proverbio("A ocasião cede ao terreno; o terreno, à concórdia entre as pessoas.", "天时不如地利，地利不如人和"),
    Proverbio("Quem segue o caminho tem muitos aliados.", "得道多助，失道寡助"),
    Proverbio("Honra teus velhos, e estende-o aos velhos alheios.", "老吾老，以及人之老"),
    Proverbio("A riqueza não o corrompe, a pobreza não o abala.", "富贵不能淫，贫贱不能移"),

    // 庄子 · Zhuangzi
    Proverbio("A vida tem limite; o saber, não.", "吾生也有涯，而知也无涯"),
    Proverbio("Não se fala do mar à rã do poço.", "井蛙不可以语于海"),
    Proverbio("Melhor esquecerem-se livres nos rios que molharem-se juntos na seca.", "相濡以沫，不如相忘于江湖"),

    // 荀子 · Xunzi
    Proverbio("Sem juntar meios-passos, não se chega a mil li.", "不积跬步，无以至千里"),
    Proverbio("Com persistência, esculpem-se metal e pedra.", "锲而不舍，金石可镂"),
    Proverbio("O azul nasce do índigo, mas supera-o.", "青，取之于蓝，而青于蓝"),
    Proverbio("O aprendizado nunca deve cessar.", "学不可以已"),

    // 易经 e clássicos
    Proverbio("O céu move-se firme; o nobre fortalece-se sem cessar.", "天行健，君子以自强不息"),
    Proverbio("A terra acolhe; o nobre sustenta tudo com virtude.", "地势坤，君子以厚德载物"),
    Proverbio("No limite, muda; mudando, encontra o caminho.", "穷则变，变则通"),

    // 诸葛亮 · Zhuge Liang
    Proverbio("Sem desapego não se clareia a vontade; sem calma não se alcança o longe.", "非淡泊无以明志，非宁静无以致远"),
    Proverbio("Dar-se por inteiro até o último fôlego.", "鞠躬尽瘁，死而后已"),

    // 谚语 · 成语 — provérbios clássicos
    Proverbio("Vive até velho, aprende até velho.", "活到老，学到老"),
    Proverbio("Melhor ensinar a pescar que dar o peixe.", "授人以鱼，不如授人以渔"),
    Proverbio("O velho perdeu o cavalo — quem sabe não é sorte?", "塞翁失马，焉知非福"),
    Proverbio("Um palmo de tempo vale um palmo de ouro.", "一寸光阴一寸金"),
    Proverbio("Nada no mundo é difícil para quem se dedica.", "世上无难事，只怕有心人"),
    Proverbio("Sem entrar na toca do tigre, não se pega o filhote.", "不入虎穴，焉得虎子"),
    Proverbio("Gota a gota, a água fura a pedra.", "水滴石穿"),
    Proverbio("Ver uma vez vale mais que ouvir cem.", "百闻不如一见"),
    Proverbio("Jade não lapidado não vira joia.", "玉不琢，不成器"),
    Proverbio("Pensa três vezes antes de agir.", "三思而后行"),
    Proverbio("Não esquecer o passado guia o futuro.", "前事不忘，后事之师"),
    Proverbio("A estrada revela a força do cavalo; o tempo, o coração das pessoas.", "路遥知马力，日久见人心"),
    Proverbio("Quem tem vontade acaba vencendo.", "有志者事竟成"),
    Proverbio("O fracasso é a mãe do sucesso.", "失败乃成功之母"),
    Proverbio("A prática gera a maestria.", "熟能生巧"),
    Proverbio("Todo começo é difícil.", "万事开头难"),
    Proverbio("Com afinco, a barra de ferro vira agulha.", "只要功夫深，铁杵磨成针"),
    Proverbio("Estudar é remar contra a corrente: não avançar é recuar.", "学如逆水行舟，不进则退"),
    Proverbio("Perto do rubro ficas rubro; perto da tinta, negro.", "近朱者赤，近墨者黑"),
    Proverbio("Bom remédio é amargo na boca.", "良药苦口"),
    Proverbio("O conselho sincero fere o ouvido.", "忠言逆耳"),
    Proverbio("Exército soberbo fatalmente perde.", "骄兵必败"),
    Proverbio("A soberba traz perda; a humildade, ganho.", "满招损，谦受益"),
    Proverbio("Quem se contenta vive sempre feliz.", "知足常乐"),
    Proverbio("O pessegueiro cala, mas trilhas se abrem até ele.", "桃李不言，下自成蹊"),
    Proverbio("Diante da sinceridade total, metal e pedra se abrem.", "精诚所至，金石为开"),
    Proverbio("Na paz, pensa no perigo.", "居安思危"),
    Proverbio("Afiar a lâmina não atrasa o corte da lenha.", "磨刀不误砍柴工"),
    Proverbio("Com muitos a juntar lenha, a chama sobe alto.", "众人拾柴火焰高"),
    Proverbio("Uma faísca pode incendiar toda a pradaria.", "星星之火，可以燎原"),
    Proverbio("Cada tropeço traz mais juízo.", "吃一堑，长一智"),
    Proverbio("Consertar o curral após perder a ovelha ainda não é tarde.", "亡羊补牢，未为迟也"),
    Proverbio("Uma gota de bondade retribui-se com uma fonte.", "滴水之恩，当涌泉相报"),
    Proverbio("Havendo a montanha verde, não faltará lenha.", "留得青山在，不怕没柴烧"),
    Proverbio("Quem joga se perde; quem assiste vê claro.", "当局者迷，旁观者清"),
    Proverbio("Ouvir todos os lados esclarece; crer num só cega.", "兼听则明，偏信则暗"),
    Proverbio("Lê dez mil livros, percorre dez mil li.", "读万卷书，行万里路"),
    Proverbio("O tempo não espera por ninguém.", "岁月不待人"),
    Proverbio("Previne o mal antes que ele surja.", "防患于未然"),
    Proverbio("O céu recompensa o esforço.", "天道酬勤"),
    Proverbio("Querendo firmar-te, firma os outros; querendo chegar, faze-os chegar.", "己欲立而立人，己欲达而达人"),
)

/** A proverb at random — re-rolled each time the home enters composition (app launch / return). */
fun proverbioAleatorio(): Proverbio = PROVERBIOS.random()

@Composable
fun ProverbWidget(modifier: Modifier = Modifier) {
    val p = remember { proverbioAleatorio() }
    Column(modifier = modifier.padding(start = 4.dp, top = 6.dp, end = 10.dp)) {
        // Portuguese, in white — the line you read.
        Text(
            text = p.pt,
            style = Y.type.bodySm.copy(
                shadow = Y.textShadow,
                lineHeight = 19.sp,
            ),
            color = Y.text,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
        )
        // The Chinese source below, in the faintest shade.
        Text(
            text = p.zh,
            style = Y.type.caption.copy(
                shadow = Y.textShadow,
                letterSpacing = 1.sp,
            ),
            color = Y.textFaint,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
