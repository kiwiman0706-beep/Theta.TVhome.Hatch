package app.thetahatch.tvhome

import app.thetahatch.tvhome.data.Ics
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
}
