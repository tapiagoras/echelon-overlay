package dev.echelonoverlay.app

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

data class DiagnosticPacketEntry(
    val timestamp: Long,
    val packetHex: String,
) {
    fun formattedTimestamp(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

class DiagnosticPacketLog(
    private val maxEntries: Int = 150,
) {
    private val deque = ArrayDeque<DiagnosticPacketEntry>()

    val entries: List<DiagnosticPacketEntry>
        get() = deque.toList()

    val count: Int
        get() = deque.size

    fun addPacket(packetHex: String): DiagnosticPacketEntry {
        val normalized = packetHex.trim()
        val entry = DiagnosticPacketEntry(System.currentTimeMillis(), normalized)
        if (deque.size >= maxEntries) {
            deque.removeFirst()
        }
        deque.addLast(entry)
        return entry
    }

    fun clear() {
        deque.clear()
    }

    fun displayLines(): List<String> =
        entries.map { "${it.formattedTimestamp()}  ${it.packetHex}" }
}
