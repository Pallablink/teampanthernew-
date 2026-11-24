package com.example.teampanthernew.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

data class DevNetworkEntry(
    val id: Long = System.currentTimeMillis(),
    val time: String,
    val method: String,
    val url: String,
    var status: Int = 0, // 0 = pending/failed, 200 = OK, 404, etc.
    var duration: String = "pending",
    var error: String = "",
    var isXhr: Boolean = false
)

data class DevConsoleEntry(
    val id: Long = System.currentTimeMillis(),
    val time: String,
    val level: String, // ERROR, WARNING, LOG, DEBUG
    val message: String,
    val source: String
)

object DevInspectorManager {

    val networkEntries = CopyOnWriteArrayList<DevNetworkEntry>()
    val consoleEntries = CopyOnWriteArrayList<DevConsoleEntry>()

    private var onDataChangedListener: (() -> Unit)? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun setListener(listener: (() -> Unit)?) {
        this.onDataChangedListener = listener
    }

    private val networkLock = Any()
    private val consoleLock = Any()

    fun recordNetwork(method: String, url: String, status: Int, duration: String = "", error: String = "", isXhr: Boolean = false) {
        val time = timeFormat.format(Date())
        synchronized(networkLock) {
            // Avoid duplicate spam for the same pending URL if updated
            val existing = networkEntries.findLast { it.url == url && (it.status == 0 || it.status == status) }
            if (existing != null && existing.status == 0 && status != 0) {
                existing.status = status
                existing.duration = duration
                existing.error = error
            } else {
                val entry = DevNetworkEntry(
                    time = time,
                    method = method.uppercase(Locale.ROOT),
                    url = url,
                    status = status,
                    duration = if (duration.isEmpty()) (if (status > 0) "ok" else "failed") else duration,
                    error = error,
                    isXhr = isXhr
                )
                networkEntries.add(0, entry)
                // Keep at most 300 items in memory safely
                while (networkEntries.size > 300) {
                    networkEntries.removeAt(networkEntries.lastIndex)
                }
            }
        }
        onDataChangedListener?.invoke()
    }

    fun recordConsole(level: String, message: String, source: String = "") {
        val time = timeFormat.format(Date())
        val entry = DevConsoleEntry(
            time = time,
            level = level.uppercase(Locale.ROOT),
            message = message,
            source = source
        )
        synchronized(consoleLock) {
            consoleEntries.add(0, entry)
            while (consoleEntries.size > 300) {
                consoleEntries.removeAt(consoleEntries.lastIndex)
            }
        }
        onDataChangedListener?.invoke()
    }

    fun clearNetwork() {
        networkEntries.clear()
        onDataChangedListener?.invoke()
    }

    fun clearConsole() {
        consoleEntries.clear()
        onDataChangedListener?.invoke()
    }

    fun getTotalNetworkCount(): Int = networkEntries.size

    fun getFailedNetworkCount(): Int {
        return networkEntries.count { it.status == 0 || it.status >= 400 || it.error.isNotEmpty() }
    }

    fun getTotalConsoleCount(): Int = consoleEntries.size

    fun getConsoleErrorCount(): Int {
        return consoleEntries.count { it.level == "ERROR" }
    }

    fun getAllNetworkAsText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== TEAM PANTHER NETWORK LOGS (${networkEntries.size}) ===")
        networkEntries.forEach {
            sb.appendLine("[${it.time}] ${it.method} | Status: ${it.status} (${it.duration})")
            sb.appendLine("URL: ${it.url}")
            if (it.error.isNotEmpty()) {
                sb.appendLine("ERROR: ${it.error}")
            }
            sb.appendLine("---")
        }
        return sb.toString()
    }

    fun getAllConsoleAsText(): String {
        val sb = StringBuilder()
        sb.appendLine("=== TEAM PANTHER CONSOLE LOGS (${consoleEntries.size}) ===")
        consoleEntries.forEach {
            sb.appendLine("[${it.time}] [${it.level}] ${it.message}")
            if (it.source.isNotEmpty()) {
                sb.appendLine("Source: ${it.source}")
            }
            sb.appendLine("---")
        }
        return sb.toString()
    }
}
