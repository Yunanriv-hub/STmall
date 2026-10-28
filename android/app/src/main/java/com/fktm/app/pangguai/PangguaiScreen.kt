package com.fktm.app.pangguai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fktm.app.ui.CatalogLiquidButton as Button
import com.fktm.app.ui.CatalogLiquidOutlinedButton as OutlinedButton
import com.fktm.app.ui.CatalogLiquidTextButton as TextButton
import com.fktm.app.ui.CumpusRoundBackButton
import com.fktm.app.ui.LiquidGlassPanel
import com.fktm.app.ujing.pay.AlipayLauncher

@Composable
fun PangguaiScreen(
    vm: PangguaiViewModel,
    onBack: () -> Unit,
    onScan: () -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var verify by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(state.externalLaunchUrl) {
        val target = state.externalLaunchUrl ?: return@LaunchedEffect
        vm.consumeExternalLaunchUrl()
        if (!AlipayLauncher.isInstalled(context)) {
            vm.onExternalLaunchFailed("识别成功，但本机未安装支付宝")
            return@LaunchedEffect
        }
        runCatching {
            context.startActivity(AlipayLauncher.deepLinkIntent(target))
        }.onFailure {
            vm.onExternalLaunchFailed("无法打开支付宝：${it.message.orEmpty().take(80)}")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PangguaiHeader(
                loggedIn = state.loggedIn,
                onBack = onBack,
                onScan = onScan,
            )

            state.busy?.let { text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(text, fontSize = 12.sp)
                }
            }

            if (!state.loggedIn) {
                PangguaiLoginCard(
                    mobile = state.mobile,
                    codeSent = state.codeSent,
                    verify = verify,
                    enabled = state.busy == null,
                    onMobileChange = vm::onMobileChange,
                    onVerifyChange = { verify = it.filter(Char::isDigit).take(6) },
                    onSendCode = vm::sendCode,
                    onLogin = { vm.login(verify) },
                )
            } else {
                PangguaiAccountCard(
                    state = state,
                    onRefresh = vm::refreshAll,
                    onLogout = vm::logout,
                )

                if (state.recentDevices.isNotEmpty()) {
                    SectionTitle("最近使用")
                    LiquidGlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.recentDevices.forEachIndexed { index, device ->
                                if (index > 0) HorizontalDivider()
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            device.goodsName.ifBlank { "胖乖设备" },
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            device.orgName.ifBlank { "校园生活设备" },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    OutlinedButton(onClick = { vm.openDevice(device.goodsId) }) {
                                        Text("查看")
                                    }
                                }
                            }
                        }
                    }
                }

                state.selectedDevice?.let { device ->
                    DeviceDetailCard(
                        device = device,
                        skus = state.skus,
                        onClose = vm::closeDevice,
                    )
                }

                SectionTitle("历史订单")
                if (state.orders.isEmpty()) {
                    GlassMessageCard("暂无胖乖生活订单")
                } else {
                    state.orders.forEach { order ->
                        PangguaiOrderCard(order)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun PangguaiHeader(
    loggedIn: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CumpusRoundBackButton(onBack = onBack)
            Column(Modifier.weight(1f)) {
                Text("胖乖生活", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "扫码 · 设备 · 历史订单",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loggedIn) {
                OutlinedButton(onClick = onScan) { Text("扫一扫") }
            }
        }
    }
}

@Composable
private fun PangguaiLoginCard(
    mobile: String,
    codeSent: Boolean,
    verify: String,
    enabled: Boolean,
    onMobileChange: (String) -> Unit,
    onVerifyChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onLogin: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("登录胖乖生活", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "手机号只用于向 userapi.qiekj.com 请求短信验证码。Token 由 Cumpus 使用 Android Keystore 加密保存在本机。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = mobile,
                onValueChange = onMobileChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("手机号") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            OutlinedButton(
                onClick = onSendCode,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && mobile.length == 11,
            ) {
                Text(if (codeSent) "重新发送验证码" else "发送验证码")
            }
            if (codeSent) {
                OutlinedTextField(
                    value = verify,
                    onValueChange = onVerifyChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("短信验证码") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled && verify.length >= 4,
                ) {
                    Text("登录")
                }
            }
        }
    }
}

@Composable
private fun PangguaiAccountCard(
    state: PangguaiUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("胖乖账号", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        maskPhone(state.user?.phone ?: state.mobile),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "余额 ¥${state.user?.balance?.ifBlank { "0.00" } ?: "--"}",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Text("同步数据")
                }
                TextButton(onClick = onLogout, modifier = Modifier.weight(1f)) {
                    Text("退出账号")
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailCard(
    device: PangguaiDevice,
    skus: List<PangguaiSku>,
    onClose: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        device.name.ifBlank { "胖乖设备" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOf(device.orgName, device.brandName)
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose) { Text("关闭") }
            }
            if (skus.isEmpty()) {
                Text("当前没有可选服务", fontSize = 12.sp)
            } else {
                skus.forEach { sku ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(sku.name.ifBlank { "洗护服务" }, fontWeight = FontWeight.Medium)
                            Text(
                                sku.feature,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "¥${sku.price}",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (device.serviceTelephone.isNotBlank()) {
                Text(
                    "服务电话 ${device.serviceTelephone}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PangguaiOrderCard(order: PangguaiOrder) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    order.machineName.ifBlank { "胖乖设备" },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    order.statusLabel(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                listOf(order.shopName, order.functionName, "${order.minutes} 分钟")
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                Text(order.createTime, modifier = Modifier.weight(1f), fontSize = 10.sp)
                Text("¥${order.payPrice}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GlassMessageCard(message: String) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Text(
            message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 4.dp),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )
}

private fun PangguaiOrder.statusLabel(): String = when (status) {
    0 -> "待支付"
    1 -> "已超时"
    2 -> "已支付"
    3 -> "已完成"
    else -> "状态 $status"
}

private fun maskPhone(phone: String): String {
    if (phone.length < 7) return phone.ifBlank { "已登录" }
    return phone.take(3) + "****" + phone.takeLast(4)
}
