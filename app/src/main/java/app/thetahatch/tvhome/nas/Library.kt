package app.thetahatch.tvhome.nas

import app.thetahatch.tvhome.music.MusicLib

/**
 * NAS のライブラリの種類（Theta.Hatch.Series の spec/THETA_LIBRARY_SPEC.md）。
 * どれも File Station でフォルダをたどる。棚は共有フォルダの直下（目印のフォルダがあればそれだけ）。
 */
enum class Kind(
    val key: String,
    val label: String,
    val icon: String,
    /** 共有フォルダの設定のキーと、既定 */
    val rootKey: String,
    val defaultRoot: String,
    /** 棚の目印（これで始まるフォルダがあれば、それだけを棚に。Movie・Book の「⑧」、Music の「▶」） */
    val marks: List<String>,
    val exts: Set<String>,
) {
    MUSIC("music", "おんがく", "🎵", "nas.music", "/music", listOf("▶"), MusicLib.AUDIO),
    VIDEO("video", "えいが", "🎬", "nas.video", "/video", listOf("⑧", "▶"),
        setOf("mkv", "mp4", "m4v", "ts", "m2ts", "mts", "avi", "mov", "wmv", "webm", "mpg", "mpeg", "flv", "3gp")),
    BOOK("book", "ほん", "📚", "nas.book", "/book", listOf("⑧", "▶"), setOf("pdf", "cbz", "zip", "epub")),
    PHOTO("photo", "しゃしん", "🖼", "nas.photo", "/photo", listOf("▶"),
        setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "mp4", "mov", "m4v", "3gp")),
    ;

    fun isItem(e: NasEntry) = !e.dir && e.name.substringAfterLast('.', "").lowercase() in exts

    fun hidden(e: NasEntry) = e.name.startsWith("_") || e.name.startsWith(".") || e.name.startsWith("@") || e.name == "#recycle" ||
        (this != MUSIC && e.name == "▶ミュージックビデオ") || (this == MUSIC && e.name == "▶ミュージックビデオ")

    fun shelves(top: List<NasEntry>): List<NasEntry> {
        val dirs = top.filter { it.dir && !hidden(it) }
        val marked = dirs.filter { d -> marks.any { d.name.startsWith(it) } }
        return marked.ifEmpty { dirs }.sortedWith(compareBy(MusicLib.natural) { it.name })
    }

    fun shelfName(name: String) = marks.fold(name) { n, m -> n.removePrefix(m) }.trim()

    /** フォルダ（名前順。写真は新しい年・日付が先）→ 中身（数字順）。 */
    fun sort(entries: List<NasEntry>): List<NasEntry> {
        val dirs = entries.filter { it.dir && !hidden(it) }.sortedWith(compareBy(MusicLib.natural) { it.name })
        return (if (this == PHOTO) dirs.reversed() else dirs) + entries.filter { isItem(it) }.sortedWith(compareBy(MusicLib.natural) { it.name })
    }

    /** 見せる名前（拡張子を外す。音楽は曲番号も）。 */
    fun title(name: String) = if (this == MUSIC) MusicLib.title(name) else name.substringBeforeLast('.')

    companion object {
        val VIDEO_EXT = VIDEO.exts
        fun byKey(k: String) = entries.firstOrNull { it.key == k } ?: MUSIC
    }
}
