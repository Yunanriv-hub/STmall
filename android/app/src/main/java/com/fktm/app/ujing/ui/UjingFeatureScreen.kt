package com.fktm.app.ujing.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.fktm.app.ui.CatalogLiquidRadioButton as RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fktm.app.FktmApp
import com.fktm.app.R
import com.fktm.app.scan.DeviceScanActivity
import com.fktm.app.ui.CatalogLiquidButton as Button
import com.fktm.app.ui.CatalogLiquidFilterChip
import com.fktm.app.ui.CatalogLiquidIconButton as IconButton
import com.fktm.app.ui.CatalogLiquidOutlinedButton as OutlinedButton
import com.fktm.app.ui.CatalogLiquidSwitch
import com.fktm.app.ui.CatalogLiquidTextButton as TextButton
import com.fktm.app.ui.LiquidGlassPanel
import com.fktm.app.ujing.data.SavedDevice
import com.fktm.app.ujing.data.UjingDeviceSnapshot
import com.fktm.app.ujing.net.DeviceWashModel
import com.fktm.app.ujing.net.ModuleType
import com.fktm.app.ujing.net.OrderData
import com.fktm.app.ujing.net.ScanResult
import com.fktm.app.ujing.net.WasherDeviceType
import com.fktm.app.ujing.net.WasherStatus
import com.fktm.app.ujing.pay.AlipayLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class UjingSortMode(val label: String) {
    ADDED("添加时间"),
    NAME("名称"),
    STATUS("空闲优先"),
    REMAIN("剩余时间"),
    ;

    companion object {
        fun from(value: String): UjingSortMode =
            entries.firstOrNull { it.name.lowercase() == value.lowercase() } ?: ADDED
    }
}

@Composable
fun UjingFeatureScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onRequestBlePermissions: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        (context.applicationContext as FktmApp).preferences
    }
    val loggedIn by vm.loggedIn.collectAsState()
    val busy by vm.busy.collectAsState()
    val toast by vm.toast.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }
    var autoRefresh by rememberSaveable { mutableStateOf(preferences.ujingAutoRefresh) }
    var freeOnly by rememberSaveable { mutableStateOf(preferences.ujingFreeOnly) }
    var sortMode by rememberSaveable {
        mutableStateOf(UjingSortMode.from(preferences.ujingSortMode))
    }
    var showRaw by remember { mutableStateOf(false) }
    var showManualAdd by remember { mutableStateOf(false) }
    var renameDevice by remember { mutableStateOf<SavedDevice?>(null) }
    var deleteDevice by remember { mutableStateOf<SavedDevice?>(null) }

    LaunchedEffect(Unit) {
        onRequestBlePermissions()
    }

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            vm.consumeToast()
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data
            ?.getStringExtra(DeviceScanActivity.EXTRA_RESULT)
            ?.takeIf(String::isNotBlank)
            ?.let(vm::onScan)
    }
    val launchScan = { scanLauncher.launch(DeviceScanActivity.intent(context)) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(vm.exportDevicesJson())
            }
        }.onSuccess {
            notify("设备清单已导出")
        }.onFailure {
            notify("导出失败：${it.message.orEmpty()}")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                .orEmpty()
        }.onSuccess { raw ->
            vm.importDevicesJson(raw)
                .onSuccess { count -> notify("已导入 $count 台设备") }
                .onFailure { notify("导入失败：${it.message.orEmpty()}") }
        }.onFailure {
            notify("读取失败：${it.message.orEmpty()}")
        }
    }

    val activity = context as? Activity
    val pendingOrderInfo by vm.pendingOrderInfo.collectAsState()
    LaunchedEffect(pendingOrderInfo) {
        val info = pendingOrderInfo ?: return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        vm.consumePendingOrderInfo()
        val (status, memo) = AlipayLauncher.pay(act, info)
        vm.onPayResult(status, memo)
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            vm.refreshSavedDevices()
            vm.refreshMyOrders()
        }
    }
    LaunchedEffect(autoRefresh, loggedIn) {
        if (!autoRefresh || !loggedIn) return@LaunchedEffect
        while (isActive) {
            delay(30_000)
            vm.refreshSavedDevices()
            vm.refreshMyOrders()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UjingHeader(
                loggedIn = loggedIn,
                onBack = onBack,
                onScan = launchScan,
                onManualAdd = { showManualAdd = true },
                onRefresh = {
                    vm.refreshAll()
                    vm.refreshSavedDevices()
                    vm.refreshMyOrders()
                },
                onLogout = vm::logout,
            )

            busy?.let { text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(text, fontSize = 12.sp)
                }
            }

            if (!loggedIn) {
                LoginCard(vm)
            } else {
                DevicesSection(
                    vm = vm,
                    freeOnly = freeOnly,
                    onFreeOnlyChange = {
                        freeOnly = it
                        preferences.ujingFreeOnly = it
                    },
                    sortMode = sortMode,
                    onCycleSort = {
                        sortMode = UjingSortMode.entries[
                            (sortMode.ordinal + 1) % UjingSortMode.entries.size
                        ]
                        preferences.ujingSortMode = sortMode.name.lowercase()
                    },
                    onRename = { renameDevice = it },
                    onDelete = { deleteDevice = it },
                    onAdd = launchScan,
                )
                ActiveDeviceSection(vm, onShowRaw = { showRaw = true })
                OrdersSection(vm)
                ToolsSection(
                    autoRefresh = autoRefresh,
                    onAutoRefreshChange = {
                        autoRefresh = it
                        preferences.ujingAutoRefresh = it
                    },
                    onRefresh = {
                        vm.refreshSavedDevices()
                        vm.refreshMyOrders()
                    },
                    onExport = {
                        exportLauncher.launch("STmall-U净设备-${LocalDate.now()}.json")
                    },
                    onImport = {
                        importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showManualAdd) {
        ManualAddDialog(
            onDismiss = { showManualAdd = false },
            onConfirm = {
                showManualAdd = false
                vm.onScan(it)
            },
        )
    }
    renameDevice?.let { saved ->
        RenameDeviceDialog(
            device = saved,
            onDismiss = { renameDevice = null },
            onConfirm = {
                vm.renameSavedDevice(saved, it)
                renameDevice = null
            },
        )
    }
    deleteDevice?.let { saved ->
        AlertDialog(
            onDismissRequest = { deleteDevice = null },
            title = { Text("移除 U净设备") },
            text = { Text("仅从本机收藏中删除，不会影响你的 U净 账户或正在进行的订单。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeSavedDevice(saved)
                    deleteDevice = null
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDevice = null }) { Text("取消") }
            },
        )
    }
    if (showRaw) {
        val scan by vm.scan.collectAsState()
        val program by vm.program.collectAsState()
        val order by vm.order.collectAsState()
        AlertDialog(
            onDismissRequest = { showRaw = false },
            title = { Text("U净原始状态") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("扫码结果", fontWeight = FontWeight.SemiBold)
                    Text(scan?.toString().orEmpty(), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    Text("模式信息", fontWeight = FontWeight.SemiBold)
                    Text(
                        program?.deviceWashModel?.joinToString("\n") {
                            "${it.workModelId}:${it.workModelName}/${it.time}min/${it.displayPriceCents}"
                        }.orEmpty(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                    Text("订单", fontWeight = FontWeight.SemiBold)
                    Text(order?.toString().orEmpty(), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showRaw = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun UjingHeader(
    loggedIn: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onManualAdd: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = onBack) { Text("‹ 返回") }
                Column(Modifier.weight(1f)) {
                    Text("U净", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "洗衣 · 洗鞋 · 烘干",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (loggedIn) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新 U净")
                    }
                    TextButton(onClick = onScan) { Text("扫码") }
                }
            }
            if (loggedIn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onManualAdd, modifier = Modifier.weight(1f)) {
                        Text("手动输入")
                    }
                    TextButton(onClick = onLogout, modifier = Modifier.weight(1f)) {
                        Text("退出账号")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginCard(vm: MainViewModel) {
    val mobile by vm.mobile.collectAsState()
    val codeSent by vm.codeSent.collectAsState()
    var captcha by rememberSaveable { mutableStateOf("") }

    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("登录 U净", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "手机号和 Token 仅加密保存在本机。登录直接连接 phoenix.ujing.online，不经过第三方代理。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = mobile,
                onValueChange = vm::onMobileChange,
                label = { Text("手机号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            if (codeSent) {
                OutlinedTextField(
                    value = captcha,
                    onValueChange = { captcha = it.filter(Char::isDigit).take(6) },
                    label = { Text("短信验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = vm::sendCode,
                    enabled = mobile.length == 11,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (codeSent) "重新发送" else "发送验证码")
                }
                Button(
                    onClick = { vm.login(captcha) },
                    enabled = codeSent && captcha.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("登录")
                }
            }
        }
    }
}

@Composable
private fun DevicesSection(
    vm: MainViewModel,
    freeOnly: Boolean,
    onFreeOnlyChange: (Boolean) -> Unit,
    sortMode: UjingSortMode,
    onCycleSort: () -> Unit,
    onRename: (SavedDevice) -> Unit,
    onDelete: (SavedDevice) -> Unit,
    onAdd: () -> Unit,
) {
    val saved by vm.savedDevices.collectAsState()
    val snapshots by vm.deviceSnapshots.collectAsState()
    val current by vm.device.collectAsState()

    val indexed = saved.withIndex()
    val filtered = indexed
        .filter { !freeOnly || snapshots[it.value.deviceId]?.createOrderEnabled == true }
        .sortedWith(
            when (sortMode) {
                UjingSortMode.ADDED -> compareBy { it.index }
                UjingSortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.value.displayName }
                UjingSortMode.STATUS -> compareBy {
                    if (snapshots[it.value.deviceId]?.createOrderEnabled == true) 0 else 1
                }
                UjingSortMode.REMAIN -> compareBy {
                    snapshots[it.value.deviceId]?.remainingSeconds ?: Int.MAX_VALUE
                }
            }
        )
        .map { it.value }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("我的设备", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            CatalogLiquidFilterChip(
                selected = freeOnly,
                onClick = { onFreeOnlyChange(!freeOnly) },
                label = { Text("只看空闲") },
            )
            TextButton(onClick = onCycleSort) { Text(sortMode.label) }
        }

        if (filtered.isEmpty()) {
            LiquidGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(18.dp),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (saved.isEmpty()) "还没有收藏 U净设备" else "当前筛选没有设备")
                    TextButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) {
                        Text("扫码添加")
                    }
                }
            }
        }

        filtered.forEach { device ->
            val snapshot = snapshots[device.deviceId]
            LiquidGlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.selectSavedDevice(device) },
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(device.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                listOf(
                                    device.storeName.takeIf(String::isNotBlank),
                                    device.deviceNo.takeIf(String::isNotBlank)?.let { "$it 号" },
                                    WasherDeviceType.displayName(device.deviceTypeId),
                                ).filterNotNull().filter(String::isNotBlank).joinToString(" · "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            snapshot?.label ?: "待查询",
                            color = when {
                                snapshot?.createOrderEnabled == true -> MaterialTheme.colorScheme.primary
                                snapshot == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.error
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    val remaining = snapshot?.remainingSeconds
                    if (remaining != null && remaining > 0) {
                        Text("剩余 ${clock(remaining)}", fontSize = 12.sp)
                    } else if (!snapshot?.detail.isNullOrBlank()) {
                        Text(
                            snapshot?.detail.orEmpty(),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (current.deviceId == device.deviceId) {
                            Text("当前设备", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onRename(device) }) { Text("改名") }
                        TextButton(onClick = { onDelete(device) }) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveDeviceSection(vm: MainViewModel, onShowRaw: () -> Unit) {
    val device by vm.device.collectAsState()
    val program by vm.program.collectAsState()
    val scan by vm.scan.collectAsState()
    val selectedId by vm.selectedModelId.collectAsState()
    if (!device.valid) return

    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.displayName, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${WasherDeviceType.displayName(device.deviceTypeId)} · ${channelName(device.moduleType)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onShowRaw) { Text("原始数据") }
            }
            Text(
                buildString {
                    append(scan?.reason?.takeIf(String::isNotBlank) ?: if (scan?.createOrderEnabled == true) "当前空闲" else "状态未知")
                    program?.warmTip?.takeIf(String::isNotBlank)?.let { append("\n").append(it) }
                },
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            val models = program?.deviceWashModel.orEmpty().filterNot { it.hide }
            Text("洗衣模式", fontWeight = FontWeight.SemiBold)
            if (models.isEmpty()) {
                Text("正在加载模式或当前设备暂无可用方案", fontSize = 12.sp)
            } else {
                models.forEach { model ->
                    WashModeRow(
                        model = model,
                        selected = model.workModelId == selectedId,
                        onSelect = { vm.selectModel(model.workModelId) },
                    )
                }
            }
            WashAction(vm)
        }
    }
}

@Composable
private fun WashModeRow(
    model: DeviceWashModel,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(model.workModelName, fontWeight = FontWeight.Medium)
            if (model.description.isNotBlank()) {
                Text(model.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("¥%.2f".format(model.displayPriceCents / 100.0), fontWeight = FontWeight.Bold)
            Text("${model.time} 分钟", fontSize = 10.sp)
        }
    }
}

@Composable
private fun WashAction(vm: MainViewModel) {
    val device by vm.device.collectAsState()
    val program by vm.program.collectAsState()
    val scan by vm.scan.collectAsState()
    val selectedId by vm.selectedModelId.collectAsState()
    val orderId by vm.orderId.collectAsState()
    val order by vm.order.collectAsState()
    val paid by vm.paid.collectAsState()
    val starting by vm.starting.collectAsState()
    val tick by vm.tick.collectAsState()
    val fetchedAt by vm.orderFetchedAt.collectAsState()
    val startedAt by vm.startedAt.collectAsState()
    val deviceRemain by vm.deviceRemain.collectAsState()
    val deviceAt by vm.deviceRemainAt.collectAsState()

    if (!device.valid) return
    val model = program?.deviceWashModel?.firstOrNull { it.workModelId == selectedId }
    val running = order?.running == true
    val justStarted = startedAt > 0 && tick - startedAt < 180_000
    val inProgress = running || justStarted
    val remain = liveRemain(order, tick, fetchedAt, startedAt, deviceRemain, deviceAt)
    val hasOrder = orderId.isNotBlank()

    Spacer(Modifier.height(2.dp))
    when {
        starting -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("正在连接洗衣机")
        }

        inProgress -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(if (remain > 0) "运行中 · ${clock(remain)}" else "运行中")
            }
            OutlinedButton(onClick = vm::syncDevice, modifier = Modifier.fillMaxWidth()) {
                Text("同步机身时间")
            }
        }

        paid || (hasOrder && order?.payFlag == 1) -> {
            Button(onClick = vm::startWash, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("蓝牙启动洗衣机")
            }
        }

        hasOrder -> Button(
            onClick = vm::startPay,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("继续支付订单")
        }

        scan?.createOrderEnabled == false -> {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("洗衣机使用中")
            }
            OutlinedButton(onClick = vm::forceSync, modifier = Modifier.fillMaxWidth()) {
                Text("强制同步状态")
            }
        }

        else -> Button(
            onClick = vm::payAndWash,
            enabled = model != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (model == null) "请先选择洗衣模式"
                else "支付 ¥%.2f · 开洗".format(model.displayPriceCents / 100.0)
            )
        }
    }
    if (hasOrder && !inProgress && !starting) {
        TextButton(onClick = vm::cancelOrder, modifier = Modifier.fillMaxWidth()) {
            Text("取消当前订单")
        }
    }
}

@Composable
private fun OrdersSection(vm: MainViewModel) {
    val orders by vm.myOrders.collectAsState()
    if (orders.isEmpty()) return
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("进行中的订单", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::refreshMyOrders) { Text("刷新") }
            }
            orders.forEach { order ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        order.deviceNo?.let { "$it 号" } ?: order.deviceId.ifBlank { "U净设备" },
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        listOfNotNull(
                            WasherStatus.displayName(order.status),
                            order.remainText.takeIf { it.isNotBlank() },
                            order.storeName,
                        ).joinToString(" · "),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsSection(
    autoRefresh: Boolean,
    onAutoRefreshChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自动刷新", fontWeight = FontWeight.Medium)
                    Text(
                        "打开页面后每 30 秒查询已收藏设备",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CatalogLiquidSwitch(
                    checked = autoRefresh,
                    onCheckedChange = onAutoRefreshChange,
                )
            }
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text("全部刷新")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text("导入设备")
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Text("导出设备")
                }
            }
            Text(
                "导入导出文件包含设备二维码原文、设备编号和 MAC。只保存在你选择的文件中，请勿公开分享。",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManualAddDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加 U净设备") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("粘贴机身二维码中的完整网址。", fontSize = 12.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("二维码内容") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) },
            ) { Text("识别") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RenameDeviceDialog(
    device: SavedDevice,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(device.deviceId) { mutableStateOf(device.customName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设备名称") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("例如 西山1舍-5层 #3") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun channelName(moduleType: Int): String = when (moduleType) {
    ModuleType.TYPE_BLE_NORDIC -> "Nordic 蓝牙"
    ModuleType.TYPE_BLE_CYPRESS -> "Cypress 蓝牙"
    ModuleType.TYPE_2G_BLE -> "2G + 蓝牙"
    ModuleType.TYPE_NB_BLE -> "NB + 蓝牙"
    ModuleType.TYPE_NB -> "NB-IoT"
    ModuleType.TYPE_4G -> "4G"
    else -> "模块 $moduleType"
}

private fun liveRemain(
    order: OrderData?,
    tick: Long,
    fetchedAt: Long,
    startedAt: Long,
    deviceRemain: Int?,
    deviceAt: Long,
): Int {
    if (order == null) return 0
    val total = order.totalSeconds
    if (deviceRemain != null && deviceRemain > 0 && deviceAt > 0 && tick > deviceAt) {
        return (deviceRemain - ((tick - deviceAt) / 1000).toInt()).coerceAtLeast(0)
    }
    val server = order.remainSeconds
    val anchor = if (fetchedAt > 0) fetchedAt else startedAt
    if (server > 0 && anchor > 0 && tick > anchor) {
        return (server - ((tick - anchor) / 1000).toInt()).coerceAtLeast(0)
    }
    val startMs = order.startMs
    if (startMs > 0 && total > 0) {
        if (tick <= startMs) return total
        return (total - ((tick - startMs) / 1000).toInt()).coerceAtLeast(0)
    }
    return 0
}

private fun clock(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)
