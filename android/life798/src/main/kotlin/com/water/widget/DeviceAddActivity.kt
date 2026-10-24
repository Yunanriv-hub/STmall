package com.water.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.water.widget.ui.WaterShapeTokens
import com.water.widget.ui.WaterTheme

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
                DeviceAddScreen(
                    initialDeviceId = initialDeviceId,
                    onSave = ::saveDevice,
                    onBack = { finish() }
                )
            }
        }
    }

    private fun saveDevice(deviceId: String) {
        val account = AccountStore.getCurrent(this)
        if (account == null) {
            toast("请先登录账户后再添加设备")
            return
        }
        val normalized = DeviceIdParser.normalize(deviceId)
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
}

@Composable
internal fun DeviceAddScreen(
    onSave: (String) -> Unit,
    onBack: () -> Unit,
    initialDeviceId: String = ""
) {
    var deviceId by rememberSaveable(initialDeviceId) {
        mutableStateOf(DeviceIdParser.normalize(initialDeviceId))
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
                    onValueChange = { deviceId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设备编号 / 二维码内容") },
                    supportingText = {
                        Text("也可以在 STmall 扫一扫或碰一碰设备贴纸后自动填入")
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
                    "添加设备",
                    modifier = Modifier.weight(1f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer
                )
            }
            Text(
                "填写设备编号，或从 STmall 扫一扫 / NFC 进入",
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
