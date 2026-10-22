package com.yooshastan.audiopath.data

/** Small, stable, read-only facts extracted from privileged reports. No inferred DAC measurements. */
data class InventoryEntry(
    val id: String,
    val group: String,
    val label: String,
    val value: String,
    val source: String,
    val evidence: String
)

/** Avoid storing huge dumps; only recognized properties and short diagnostic facts are indexed. */
object HardwareInventory {
    private const val MAX_PER_SECTION = 90
    private const val MAX_VALUE = 400

    fun collect(sections: List<HardwareSection>): List<InventoryEntry> {
        val all = linkedMapOf<String, InventoryEntry>()
        for (section in sections) {
            var count = 0
            fun add(key: String, label: String, value: String, evidence: String = "REPORTED", source: String = section.title) {
                if (count >= MAX_PER_SECTION) return
                val safe = value.trim().take(MAX_VALUE)
                if (safe.isBlank()) return
                val id = "${section.title}|$key"
                if (id !in all) {
                    all[id] = InventoryEntry(id, section.title, label, safe, source, evidence)
                    count++
                }
            }
            add("probe-status", "Collector status", section.status, "COLLECTOR")
            val details = section.details.lineSequence().take(900).toList()
            when {
                section.title == "ALSA: Cards" -> details.forEach { line ->
                    val match = Regex("^\\s*(\\d+)\\s+\\[([^]]+)]\\s*:\\s*(.+)$").find(line)
                    if (match != null) add("card-${match.groupValues[1]}", "Card ${match.groupValues[1]}",
                        "${match.groupValues[2]} — ${match.groupValues[3]}")
                }
                section.title == "ALSA: PCM devices" -> details.forEach { line ->
                    val match = Regex("^\\s*(\\d+)-(\\d+):\\s*(.+)$").find(line)
                    if (match != null) add("pcm-${match.groupValues[1]}-${match.groupValues[2]}",
                        "PCM ${match.groupValues[1]}:${match.groupValues[2]}", match.groupValues[3])
                }
                section.title == "ALSA: Active PCM parameters" -> {
                    var current: String? = null
                    for (line in details) {
                        if (line.startsWith("/proc/asound/") && line.contains("/hw_params:")) {
                            current = line.substringBefore(": ").trim()
                            add("stream-$current", "Stream node", line.substringAfter(": ", "Unknown"), "COLLECTOR", current)
                        } else {
                            val match = Regex("^\\s*(access|format|subformat|channels|rate|period_size|buffer_size):\\s*(.+)$").find(line)
                            if (match != null && current != null) add("stream-$current-${match.groupValues[1]}",
                                "${match.groupValues[1]} · ${current.substringAfterLast("/pcm").substringBefore("/hw_params")}",
                                match.groupValues[2], "OBSERVED_IF_ACTIVE", current)
                        }
                    }
                }
                section.title == "HAL: Audio services" || section.title == "HAL: Legacy HIDL services" -> {
                    details.filter { it.contains("audio", ignoreCase = true) }.take(60).forEachIndexed { index, line ->
                        add("service-$index", "Audio service ${index + 1}", line)
                    }
                }
                section.title == "HAL: VINTF audio declarations" -> {
                    val halNames = Regex("(?:android\\.hardware\\.audio[^<\\s]*|audio\\.core|audio\\.effect)", RegexOption.IGNORE_CASE)
                    details.filter { halNames.containsMatchIn(it) }.take(40).forEachIndexed { index, line ->
                        add("vintf-$index", "HAL manifest declaration ${index + 1}", line, "CONFIGURED")
                    }
                }
                section.title == "HAL: Audio properties" -> {
                    val props = Regex("^\\[([^]]+)]:\\s*\\[(.*)]$")
                    for (line in details) {
                        val match = props.find(line) ?: continue
                        val key = match.groupValues[1]
                        // Ignore suspicious/non-audio values even when surfaced by a vendor tool.
                        if (!Regex("audio|alsa|a2dp|offload|mixer|codec", RegexOption.IGNORE_CASE).containsMatchIn(key)) continue
                        add("prop-$key", key, match.groupValues[2], "CONFIGURED", "getprop")
                    }
                }
                section.title.startsWith("VENDOR:") -> {
                    var file = "vendor XML"
                    val header = Regex("^--- (.+) \\[([A-Z_]+)] ---$")
                    val attr = Regex("([A-Za-z_][A-Za-z0-9_-]*)=[\\\"]([^\\\"]{1,100})[\\\"]")
                    val allowed = setOf("module", "mixPort", "devicePort", "profile", "route", "path", "ctl", "attachedDevices", "defaultOutputDevice", "format", "device")
                    var lineIndex = 0
                    for (line in details) {
                        val h = header.find(line)
                        if (h != null) { file = h.groupValues[1]; lineIndex = 0; add("file-$file", "Config file", h.groupValues[2], "COLLECTOR", file); continue }
                        val tag = Regex("<([A-Za-z][A-Za-z0-9_]*)\\b").find(line)?.groupValues?.get(1) ?: continue
                        if (tag !in allowed) continue
                        val properties = attr.findAll(line).take(7).map { "${it.groupValues[1]}=${it.groupValues[2]}" }.joinToString(", ")
                        if (properties.isNotBlank()) add("xml-$file-${lineIndex++}-$tag", "$tag configuration", properties, "CONFIGURED", file)
                    }
                }
                section.title == "TINYMIX: Controls" -> details.filter { it.matches(Regex("^\\s*\\d+\\s+.*")) }
                    .take(65).forEachIndexed { index, line -> add("control-$index", "Tinymix control ${index+1}", line, "REPORTED") }
            }
        }
        return all.values.toList()
    }
}
