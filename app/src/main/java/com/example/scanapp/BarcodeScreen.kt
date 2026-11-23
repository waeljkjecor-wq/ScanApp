package com.example.scanapp

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// عنصر بالسجل: المحتوى، صيغة الكود، الوقت، وملاحظة تكتبها إنت
data class ScanItem(val value: String, val format: Int, val time: Long, val note: String = "")

object HistoryStore {
    private const val PREFS = "history"
    private const val KEY = "items"

    fun load(context: Context): List<ScanItem> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ScanItem(o.getString("v"), o.optInt("f", -1), o.getLong("t"), o.optString("n", ""))
        }
    }

    fun save(context: Context, items: List<ScanItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("v", it.value).put("f", it.format).put("t", it.time).put("n", it.note))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}

@Composable
fun BarcodeScreen() {
    val context = LocalContext.current
    var history by remember { mutableStateOf(HistoryStore.load(context)) }
    var latest by remember { mutableStateOf<ScanItem?>(null) }     // آخر نتيجة تطلع بالأعلى
    var selected by remember { mutableStateOf<ScanItem?>(null) }   // العنصر المفتوح بالنافذة
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun update(list: List<ScanItem>) {
        history = list
        HistoryStore.save(context, list)
    }

    fun addResult(raw: String, format: Int, offset: Int = 0) {
        val item = ScanItem(raw, format, System.currentTimeMillis() + offset)
        latest = item
        update(listOf(item) + history)
        error = null
    }

    // مسح بالكاميرا (شاشة Google الجاهزة: بيها فلاش وزووم)
    fun scanCamera() {
        val options = GmsBarcodeScannerOptions.Builder().enableAutoZoom().build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { addResult(it, b.format) } }
            .addOnFailureListener { error = "صار خطأ: ${it.message}" }
    }

    // مسح من صورة بالمعرض (ما يحتاج صلاحية تخزين)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val image = InputImage.fromFilePath(context, uri)
            BarcodeScanning.getClient().process(image)
                .addOnSuccessListener { codes ->
                    val found = codes.filter { it.rawValue != null }
                    if (found.isEmpty()) error = "ما لكيت أي كود بهالصورة"
                    else found.forEachIndexed { i, b -> addResult(b.rawValue!!, b.format, i) }
                }
                .addOnFailureListener { error = "ما كدرت أقرا الصورة: ${it.message}" }
        } catch (e: Exception) {
            error = "ما كدرت أفتح الصورة"
        }
    }

    val filtered = if (query.isBlank()) history else history.filter {
        it.value.contains(query, ignoreCase = true) || it.note.contains(query, ignoreCase = true)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scanCamera() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("مسح بالكاميرا")
                }
                OutlinedButton(
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("من صورة")
                }
            }
        }

        error?.let { msg ->
            item { Text(msg, color = MaterialTheme.colorScheme.error) }
        }

        latest?.let { res -> item { ResultCard(res) } }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("السجل (${history.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    IconButton(onClick = { exportCsv(context, history) }) {
                        Icon(Icons.Default.TableView, contentDescription = "تصدير CSV")
                    }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "مسح الكل")
                    }
                }
            }
        }

        if (history.isEmpty()) {
            item { Text("ما مسحت أي كود بعد", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("بحث بالسجل والملاحظات") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(filtered, key = { it.time }) { item ->
            HistoryRow(item, onClick = { selected = item })
        }
    }

    // نافذة التفاصيل: الأزرار + الملاحظة + الحذف
    selected?.let { item ->
        ItemDialog(
            item = item,
            onDismiss = { selected = null },
            onSaveNote = { note ->
                val updated = item.copy(note = note)
                update(history.map { if (it.time == item.time) updated else it })
                if (latest?.time == item.time) latest = updated
                selected = null
            },
            onDelete = {
                update(history.filter { it.time != item.time })
                if (latest?.time == item.time) latest = null
                selected = null
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح السجل كله؟") },
            text = { Text("ما تكدر ترجعه بعدين.") },
            confirmButton = {
                TextButton(onClick = {
                    update(emptyList())
                    latest = null
                    confirmClear = false
                }) { Text("مسح", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } }
        )
    }
}

// كارت النتيجة الأخيرة بالأعلى
@Composable
fun ResultCard(item: ScanItem) {
    val smart = parseSmart(item.value, item.format)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(smart.icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(smart.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(formatName(item.format), style = MaterialTheme.typography.bodySmall)
            }
            SelectionContainer {
                Text(smart.summary(), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            ActionButtons(smart, item.value)
        }
    }
}

@Composable
fun HistoryRow(item: ScanItem, onClick: () -> Unit) {
    val smart = parseSmart(item.value, item.format)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(smart.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(smart.summary(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${smart.label} · ${formatName(item.format)} · " +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.note.isNotBlank()) {
                    Text(
                        "ملاحظة: ${item.note}",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

// الأزرار الذكية + نسخ + مشاركة
@Composable
fun ActionButtons(smart: Smart, raw: String) {
    val context = LocalContext.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        actionsFor(smart).forEach { action ->
            FilledTonalButton(onClick = { action.run(context) }) { Text(action.label) }
        }
        OutlinedButton(onClick = { copyText(context, raw) }) { Text("نسخ") }
        OutlinedButton(onClick = { shareText(context, raw) }) { Text("مشاركة") }
    }
}

@Composable
fun ItemDialog(item: ScanItem, onDismiss: () -> Unit, onSaveNote: (String) -> Unit, onDelete: () -> Unit) {
    val smart = parseSmart(item.value, item.format)
    var note by remember { mutableStateOf(item.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(smart.icon, contentDescription = null) },
        title = { Text(smart.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionContainer { Text(item.value, maxLines = 8, overflow = TextOverflow.Ellipsis) }
                Text(
                    formatName(item.format) + " · " +
                        DateFormat.getDateTimeInstance().format(Date(item.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActionButtons(smart, item.value)
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("ملاحظة") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSaveNote(note.trim()) }) { Text("حفظ") } },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("حذف", color = MaterialTheme.colorScheme.error) }
        }
    )
}

// تصدير السجل كملف CSV ينفتح بـ Excel أو Google Sheets
fun exportCsv(context: Context, items: List<ScanItem>) {
    val dir = File(context.cacheDir, "codes").apply { mkdirs() }
    val file = File(dir, "scan_history.csv")
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    fun cell(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    file.bufferedWriter(Charsets.UTF_8).use { w ->
        w.write("﻿")   // حتى Excel يعرض العربي صح
        w.write("التاريخ,النوع,الصيغة,المحتوى,ملاحظة\n")
        items.forEach {
            val smart = parseSmart(it.value, it.format)
            w.write(
                listOf(fmt.format(Date(it.time)), smart.label, formatName(it.format), it.value, it.note)
                    .joinToString(",") { c -> cell(c) } + "\n"
            )
        }
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "تصدير السجل"))
}
