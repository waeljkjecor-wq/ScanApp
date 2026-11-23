package com.example.scanapp

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.mlkit.vision.barcode.common.Barcode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
 * هذا الملف "يفهم" محتوى الكود: هل هو رابط، واي فاي، جهة اتصال، موقع...
 * ويحدد الأزرار المناسبة لكل نوع.
 */

sealed class Smart(val label: String, val icon: ImageVector) {
    data class Url(val url: String) : Smart("رابط", Icons.Default.Link)
    data class Wifi(val ssid: String, val password: String, val security: String, val hidden: Boolean) :
        Smart("واي فاي", Icons.Default.Wifi)
    data class Phone(val number: String) : Smart("رقم هاتف", Icons.Default.Phone)
    data class Sms(val number: String, val message: String) : Smart("رسالة SMS", Icons.Default.Sms)
    data class Email(val to: String, val subject: String, val body: String) : Smart("إيميل", Icons.Default.Email)
    data class Contact(val name: String, val phone: String, val email: String) :
        Smart("جهة اتصال", Icons.Default.Person)
    data class Geo(val lat: String, val lng: String) : Smart("موقع", Icons.Default.Place)
    data class Event(val title: String, val start: Long?, val end: Long?, val location: String) :
        Smart("حدث تقويم", Icons.Default.Event)
    data class Product(val code: String) : Smart("منتج", Icons.Default.ShoppingCart)
    data class Plain(val text: String) : Smart("نص", Icons.Default.TextFields)
}

// النص المختصر اللي يطلع بالسجل
fun Smart.summary(): String = when (this) {
    is Smart.Url -> url
    is Smart.Wifi -> "الشبكة: $ssid"
    is Smart.Phone -> number
    is Smart.Sms -> "إلى: $number"
    is Smart.Email -> to
    is Smart.Contact -> name.ifBlank { phone }
    is Smart.Geo -> "$lat, $lng"
    is Smart.Event -> title.ifBlank { "حدث" }
    is Smart.Product -> code
    is Smart.Plain -> text
}

private val productFormats = setOf(
    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E
)

fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR"
    Barcode.FORMAT_EAN_13 -> "EAN-13"
    Barcode.FORMAT_EAN_8 -> "EAN-8"
    Barcode.FORMAT_UPC_A -> "UPC-A"
    Barcode.FORMAT_UPC_E -> "UPC-E"
    Barcode.FORMAT_CODE_128 -> "Code 128"
    Barcode.FORMAT_CODE_39 -> "Code 39"
    Barcode.FORMAT_CODE_93 -> "Code 93"
    Barcode.FORMAT_CODABAR -> "Codabar"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_AZTEC -> "Aztec"
    else -> "كود"
}

// ---------- قراءة المحتوى ----------

fun parseSmart(raw: String, format: Int): Smart {
    val t = raw.trim()
    val up = t.uppercase(Locale.US)
    return when {
        up.startsWith("WIFI:") -> {
            val f = fields(t.substring(5))
            Smart.Wifi(
                ssid = f["S"].orEmpty(),
                password = f["P"].orEmpty(),
                security = f["T"].orEmpty().ifBlank { "nopass" },
                hidden = f["H"].equals("true", ignoreCase = true)
            )
        }
        up.startsWith("TEL:") -> Smart.Phone(t.substring(4))
        up.startsWith("SMSTO:") || up.startsWith("SMS:") -> {
            val rest = t.substringAfter(':')
            val number = rest.substringBefore('?').substringBefore(':')
            val message = if (rest.contains("body=", ignoreCase = true))
                Uri.decode(rest.substringAfter("body=")) else rest.substringAfter(':', "")
            Smart.Sms(number, message)
        }
        up.startsWith("MAILTO:") -> {
            val rest = t.substring(7)
            val params = rest.substringAfter('?', "").split('&').associate {
                it.substringBefore('=').lowercase(Locale.US) to Uri.decode(it.substringAfter('=', ""))
            }
            Smart.Email(rest.substringBefore('?'), params["subject"].orEmpty(), params["body"].orEmpty())
        }
        up.startsWith("MATMSG:") -> {
            val f = fields(t.substring(7))
            Smart.Email(f["TO"].orEmpty(), f["SUB"].orEmpty(), f["BODY"].orEmpty())
        }
        up.startsWith("MECARD:") -> {
            val f = fields(t.substring(7))
            Smart.Contact(f["N"].orEmpty().replace(',', ' ').trim(), f["TEL"].orEmpty(), f["EMAIL"].orEmpty())
        }
        up.startsWith("BEGIN:VCARD") -> {
            val v = vLines(t)
            val name = v["FN"] ?: v["N"]?.split(';')?.filter { it.isNotBlank() }?.reversed()?.joinToString(" ")
            Smart.Contact(name.orEmpty(), v["TEL"].orEmpty(), v["EMAIL"].orEmpty())
        }
        up.startsWith("GEO:") -> {
            val c = t.substring(4).substringBefore('?').split(',')
            if (c.size >= 2) Smart.Geo(c[0].trim(), c[1].trim()) else Smart.Plain(t)
        }
        up.contains("BEGIN:VEVENT") -> {
            val v = vLines(t)
            Smart.Event(v["SUMMARY"].orEmpty(), icsDate(v["DTSTART"]), icsDate(v["DTEND"]), v["LOCATION"].orEmpty())
        }
        up.startsWith("HTTP://") || up.startsWith("HTTPS://") -> Smart.Url(t)
        up.startsWith("WWW.") -> Smart.Url("https://$t")
        format in productFormats && t.all { it.isDigit() } -> Smart.Product(t)
        else -> Smart.Plain(t)
    }
}

// يقسم "S:Home;P:1234;;" لحقول، ويحترم الرموز المسبوقة بـ \
private fun fields(body: String): Map<String, String> {
    val parts = mutableListOf<String>()
    val sb = StringBuilder()
    var escaped = false
    for (c in body) {
        when {
            escaped -> { sb.append(c); escaped = false }
            c == '\\' -> escaped = true
            c == ';' -> { parts.add(sb.toString()); sb.clear() }
            else -> sb.append(c)
        }
    }
    if (sb.isNotEmpty()) parts.add(sb.toString())
    val map = mutableMapOf<String, String>()
    for (p in parts) {
        val i = p.indexOf(':')
        if (i > 0) map.putIfAbsent(p.substring(0, i).uppercase(Locale.US), p.substring(i + 1))
    }
    return map
}

// صيغة vCard و iCal: كل سطر "KEY;PARAM=X:VALUE"
private fun vLines(raw: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    raw.lines().forEach { line ->
        val i = line.indexOf(':')
        if (i > 0) {
            val key = line.substring(0, i).substringBefore(';').uppercase(Locale.US)
            map.putIfAbsent(key, line.substring(i + 1).trim())
        }
    }
    return map
}

private fun icsDate(s: String?): Long? {
    val v = s?.trim() ?: return null
    val patterns = listOf(
        "yyyyMMdd'T'HHmmss'Z'" to true,
        "yyyyMMdd'T'HHmmss" to false,
        "yyyyMMdd" to false
    )
    for ((pattern, utc) in patterns) {
        if (v.length != pattern.replace("'", "").length) continue
        try {
            val f = SimpleDateFormat(pattern, Locale.US)
            if (utc) f.timeZone = TimeZone.getTimeZone("UTC")
            f.isLenient = false
            return f.parse(v)?.time
        } catch (_: Exception) {
        }
    }
    return null
}

// ---------- الأزرار الذكية ----------

data class SmartAction(val label: String, val run: (Context) -> Unit)

fun actionsFor(s: Smart): List<SmartAction> = when (s) {
    is Smart.Url -> listOf(SmartAction("فتح") { openUrl(it, s.url) })
    is Smart.Wifi -> buildList {
        add(SmartAction("اتصال") { connectWifi(it, s) })
        if (s.password.isNotEmpty()) add(SmartAction("نسخ الرمز") { copyText(it, s.password) })
    }
    is Smart.Phone -> listOf(
        SmartAction("اتصال") { start(it, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${s.number}"))) },
        SmartAction("حفظ الرقم") { addContact(it, "", s.number, "") }
    )
    is Smart.Sms -> listOf(SmartAction("إرسال رسالة") {
        start(it, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${s.number}")).putExtra("sms_body", s.message))
    })
    is Smart.Email -> listOf(SmartAction("إرسال إيميل") {
        start(
            it, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(s.to))
                .putExtra(Intent.EXTRA_SUBJECT, s.subject)
                .putExtra(Intent.EXTRA_TEXT, s.body)
        )
    })
    is Smart.Contact -> listOf(SmartAction("حفظ جهة الاتصال") { addContact(it, s.name, s.phone, s.email) })
    is Smart.Geo -> listOf(SmartAction("فتح بالخريطة") {
        start(it, Intent(Intent.ACTION_VIEW, Uri.parse("geo:${s.lat},${s.lng}?q=${s.lat},${s.lng}")))
    })
    is Smart.Event -> listOf(SmartAction("إضافة للتقويم") { addEvent(it, s) })
    is Smart.Product -> listOf(SmartAction("بحث عن المنتج") {
        openUrl(it, "https://www.google.com/search?q=${s.code}")
    })
    is Smart.Plain -> listOf(SmartAction("بحث بالنت") {
        openUrl(it, "https://www.google.com/search?q=" + Uri.encode(s.text))
    })
}

fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

fun start(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        toast(context, "ما لكيت تطبيق يفتح هذا")
    }
}

// يفتح الرابط داخل التطبيق (Custom Tabs) بحماية Chrome
fun openUrl(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("code", text))
    toast(context, "تم النسخ")
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "مشاركة"))
}

// أندرويد 11+ يعرض نافذة "إضافة شبكة" مباشرة، والأقدم نفتح إعدادات الواي فاي وننسخ الرمز
fun connectWifi(context: Context, w: Smart.Wifi) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !w.security.equals("WEP", true)) {
        try {
            val b = WifiNetworkSuggestion.Builder().setSsid(w.ssid)
            when {
                w.security.equals("WPA3", true) || w.security.equals("SAE", true) -> b.setWpa3Passphrase(w.password)
                w.password.isNotEmpty() -> b.setWpa2Passphrase(w.password)
            }
            if (w.hidden) b.setIsHiddenSsid(true)
            val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(b.build()))
            context.startActivity(intent)
            return
        } catch (_: Exception) {
            // نكمل للطريقة اليدوية
        }
    }
    if (w.password.isNotEmpty()) copyText(context, w.password)
    toast(context, "اختار الشبكة ${w.ssid} والصق الرمز")
    start(context, Intent(Settings.ACTION_WIFI_SETTINGS))
}

fun addContact(context: Context, name: String, phone: String, email: String) {
    val intent = Intent(ContactsContract.Intents.Insert.ACTION)
        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
        .putExtra(ContactsContract.Intents.Insert.NAME, name)
        .putExtra(ContactsContract.Intents.Insert.PHONE, phone)
        .putExtra(ContactsContract.Intents.Insert.EMAIL, email)
    start(context, intent)
}

fun addEvent(context: Context, e: Smart.Event) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, e.title)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, e.location)
    e.start?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
    e.end?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
    start(context, intent)
}
