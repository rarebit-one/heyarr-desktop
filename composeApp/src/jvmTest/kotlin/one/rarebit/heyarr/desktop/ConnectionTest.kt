package one.rarebit.heyarr.desktop

import one.rarebit.heyarr.desktop.state.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

/** The heartbeat's reading of one probe: a refused credential must not be reported as an unreachable node. */
class ConnectionTest {
    @Test fun probeStatusMapsToConnection() {
        assertEquals(Connection.ONLINE, Connection.fromProbe(200))
        assertEquals(Connection.UNAUTHORIZED, Connection.fromProbe(401))
        assertEquals(Connection.UNAUTHORIZED, Connection.fromProbe(403))
        assertEquals(Connection.OFFLINE, Connection.fromProbe(0))
        assertEquals(Connection.OFFLINE, Connection.fromProbe(502))
        assertEquals(Connection.OFFLINE, Connection.fromProbe(404))
    }
}
