package com.yooshastan.audiopath.data

/** Parser for sections from our allowlisted Shizuku collector; never interprets a file as a live DAC measurement. */
data class HardwareSection(val title: String, val status: String, val details: String)

object HardwareReport {
    private val heading = Regex("^=== ((?:ALSA|HAL|VENDOR|TINYMIX): .+) ===$")

    fun parse(report: String): List<HardwareSection> {
        val sections = mutableListOf<HardwareSection>()
        var title: String? = null
        val data = StringBuilder()
        fun flush() {
            val current = title ?: return
            val text = data.toString().trim()
            val status = text.lineSequence().firstOrNull()?.removePrefix("Status: ") ?: "UNKNOWN"
            sections.add(HardwareSection(current, status, text.lineSequence().drop(1).joinToString("\n").trim()))
            data.setLength(0)
        }
        for (line in report.lineSequence()) {
            val match = heading.matchEntire(line.trimEnd())
            if (match != null) {
                flush()
                title = match.groupValues[1]
            } else if (line.startsWith("=== ") && line.endsWith(" ===")) {
                flush()
                title = null
            } else if (title != null) {
                data.appendLine(line)
            }
        }
        flush()
        return sections
    }
}
