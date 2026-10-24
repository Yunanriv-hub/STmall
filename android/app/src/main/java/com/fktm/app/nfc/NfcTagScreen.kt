package com.fktm.app.nfc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fktm.app.ui.LiquidGlassAmbientBackground
import com.fktm.app.ui.LiquidGlassHeader
import com.fktm.app.ui.LiquidGlassPanel
import com.fktm.app.ui.LocalLiquidGlassBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun NfcTagScreen(
    state: NfcTagUiState,
    onClose: () -> Unit,
    onReadMode: () -> Unit,
    onWriteDevice: (String) -> Unit,
    onAddDevice: (String) -> Unit,
    onCopyDeviceId: (String) -> Unit,
) {
    var writeId by rememberSaveable { mutableStateOf(state.writeDeviceId) }
    val glassBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalLiquidGlassBackdrop provides glassBackdrop,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .layerBackdrop(glassBackdrop)
            ) {
                LiquidGlassAmbientBackground(
                    darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
        LiquidGlassHeader(
            title = "NFC 碰一碰",
            subtitle = "读取或写入设备标签",
            onBack = onClose,
        )

        LiquidGlassPanel(
            shape = RoundedCornerShape(24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (state.mode == NfcTagMode.WRITE) "正在等待写入" else "正在读取标签",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    state.status,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    lineHeight = 21.sp
                )
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        if (state.deviceId != null) {
            LiquidGlassPanel(
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("设备编号", fontWeight = FontWeight.SemiBold)
                    SelectionContainer {
                        Text(
                            state.deviceId,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onAddDevice(state.deviceId) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("添加到慧生活")
                        }
                        OutlinedButton(
                            onClick = { onCopyDeviceId(state.deviceId) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("复制编号")
                        }
                    }
                }
            }
        }

        if (!state.payload.isNullOrBlank()) {
            LiquidGlassPanel(
                shape = RoundedCornerShape(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text("标签原始内容", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            state.payload,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        Text("标签工具", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        OutlinedButton(
            onClick = onReadMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("仅读取标签")
        }

        OutlinedTextField(
            value = writeId,
            onValueChange = { writeId = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("要写入的设备编号") },
            supportingText = { Text("写入格式：https://i.ilife798.com/nfc.html?q=编号") },
            singleLine = true
        )
        Button(
            onClick = { onWriteDevice(writeId) },
            modifier = Modifier.fillMaxWidth(),
            enabled = writeId.isNotBlank() && !state.busy
        ) {
            Text("写入到 NFC 标签")
        }

        Text(
            "仅对你有权管理和使用的设备标签写入。不要复制他人的门禁卡或校园卡。",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
        }
    }
}
