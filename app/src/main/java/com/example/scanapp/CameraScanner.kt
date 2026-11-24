package com.example.scanapp

import androidx.activity.compose.BackHandler
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import java.util.concurrent.atomic.AtomicBoolean

/*
 * كاميرا الباركود الخاصة بالتطبيق:
 * CameraX تعرض الكاميرا، و ML Kit (المدمجة داخل التطبيق) تقرا الكود من كل إطار.
 * ما تحتاج Google Play Services، فتشتغل على كل الموبايلات.
 */
@Composable
fun CameraScanner(onResult: (String, Int) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    var torch by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(0f) }
    val done = remember { AtomicBoolean(false) }       // حتى ناخذ أول نتيجة بس
    val scanner = remember { BarcodeScanning.getClient() }

    val controller = remember {
        LifecycleCameraController(context).apply {
            val executor = ContextCompat.getMainExecutor(context)
            setImageAnalysisAnalyzer(
                executor,
                MlKitAnalyzer(
                    listOf(scanner),
                    ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
                    executor
                ) { result ->
                    val code = result.getValue(scanner)?.firstOrNull { it.rawValue != null }
                        ?: return@MlKitAnalyzer
                    if (done.compareAndSet(false, true)) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)   // هزة خفيفة
                        onResult(code.rawValue!!, code.format)
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            scanner.close()
        }
    }

    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // صورة الكاميرا (تدعم الزووم بإصبعين همين)
        AndroidView(
            factory = { ctx -> PreviewView(ctx).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize()
        )

        // إطار التوجيه
        Box(
            Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .border(3.dp, Color.White, RoundedCornerShape(20.dp))
        )

        Text(
            "وجّه الكاميرا للكود",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
                .background(Color(0x88000000), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )

        // الأزرار بالأسفل: زووم + فلاش + إغلاق
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x88000000))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White)
                Slider(
                    value = zoom,
                    onValueChange = {
                        zoom = it
                        controller.setLinearZoom(it)
                    },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color(0x55FFFFFF), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        torch = !torch
                        controller.enableTorch(torch)
                    },
                    modifier = Modifier.background(Color(0x55FFFFFF), CircleShape)
                ) {
                    Icon(
                        if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "الفلاش",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
