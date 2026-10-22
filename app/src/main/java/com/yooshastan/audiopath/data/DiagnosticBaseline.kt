package com.yooshastan.audiopath.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Only one user-selected baseline is stored, locally in app-private storage. Raw dumps are never saved. */
data class DiagnosticBaseline(val createdAt: Long, val model: String, val entries: List<InventoryEntry>)

data class DiagnosticChange(val group: String, val label: String, val before: String?, val after: String?, val kind: String)

object BaselineDiff {
    fun compare(before: List<InventoryEntry>, after: List<InventoryEntry>): List<DiagnosticChange> {
        val old = before.associateBy { it.id }
        val fresh = after.associateBy { it.id }
        return (old.keys + fresh.keys).distinct().sorted().mapNotNull { key ->
            val a = old[key]
            val b = fresh[key]
            val type = when {
                a == null -> "ADDED"
                b == null -> "REMOVED"
                a.value != b.value -> "CHANGED"
                else -> null
            } ?: return@mapNotNull null
            DiagnosticChange(b?.group ?: a!!.group, b?.label ?: a!!.label, a?.value, b?.value, type)
        }
    }
}

class DiagnosticBaselineStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "audio_inspector_baseline_v1.json")

    fun save(model: String, entries: List<InventoryEntry>): DiagnosticBaseline {
        require(entries.isNotEmpty()) { "Collect a hardware report first" }
        val captured = DiagnosticBaseline(System.currentTimeMillis(), model, entries.take(800))
        val json = JSONObject().apply {
            put("schema", 1)
            put("createdAt", captured.createdAt)
            put("model", captured.model)
            put("entries", JSONArray().apply { captured.entries.forEach { item -> put(JSONObject().apply {
                put("id", item.id); put("group", item.group); put("label", item.label)
                put("value", item.value); put("source", item.source); put("evidence", item.evidence)
            }) } })
        }
        // Atomic replace so a crash never leaves a partially written baseline.
        val staged = File(context.filesDir, "audio_inspector_baseline_v1.tmp")
        staged.writeText(json.toString(), Charsets.UTF_8)
        if (!staged.renameTo(file)) {
            staged.delete()
            error("Could not save the baseline")
        }
        return captured
    }

    fun load(): DiagnosticBaseline? = runCatching {
        if (!file.isFile) return null
        val json = JSONObject(file.readText(Charsets.UTF_8))
        if (json.optInt("schema") != 1) return null
        val array = json.getJSONArray("entries")
        val entries = (0 until minOf(array.length(), 800)).map { i ->
            val item = array.getJSONObject(i)
            InventoryEntry(item.getString("id"), item.getString("group"),
                item.getString("label"), item.getString("value"),
                item.getString("source"), item.getString("evidence"))
        }
        DiagnosticBaseline(json.getLong("createdAt"), json.optString("model"), entries)
    }.getOrNull()

    fun clear() {
        if (file.exists() && !file.delete()) error("Could not clear baseline")
    }
}
