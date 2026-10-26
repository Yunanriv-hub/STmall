package com.water.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.water.widget.ui.CatalogLiquidButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import com.water.widget.ui.CatalogLiquidIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.water.widget.ui.CatalogLiquidOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.water.widget.ui.WaterShapeTokens
import com.water.widget.ui.WaterTheme
import com.water.widget.ui.HuishengLiquidGlassHost

class DeviceAddActivity : ComponentActivity() {
    companion object {
        const val EXTRA_DEVICE_ID = "com.water.widget.EXTRA_DEVICE_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDeviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        UI.applySystemBarAppearance(this, ThemeSettings.isDark(this))
        setContent {
            WaterTheme(mode = ThemeSettings.mode(this)) {
                HuishengLiquidGlassHost(GlassPreferences.isEnabled(this)) {
                    DeviceAddScreen(
                        initialDeviceId = initialDeviceId,
                        onSave = ::saveDevice,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private fun saveDevice(deviceId: String) {
        val account = AccountStore.getCurrent(this)
        if (account == null) {
            toast("请先登录账户后再添加设备")
            return
        }
        if (isTmallWaterPayload(deviceId)) {
            toast("这是天猫校园饮水机，请从智能宿舍饮水页添加")
            return
        }
        val normalized = DeviceIdParser.normalizeOrNull(deviceId)
            ?: DeviceIdParser.normalize(deviceId).takeUnless {
                it.contains("://") || it.contains('?') || it.contains('=')
            }.orEmpty()
        if (normalized.isBlank()) {
            toast("请输入有效的设备编号")
            return
        }
        account.selectDevice(normalized)
        AccountStore.updateCurrent(this, account)
        toast("设备已保存")
        setResult(RESULT_OK)
        finish()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun isTmallWaterPayload(raw: String): Boolean {
        val text = raw.lowercase()
        return "share.confong.cn/xh" in text || Regex("""\btmxy\d{10,20}\b""").containsMatchIn(text)
    }
}

@Composable
internal fun DeviceAddScreen(
    onSave: (String) -> Unit,
    onBack: () -> Unit,
    initialDeviceId: String = ""
) {
    val context = LocalContext.current
    var deviceId by rememberSaveable(initialDeviceId) {
        mutableStateOf(DeviceIdParser.normalize(initialDeviceId))
    }
    var payloadError by rememberSaveable { mutableStateOf<String?>(null) }
    fun acceptPayload(raw: String) {
        val parsed = DeviceIdParser.normalizeOrNull(raw)
        if (parsed == null) {
            payloadError = "未识别到慧生活设备编号"
            return
        }
        if (
            raw.lowercase().contains("share.confong.cn/xh") ||
            Regex("""\btmxy\d{10,20}\b""").containsMatchIn(raw.lowercase())
        ) {
            payloadError = "识别到天猫校园饮水机，请从智能宿舍饮水页添加"
            return
        }
        deviceId = parsed
        payloadError = null
    }
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("com.fktm.app.scan.EXTRA_RESULT")
                ?.let(::acceptPayload)
        }
    }
    val nfcLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("com.fktm.app.nfc.EXTRA_DEVICE_ID_RESULT")
                ?.let(::acceptPayload)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DeviceAddHero(onBack)

        DeviceAddCard {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("设备编号", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = {
                        deviceId = it
                        payloadError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设备编号 / 二维码内容") },
                    supportingText = {
                        Text("支持设备贴纸二维码、NFC 标签或手动输入")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            scanLauncher.launch(
                                Intent().setClassName(
                                    context.packageName,
                                    "com.fktm.app.scan.DeviceScanActivity",
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = WaterShapeTokens.compact,
                    ) {
                        Text("扫码识别")
                    }
                    OutlinedButton(
                        onClick = {
                            nfcLauncher.launch(
                                Intent()
                                    .setClassName(
                                        context.packageName,
                                        "com.fktm.app.nfc.NfcTagActivity",
                                    )
                                    .putExtra("com.fktm.app.nfc.EXTRA_RETURN_DEVICE_ID", true)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = WaterShapeTokens.compact,
                    ) {
                        Text("NFC 碰一碰")
                    }
                }
                payloadError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }

        Button(
            onClick = { onSave(deviceId) },
            modifier = Modifier.fillMaxWidth(),
            shape = WaterShapeTokens.compact
        ) {
            Text("保存设备")
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            shape = WaterShapeTokens.compact
        ) {
            Text("取消")
        }
    }
}

@Composable
private fun DeviceAddHero(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = WaterShapeTokens.prominent,
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.onPrimaryContainer
                    )
                }
                Text(
                    "添加慧生活设备",
                    modifier = Modifier.weight(1f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer
                )
            }
            Text(
                "可从设备贴纸扫码或碰一碰 NFC 自动识别",
                color = colors.onPrimaryContainer.copy(alpha = 0.78f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DeviceAddCard(content: @Composable () -> Unit) {
    Card(
        shape = WaterShapeTokens.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) { content() }
    }
}
