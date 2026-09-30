package dev.echelonoverlay.app

import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticPacketLogTest {
    @Test
    fun `packet log limits retained entries`() {
        val log = DiagnosticPacketLog(maxEntries = 3)

        log.addPacket("F0 A1 00 91")
        log.addPacket("F0 A1 00 93")
        log.addPacket("F0 A3 00 93")
        log.addPacket("F0 B0 01 01 A2")

        assertEquals(3, log.entries.size)
        assertEquals("F0 B0 01 01 A2", log.entries.last().packetHex)
    }
}
