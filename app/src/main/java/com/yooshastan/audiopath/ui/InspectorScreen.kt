package com.yooshastan.audiopath.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yooshastan.audiopath.data.AudioFact
import com.yooshastan.audiopath.data.DiagnosticBaseline
import com.yooshastan.audiopath.data.BaselineDiff
import com.yooshastan.audiopath.data.HardwareInventory
import com.yooshastan.audiopath.data.InventoryEntry
import com.yooshastan.audiopath.data.DeviceFacts
import com.yooshastan.audiopath.data.Evidence
import com.yooshastan.audiopath.data.HardwareReport
import com.yooshastan.audiopath.data.HardwareSection
import com.yooshastan.audiopath.data.Snapshot
import com.yooshastan.audiopath.shizuku.AccessState
import java.text.DateFormat
import java.util.Date

private val Ink = Color(0xFF0A1019)
private val Panel = Color(0xFF17212E)
private val Border = Color(0xFF344455)
private val LightText = Color(0xFFEDF3FA)
private val DimText = Color(0xFFACBDD0)
private val Seafoam = Color(0xFF8DE2CB)
private val Amber = Color(0xFFFFC586)

@Composable
fun InspectorScreen(
    snapshot: Snapshot?, access: AccessState, toneResult: String, rawDump: String,
    baseline: DiagnosticBaseline?,
    status: String, collecting: Boolean, playing: Boolean,
    onRefresh: () -> Unit, onPermission: () -> Unit, onCollect: () -> Unit,
    onPlayTone: () -> Unit, onSharePublic: () -> Unit, onShareRaw: () -> Unit,
    onSaveBaseline: () -> Unit, onClearBaseline: () -> Unit
) {
    var selected by remember { mutableStateOf(0) }
    val hardware = remember(rawDump) { HardwareReport.parse(rawDump) }
    val inventory = remember(hardware) { HardwareInventory.collect(hardware) }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Seafoam,
        onBackground = LightText, onSurface = LightText, onPrimary = Ink
    )) {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().safeDrawingPadding().verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Seafoam, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center) {
                        Text("↝", fontSize = 30.sp, color = Ink)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("YOO SHASTAN  /  AUDIO LAB", fontSize = 10.sp, color = Seafoam,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        Text("Audio Path Inspector", fontSize = 25.sp,
                            fontWeight = FontWeight.Bold, color = LightText)
                    }
                }
                Text("Inspect what Android reports, test your app's route, and collect deeper system diagnostics when Shizuku is available.",
                    color = DimText, fontSize = 13.sp, lineHeight = 19.sp)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatePill("● STANDARD MODE", Seafoam)
                    if (access is AccessState.Ready) StatePill("● ${if (access.uid == 0) "ROOT" else "SHIZUKU"}", Amber)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                }
                if (snapshot != null) {
                    Text("${snapshot.model}  •  Android ${snapshot.osVersion}  •  ${snapshot.devices.size} devices reported",
                        color = DimText, fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Signal path", "Devices", "Settings", "Hardware", "Compare", "Diagnostics").forEachIndexed { index, title ->
                        val active = selected == index
                        val bg = if (active) Seafoam else Panel
                        Surface(shape = RoundedCornerShape(25.dp), color = bg,
                            modifier = Modifier.selectable(selected = active, onClick = { selected = index })) {
                            Text(title, modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                                color = if (active) Ink else LightText, fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                when (selected) {
                    0 -> PathPanel(snapshot, rawDump.isNotBlank(), hardware, toneResult, playing, onPlayTone)
                    1 -> DevicesPanel(snapshot)
                    2 -> SettingsPanel(snapshot)
                    3 -> HardwarePanel(access, hardware, inventory, collecting, onPermission, onCollect)
                    4 -> ComparePanel(snapshot, rawDump, inventory, baseline, status, onSaveBaseline, onClearBaseline)
                    5 -> DiagnosticPanel(snapshot, access, status, collecting, rawDump,
                        onPermission, onCollect, onSharePublic, onShareRaw)
                }
                HorizontalDivider(color = Border)
                Text("Read-only preview · No remote uploads · No assumption of bit-perfect output",
                    color = DimText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StatePill(label: String, color: Color) {
    Text(label, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
        modifier = Modifier.background(Panel, RoundedCornerShape(16.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp))
}

@Composable
private fun PathPanel(snapshot: Snapshot?, hasDump: Boolean, hardware: List<HardwareSection>,
                      toneResult: String, playing: Boolean, onPlayTone: () -> Unit) {
    Text("SIGNAL FLOW", fontSize = 11.sp, color = Seafoam, fontFamily = FontFamily.Monospace)
    Text("A map of what we can verify", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
    Text("The connections are a conceptual signal chain. They are not proof that a third-party app uses a particular mixer or DAC rate.",
        fontSize = 12.sp, color = DimText)
    FlowStage("01", "Media playback source", "Other apps' streams are not directly observable", "UNKNOWN", Amber)
    Connector()
    FlowStage("02", "Android Audio Framework",
        "${snapshot?.facts?.firstOrNull { it.label == "Framework output sample rate" }?.value ?: "—"} reported default  ·  AudioManager",
        "CONFIGURED", Seafoam)
    Connector()
    FlowStage("03", "AudioFlinger / Audio Policy",
        if (hasDump) "Diagnostic dump captured. See Diagnostics tab." else "Collect additional evidence with Shizuku",
        if (hasDump) "DUMP AVAILABLE" else "NOT COLLECTED", if (hasDump) Seafoam else Amber)
    Connector()
    val readableHardware = hardware.count { it.status == "OK" }
    FlowStage("04", "Vendor HAL / ALSA mixer",
        if (hardware.isEmpty()) "Hardware probe not collected yet"
        else "$readableHardware / ${hardware.size} hardware diagnostic sources readable; see Hardware tab",
        if (readableHardware > 0) "METADATA AVAILABLE" else "NOT VERIFIED",
        if (readableHardware > 0) Seafoam else Amber)
    Connector()
    FlowStage("05", "Connected output candidates",
        snapshot?.devices?.filter { it.direction.contains("Output") }?.joinToString(", ") { it.name }
            ?.ifBlank { "No output devices reported" } ?: "Loading…",
        "REPORTED", Seafoam)

    PanelCard {
        Text("TEST YOUR OWN PLAYBACK ROUTE", color = Seafoam, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text("Play a 400 ms quiet, 750 Hz tone to ask Android which output device this app's AudioTrack used.",
            color = DimText, fontSize = 13.sp)
        Button(onClick = onPlayTone, enabled = !playing) {
            Text(if (playing) "Testing…" else "▶ Play quiet test tone")
        }
        Text(toneResult, fontSize = 12.sp, color = LightText, lineHeight = 17.sp)
    }
}

@Composable
private fun FlowStage(index: String, title: String, detail: String, status: String, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Text(index, color = accent, fontFamily = FontFamily.Monospace,
                fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = LightText, fontSize = 16.sp)
                Text(detail, color = DimText, fontSize = 12.sp, lineHeight = 18.sp)
                Text(status, color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun Connector() {
    Canvas(Modifier.fillMaxWidth().height(17.dp)) {
        drawLine(Seafoam.copy(alpha = .65f), start = androidx.compose.ui.geometry.Offset(size.width/2, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width/2, size.height),
            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun DevicesPanel(snapshot: Snapshot?) {
    SectionTitle("DEVICE INVENTORY", "Available Android input/output devices")
    Text("Reported formats are capabilities, not evidence of the format currently leaving the device.",
        color = DimText, fontSize = 12.sp)
    if (snapshot?.devices.isNullOrEmpty()) Text("No devices reported", color = DimText)
    snapshot?.devices?.forEach { device -> DeviceCard(device) }
}

@Composable
private fun DeviceCard(device: DeviceFacts) {
    var expanded by remember(device.id) { mutableStateOf(false) }
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = LightText)
                Text("${device.type} • ${device.direction} • ID ${device.id}",
                    color = DimText, fontSize = 11.sp)
            }
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Less" else "Details")
            }
        }
        if (expanded) {
            FactLine("Sample rates", device.sampleRates)
            FactLine("Channel counts", device.channelCounts)
            FactLine("Encodings", device.encodings)
            FactLine("Audio profiles", device.audioProfiles)
            FactLine("Address", device.address)
        }
    }
}

@Composable
private fun SettingsPanel(snapshot: Snapshot?) {
    SectionTitle("CONFIGURATION INVENTORY", "Reported values and provenance")
    Text("Android provides only a subset of system configuration to ordinary apps. Missing parameters are explicitly labeled rather than invented.",
        color = DimText, fontSize = 12.sp)
    snapshot?.facts?.forEach { fact -> SettingCard(fact) }
}

@Composable
private fun SettingCard(fact: AudioFact) {
    PanelCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(fact.label, fontSize = 12.sp, color = DimText)
                Text(fact.value, fontSize = 16.sp, color = LightText,
                    fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
            }
            val color = if (fact.evidence == Evidence.UNKNOWN) Amber else Seafoam
            Text(fact.evidence.name, fontSize = 9.sp, color = color,
                fontFamily = FontFamily.Monospace)
        }
        Text(fact.source, fontSize = 11.sp, color = DimText)
    }
}

@Composable
private fun HardwarePanel(
    access: AccessState, sections: List<HardwareSection>, inventory: List<InventoryEntry>, collecting: Boolean,
    onPermission: () -> Unit, onCollect: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    SectionTitle("HARDWARE INSPECTION", "ALSA and vendor HAL")
    Text("Read-only discovery via Shizuku. Available metadata is not proof of the DAC's actual output sample rate or DSP signal path.",
        fontSize = 12.sp, color = DimText)
    PanelCard {
        Text(access.label, fontWeight = FontWeight.SemiBold, color = LightText)
        Text("ADB shell may read some ALSA and vendor files; root can expose more. Denied is a legitimate result, not an app failure.",
            color = DimText, fontSize = 12.sp)
        when (access) {
            is AccessState.Offline -> Text("Start Shizuku to collect hardware diagnostics.", color = Amber, fontSize = 12.sp)
            is AccessState.PermissionRequired -> Button(onClick = onPermission) { Text("Authorize Shizuku") }
            is AccessState.Ready -> Button(onClick = onCollect, enabled = !collecting) {
                Text(if (collecting) "Collecting…" else "Scan hardware (read only)")
            }
        }
    }
    if (sections.isEmpty()) {
        PanelCard {
            Text("No hardware report yet", fontWeight = FontWeight.SemiBold)
            Text("A new scan checks /proc/asound, vendor audio XML, VINTF declarations, registered HAL services and optional root Tinymix.",
                color = DimText, fontSize = 12.sp)
        }
    } else {
        val accessible = sections.count { it.status == "OK" }
        Text("$accessible of ${sections.size} sources readable; ${inventory.size} indexed facts. Tap any source for details.",
            color = DimText, fontSize = 12.sp)
        OutlinedTextField(value = query, onValueChange = { query = it.take(120) },
            label = { Text("Search hardware settings") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        val matching = inventory.filter { item -> query.isBlank() ||
            listOf(item.group, item.label, item.value, item.source).any { it.contains(query, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            Text("${matching.size} indexed matches", color = DimText, fontSize = 12.sp)
            matching.take(60).forEach { entry -> InventoryFactCard(entry) }
            if (matching.size > 60) Text("Showing the first 60 matches; refine your search.", color = Amber, fontSize = 11.sp)
        } else {
            sections.forEach { entry -> HardwareSourceCard(entry) }
        }
    }
}

@Composable
private fun HardwareSourceCard(entry: HardwareSection) {
    var expanded by remember(entry.title) { mutableStateOf(false) }
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, color = LightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(entry.status, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = if (entry.status == "OK") Seafoam else Amber)
            }
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide" else "Inspect")
            }
        }
        if (expanded) {
            Text(entry.details.take(12_000) + if (entry.details.length > 12_000) "\n[Preview clipped; raw report contains more]" else "",
                color = DimText, fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun InventoryFactCard(entry: InventoryEntry) {
    PanelCard {
        Text(entry.group, color = Seafoam, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(entry.label, color = LightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(entry.value, color = DimText, fontSize = 12.sp, lineHeight = 17.sp)
        Text("${entry.evidence} · ${entry.source}", color = Amber, fontSize = 10.sp)
    }
}

@Composable
private fun ComparePanel(snapshot: Snapshot?, rawDump: String, inventory: List<InventoryEntry>,
    baseline: DiagnosticBaseline?, status: String, onSaveBaseline: () -> Unit, onClearBaseline: () -> Unit
) {
    SectionTitle("CONFIGURATION COMPARISON", "Local snapshot baseline")
    Text("Capture a baseline before connecting a DAC, changing an output, or rebooting. After the next Shizuku scan, compare known values; this does not certify hardware routing.",
        color = DimText, fontSize = 12.sp)
    PanelCard {
        Text("BASELINE", color = Seafoam, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(if (baseline == null) "No baseline saved" else "${baseline.model} · ${baseline.entries.size} indexed facts",
            color = LightText, fontSize = 14.sp)
        if (baseline != null) {
            Text(DateFormat.getDateTimeInstance().format(Date(baseline.createdAt)), color = DimText, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSaveBaseline, enabled = rawDump.isNotBlank() && inventory.isNotEmpty()) { Text("Save baseline") }
            if (baseline != null) OutlinedButton(onClick = onClearBaseline) { Text("Clear") }
        }
        Text("Saved locally in private app storage. Only indexed facts are saved, never the full raw dumpsys output.", color = DimText, fontSize = 11.sp)
    }
    if (baseline != null && rawDump.isNotBlank()) {
        val changes = remember(inventory, baseline) { BaselineDiff.compare(baseline.entries, inventory) }
        Text("${changes.size} differences", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (snapshot?.model != baseline.model) {
            Text("Different device from baseline; hardware differences may be expected.", color = Amber, fontSize = 12.sp)
        }
        if (changes.isEmpty()) Text("No indexed settings changed. Hardware output may still differ.", color = DimText, fontSize = 12.sp)
        changes.take(75).forEach { change -> PanelCard {
            Text("${change.kind} · ${change.group}", color = Amber, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(change.label, color = LightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (change.before != null) Text("Before: ${change.before}", color = DimText, fontSize = 11.sp)
            if (change.after != null) Text("After: ${change.after}", color = Seafoam, fontSize = 11.sp)
        } }
        if (changes.size > 75) Text("Displaying 75 of ${changes.size} changes.", color = DimText, fontSize = 11.sp)
    } else if (baseline != null) {
        Text("Run a hardware scan to compare against the stored baseline.", color = DimText)
    }
    if (status.isNotBlank()) Text(status, color = DimText, fontSize = 11.sp)
}

@Composable
private fun DiagnosticPanel(snapshot: Snapshot?, access: AccessState, status: String,
    collecting: Boolean, rawDump: String, onPermission: () -> Unit, onCollect: () -> Unit,
    onSharePublic: () -> Unit, onShareRaw: () -> Unit) {
    SectionTitle("SYSTEM DIAGNOSTICS", "Shizuku extension")
    PanelCard {
        Text(access.label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Text("Requires the separately installed Shizuku service. ADB mode runs as shell (UID 2000); root mode may run as UID 0. No system mixer values are modified.",
            color = DimText, fontSize = 12.sp)
        when (access) {
            is AccessState.Offline -> Text("Start Shizuku on your device, then reopen this screen.", color = Amber, fontSize = 12.sp)
            is AccessState.PermissionRequired -> Button(onClick = onPermission) { Text("Authorize Shizuku") }
            is AccessState.Ready -> Button(onClick = onCollect, enabled = !collecting) {
                Text(if (collecting) "Collecting…" else "Collect framework + hardware snapshot")
            }
        }
    }
    if (status.isNotBlank()) Text(status, color = Amber, fontSize = 12.sp)
    PanelCard {
        Text("SHARE STANDARD REPORT", color = Seafoam, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace)
        Text("${snapshot?.devices?.size ?: 0} device records • ${snapshot?.facts?.size ?: 0} framework facts",
            color = DimText, fontSize = 12.sp)
        OutlinedButton(onClick = onSharePublic) { Text("Share structured report") }
    }
    if (rawDump.isNotBlank()) {
        PanelCard {
            Text("PRIVILEGED DUMP PREVIEW", color = Seafoam, fontSize = 11.sp,
                fontFamily = FontFamily.Monospace)
            Text("Raw dumps may reveal package names, routes, hardware IDs, and other device details. Review before sharing.",
                color = Amber, fontSize = 12.sp)
            Text(rawDump.take(6_000) + if (rawDump.length > 6_000) "\n… preview truncated" else "",
                color = DimText, fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, lineHeight = 14.sp)
            OutlinedButton(onClick = onShareRaw) { Text("Share complete raw dump") }
        }
    }
}

@Composable
private fun SectionTitle(kicker: String, title: String) {
    Text(kicker, color = Seafoam, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = LightText)
}

@Composable
private fun PanelCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun FactLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), color = Seafoam, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = LightText, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
