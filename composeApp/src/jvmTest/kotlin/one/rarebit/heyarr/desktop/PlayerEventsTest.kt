package one.rarebit.heyarr.desktop

import one.rarebit.heyarr.desktop.playback.PlayerEvents
import one.rarebit.heyarr.desktop.playback.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** mpv's IPC event lines → the transport's state, without an mpv. */
class PlayerEventsTest {

    @Test fun propertyChangesLandInState() {
        var s = PlayerState()
        s = PlayerEvents.apply(s, """{"event":"file-loaded"}""")
        assertTrue(s.loaded)
        s = PlayerEvents.apply(s, """{"event":"property-change","id":2,"name":"duration","data":3312.5}""")
        s = PlayerEvents.apply(s, """{"event":"property-change","id":1,"name":"time-pos","data":1425.25}""")
        s = PlayerEvents.apply(s, """{"event":"property-change","id":3,"name":"pause","data":false}""")
        s = PlayerEvents.apply(s, """{"event":"property-change","id":4,"name":"volume","data":80}""")
        assertEquals(3312.5, s.duration); assertEquals(1425.25, s.position); assertFalse(s.paused); assertEquals(80.0, s.volume)
        assertEquals(0.43, s.fraction.toDouble(), 0.01)
    }

    @Test fun trackListSplitsSubtitlesAndAudio() {
        val line = """{"event":"property-change","id":8,"name":"track-list","data":[{"id":1,"type":"video","selected":true},{"id":1,"type":"audio","lang":"eng","selected":true},{"id":1,"type":"sub","lang":"en","title":"English","selected":false,"external":true},{"id":2,"type":"sub","lang":"es","selected":false}]}"""
        val s = PlayerEvents.apply(PlayerState(), line)
        assertEquals(listOf("EN · English", "ES"), s.subtitles.map { it.label })
        assertTrue(s.subtitles[0].external)
        assertEquals(1, s.audio.size)
        val off = PlayerEvents.apply(s, """{"event":"property-change","id":9,"name":"sid","data":false}""")
        assertNull(off.subtitleId)
        assertEquals(2, PlayerEvents.apply(s, """{"event":"property-change","id":9,"name":"sid","data":2}""").subtitleId)
    }

    @Test fun endOfFileAndErrorsAreDistinct() {
        val eof = PlayerEvents.apply(PlayerState(loaded = true), """{"event":"end-file","reason":"eof"}""")
        assertTrue(eof.eof); assertNull(eof.error)
        val err = PlayerEvents.apply(PlayerState(loaded = true), """{"event":"end-file","reason":"error","file_error":"loading failed"}""")
        assertEquals("loading failed", err.error); assertFalse(err.loaded)
        assertEquals(PlayerState(), PlayerEvents.apply(PlayerState(), "not json"))
    }
}

class LanguageNameTest {
    @Test fun isoCodesBecomeNames() {
        assertEquals("English", one.rarebit.heyarr.desktop.ui.screens.languageName("eng"))
        assertEquals("English", one.rarebit.heyarr.desktop.ui.screens.languageName("en"))
        assertEquals("Spanish", one.rarebit.heyarr.desktop.ui.screens.languageName("spa"))
        assertNull(one.rarebit.heyarr.desktop.ui.screens.languageName("und"))
        assertNull(one.rarebit.heyarr.desktop.ui.screens.languageName(null))
    }

    @Test fun wikipediaYearGuard() {
        assertTrue(one.rarebit.heyarr.desktop.state.ExternalParsers.yearAgrees("Yellowstone is a 2018 drama series.", 2018))
        assertFalse(one.rarebit.heyarr.desktop.state.ExternalParsers.yearAgrees("Yellowstone is a 1936 American Western film.", 2018))
        assertTrue(one.rarebit.heyarr.desktop.state.ExternalParsers.yearAgrees("No year here.", 2018))
        assertTrue(one.rarebit.heyarr.desktop.state.ExternalParsers.yearAgrees("Released in 1936.", null))
    }
}

class PlayerClientMessageTest {
    @kotlin.test.Test fun heyarrScriptMessagesAreRecognisedAndOthersIgnored() {
        kotlin.test.assertEquals("fullscreen", one.rarebit.heyarr.desktop.playback.PlayerEvents.clientMessage("""{"event":"client-message","args":["heyarr","fullscreen"]}"""))
        kotlin.test.assertEquals("wake", one.rarebit.heyarr.desktop.playback.PlayerEvents.clientMessage("""{"event":"client-message","args":["heyarr","wake"]}"""))
        kotlin.test.assertNull(one.rarebit.heyarr.desktop.playback.PlayerEvents.clientMessage("""{"event":"client-message","args":["osc-visibility","auto"]}"""))
        kotlin.test.assertNull(one.rarebit.heyarr.desktop.playback.PlayerEvents.clientMessage("""{"event":"property-change","id":1,"name":"pause","data":true}"""))
    }
}
