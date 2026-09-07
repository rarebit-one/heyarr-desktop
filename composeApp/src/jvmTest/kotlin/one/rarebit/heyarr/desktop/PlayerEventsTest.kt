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
