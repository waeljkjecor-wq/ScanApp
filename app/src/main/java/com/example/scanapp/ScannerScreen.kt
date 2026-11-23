package com.example.scanapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Uri>>(emptyList()) }   // صور صفحات آخر مستند
    var lastPdf by remember { mutableStateOf<File?>(null) }            // ملف آخر مستند
    var savedPdfs by remember { mutableStateOf(listPdfs(context)) }    // كل الملفات المحفوظة
    var error by remember { mutableStateOf<String?>(null) }

    // هذا يستلم النتيجة بعد ما يخلص المستخدم التصوير ويضغط حفظ
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            pages = scan?.pages?.map { it.imageUri } ?: emptyList()
            scan?.pdf?.let { pdf ->
                lastPdf = copyPdfToAppFolder(context, pdf.uri)
                savedPdfs = listPdfs(context)
            }
        }
    }

    fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)   // يقدر يختار صور من المعرض همين
            .setPageLimit(30)                // صفحة 1، صفحة 2، ... لحد 30
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,   // صور الصفحات
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF     // ملف PDF واحد بكل الصفحات
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL) // قص + فلاتر + تدوير
            .build()

        val activity = context.findActivity() ?: return
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                error = "ما كدرت أفتح السكنر: ${e.message}"
            }
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
            Icon(Icons.Default.DocumentScanner, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("مستند جديد")
        }
        Text(
            "صوّر صفحة ورا صفحة، ومن تخلص اضغط حفظ. كل الصفحات تطلع بملف PDF واحد.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        // آخر مستند: صور مصغرة لكل الصفحات + أزرار
        lastPdf?.let { file ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("آخر مستند · ${pages.size} صفحات", style = MaterialTheme.typography.titleMedium)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 10.dp)
                    ) {
                        itemsIndexed(pages) { index, uri ->
                            Column {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "صفحة ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 70.dp, height = 95.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Text("${index + 1}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    PdfActions(file, onDeleted = {
                        lastPdf = null
                        pages = emptyList()
                        savedPdfs = listPdfs(context)
                    })
                }
            }
        }

        Text(
            "ملفاتي",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )
        if (savedPdfs.isEmpty()) {
            Text("ما عندك ملفات بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(savedPdfs, key = { it.name }) { file ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(file.name)
                        Text(
                            "${file.length() / 1024} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PdfActions(file, onDeleted = {
                            if (lastPdf == file) { lastPdf = null; pages = emptyList() }
                            savedPdfs = listPdfs(context)
                        })
                    }
                }
            }
        }
    }
}

// أزرار الملف: فتح / مشاركة / حفظ بالتنزيلات / حذف
@Composable
fun PdfActions(file: File, onDeleted: () -> Unit) {
    val context = LocalContext.current
    Row {
        TextButton(onClick = { openPdf(context, file) }) { Text("فتح") }
        TextButton(onClick = { sharePdf(context, file) }) { Text("مشاركة") }
        TextButton(onClick = {
            val ok = saveToDownloads(context, file)
            val msg = if (ok) "انحفظ بمجلد التنزيلات" else "استخدم مشاركة حتى تحفظه"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }) { Text("تنزيل") }
        TextButton(onClick = {
            file.delete()
            onDeleted()
        }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
    }
}

// ---------- دوال مساعدة للملفات ----------

// السكنر يحط الـ PDF بمكان مؤقت، فننقله لمجلد التطبيق حتى ما ينمسح
fun copyPdfToAppFolder(context: Context, source: Uri): File {
    val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val out = File(dir, "scan_$stamp.pdf")
    context.contentResolver.openInputStream(source)?.use { input ->
        out.outputStream().use { input.copyTo(it) }
    }
    return out
}

fun listPdfs(context: Context): List<File> =
    File(context.filesDir, "pdfs").listFiles()
        ?.filter { it.extension == "pdf" }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

private fun uriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

fun sharePdf(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "مشاركة PDF"))
}

fun openPdf(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(context, file), "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "ما عندك تطبيق يفتح PDF", Toast.LENGTH_SHORT).show()
    }
}

// يحفظ نسخة بمجلد Download (أندرويد 10 وأحدث)
fun saveToDownloads(context: Context, file: File): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { out ->
        file.inputStream().use { it.copyTo(out) }
    } ?: return false
    return true
}

// نحتاج الـ Activity حتى نفتح شاشة السكنر
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
