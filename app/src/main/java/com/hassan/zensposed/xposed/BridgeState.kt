package com.hassan.zensposed.xposed

import java.io.File

/**
 * Reads the world-readable state file written by the app (via su) so hooks running
 * inside system_server / SystemUI can learn the live session state without IPC.
 */
object BridgeState {

    private const val BRIDGE_FILE = "/data/local/tmp/zensposed/state.prop"

    data class State(
        val locked: Boolean,
        val endTime: Long,
        val allowPanel: Boolean,
        val blockHome: Boolean,
        val blockRecent: Boolean,
        val whitelist: Set<String>
    )

    fun read(): State {
        return try {
            val f = File(BRIDGE_FILE)
            if (!f.canRead()) return DEFAULT
            val map = HashMap<String, String>()
            f.forEachLine { line ->
                val idx = line.indexOf('=')
                if (idx > 0) map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
            }
            val locked = map["locked"] == "true"
            val endTime = map["endTime"]?.toLongOrNull() ?: 0L
            val stillActive = locked && (endTime == Long.MAX_VALUE || endTime > System.currentTimeMillis())
            State(
                locked = stillActive,
                endTime = endTime,
                allowPanel = map["allowPanel"] != "false",
                blockHome = map["blockHome"] == "true",
                blockRecent = map["blockRecent"] == "true",
                whitelist = map["whitelist"]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            )
        } catch (_: Throwable) {
            DEFAULT
        }
    }

    private val DEFAULT = State(
        locked = false,
        endTime = 0L,
        allowPanel = true,
        blockHome = false,
        blockRecent = false,
        whitelist = emptySet()
    )
}
