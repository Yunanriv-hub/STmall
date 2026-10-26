package com.fktm.app.perfectcampus

import android.content.Intent
import android.net.Uri
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
import com.fktm.app.ui.LiquidGlassPanel

private const val SCHOOL_CODE_DOCS =
    "https://s.apifox.cn/ad1e6ba6-6cd2-4b6e-88b2-a1ca1577e14c/doc-5123374"

@Composable
fun PerfectCampusScreen(
    vm: PerfectCampusViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var customerCode by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
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
            Header(onBack = onBack)
            ReadOnlyNotice(
                onOpenSchoolCodes = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(SCHOOL_CODE_DOCS))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )

            LiquidGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(18.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = customerCode,
                        onValueChange = { customerCode = it.filter(Char::isDigit).take(12) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("学校代码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = account,
                        onValueChange = { account = it.filter { char -> char.isLetterOrDigit() }.take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("学号 / 工号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    )
                    Button(
                        onClick = { vm.queryRooms(customerCode.trim(), account.trim()) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.busy == null && customerCode.isNotBlank() && account.isNotBlank(),
                    ) {
                        Text("查询绑定房间")
                    }
                }
            }

            state.busy?.let { text ->
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

            if (state.rooms.isNotEmpty()) {
                SectionTitle("已绑定房间")
                state.rooms.forEach { room ->
                    RoomCard(
                        room = room,
                        onOpen = {
                            vm.loadRoom(customerCode.trim(), account.trim(), room)
                        },
                    )
                }
            } else if (state.queried && state.busy == null) {
                GlassMessage("该账号暂无可查询的绑定房间")
            }

            state.selectedRoom?.let { room ->
                val detail = state.detail
                if (detail != null) {
                    DetailCard(
                        detail = detail,
                        onClose = vm::closeRoom,
                    )
                } else if (state.busy != null) {
                    GlassMessage("正在读取 ${room.name}")
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
private fun Header(onBack: () -> Unit) {
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
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Column(Modifier.weight(1f)) {
                Text("完美校园", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    "宿舍水电只读查询",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReadOnlyNotice(onOpenSchoolCodes: () -> Unit) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text("仅查询，不签到", fontWeight = FontWeight.SemiBold)
            Text(
                "本模块只读取账号绑定房间和水电用量，不发送健康打卡、校内定位、粮票任务或修改请求。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenSchoolCodes) {
                Text("查看学校代码文档")
            }
        }
    }
}

@Composable
private fun RoomCard(
    room: PerfectCampusRoom,
    onOpen: () -> Unit,
) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room.name,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(onClick = onOpen) { Text("详情") }
            }
            room.meters.forEach { meter ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("已用 ${meter.used.ifBlank { "--" }}", modifier = Modifier.weight(1f))
                    Text("剩余 ${meter.remaining.ifBlank { "--" }}")
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    detail: PerfectCampusRoomDetail,
    onClose: () -> Unit,
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
                    Text(detail.roomName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    detail.roomNumber.takeIf(String::isNotBlank)?.let {
                        Text(
                            "房间号 $it",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onClose) { Text("关闭") }
            }
            detail.devices.forEachIndexed { index, device ->
                if (index > 0) HorizontalDivider()
                Text(device.name, fontWeight = FontWeight.SemiBold)
                Row {
                    Metric("账户剩余", device.remaining, Modifier.weight(1f))
                    Metric("今日使用", device.todayUsed, Modifier.weight(1f))
                }
                Row {
                    Metric("累计购买", device.totalBought, Modifier.weight(1f))
                    Metric("累计使用", device.totalUsed, Modifier.weight(1f))
                }
                if (device.weekUsage.isNotEmpty()) {
                    Text(
                        "近七日使用",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    device.weekUsage.forEach { day ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(day.date, modifier = Modifier.weight(1f), fontSize = 11.sp)
                            Text(day.amount.ifBlank { "--" }, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value.ifBlank { "--" }, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GlassMessage(text: String) {
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
