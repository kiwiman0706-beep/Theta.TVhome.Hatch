package app.thetahatch.tvhome

import app.thetahatch.tvhome.data.Event
import app.thetahatch.tvhome.data.Ics
import app.thetahatch.tvhome.nas.Kind
import app.thetahatch.tvhome.data.Place
import app.thetahatch.tvhome.hub.ApkFacts
import app.thetahatch.tvhome.hub.Catalog
import app.thetahatch.tvhome.hub.InstallKind
import app.thetahatch.tvhome.hub.UpdateHub
import app.thetahatch.tvhome.hub.Verdict
import app.thetahatch.tvhome.hub.Verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LogicTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val from = ZonedDateTime.of(2026, 11, 15, 0, 0, 0, 0, tokyo)

    private fun cal(vararg ev: String) = "BEGIN:VCALENDAR\r\n" + ev.joinToString("") { "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n"

    @Test fun icsSimpleAndAllDay() {
        val text = cal(
            "DTSTART:20261115T010000Z\r\nDTEND:20261115T020000Z\r\nSUMMARY:歯医者\\, 駅前",
            "DTSTART;VALUE=DATE:20261116\r\nDTEND;VALUE=DATE:20261117\r\nSUMMARY:遠足",
            "DTSTART:20261201T010000Z\r\nSUMMARY:先の予定",
        )
        val e = Ics.events(text, from, 3)
        assertEquals(listOf("歯医者, 駅前", "遠足"), e.map { it.title })
        assertEquals(10, e[0].start.hour) // UTC 1 時 → 東京 10 時
        assertTrue(e[1].allDay)
    }

    @Test fun icsFoldedAndTzid() {
        val text = cal("DTSTART;TZID=Asia/Tokyo:20261115T183000\r\nSUMMARY:長い\r\n 題名")
        val e = Ics.events(text, from, 1)
        assertEquals("長い題名", e.single().title)
        assertEquals(18, e.single().start.hour)
    }

    @Test fun icsWeeklyRule() {
        // 毎週 月・水（2026-11-02 から）。11/15（日）から 3 日 → 11/16（月）だけ
        val text = cal("DTSTART;TZID=Asia/Tokyo:20261102T170000\r\nDTEND;TZID=Asia/Tokyo:20261102T180000\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE\r\nSUMMARY:習い事")
        val e = Ics.events(text, from, 3)
        assertEquals(listOf(16), e.map { it.start.dayOfMonth })
    }

    @Test fun icsDailyCountAndExdate() {
        val text = cal("DTSTART;TZID=Asia/Tokyo:20261114T070000\r\nRRULE:FREQ=DAILY;COUNT=3\r\nEXDATE;TZID=Asia/Tokyo:20261115T070000\r\nSUMMARY:朝")
        val e = Ics.events(text, from, 5)
        assertEquals(listOf(16), e.map { it.start.dayOfMonth })
    }

    @Test fun icsCancelledIsSkipped() {
        val text = cal("DTSTART:20261115T010000Z\r\nSTATUS:CANCELLED\r\nSUMMARY:中止")
        assertTrue(Ics.events(text, from, 3).isEmpty())
    }

    @Test fun place() {
        val p = Place.decode("35.4437,139.6380,横浜, 神奈川")!!
        assertEquals("横浜, 神奈川", p.name)
        assertEquals(p, Place.decode(p.encode()))
        assertEquals(null, Place.decode("abc"))
    }

    @Test fun versions() {
        assertTrue(UpdateHub.isNewer("0.10.0", "0.9.9"))
        assertFalse(UpdateHub.isNewer("0.1.0", "0.1.0"))
        assertTrue(UpdateHub.isNewer("1.0", "0.9.9"))
    }

    @Test fun hubOwner() {
        val me = "app.thetahatch.tvhome"
        assertTrue(UpdateHub.ownerFor(me, me, setOf("app.thetahatch.home")))
        assertFalse(UpdateHub.ownerFor(me, "app.thetahatch.home", emptySet()))
        assertTrue(UpdateHub.ownerFor(me, "com.google.android.tvlauncher", emptySet()))
        assertFalse(UpdateHub.ownerFor(me, "com.google.android.tvlauncher", setOf("app.thetahatch.kidshome")))
        assertTrue(UpdateHub.ownerFor(me, null, emptySet()))
    }

    @Test fun selfIsAlwaysInCatalog() {
        Catalog.setForTest(emptyList())
        assertEquals("tvhome", Catalog.byPackage("app.thetahatch.tvhome")?.id)
    }

    @Test fun verifyOnlySeriesKey() {
        val self = Catalog.SELF
        val good = ApkFacts("app.thetahatch.tvhome", "0.2.0", 2, setOf(Verify.SERIES_KEY))
        val installed = ApkFacts("app.thetahatch.tvhome", "0.1.0", 1, setOf(Verify.SERIES_KEY))
        assertEquals(Verdict.Ok(InstallKind.UPDATE), Verify.judge(self, good) { installed })
        assertTrue(Verify.judge(self, good.copy(signers = setOf("x"))) { installed } is Verdict.Bad)
        assertTrue(Verify.judge(self, good.copy(versionCode = 1)) { installed } is Verdict.Bad)
        assertTrue(Verify.judge(self, good.copy(packageName = "evil")) { installed } is Verdict.Bad)
    }

    @Test fun monthGridAndMultiDay() {
        val g = app.thetahatch.tvhome.ui.monthGrid(java.time.LocalDate.of(2026, 11, 15))
        assertEquals(java.time.LocalDate.of(2026, 11, 1), g.first()) // 2026-11-01 は日曜
        assertEquals(42, g.size)
        val e = Event("連休", ZonedDateTime.of(2026, 11, 21, 0, 0, 0, 0, tokyo), ZonedDateTime.of(2026, 11, 24, 0, 0, 0, 0, tokyo), true)
        val on = (20..24).filter { app.thetahatch.tvhome.ui.eventsOn(java.time.LocalDate.of(2026, 11, it), listOf(e)).isNotEmpty() }
        assertEquals(listOf(21, 22, 23), on)
    }

    @Test fun backSevenTimes() {
        val b = app.thetahatch.tvhome.rescue.BackCounter()
        val t = 1_000_000L
        assertFalse((0 until 6).any { b.hit(t + it * 300) })
        assertTrue(b.hit(t + 6 * 300))
        // ゆっくり押した分は数えない
        val c = app.thetahatch.tvhome.rescue.BackCounter()
        assertFalse((0 until 7).any { c.hit(t + it * 1000L) })
    }

    @Test fun googleCalendarEvent() {
        val timed = org.json.JSONObject("""{"summary":"歯医者","location":"駅前","start":{"dateTime":"2026-11-16T07:30:00Z"},"end":{"dateTime":"2026-11-16T08:00:00Z"}}""")
        val e = app.thetahatch.tvhome.data.GCal.parse(timed, tokyo, "#7986cb")!!
        assertEquals(16, e.start.hour)
        assertFalse(e.allDay)
        assertEquals("#7986cb", e.color)
        val day = org.json.JSONObject("""{"summary":"遠足","start":{"date":"2026-11-18"},"end":{"date":"2026-11-19"}}""")
        assertTrue(app.thetahatch.tvhome.data.GCal.parse(day, tokyo)!!.allDay)
        assertEquals(null, app.thetahatch.tvhome.data.GCal.parse(org.json.JSONObject("""{"status":"cancelled","start":{"date":"2026-11-18"}}"""), tokyo))
    }

    @Test fun musicFolders() {
        val ml = app.thetahatch.tvhome.music.MusicLib
        fun d(n: String) = app.thetahatch.tvhome.nas.NasEntry("/music/$n", n, true, 0, 0)
        fun f(n: String) = app.thetahatch.tvhome.nas.NasEntry("/music/x/$n", n, false, 1, 0)
        assertEquals(listOf("▶洋楽", "▶邦楽"), ml.shelves(listOf(d("▶洋楽"), d("Artist"), d("▶邦楽"), d("_整理の記録"), d("▶ミュージックビデオ"))).map { it.name })
        assertEquals(listOf("A", "B"), ml.shelves(listOf(d("B"), d("A"), d("@eaDir"))).map { it.name })
        assertEquals("Hello", ml.title("01 Hello.flac"))
        assertEquals("Hello World", ml.title("1-03. Hello World.mp3"))
        assertEquals("2001", ml.title("2001.mp3"))
        assertEquals(listOf("2 b.mp3", "10 a.mp3"), ml.sort(listOf(f("10 a.mp3"), f("2 b.mp3"), f("cover.jpg"))).map { it.name })
        assertEquals("cover.jpg", ml.cover(listOf(f("01 a.mp3"), f("Cover.JPG").copy(name = "cover.jpg")))?.name)
    }

    @Test fun nasUrl() {
        val n = app.thetahatch.tvhome.nas.Dsm
        assertEquals("http://192.168.1.5:5000", n.normalize("192.168.1.5"))
        assertEquals("http://nas.ts.net:5001/x", n.normalize("nas.ts.net:5001/x/"))
        assertEquals("https://nas:5001", n.normalize("https://nas:5001"))
    }

    @Test fun nasKinds() {

        fun d(n: String) = app.thetahatch.tvhome.nas.NasEntry("/v/$n", n, true, 0, 0)
        fun f(n: String) = app.thetahatch.tvhome.nas.NasEntry("/v/$n", n, false, 1, 0)
        assertEquals(listOf("⑧アニメ", "⑧映画"), Kind.VIDEO.shelves(listOf(d("⑧映画"), d("その他"), d("⑧アニメ"), d("#recycle"))).map { it.name })
        assertEquals("映画", Kind.VIDEO.shelfName("⑧映画"))
        assertTrue(Kind.VIDEO.isItem(f("君の名は。 (2016).mkv")))
        assertFalse(Kind.VIDEO.isItem(f("poster.jpg")))
        assertTrue(Kind.BOOK.isItem(f("第01巻.pdf")))
        // 写真は新しい年が先
        assertEquals(listOf("2025", "2024"), Kind.PHOTO.sort(listOf(d("2024"), d("2025"), d("_未整理"))).map { it.name })
    }

    @Test fun namesAndIndex() {
        val N = app.thetahatch.tvhome.nas.Names
        assertEquals("きみのなは2016", N.norm("キミノナハ (２０１６)"))
        assertEquals("君の名は。" to "2016", N.work("君の名は。 (2016)"))
        assertEquals("3 話 サブタイトル", N.episode("〇〇 S01E03「サブタイトル」 [2019-04-21 TBS].ts"))
        assertEquals("4/7 今回の内容", N.episode("〇〇の番組 2019-04-07「今回の内容」 [テレ朝].ts"))
        assertEquals("シーズン 1", N.season("Season 01"))
        assertEquals("2019 年", N.season("Season 2019"))
        val I = app.thetahatch.tvhome.nas.Index
        fun it(p: String, dir: Boolean, t: Long) = app.thetahatch.tvhome.nas.Index.Item(p, p.substringAfterLast('/'), dir, t)
        val items = listOf(
            it("/8Tube/▶アニメ/トトロ (1988)", true, 1), it("/8Tube/▶アニメ/トトロ (1988)/トトロ (1988).mkv", false, 5),
            it("/8Tube/▶ドラマ/〇〇 (2019)/Season 01/〇〇 S01E01.ts", false, 9), it("/8Tube/▶ドラマ/〇〇 (2019)/Season 01/〇〇 S01E02.ts", false, 8),
        )
        assertEquals(listOf("/8Tube/▶ドラマ/〇〇 (2019)", "/8Tube/▶アニメ/トトロ (1988)"), I.recentOf(items, Kind.VIDEO).map { it.path })
        assertEquals("/8Tube/▶アニメ/トトロ (1988)", I.searchOf(items, "ととろ").first().path)
    }

    @Test fun geminiAnswer() {
        val o = org.json.JSONObject("""{"candidates":[{"content":{"parts":[{"text":"**公園**を 3 つ\n* 元町公園"}]},
          "groundingMetadata":{"groundingChunks":[{"maps":{"uri":"https://maps.google.com/maps?cid=1","title":"Motomachi Park - Google Maps"}},{"web":{"uri":"https://example.com","title":"example.com"}}]}}]}""")
        val a = app.thetahatch.tvhome.data.Gemini.parse(o)
        assertEquals("公園を 3 つ\n・元町公園", a.text)
        assertEquals("Motomachi Park", a.sources[0].title)
        assertTrue(a.sources[0].place)
        assertFalse(a.sources[1].place)
    }

    @Test fun phoneFormParse() {
        val m = app.thetahatch.tvhome.link.PhoneInput.parse("nas.url=http%3A%2F%2F192.168.1.5%3A5000&gemini.key=AIza+x%2By&empty=")
        assertEquals("http://192.168.1.5:5000", m["nas.url"])
        assertEquals("AIza x+y", m["gemini.key"])
        assertEquals("", m["empty"])
    }
}
