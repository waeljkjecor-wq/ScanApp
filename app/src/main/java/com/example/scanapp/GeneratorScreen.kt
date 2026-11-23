package com.example.scanapp

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.File

// شنو نحط داخل الكود
enum class ContentType(val label: String) {
    TEXT("نص / رابط"), WIFI("واي فاي"), PHONE("هاتف"), CONTACT("جهة اتصال")
}

// نوع الكود نفسه
enum class CodeType(val label: String, val format: BarcodeFormat) {
    QR("QR", BarcodeFormat.QR_CODE),
    CODE128("باركود", BarcodeFormat.CODE_128),
    EAN13("EAN-13", BarcodeFormat.EAN_13)
}

@Composable
fun GeneratorScreen() {
    val context = LocalContext.current

    var contentType by remember { mutableStateOf(ContentType.TEXT) }
    var codeType by remember { mutableStateOf(CodeType.QR) }

    // الحقول
    var text by remember { mutableStateOf("") }
    var wifiName by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var shownText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // الواي فاي وجهة الاتصال لازم QR، لأن الباركود العادي ما يستوعبهم
    val onlyQr = contentType == ContentType.WIFI || contentType == ContentType.CONTACT

    fun generate() {
        error = null
        bitmap = null
        val content = when (contentType) {
            ContentType.TEXT -> text.trim()
            ContentType.WIFI ->
                if (wifiName.isBlank()) "" else
                    "WIFI:T:${if (wifiPass.isBlank()) "nopass" else "WPA"};" +
                        "S:${escape(wifiName)};P:${escape(wifiPass)};;"
            ContentType.PHONE -> if (phone.isBlank()) "" else "tel:${phone.trim()}"
            ContentType.CONTACT ->
                if (contactName.isBlank()) "" else
                    "MECARD:N:${escape(contactName)};TEL:${escape(phone)};EMAIL:${escape(contactEmail)};;"
        }

        // التحقق قبل التوليد
        when {
            content.isEmpty() -> { error = "املأ الحقول أول"; return }
            codeType == CodeType.EAN13 && !content.matches(Regex("\\d{12,13}")) -> {
                error = "EAN-13 يقبل أرقام بس: 12 أو 13 رقم"; return
            }
            codeType == CodeType.CODE128 && content.any { it.code > 126 } -> {
                error = "الباركود العادي ما يقبل حروف عربية، استخدم QR"; return
            }
        }

        try {
            bitmap = makeBitmap(content, codeType.format)
            shownText = when (contentType) {
                ContentType.WIFI -> "واي فاي: $wifiName"
                ContentType.CONTACT -> "جهة اتصال: $contactName"
                else -> content.removePrefix("tel:")
            }
        } catch (e: Exception) {
            error = "ما كدرت أولّد الكود: ${e.message}"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("شنو تريد تحط بالكود؟", style = MaterialTheme.typography.titleMedium)

        // أزرار النوع بصفّين
        ContentType.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { type ->
                    FilterChip(
                        selected = contentType == type,
                        onClick = {
                            contentType = type
                            bitmap = null
                            if (type == ContentType.WIFI || type == ContentType.CONTACT) codeType = CodeType.QR
                        },
                        label = { Text(type.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // الحقول تتغير حسب النوع
        when (contentType) {
            ContentType.TEXT -> OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("الرابط أو النص") },
                modifier = Modifier.fillMaxWidth()
            )
            ContentType.WIFI -> {
                OutlinedTextField(
                    value = wifiName, onValueChange = { wifiName = it },
                    label = { Text("اسم الشبكة") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = wifiPass, onValueChange = { wifiPass = it },
                    label = { Text("الرمز (اتركه فارغ إذا مفتوحة)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ContentType.PHONE -> OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("رقم الهاتف") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            ContentType.CONTACT -> {
                OutlinedTextField(
                    value = contactName, onValueChange = { contactName = it },
                    label = { Text("الاسم") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contactEmail, onValueChange = { contactEmail = it },
                    label = { Text("الإيميل (اختياري)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text("نوع الكود", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CodeType.entries.forEach { type ->
                FilterChip(
                    selected = codeType == type,
                    enabled = !onlyQr || type == CodeType.QR,
                    onClick = { codeType = type; bitmap = null },
                    label = { Text(type.label) }
                )
            }
        }

        Button(
            onClick = { generate() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("توليد")
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        // النتيجة
        bitmap?.let { bmp ->
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "الكود",
                    modifier = Modifier
                        .fillMaxWidth(if (codeType == CodeType.QR) 0.7f else 1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(8.dp)
                )
                Text(shownText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val ok = saveToGallery(context, bmp)
                        val msg = if (ok) "انحفظ بالمعرض" else "استخدم مشاركة حتى تحفظه"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }) { Text("حفظ بالمعرض") }
                    OutlinedButton(onClick = { shareImage(context, bmp) }) { Text("مشاركة") }
                }
            }
        }
    }
}

// ---------- دوال مساعدة ----------

// صيغة الواي فاي وجهة الاتصال تعتبر هذي الرموز خاصة، فنحط قبلها \
private fun escape(s: String): String =
    s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace(":", "\\:").replace("\"", "\\\"")

// يحوّل النص لصورة كود بمكتبة ZXing
fun makeBitmap(content: String, format: BarcodeFormat): Bitmap {
    val (w, h) = if (format == BarcodeFormat.QR_CODE) 800 to 800 else 1000 to 400
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",   // حتى يدعم العربي
        EncodeHintType.MARGIN to 2
    )
    val matrix = MultiFormatWriter().encode(content, format, w, h, hints)
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

// يحفظ الصورة بمجلد Pictures/ScanApp (أندرويد 10 وأحدث)
fun saveToGallery(context: Context, bmp: Bitmap): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "code_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScanApp")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    context.contentResolver.openOutputStream(uri)?.use {
        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
    } ?: return false
    return true
}

// يحفظ نسخة مؤقتة ويفتح قائمة المشاركة
fun shareImage(context: Context, bmp: Bitmap) {
    val dir = File(context.cacheDir, "codes").apply { mkdirs() }
    val file = File(dir, "code.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "مشاركة الكود"))
}
