package com.fktm.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fktm.app.FktmApp
import com.fktm.app.credential.LockCredential
import com.fktm.app.settings.AppPreferences
import com.fktm.app.settings.ThemeMode
import com.fktm.app.vm.UnlockViewModel
import com.fktm.app.vm.UnlockState
import com.fktm.app.vm.WaterViewModel
import kotlinx.coroutines.delay

private enum class CampusPage {
    Home,
    SmartDorm,
    Scanner,
    Profile,
    Settings,
}

private enum class HomeModule(
    val key: String,
    val title: String,
    val description: String,
) {
    SmartDorm(
        AppPreferences.MODULE_SMART_DORM,
        "智能宿舍",
        "门锁、饮水与宿舍服务",
    ),
    Scanner(
        AppPreferences.MODULE_SCANNER,
        "扫一扫",
        "扫描校园设备二维码",
    ),
    QuickUnlock(
        AppPreferences.MODULE_QUICK_UNLOCK,
        "一键开门",
        "首页圆形蓝牙开锁按钮",
    ),
}

/**
 * Independent campus shell. It never modifies or hooks the official Tmall Campus
 * application: BLE traffic is implemented locally and account cookies are read
 * only after the user explicitly authorizes the fixed-path Shizuku UserService.
 */
@Composable
fun CampusLiteApp(
    lockVm: UnlockViewModel,
    waterVm: WaterViewModel,
    onRequestBlePermissions: () -> Unit,
    onEnsureNotificationPermission: () -> Unit,
    onQuickUnlock: () -> Unit,
    themeMode: ThemeMode,
    darkTheme: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context.applicationContext) {
        (context.applicationContext as FktmApp).preferences
    }
    var page by rememberSaveable { mutableStateOf(CampusPage.Home) }
    var settingsOrigin by rememberSaveable { mutableStateOf(CampusPage.Home) }
    var dormSection by rememberSaveable { mutableIntStateOf(0) }
    var themeReveal by remember { mutableStateOf<ThemeRevealSpec?>(null) }
    var enabledModules by remember {
        mutableStateOf(
            HomeModule.entries
                .filter { it.key in preferences.enabledHomeModules() }
                .toSet()
        )
    }

    fun openSettings(origin: CampusPage) {
        settingsOrigin = origin
        page = CampusPage.Settings
    }

    fun setModuleEnabled(module: HomeModule, enabled: Boolean) {
        preferences.setHomeModuleEnabled(module.key, enabled)
        enabledModules = HomeModule.entries
            .filter { it.key in preferences.enabledHomeModules() }
            .toSet()
    }

    fun resetModules() {
        preferences.resetHomeModules()
        enabledModules = HomeModule.entries
            .filter { it.key in preferences.enabledHomeModules() }
            .toSet()
    }

    LaunchedEffect(Unit) {
        waterVm.prepare(context.applicationContext)
    }

    BackHandler(enabled = page != CampusPage.Home) {
        page = if (page == CampusPage.Settings) settingsOrigin else CampusPage.Home
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                waterVm.prepare(context.applicationContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (page != CampusPage.Scanner && page != CampusPage.Settings) {
                    LiquidGlassBottomBar(
                        selectedIndex = if (page == CampusPage.Profile) 1 else 0,
                        onSelected = { index ->
                            page = if (index == 0) CampusPage.Home else CampusPage.Profile
                        },
                        darkTheme = darkTheme,
                    )
                }
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (page) {
                CampusPage.Home -> CampusHome(
                    onSmartDorm = {
                        dormSection = 0
                        page = CampusPage.SmartDorm
                    },
                    onScan = { page = CampusPage.Scanner },
                    credential = lockVm.locks.firstOrNull(),
                    unlockState = lockVm.state,
                    onQuickUnlock = onQuickUnlock,
                    onOpenSettings = { openSettings(CampusPage.Home) },
                    enabledModules = enabledModules,
                    darkTheme = darkTheme,
                )

                CampusPage.SmartDorm -> SmartDormScreen(
                    selectedSection = dormSection,
                    onSectionChange = { dormSection = it },
                    onBack = { page = CampusPage.Home },
                    lockVm = lockVm,
                    waterVm = waterVm,
                    onRequestBlePermissions = onRequestBlePermissions,
                    onEnsureNotificationPermission = onEnsureNotificationPermission,
                )

                CampusPage.Scanner -> QrScannerScreen(
                    onDetected = { text ->
                        waterVm.resolve(context.applicationContext, text)
                        dormSection = 1
                        page = CampusPage.SmartDorm
                    },
                    onClose = { page = CampusPage.Home },
                )

                CampusPage.Profile -> ProfileScreen(
                    waterVm = waterVm,
                    onSyncAccount = { waterVm.importFromDevice(context.applicationContext) },
                    onOpenOfficial = { openOfficialCampus(context) },
                    onOpenOrders = { openOfficialOrders(context) },
                    onOpenAddress = { openOfficialAddress(context) },
                    onOpenShizuku = { openShizukuManager(context) },
                )

                CampusPage.Settings -> SettingsScreen(
                    themeMode = themeMode,
                    darkTheme = darkTheme,
                    enabledModules = enabledModules,
                    onThemeModeChange = onThemeModeChange,
                    onModuleToggle = ::setModuleEnabled,
                    onResetModules = ::resetModules,
                    onBack = { page = settingsOrigin },
                )
            }
        }
        }

        if (page == CampusPage.Home || page == CampusPage.Profile) {
            LiquidGlassTopActions(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 20.dp),
                showThemeToggle = page == CampusPage.Home,
                darkTheme = darkTheme,
                enabled = themeReveal == null,
                onThemeToggle = { center ->
                    if (themeReveal == null) {
                        themeReveal = ThemeRevealSpec(
                            id = System.nanoTime(),
                            center = center,
                            targetDark = !darkTheme,
                        )
                    }
                },
                onSettings = { openSettings(page) },
            )
        }

        themeReveal?.let { spec ->
            ThemeRevealOverlay(
                spec = spec,
                onCovered = {
                    onThemeModeChange(
                        if (spec.targetDark) ThemeMode.DARK else ThemeMode.LIGHT
                    )
                },
                onFinished = { themeReveal = null },
            )
        }
    }
}

@Composable
private fun CampusHome(
    onSmartDorm: () -> Unit,
    onScan: () -> Unit,
    credential: LockCredential?,
    unlockState: UnlockState,
    onQuickUnlock: () -> Unit,
    onOpenSettings: () -> Unit,
    enabledModules: Set<HomeModule>,
    darkTheme: Boolean,
) {
    val quickEnabled = HomeModule.QuickUnlock in enabledModules
    val cardModules = listOf(HomeModule.SmartDorm, HomeModule.Scanner)
        .filter { it in enabledModules }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (darkTheme) {
                        listOf(Color.Black, Color.Black, Color.Black)
                    } else {
                        listOf(Color(0xFFF9FBF9), Color(0xFFF0F5F2), Color(0xFFF7F9F8))
                    }
                )
            )
            .padding(horizontal = 24.dp),
    ) {
        val compact = maxHeight < 660.dp
        val scrollable = maxHeight < 540.dp
        val scrollState = rememberScrollState()
        Column(
            modifier = (
                if (scrollable) {
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                } else {
                    Modifier.fillMaxSize()
                }
            ).padding(
                top = if (scrollable) 58.dp else 8.dp,
                bottom = if (compact) 18.dp else 34.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!scrollable) {
                Spacer(Modifier.weight(if (enabledModules.isEmpty()) 0.68f else 0.4f))
            }
            Text(
                "STmall",
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
            Text(
                "今天想做什么？",
                fontSize = if (compact) 26.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "校园服务，一步直达",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(if (compact) 18.dp else if (quickEnabled) 26.dp else 34.dp))

            if (enabledModules.isEmpty()) {
                EmptyHomeState(onOpenSettings = onOpenSettings)
            } else {
                val cardHeight = when {
                    quickEnabled && compact -> 150.dp
                    quickEnabled -> 178.dp
                    compact -> 184.dp
                    else -> 210.dp
                }
                when (cardModules.size) {
                    1 -> ModuleFeatureCard(
                        module = cardModules.first(),
                        modifier = Modifier.fillMaxWidth(),
                        height = cardHeight,
                        darkTheme = darkTheme,
                        onSmartDorm = onSmartDorm,
                        onScan = onScan,
                    )

                    2 -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        cardModules.forEach { module ->
                            ModuleFeatureCard(
                                module = module,
                                modifier = Modifier.weight(1f),
                                height = cardHeight,
                                darkTheme = darkTheme,
                                onSmartDorm = onSmartDorm,
                                onScan = onScan,
                            )
                        }
                    }
                }

                if (quickEnabled) {
                    Spacer(Modifier.height(if (cardModules.isEmpty()) 8.dp else 16.dp))
                    QuickUnlockCircle(
                        credential = credential,
                        state = unlockState,
                        onUnlock = onQuickUnlock,
                        onManageLock = onSmartDorm,
                        compact = compact,
                        darkTheme = darkTheme,
                    )
                }
            }

            Spacer(
                if (scrollable) {
                    Modifier.height(20.dp)
                } else {
                    Modifier.weight(0.3f)
                }
            )
            Text(
                "无广告 · 官方账号同步 · 数据只保存在本机",
                fontSize = if (compact) 10.sp else 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ModuleFeatureCard(
    module: HomeModule,
    modifier: Modifier,
    height: Dp,
    darkTheme: Boolean,
    onSmartDorm: () -> Unit,
    onScan: () -> Unit,
) {
    val isSmartDorm = module == HomeModule.SmartDorm
    val accent = when {
        isSmartDorm && darkTheme -> MaterialTheme.colorScheme.primary
        isSmartDorm -> Color(0xFF25735F)
        darkTheme -> MaterialTheme.colorScheme.tertiary
        else -> Color(0xFF3C6688)
    }
    val container = when {
        darkTheme -> MaterialTheme.colorScheme.surfaceVariant
        isSmartDorm -> Color(0xFFE7F5F0)
        else -> Color(0xFFEAF1F7)
    }
    FeatureCard(
        modifier = modifier,
        height = height,
        darkTheme = darkTheme,
        icon = { color ->
            if (isSmartDorm) SmartDormGlyph(color) else ScannerGlyph(color)
        },
        title = if (isSmartDorm) "智能宿舍" else "扫一扫",
        subtitle = if (isSmartDorm) "门锁 · 饮水" else "扫码进入校园服务",
        accent = accent,
        container = container,
        onClick = if (isSmartDorm) onSmartDorm else onScan,
    )
}

@Composable
private fun EmptyHomeState(onOpenSettings: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("首页是空的", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "点右上角设置，选择要显示的模块",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun QuickUnlockCircle(
    credential: LockCredential?,
    state: UnlockState,
    onUnlock: () -> Unit,
    onManageLock: () -> Unit,
    compact: Boolean,
    darkTheme: Boolean,
) {
    val working = state is UnlockState.Working
    val hasCredential = credential != null
    val title = when {
        working -> "开门中"
        state is UnlockState.Success -> "已开门"
        state is UnlockState.Failure -> "重新开门"
        else -> "一键开门"
    }
    val subtitle = when {
        !hasCredential -> "未录入钥匙 · 点此设置"
        working -> "连接门锁并验证钥匙"
        state is UnlockState.Success -> state.message
        state is UnlockState.Failure -> state.message
        else -> credential.name
    }
    val containerTopColor = when {
        state is UnlockState.Failure -> MaterialTheme.colorScheme.errorContainer
        state is UnlockState.Success -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val containerBottomColor = when {
        state is UnlockState.Failure -> MaterialTheme.colorScheme.errorContainer
        state is UnlockState.Success -> MaterialTheme.colorScheme.secondaryContainer
        darkTheme -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val foregroundColor = when {
        state is UnlockState.Failure -> MaterialTheme.colorScheme.onErrorContainer
        state is UnlockState.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(if (compact) 132.dp else 154.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(containerTopColor, containerBottomColor),
                    )
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                    CircleShape,
                )
                .clickable(enabled = !working) {
                    if (hasCredential) onUnlock() else onManageLock()
                }
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (working) "蓝牙直连中" else "蓝牙门锁",
                    fontSize = if (compact) 9.sp else 10.sp,
                    color = foregroundColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    fontSize = if (compact) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = foregroundColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    subtitle,
                    fontSize = if (compact) 8.sp else 9.sp,
                    lineHeight = if (compact) 10.sp else 12.sp,
                    color = foregroundColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
        Text(
            if (hasCredential) "点击圆钮即可开门" else "添加一次钥匙，以后可离线开门",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier,
    height: Dp,
    darkTheme: Boolean,
    icon: @Composable (Color) -> Unit,
    title: String,
    subtitle: String,
    accent: Color,
    container: Color,
    onClick: () -> Unit,
) {
    val bottomColor = if (darkTheme) {
        lerp(container, Color.Black, 0.18f)
    } else {
        lerp(container, MaterialTheme.colorScheme.background, 0.16f)
    }
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(listOf(container, bottomColor))
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                RoundedCornerShape(28.dp),
            )
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center,
            ) {
                icon(accent)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(7.dp))
            Text(subtitle, fontSize = 12.sp, color = accent.copy(alpha = 0.82f))
        }
    }
}

@Composable
private fun SmartDormGlyph(color: Color) {
    Canvas(Modifier.size(32.dp)) {
        val stroke = Stroke(
            width = 2.1.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val roof = Path().apply {
            moveTo(size.width * 0.13f, size.height * 0.45f)
            lineTo(size.width * 0.5f, size.height * 0.14f)
            lineTo(size.width * 0.87f, size.height * 0.45f)
        }
        drawPath(roof, color, style = stroke)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.23f, size.height * 0.4f),
            size = Size(size.width * 0.54f, size.height * 0.48f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = stroke,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.41f, size.height * 0.62f),
            size = Size(size.width * 0.18f, size.height * 0.26f),
            cornerRadius = CornerRadius(1.2.dp.toPx()),
            style = stroke,
        )
        drawCircle(
            color = color.copy(alpha = 0.82f),
            radius = 1.5.dp.toPx(),
            center = Offset(size.width * 0.35f, size.height * 0.55f),
        )
        drawCircle(
            color = color.copy(alpha = 0.82f),
            radius = 1.5.dp.toPx(),
            center = Offset(size.width * 0.65f, size.height * 0.55f),
        )
    }
}

@Composable
private fun ScannerGlyph(color: Color) {
    Canvas(Modifier.size(32.dp)) {
        val stroke = Stroke(
            width = 2.1.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val corner = 7.dp.toPx()
        val left = size.width * 0.17f
        val right = size.width * 0.83f
        val top = size.height * 0.17f
        val bottom = size.height * 0.83f
        val brackets = Path().apply {
            moveTo(left, top + corner)
            lineTo(left, top)
            lineTo(left + corner, top)

            moveTo(right - corner, top)
            lineTo(right, top)
            lineTo(right, top + corner)

            moveTo(left, bottom - corner)
            lineTo(left, bottom)
            lineTo(left + corner, bottom)

            moveTo(right - corner, bottom)
            lineTo(right, bottom)
            lineTo(right, bottom - corner)
        }
        drawPath(brackets, color, style = stroke)
        drawLine(
            color = color,
            start = Offset(size.width * 0.23f, size.height * 0.5f),
            end = Offset(size.width * 0.77f, size.height * 0.5f),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawRoundRect(
            color = color.copy(alpha = 0.82f),
            topLeft = Offset(size.width * 0.32f, size.height * 0.31f),
            size = Size(2.2.dp.toPx(), 2.2.dp.toPx()),
            cornerRadius = CornerRadius(0.7.dp.toPx()),
        )
        drawRoundRect(
            color = color.copy(alpha = 0.82f),
            topLeft = Offset(size.width * 0.6f, size.height * 0.62f),
            size = Size(2.2.dp.toPx(), 2.2.dp.toPx()),
            cornerRadius = CornerRadius(0.7.dp.toPx()),
        )
    }
}

@Composable
private fun SmartDormScreen(
    selectedSection: Int,
    onSectionChange: (Int) -> Unit,
    onBack: () -> Unit,
    lockVm: UnlockViewModel,
    waterVm: WaterViewModel,
    onRequestBlePermissions: () -> Unit,
    onEnsureNotificationPermission: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(title = "智能宿舍", onBack = onBack)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = selectedSection == 0,
                onClick = { onSectionChange(0) },
                label = { Text("门锁") },
            )
            FilterChip(
                selected = selectedSection == 1,
                onClick = { onSectionChange(1) },
                label = { Text("饮水") },
            )
        }
        Box(Modifier.weight(1f)) {
            if (selectedSection == 0) {
                MainScreen(
                    vm = lockVm,
                    onRequestPermissions = onRequestBlePermissions,
                    onEnsureNotificationPermission = onEnsureNotificationPermission,
                    embedded = true,
                )
            } else {
                WaterScreen(vm = waterVm, embedded = true)
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    waterVm: WaterViewModel,
    onSyncAccount: () -> Unit,
    onOpenOfficial: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenAddress: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    val state = waterVm.state
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("我的", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "登录、订单与本地设置",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AccountCard(
            loggedIn = state.loggedIn,
            busy = state.busy,
            account = state.account,
            status = state.status,
            error = state.error,
            onSync = onSyncAccount,
            onOpenOfficial = onOpenOfficial,
            onOpenShizuku = onOpenShizuku,
        )

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                ProfileRow("我的订单", "在官方应用中查看校园订单", onOpenOrders)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ProfileRow("地址管理", "在官方应用中管理收货地址", onOpenAddress)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ProfileRow("打开官方天猫校园", "登录失效时可重新登录", onOpenOfficial)
            }
        }

        if (state.loggedIn) {
            OutlinedButton(
                onClick = {
                    waterVm.logout(context.applicationContext)
                    Toast.makeText(context, "已清除本机登录态", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清除本机登录态", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AccountCard(
    loggedIn: Boolean,
    busy: Boolean,
    account: String,
    status: String,
    error: String?,
    onSync: () -> Unit,
    onOpenOfficial: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (loggedIn) "已同步官方登录" else "登录天猫校园",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    loggedIn && account.isNotBlank() -> "$account · ${status.ifBlank { "登录态可用" }}"
                    loggedIn -> status.ifBlank { "登录态已就绪" }
                    busy -> status.ifBlank { "正在读取登录态…" }
                    else -> "先在官方天猫校园登录，再回到这里同步一次。"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
            if (error != null) {
                Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSync,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (loggedIn) "重新同步" else "同步官方登录")
                }
                OutlinedButton(
                    onClick = onOpenOfficial,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("打开官方应用")
                }
            }
            Text(
                "同步通过 Shizuku 完成，只读取官方应用的登录态，不修改官方应用。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f),
            )
            TextButton(onClick = onOpenShizuku) {
                Text("打开 Shizuku 管理器", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProfileRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "›",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    darkTheme: Boolean,
    enabledModules: Set<HomeModule>,
    onThemeModeChange: (ThemeMode) -> Unit,
    onModuleToggle: (HomeModule, Boolean) -> Unit,
    onResetModules: () -> Unit,
    onBack: () -> Unit,
) {
    var lastChangedIndex by remember { mutableIntStateOf(-1) }
    var pulseKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(pulseKey) {
        if (lastChangedIndex >= 0) {
            delay(520)
            lastChangedIndex = -1
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Column {
                Text("界面设置", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "首页布局与应用外观",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsCard(
            title = "首页模块",
            subtitle = "关闭模块后首页会自动重排；隐藏全部模块时会显示设置引导。",
        ) {
            HomeModule.entries.forEachIndexed { index, module ->
                ModuleToggleRow(
                    title = module.title,
                    subtitle = module.description,
                    checked = module in enabledModules,
                    pulseDistance = if (lastChangedIndex >= 0) {
                        kotlin.math.abs(index - lastChangedIndex)
                    } else {
                        0
                    },
                    pulseKey = pulseKey,
                    onCheckedChange = {
                        onModuleToggle(module, it)
                        lastChangedIndex = index
                        pulseKey++
                    },
                )
                if (index != HomeModule.entries.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    )
                }
            }
            OutlinedButton(
                onClick = onResetModules,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("恢复全部首页模块")
            }
        }

        SettingsCard(
            title = "显示模式",
            subtitle = if (darkTheme) {
                "暗黑模式使用纯黑背景，文字与控件颜色会同步切换。"
            } else {
                "日间模式使用浅色背景，文字与控件保持高对比度。"
            },
        ) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                ThemeChoiceRow(
                    mode = mode,
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                )
                if (index != ThemeMode.entries.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    )
                }
            }
        }

        SettingsCard(
            title = "关于与安全",
            subtitle = "STmall 0.2.6",
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "本应用不修改、不注入官方天猫校园。登录由官方应用完成，本应用只读取并加密保存本机登录态。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "门锁凭证和登录 Cookie 均使用 Android Keystore 加密，不参与系统备份。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "门锁协议与饮水能力参考 TMallKey（MIT License）。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(22.dp),
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        ),
    ) {
        Column {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
            content()
        }
    }
}

@Composable
private fun ModuleToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    pulseDistance: Int,
    pulseKey: Int,
    onCheckedChange: (Boolean) -> Unit,
) {
    val nudge = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(pulseKey) {
        if (pulseDistance == 1 || pulseDistance == 2) {
            val amplitude = if (pulseDistance == 1) 3f else 1.5f
            nudge.snapTo(0f)
            nudge.animateTo(amplitude, tween(65))
            nudge.animateTo(-amplitude * 0.68f, tween(65))
            nudge.animateTo(0f, spring(stiffness = 620f, dampingRatio = 0.7f))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = with(density) { nudge.value.dp.toPx() }
            }
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ThemeChoiceRow(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val title = when (mode) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "日间模式"
        ThemeMode.DARK -> "纯黑暗黑模式"
    }
    val subtitle = when (mode) {
        ThemeMode.SYSTEM -> "随手机系统的深浅色设置自动切换"
        ThemeMode.LIGHT -> "浅色背景与深色文字"
        ThemeMode.DARK -> "纯黑背景、亮色文字，适合夜间使用"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("返回") }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

private fun openOfficialCampus(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(OFFICIAL_PACKAGE)
    if (launch == null) {
        Toast.makeText(context, "未安装官方天猫校园", Toast.LENGTH_SHORT).show()
        return
    }
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(launch) }
        .onFailure { Toast.makeText(context, "无法打开官方应用", Toast.LENGTH_SHORT).show() }
}

private fun openOfficialOrders(context: Context) {
    openOfficialWebPage(context, OFFICIAL_ORDERS_URL)
}

private fun openOfficialAddress(context: Context) {
    openOfficialWebPage(context, OFFICIAL_ADDRESS_URL)
}

/**
 * Official MainActivity and CampusWebActivity are not exported. The supported
 * external route is DispatchActivity + tmallcampus://web/open, confirmed against
 * Tmall Campus 5.7.2 at runtime.
 */
private fun openOfficialWebPage(context: Context, url: String) {
    val deepLink = Uri.parse("tmallcampus://web/open").buildUpon()
        .appendQueryParameter("url", url)
        .build()
    val intent = Intent(Intent.ACTION_VIEW, deepLink).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        setPackage(OFFICIAL_PACKAGE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            openOfficialCampus(context)
            Toast.makeText(
                context,
                "已打开官方应用，请从“我的”进入对应页面",
                Toast.LENGTH_SHORT,
            ).show()
        }
}

private fun openShizukuManager(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
    if (launch == null) {
        Toast.makeText(context, "未安装 Shizuku", Toast.LENGTH_SHORT).show()
        return
    }
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(launch) }
        .onFailure { Toast.makeText(context, "无法打开 Shizuku", Toast.LENGTH_SHORT).show() }
}

private const val OFFICIAL_PACKAGE = "com.tmall.campus.and"
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val OFFICIAL_ADDRESS_URL =
    "https://biz.confong.cn/app/tmall-xiaoyuan/page-m-webview/deliveryAddress/list" +
        "?hideNavigator=true&spm=a21srm.page_mine.0.0"
private const val OFFICIAL_ORDERS_URL =
    "https://share.confong.cn/app/tmall-xiaoyuan/tmxy-m-share/mine-order" +
        "?loginRequired=true&hideNavigator=true&statusBarMode=dark" +
        "&useNativeStack=true&spm=a21srm.page_mine.0.0"
