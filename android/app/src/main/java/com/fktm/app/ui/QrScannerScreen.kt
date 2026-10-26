package com.fktm.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.water.widget.GlassPreferences
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 扫二维码（只认 QR）。
 *
 * 用 CameraX 出帧 + ZXing 纯 Java 解码，**不依赖 Google Play 服务** ——
 * 国内 ROM 上 ML Kit 不一定能用，而扫个 QR 也不需要它。
 *
 * 识别成功后立刻回调并把分析器关掉（`found` 一次性），避免同一张码触发多次。
 */
@Composable
fun QrScannerScreen(
    onDetected: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val glassBackdrop = rememberLayerBackdrop()
    val glassEnabled = GlassPreferences.isEnabled(context)

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    val found = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                if (found.get()) {
                    proxy.close()
                    return@setAnalyzer
                }
                val text = decode(proxy)
                proxy.close()
                if (text != null && found.compareAndSet(false, true)) {
                    onDetected(text)
                }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    CompositionLocalProvider(
        LocalLiquidGlassBackdrop provides glassBackdrop,
        LocalLiquidGlassEnabled provides glassEnabled,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .then(
                        if (glassEnabled) {
                            Modifier.layerBackdrop(glassBackdrop)
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (granted) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                } else {
                    if (glassEnabled) {
                        LiquidGlassAmbientBackground(
                            darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("需要相机权限才能扫码", fontSize = 15.sp)
                        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                            Text("授予权限")
                        }
                        Button(onClick = onClose) { Text("手动输入") }
                    }
                }
            }

            LiquidGlassHeader(
                title = "扫一扫",
                subtitle = "二维码识别",
                onBack = onClose,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LiquidGlassPanel(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 92.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 9.dp,
                ),
            ) {
                Text(
                    "把二维码放进取景框",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            LiquidGlassPanel(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            ) {
                TextButton(onClick = onClose, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("取消扫码")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            found.set(true)
            executor.shutdownNow()
        }
    }
}

/** 单帧解码：YUV_420_888 只取 Y 平面（灰度足够解 QR）。 */
private fun decode(proxy: ImageProxy): String? = runCatching {
    val y = proxy.planes[0]
    val buffer = y.buffer
    val w = proxy.width
    val h = proxy.height
    val rowStride = y.rowStride
    val pixelStride = y.pixelStride

    val data = ByteArray(w * h)
    if (pixelStride == 1 && rowStride == w) {
        buffer.get(data, 0, w * h)
    } else {
        // 行末有 padding，逐行拷；顺便按 pixelStride 抽点
        val row = ByteArray(rowStride)
        var out = 0
        for (r in 0 until h) {
            buffer.position(r * rowStride)
            val len = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, len)
            var c = 0
            for (x in 0 until w) {
                data[out++] = row[c]
                c += pixelStride
                if (c >= rowStride) break
            }
        }
    }

    val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }
    reader.decodeWithState(bitmap).text
}.getOrNull()
