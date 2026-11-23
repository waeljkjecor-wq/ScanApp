package com.example.scanapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

// عنصر واحد بالسجل: قيمة الكود، هل هو رابط، ووقت القراءة
data class ScanItem(val value: String, val isUrl: Boolean, val time: Long)

// حفظ السجل بذاكرة الموبايل حتى يبقى بعد ما تسد التطبيق
object HistoryStore {
    private const val PREFS = "history"
    private const val KEY = "items"

    fun load(context: Context): List<ScanItem> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ScanItem(o.getString("v"), o.getBoolean("u"), o.getLong("t"))
        }
    }

    fun save(context: Context, items: List<ScanItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("v", it.value).put("u", it.isUrl).put("t", it.time))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}

@Composable
fun BarcodeScreen() {
    val context = LocalContext.current
    var history by remember { mutableStateOf(HistoryStore.load(context)) }
    var error by remember { mutableStateOf<String?>(null) }

    // يفتح كاميرا Google الجاهزة ويرجع النتيجة
    fun startScan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .enableAutoZoom()   // يقرّب تلقائياً إذا الكود صغير أو بعيد
            .build()

        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.url?.url ?: barcode.rawValue ?: return@addOnSuccessListener
                val item = ScanItem(
                    value = value,
                    isUrl = barcode.valueType == Barcode.TYPE_URL,
                    time = System.currentTimeMillis()
                )
                history = listOf(item) + history   // الأحدث بالأعلى
                HistoryStore.save(context, history)
                error = null
            }
            .addOnFailureListener { e ->
                error = "صار خطأ: ${e.message}"
            }
        // إذا المستخدم رجع بدون ما يمسح، ما نسوي شي
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = { startScan() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("امسح باركود")
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("السجل", style = MaterialTheme.typography.titleMedium)
            if (history.isNotEmpty()) {
                TextButton(onClick = {
                    history = emptyList()
                    HistoryStore.save(context, history)
                }) { Text("مسح الكل") }
            }
        }

        if (history.isEmpty()) {
            Text(
                "ما مسحت أي كود بعد",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history, key = { it.time }) { item ->
                HistoryRow(item)
            }
        }
    }
}

// كارت لكل كود: النص + أزرار نسخ / فتح / مشاركة
@Composable
fun HistoryRow(item: ScanItem) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(item.value, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                DateFormat.getDateTimeInstance().format(Date(item.time)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(item.value))
                    Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                }) { Text("نسخ") }

                if (item.isUrl) {
                    TextButton(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.value)))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "ما كدرت أفتح الرابط", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("فتح") }
                }

                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, item.value)
                    context.startActivity(Intent.createChooser(send, "مشاركة"))
                }) { Text("مشاركة") }
            }
        }
    }
}
