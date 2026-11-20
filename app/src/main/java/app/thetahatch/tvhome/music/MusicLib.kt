package app.thetahatch.tvhome.music

import app.thetahatch.tvhome.nas.NasEntry

/**
 * NAS の音楽のフォルダの読み方（Theta.Hatch.Series の spec/THETA_LIBRARY_SPEC.md §4、Theta Music Hatch と同じ）。
 * - 音楽の共有フォルダの直下に「▶」で始まるフォルダ（▶邦楽 など）があれば、それが棚。無ければ直下ぜんぶ
 * - 「_」で始まるフォルダと「▶ミュージックビデオ」は曲として読まない
 * - 曲名はファイル名から（「01 Title.flac」→「Title」）。タグは次の版で
 */
object MusicLib {
    val AUDIO = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "aiff", "aif", "alac", "wma", "ape", "dsf")
    private val COVERS = listOf("cover.jpg", "folder.jpg", "cover.png", "folder.png", "front.jpg", "album.jpg")

    fun isAudio(e: NasEntry) = !e.dir && e.name.substringAfterLast('.', "").lowercase() in AUDIO
    fun hidden(e: NasEntry) = e.name.startsWith("_") || e.name.startsWith(".") || e.name.startsWith("@") || e.name == "#recycle" || e.name == "▶ミュージックビデオ"

    /** 棚：▶ のフォルダがあればそれだけ、無ければ直下のフォルダ。名前の ▶ は外して見せる。 */
    fun shelves(top: List<NasEntry>): List<NasEntry> {
        val dirs = top.filter { it.dir && !hidden(it) }
        val marked = dirs.filter { it.name.startsWith("▶") }
        return (marked.ifEmpty { dirs }).sortedWith(compareBy(natural) { it.name })
    }

    fun shelfName(name: String) = name.removePrefix("▶").trim()

    /** 「01 Title」「1-03. Title」「01_Title」→「Title」。 */
    fun title(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        // 曲番号のあとに区切り（空白・- . _ ）が無ければ外さない（「2001」は曲名のまま）
        return base.replace(Regex("^\\s*(\\d{1,2}[-.])?\\d{1,3}(\\s*[-._)）]\\s*|\\s+)(?=\\S)"), "").ifBlank { base }
    }

    fun cover(entries: List<NasEntry>): NasEntry? = entries.filter { !it.dir }.let { files ->
        COVERS.firstNotNullOfOrNull { c -> files.firstOrNull { it.name.equals(c, ignoreCase = true) } }
    }

    /** フォルダの中身：フォルダ（名前順）→ 曲（ファイル名の数字順）。 */
    fun sort(entries: List<NasEntry>): List<NasEntry> =
        entries.filter { it.dir && !hidden(it) }.sortedWith(compareBy(natural) { it.name }) +
            entries.filter { isAudio(it) }.sortedWith(compareBy(natural) { it.name })

    /** 数字を数として比べる（「2」が「10」より前）。 */
    val natural = Comparator<String> { a, b ->
        val ra = Regex("(\\d+)|(\\D+)").findAll(a.lowercase()).map { it.value }.toList()
        val rb = Regex("(\\d+)|(\\D+)").findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ra.size, rb.size)) {
            val x = ra[i]; val y = rb[i]
            val c = if (x[0].isDigit() && y[0].isDigit()) (x.toBigInteger().compareTo(y.toBigInteger())) else x.compareTo(y)
            if (c != 0) return@Comparator c
        }
        ra.size - rb.size
    }
}
