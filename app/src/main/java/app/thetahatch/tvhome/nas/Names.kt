package app.thetahatch.tvhome.nas

import java.text.Normalizer

/** 名前の読み方（探す・作品のまとめ）。Theta Movie Hatch の docs/naming-convention.md・THETA_LIBRARY_SPEC に合わせる。 */
object Names {
    /** 探すための形：全角・半角をそろえ、小文字、カタカナはひらがなに、空白・記号を外す。 */
    fun norm(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        val sb = StringBuilder(n.length)
        for (c in n) {
            when {
                c in 'ァ'..'ヶ' -> sb.append(c - 0x60)
                c.isLetterOrDigit() -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 「君の名は。 (2016)」→ 題名と年。 */
    fun work(folder: String): Pair<String, String?> {
        val m = Regex("^(.*?)\\s*\\((\\d{4})\\)\\s*$").find(folder) ?: return folder to null
        return m.groupValues[1] to m.groupValues[2]
    }

    /** 話のラベル：「〇〇 S01E03「サブタイトル」 [2019-04-21 TBS].ts」→「3 話 サブタイトル」。読めなければ拡張子を外しただけ。 */
    fun episode(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        val noTail = base.replace(Regex("\\s*\\[[^\\]]*\\]\\s*$"), "")
        val ep = Regex("[Ss](\\d{1,2})[Ee](\\d{1,3})").find(noTail)
        val sub = Regex("「([^」]*)」").find(noTail)?.groupValues?.get(1)
        val date = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(noTail)
        return when {
            ep != null -> "${ep.groupValues[2].toInt()} 話" + (sub?.let { " $it" } ?: "")
            date != null && sub != null -> "${date.groupValues[2].toInt()}/${date.groupValues[3].toInt()} $sub"
            else -> work(noTail).first
        }
    }

    /** 「Season 01」→「シーズン 1」、「Season 2019」→「2019 年」、「Specials」→「とくべつ」。 */
    fun season(folder: String): String? {
        if (folder.equals("Specials", true)) return "とくべつ"
        val m = Regex("^Season\\s*(\\d+)", RegexOption.IGNORE_CASE).find(folder) ?: return null
        val n = m.groupValues[1].toInt()
        return if (n >= 1900) "$n 年" else "シーズン $n"
    }
}
