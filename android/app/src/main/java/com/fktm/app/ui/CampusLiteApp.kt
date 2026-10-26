package com.fktm.app.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.fktm.app.ui.CatalogLiquidButton as Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.fktm.app.ui.CatalogLiquidFilterChip as FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.fktm.app.ui.CatalogLiquidOutlinedButton as OutlinedButton
import com.fktm.app.ui.CatalogLiquidRadioButton as RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.fktm.app.ui.CatalogLiquidTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fktm.app.BuildConfig
import com.fktm.app.FktmApp
import com.fktm.app.credential.LockCredential
import com.fktm.app.pangguai.PangguaiScreen
import com.fktm.app.pangguai.PangguaiViewModel
import com.fktm.app.perfectcampus.PerfectCampusScreen
import com.fktm.app.perfectcampus.PerfectCampusViewModel
import com.fktm.app.scan.DeviceScanActivity
import com.fktm.app.settings.AppPreferences
import com.fktm.app.settings.ThemeMode
import com.fktm.app.ujing.ui.MainViewModel as UjingViewModel
import com.fktm.app.ujing.ui.UjingFeatureScreen
import com.fktm.app.vm.UnlockViewModel
import com.fktm.app.vm.UnlockState
import com.fktm.app.vm.WaterViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.water.widget.ConfigActivity
import com.water.widget.AccountStore
import com.water.widget.AccountsActivity
import com.water.widget.AppThemeMode as HuishengThemeMode
import com.water.widget.IlifeApi
import com.water.widget.LoginActivity
import com.water.widget.LoginStatusStore
import com.water.widget.ScoreActivity
import com.water.widget.ThemeSettings
import com.water.widget.TokenValidity
import com.water.widget.WalletActivity
import com.water.widget.WalletResponseParser
import com.water.widget.WaterControlState
import com.water.widget.WaterService
import com.water.widget.ui.DashboardViewModel
import com.water.widget.ui.HuishengMineContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CampusPage {
    Home,
    Features,
    SmartDorm,
    Ujing,
    Pangguai,
    PerfectCampus,
    MyHub,
    TmallProfile,
    IlifeProfile,
    Settings,
}

private fun CampusPage.isMyPage(): Boolean =
    this == CampusPage.MyHub ||
        this == CampusPage.TmallProfile ||
        this == CampusPage.IlifeProfile

private val CampusPage.depth: Int
    get() = when (this) {
        CampusPage.Home,
        CampusPage.Features,
        CampusPage.MyHub,
        -> 0

        CampusPage.SmartDorm,
        CampusPage.Ujing,
        CampusPage.Pangguai,
        CampusPage.PerfectCampus,
        CampusPage.TmallProfile,
        CampusPage.IlifeProfile,
        -> 1

        CampusPage.Settings -> 2
    }

private enum class ModuleSection {
    HOME,
    FEATURES,
}

private enum class HomeModule(
    val key: String,
    val title: String,
    val description: String,
    val section: ModuleSection,
) {
    SmartDorm(
        AppPreferences.MODULE_SMART_DORM,
        "智能宿舍",
        "门锁、饮水与宿舍服务",
        ModuleSection.FEATURES,
    ),
    Ilife(
        AppPreferences.MODULE_ILIFE,
        "慧生活",
        "饮水、钱包、积分与账单",
        ModuleSection.FEATURES,
    ),
    Ujing(
        AppPreferences.MODULE_UJING,
        "U净",
        "洗衣机、洗鞋机与烘干机",
        ModuleSection.FEATURES,
    ),
    Pangguai(
        AppPreferences.MODULE_PANGGUAI,
        "胖乖生活",
        "扫码洗护、最近设备与历史订单",
        ModuleSection.FEATURES,
    ),
    PerfectCampus(
        AppPreferences.MODULE_PERFECT_CAMPUS,
        "完美校园",
        "账号绑定房间与水电用量",
        ModuleSection.FEATURES,
    ),
    QuickUnlock(
        AppPreferences.MODULE_QUICK_UNLOCK,
        "一键开门",
        "首页圆形蓝牙开锁按钮",
        ModuleSection.HOME,
    ),
    QuickWater(
        AppPreferences.MODULE_QUICK_WATER,
        "一键接水",
        "首页圆形慧生活接水按钮",
        ModuleSection.HOME,
    ),
    UjingScanner(
        AppPreferences.MODULE_UJING_SCAN,
        "U净扫一扫",
        "扫描机身二维码直达设备",
        ModuleSection.HOME,
    ),
    PangguaiScanner(
        AppPreferences.MODULE_PANGGUAI_SCAN,
        "扫一扫",
        "扫描胖乖设备二维码",
        ModuleSection.HOME,
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
    glassEnabled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onGlassEnabledChange: (Boolean) -> Unit,
    backgroundImageUri: String? = null,
    onPickBackgroundImage: () -> Unit = {},
    onClearBackgroundImage: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as FktmApp
    val preferences = remember(context.applicationContext) {
        app.preferences
    }
    var page by rememberSaveable { mutableStateOf(CampusPage.Home) }
    var settingsOrigin by rememberSaveable { mutableStateOf(CampusPage.Home) }
    var detailOrigin by rememberSaveable { mutableStateOf(CampusPage.Home) }
    var ujingOrigin by rememberSaveable { mutableStateOf(CampusPage.Features) }
    var pangguaiOrigin by rememberSaveable { mutableStateOf(CampusPage.Features) }
    var perfectCampusOrigin by rememberSaveable { mutableStateOf(CampusPage.Features) }
    var dormSection by rememberSaveable { mutableIntStateOf(0) }
    var showLockPicker by remember { mutableStateOf(false) }
    var themeReveal by remember { mutableStateOf<ThemeRevealSpec?>(null) }
    val ujingVm: UjingViewModel = viewModel()
    val pangguaiVm: PangguaiViewModel = viewModel()
    val perfectCampusVm: PerfectCampusViewModel = viewModel()
    val homeUjingScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val raw = result.data
            ?.getStringExtra(DeviceScanActivity.EXTRA_RESULT)
            ?.takeIf(String::isNotBlank)
            ?: return@rememberLauncherForActivityResult
        ujingVm.onScan(raw)
        ujingOrigin = CampusPage.Home
        page = CampusPage.Ujing
    }
    val homePangguaiScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        pangguaiVm.onScanned(result.data?.getStringExtra(DeviceScanActivity.EXTRA_RESULT))
        pangguaiOrigin = CampusPage.Home
        page = CampusPage.Pangguai
    }
    val pangguaiScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        pangguaiVm.onScanned(result.data?.getStringExtra(DeviceScanActivity.EXTRA_RESULT))
    }
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
        page = when (page) {
            CampusPage.Settings -> settingsOrigin
            CampusPage.TmallProfile,
            CampusPage.IlifeProfile,
            -> detailOrigin

            CampusPage.SmartDorm -> detailOrigin
            CampusPage.Ujing -> ujingOrigin
            CampusPage.Pangguai -> pangguaiOrigin
            CampusPage.PerfectCampus -> perfectCampusOrigin
            CampusPage.Features,
            CampusPage.MyHub,
            -> CampusPage.Home

            else -> CampusPage.Home
        }
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

    val showTopActions = page.depth == 0
    val showBottomBar = page != CampusPage.Settings
    val glassBackdrop = rememberLayerBackdrop()
    val backgroundBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalLiquidGlassBackdrop provides glassBackdrop,
        LocalLiquidGlassEnabled provides glassEnabled,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (glassEnabled) Modifier.layerBackdrop(glassBackdrop) else Modifier
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                            .then(
                                if (glassEnabled) {
                                    Modifier.layerBackdrop(backgroundBackdrop)
                                } else {
                                    Modifier
                                }
                            )
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    BackgroundImageLayer(
                        uri = backgroundImageUri,
                        darkTheme = darkTheme,
                    )
                    if (glassEnabled) {
                        LiquidGlassAmbientBackground(darkTheme)
                    }
                    themeReveal?.let { spec ->
                        ThemeRevealOverlay(
                            spec = spec,
                            onFinished = { themeReveal = null },
                        )
                    }
                }
                CompositionLocalProvider(
                    LocalLiquidGlassBackdrop provides backgroundBackdrop
                ) {
                    Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    topBar = {},
                    bottomBar = {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .height(80.dp)
                        )
                    },
                    ) { innerPadding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            AnimatedContent(
                                targetState = page,
                                transitionSpec = {
                                    val animationsEnabled =
                                        ValueAnimator.areAnimatorsEnabled()
                                    if (animationsEnabled && initialState != targetState) {
                                        val isTopLevelSwitch =
                                            initialState.depth == 0 && targetState.depth == 0
                                        val movingForward = if (isTopLevelSwitch) {
                                            when {
                                                initialState == CampusPage.Home -> true
                                                targetState == CampusPage.Home -> false
                                                initialState == CampusPage.Features -> true
                                                else -> false
                                            }
                                        } else {
                                            targetState.depth > initialState.depth
                                        }
                                        val direction = if (movingForward) 1 else -1
                                        val offsetSpec = spring<IntOffset>(
                                            dampingRatio = 1f,
                                            stiffness = 520f,
                                        )
                                        slideInHorizontally(offsetSpec) {
                                            it * direction
                                        } togetherWith slideOutHorizontally(offsetSpec) {
                                            -it * direction
                                        }
                                    } else {
                                        fadeIn(tween(if (animationsEnabled) 180 else 0)) togetherWith
                                            fadeOut(tween(if (animationsEnabled) 120 else 0))
                                    }
                                },
                                label = "campus-page-transition",
                            ) { targetPage ->
                            when (targetPage) {
                CampusPage.Home -> CampusHome(
                    onSmartDorm = {
                        detailOrigin = CampusPage.Home
                        dormSection = 0
                        page = CampusPage.SmartDorm
                    },
                    onIlife = {
                        detailOrigin = CampusPage.Home
                        page = CampusPage.IlifeProfile
                    },
                    credential = (context.applicationContext as FktmApp)
                        .credentialStore.primary(),
                    onChangeDefaultLock = { showLockPicker = true },
                    unlockState = lockVm.state,
                    onQuickUnlock = onQuickUnlock,
                    onOpenSettings = { openSettings(CampusPage.Home) },
                    onUjingScan = {
                        homeUjingScanLauncher.launch(DeviceScanActivity.intent(context))
                    },
                    onPangguaiScan = {
                        homePangguaiScanLauncher.launch(DeviceScanActivity.intent(context))
                    },
                    enabledModules = enabledModules,
                    darkTheme = darkTheme,
                )

                CampusPage.Features -> FeatureHubScreen(
                    enabledModules = enabledModules,
                    onSmartDorm = {
                        detailOrigin = CampusPage.Features
                        dormSection = 0
                        page = CampusPage.SmartDorm
                    },
                    onIlife = {
                        detailOrigin = CampusPage.Features
                        page = CampusPage.IlifeProfile
                    },
                    onUjing = {
                        ujingOrigin = CampusPage.Features
                        page = CampusPage.Ujing
                    },
                    onPangguai = {
                        pangguaiOrigin = CampusPage.Features
                        page = CampusPage.Pangguai
                    },
                    onPerfectCampus = {
                        perfectCampusOrigin = CampusPage.Features
                        page = CampusPage.PerfectCampus
                    },
                    onOpenSettings = { openSettings(CampusPage.Features) },
                )

                CampusPage.SmartDorm -> SmartDormScreen(
                    selectedSection = dormSection,
                    onSectionChange = { dormSection = it },
                    onBack = { page = detailOrigin },
                    lockVm = lockVm,
                    waterVm = waterVm,
                    onRequestBlePermissions = onRequestBlePermissions,
                    onEnsureNotificationPermission = onEnsureNotificationPermission,
                )

                CampusPage.Ujing -> {
                    UjingFeatureScreen(
                        vm = ujingVm,
                        onBack = { page = ujingOrigin },
                        onRequestBlePermissions = onRequestBlePermissions,
                    )
                }

                CampusPage.Pangguai -> PangguaiScreen(
                    vm = pangguaiVm,
                    onBack = { page = pangguaiOrigin },
                    onScan = {
                        pangguaiScanLauncher.launch(DeviceScanActivity.intent(context))
                    },
                )

                CampusPage.PerfectCampus -> PerfectCampusScreen(
                    vm = perfectCampusVm,
                    onBack = { page = perfectCampusOrigin },
                )

                CampusPage.MyHub -> MyHubScreen(
                    onTmall = {
                        detailOrigin = CampusPage.MyHub
                        page = CampusPage.TmallProfile
                    },
                    onIlife = {
                        detailOrigin = CampusPage.MyHub
                        page = CampusPage.IlifeProfile
                    },
                    onUjing = {
                        ujingOrigin = CampusPage.MyHub
                        page = CampusPage.Ujing
                    },
                    onPangguai = {
                        pangguaiOrigin = CampusPage.MyHub
                        page = CampusPage.Pangguai
                    },
                    onPerfectCampus = {
                        perfectCampusOrigin = CampusPage.MyHub
                        page = CampusPage.PerfectCampus
                    },
                )

                CampusPage.TmallProfile -> ProfileScreen(
                    waterVm = waterVm,
                    onBack = { page = detailOrigin },
                    onSyncAccount = { waterVm.importFromDevice(context.applicationContext) },
                    onOpenOfficial = { openOfficialCampus(context) },
                    onOpenOrders = { openOfficialOrders(context) },
                    onOpenAddress = { openOfficialAddress(context) },
                    onOpenShizuku = { openShizukuManager(context) },
                )

                CampusPage.IlifeProfile -> IlifeProfileScreen(
                    onBack = { page = detailOrigin },
                    onOpenAppearance = { openSettings(CampusPage.IlifeProfile) },
                    appThemeMode = themeMode,
                )

                CampusPage.Settings -> SettingsScreen(
                    themeMode = themeMode,
                    darkTheme = darkTheme,
                    enabledModules = enabledModules,
                    glassEnabled = glassEnabled,
                    onGlassEnabledChange = onGlassEnabledChange,
                    onThemeModeChange = onThemeModeChange,
                    onModuleToggle = ::setModuleEnabled,
                    onResetModules = ::resetModules,
                    backgroundImageUri = backgroundImageUri,
                    onPickBackgroundImage = onPickBackgroundImage,
                    onClearBackgroundImage = onClearBackgroundImage,
                    onBack = { page = settingsOrigin },
                )
            }
                            }
        }
        }

                }
            }

            AnimatedVisibility(
                visible = showTopActions,
                enter = fadeIn(tween(220)) + scaleIn(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    initialScale = 0.9f,
                ),
                exit = fadeOut(tween(140, easing = FastOutSlowInEasing)) + scaleOut(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    targetScale = 0.94f,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 20.dp, bottom = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    LiquidGlassTopActions(
                        showThemeToggle = page.depth == 0,
                        darkTheme = darkTheme,
                        enabled = themeReveal == null,
                        onThemeToggle = { center ->
                            if (themeReveal == null) {
                                val targetDark = !darkTheme
                                onThemeModeChange(
                                    if (targetDark) ThemeMode.DARK else ThemeMode.LIGHT
                                )
                                themeReveal = ThemeRevealSpec(
                                    id = System.nanoTime(),
                                    center = center,
                                    targetDark = targetDark,
                                )
                            }
                        },
                        onSettings = { openSettings(page) },
                    )
                }
            }

            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn(tween(220)) + slideInVertically(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 2 },
                ),
                exit = fadeOut(tween(140)) + slideOutVertically(
                    animationSpec = tween(200),
                    targetOffsetY = { it / 3 },
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                LiquidGlassBottomBar(
                    selectedIndex = when (page) {
                        CampusPage.Home -> 0
                        CampusPage.Features -> 1
                        CampusPage.MyHub,
                        CampusPage.TmallProfile,
                        -> 2
                        CampusPage.SmartDorm -> when (detailOrigin) {
                            CampusPage.Home -> 0
                            CampusPage.Features -> 1
                            else -> 2
                        }
                        CampusPage.IlifeProfile -> when (detailOrigin) {
                            CampusPage.Home -> 0
                            CampusPage.Features -> 1
                            else -> 2
                        }
                        CampusPage.Ujing -> when (ujingOrigin) {
                            CampusPage.Home -> 0
                            CampusPage.Features -> 1
                            else -> 2
                        }
                        CampusPage.Pangguai -> when (pangguaiOrigin) {
                            CampusPage.Home -> 0
                            CampusPage.Features -> 1
                            else -> 2
                        }
                        CampusPage.PerfectCampus -> when (perfectCampusOrigin) {
                            CampusPage.Home -> 0
                            CampusPage.Features -> 1
                            else -> 2
                        }
                        CampusPage.Settings -> settingsOrigin.let {
                            when (it) {
                                CampusPage.Features -> 1
                                CampusPage.MyHub -> 2
                                else -> 0
                            }
                        }
                    },
                    onSelected = { index ->
                        page = when (index) {
                            0 -> CampusPage.Home
                            1 -> CampusPage.Features
                            else -> CampusPage.MyHub
                        }
                    },
                    darkTheme = darkTheme,
                    modifier = Modifier,
                )
            }

            if (showLockPicker) {
                LockDefaultDialog(
                    locks = lockVm.locks,
                    selectedAddress = app.credentialStore.defaultAddress()
                        ?: app.credentialStore.primary()?.address,
                    onDismiss = { showLockPicker = false },
                    onSelect = { address ->
                        app.credentialStore.setPrimary(address)
                        lockVm.refresh()
                        lockVm.prewarm()
                        showLockPicker = false
                    },
                )
            }

    }
}
}

@Composable
private fun BackgroundImageLayer(uri: String?, darkTheme: Boolean) {
    if (uri.isNullOrBlank()) return
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))
                    ?.use(BitmapFactory::decodeStream)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let { image ->
        val scrim by animateColorAsState(
            targetValue = if (darkTheme) {
                Color.Black.copy(alpha = 0.48f)
            } else {
                Color.White.copy(alpha = 0.38f)
            },
            animationSpec = tween(360, easing = FastOutSlowInEasing),
            label = "background-image-scrim",
        )
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim)
        )
    }
}

@Composable
private fun CampusHome(
    onSmartDorm: () -> Unit,
    onIlife: () -> Unit,
    credential: LockCredential?,
    unlockState: UnlockState,
    onQuickUnlock: () -> Unit,
    onChangeDefaultLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onUjingScan: () -> Unit,
    onPangguaiScan: () -> Unit,
    enabledModules: Set<HomeModule>,
    darkTheme: Boolean,
) {
    val quickEnabled = HomeModule.QuickUnlock in enabledModules
    val quickWaterEnabled = HomeModule.QuickWater in enabledModules
    val ujingScanEnabled = HomeModule.UjingScanner in enabledModules
    val pangguaiScanEnabled = HomeModule.PangguaiScanner in enabledModules
    val quickCount = listOf(quickEnabled, quickWaterEnabled).count { it }
    val scanCount = listOf(ujingScanEnabled, pangguaiScanEnabled).count { it }
    val hasHomeAction = quickCount > 0 || scanCount > 0

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 70.dp)
            .padding(horizontal = 24.dp),
    ) {
        val compact = maxHeight < 700.dp
        val dense = maxHeight < 560.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = if (compact) 18.dp else 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasHomeAction) {
                Spacer(Modifier.weight(0.72f))
                Text(
                    "Cumpus",
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
                Spacer(Modifier.height(if (compact) 18.dp else 34.dp))
                EmptyHomeState(onOpenSettings = onOpenSettings)
            } else {
                Spacer(Modifier.weight(if (quickCount <= 1) 1.2f else 0.88f))
                if (quickCount > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (dense) 12.dp else 18.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (quickEnabled) {
                            QuickUnlockCircle(
                                credential = credential,
                                state = unlockState,
                                onUnlock = onQuickUnlock,
                                onManageLock = onSmartDorm,
                                onChangeDefaultLock = onChangeDefaultLock,
                                compact = compact,
                                darkTheme = darkTheme,
                                dense = dense,
                            )
                        }
                        if (quickWaterEnabled) {
                            HomeQuickWaterButton(
                                compact = compact,
                                darkTheme = darkTheme,
                                dense = dense,
                                onManage = onIlife,
                            )
                        }
                    }
                }
                if (scanCount > 0) {
                    Spacer(Modifier.height(if (compact) 14.dp else 22.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(if (scanCount == 1) 0.72f else 1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (pangguaiScanEnabled) {
                            HomeScanButton(
                                title = "扫一扫",
                                subtitle = "胖乖设备",
                                accent = MaterialTheme.colorScheme.secondary,
                                compact = compact,
                                onScan = onPangguaiScan,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (ujingScanEnabled) {
                            HomeScanButton(
                                title = "U净扫一扫",
                                subtitle = "U净设备",
                                accent = MaterialTheme.colorScheme.tertiary,
                                compact = compact,
                                onScan = onUjingScan,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(if (quickCount <= 1) 0.72f else 1f))
        }
    }
}

@Composable
private fun HomeScanButton(
    title: String,
    subtitle: String,
    accent: Color,
    compact: Boolean,
    onScan: () -> Unit,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = onScan,
        modifier = modifier
            .height(if (compact) 58.dp else 64.dp),
        shape = RoundedCornerShape(percent = 50),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        ScannerGlyph(accent)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(
                title,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                subtitle,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FeatureHubScreen(
    enabledModules: Set<HomeModule>,
    onSmartDorm: () -> Unit,
    onIlife: () -> Unit,
    onUjing: () -> Unit,
    onPangguai: () -> Unit,
    onPerfectCampus: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val modules = HomeModule.entries.filter {
        it.section == ModuleSection.FEATURES && it in enabledModules
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(top = 88.dp, start = 20.dp, end = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HubPageHeader(
            title = "功能",
            subtitle = "校园服务集中入口",
        )

        if (modules.isEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSettings)
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("功能页暂无模块", fontWeight = FontWeight.Bold)
                    Text(
                        "点右上角设置，选择要显示的功能模块",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        } else {
            modules.forEach { module ->
                val title = module.title
                val subtitle = module.description
                val badge = when (module) {
                    HomeModule.SmartDorm -> "舍"
                    HomeModule.Ilife -> "慧"
                    HomeModule.Ujing -> "洗"
                    HomeModule.Pangguai -> "胖"
                    HomeModule.PerfectCampus -> "完"
                    else -> "·"
                }
                val container = when (module) {
                    HomeModule.SmartDorm -> MaterialTheme.colorScheme.primaryContainer
                    HomeModule.Ilife -> MaterialTheme.colorScheme.secondaryContainer
                    HomeModule.Ujing -> MaterialTheme.colorScheme.tertiaryContainer
                    HomeModule.Pangguai -> MaterialTheme.colorScheme.surfaceVariant
                    HomeModule.PerfectCampus -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val content = when (module) {
                    HomeModule.SmartDorm -> MaterialTheme.colorScheme.onPrimaryContainer
                    HomeModule.Ilife -> MaterialTheme.colorScheme.onSecondaryContainer
                    HomeModule.Ujing -> MaterialTheme.colorScheme.onTertiaryContainer
                    HomeModule.Pangguai -> MaterialTheme.colorScheme.onSurfaceVariant
                    HomeModule.PerfectCampus -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                MyHubEntryCard(
                    title = title,
                    subtitle = subtitle,
                    badge = badge,
                    container = container,
                    contentColor = content,
                    onClick = when (module) {
                        HomeModule.SmartDorm -> onSmartDorm
                        HomeModule.Ilife -> onIlife
                        HomeModule.Ujing -> onUjing
                        HomeModule.Pangguai -> onPangguai
                        HomeModule.PerfectCampus -> onPerfectCampus
                        else -> ({})
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HubPageHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModuleFeatureCard(
    module: HomeModule,
    modifier: Modifier,
    height: Dp,
    darkTheme: Boolean,
    onSmartDorm: () -> Unit,
    onIlife: () -> Unit,
    dense: Boolean,
) {
    val accent = when (module) {
        HomeModule.SmartDorm -> if (darkTheme) MaterialTheme.colorScheme.primary else Color(0xFF25735F)
        HomeModule.Ilife -> if (darkTheme) Color(0xFF8AB4F8) else Color(0xFF315F9C)
        HomeModule.Ujing -> if (darkTheme) Color(0xFFC7A7FF) else Color(0xFF6E4AB0)
        HomeModule.Pangguai -> if (darkTheme) Color(0xFF78D8CE) else Color(0xFF087E76)
        HomeModule.PerfectCampus -> if (darkTheme) Color(0xFFF0C36A) else Color(0xFF93610A)
        HomeModule.QuickUnlock -> MaterialTheme.colorScheme.primary
        HomeModule.QuickWater -> MaterialTheme.colorScheme.secondary
        HomeModule.UjingScanner -> MaterialTheme.colorScheme.tertiary
        HomeModule.PangguaiScanner -> MaterialTheme.colorScheme.secondary
    }
    val container = when (module) {
        HomeModule.SmartDorm -> if (darkTheme) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE7F5F0)
        HomeModule.Ilife -> if (darkTheme) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE8EEFB)
        HomeModule.Ujing -> if (darkTheme) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF2ECFF)
        HomeModule.Pangguai -> MaterialTheme.colorScheme.surfaceVariant
        HomeModule.PerfectCampus -> MaterialTheme.colorScheme.surfaceVariant
        HomeModule.QuickUnlock -> MaterialTheme.colorScheme.surfaceVariant
        HomeModule.QuickWater -> MaterialTheme.colorScheme.surfaceVariant
        HomeModule.UjingScanner -> MaterialTheme.colorScheme.surfaceVariant
        HomeModule.PangguaiScanner -> MaterialTheme.colorScheme.surfaceVariant
    }
    val title = when (module) {
        HomeModule.SmartDorm -> "智能宿舍"
        HomeModule.Ilife -> "慧生活"
        HomeModule.Ujing -> "U净"
        HomeModule.Pangguai -> "胖乖生活"
        HomeModule.PerfectCampus -> "完美校园"
        HomeModule.QuickUnlock -> "一键开门"
        HomeModule.QuickWater -> "一键接水"
        HomeModule.UjingScanner -> "U净扫一扫"
        HomeModule.PangguaiScanner -> "扫一扫"
    }
    val subtitle = when (module) {
        HomeModule.SmartDorm -> "门锁 · 饮水"
        HomeModule.Ilife -> "钱包 · 积分 · 账单"
        HomeModule.Ujing -> "洗衣 · 洗鞋 · 烘干"
        HomeModule.Pangguai -> "扫码洗护 · 历史订单"
        HomeModule.PerfectCampus -> "绑定房间 · 水电用量"
        HomeModule.QuickUnlock -> "快速开门"
        HomeModule.QuickWater -> "默认饮水机"
        HomeModule.UjingScanner -> "扫描机身二维码"
        HomeModule.PangguaiScanner -> "扫描胖乖设备"
    }
    val onClick: () -> Unit = when (module) {
        HomeModule.SmartDorm -> ({ onSmartDorm() })
        HomeModule.Ilife -> ({ onIlife() })
        HomeModule.Ujing -> ({})
        HomeModule.Pangguai -> ({})
        HomeModule.PerfectCampus -> ({})
        HomeModule.QuickUnlock -> ({ onSmartDorm() })
        HomeModule.QuickWater -> ({ onIlife() })
        HomeModule.UjingScanner -> ({})
        HomeModule.PangguaiScanner -> ({})
    }
    FeatureCard(
        modifier = modifier,
        height = height,
        darkTheme = darkTheme,
        icon = { color ->
            when (module) {
                HomeModule.SmartDorm -> SmartDormGlyph(color)
                HomeModule.Ilife -> IlifeGlyph(color)
                HomeModule.Ujing -> UjingGlyph(color)
                HomeModule.Pangguai -> ScannerGlyph(color)
                HomeModule.PerfectCampus -> WaterDropGlyph(color)
                HomeModule.QuickUnlock -> SmartDormGlyph(color)
                HomeModule.QuickWater -> WaterDropGlyph(color)
                HomeModule.UjingScanner -> ScannerGlyph(color)
                HomeModule.PangguaiScanner -> ScannerGlyph(color)
            }
        },
        title = title,
        subtitle = subtitle,
        accent = accent,
        container = container,
        onClick = onClick,
        dense = dense,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickUnlockCircle(
    credential: LockCredential?,
    state: UnlockState,
    onUnlock: () -> Unit,
    onManageLock: () -> Unit,
    onChangeDefaultLock: () -> Unit,
    compact: Boolean,
    darkTheme: Boolean,
    dense: Boolean,
) {
    val working = state is UnlockState.Working
    val hasCredential = credential != null
    val interaction = remember { MutableInteractionSource() }
    val catalogInteraction = rememberCatalogGlassInteraction()
    val haptic = LocalHapticFeedback.current
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
    val denseSubtitle = when {
        !hasCredential -> "点此设置"
        working -> "连接中"
        state is UnlockState.Success -> "已开门"
        state is UnlockState.Failure -> "重试"
        else -> credential.name
    }
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val useNativeGlass = glassEnabled &&
        backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val surfaceModifier = if (useNativeGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(5.dp.toPx())
                lens(
                    refractionHeight = 20.dp.toPx(),
                    refractionAmount = 36.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true,
                )
            },
            highlight = { Highlight.Ambient.copy(alpha = 0.92f) },
            shadow = {
                Shadow.Default.copy(
                    radius = 22.dp,
                    color = Color.Black.copy(alpha = 0.24f),
                )
            },
            layerBlock = {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            },
            onDrawSurface = {
                drawRect(containerTopColor.copy(alpha = if (darkTheme) 0.14f else 0.12f))
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            containerTopColor.copy(alpha = if (darkTheme) 0.58f else 0.62f),
                            containerBottomColor.copy(alpha = if (darkTheme) 0.38f else 0.44f),
                        )
                    ),
                    radius = size.minDimension * 0.43f,
                    center = center,
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.28f, 0f),
                        radius = size.maxDimension * 0.85f,
                    )
                )
            },
        )
    } else if (glassEnabled) {
        Modifier
            .graphicsLayer {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            }
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
    } else {
        Modifier
            .graphicsLayer {
                catalogPressTransform(catalogInteraction, 1.04f)
            }
            .clip(CircleShape)
            .background(containerTopColor)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 7.dp),
    ) {
        Box(
            Modifier
                .size(if (dense) 96.dp else if (compact) 132.dp else 154.dp)
                .then(surfaceModifier)
                .clip(CircleShape)
                .combinedClickable(
                    enabled = !working,
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = if (hasCredential) "开锁" else "设置钥匙",
                    onLongClickLabel = "更改默认门锁",
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChangeDefaultLock()
                    },
                    onClick = {
                    if (hasCredential) onUnlock() else onManageLock()
                    },
                )
                .then(catalogInteraction.modifier)
                .then(catalogInteraction.gestureModifier)
                .padding(if (dense) 10.dp else 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (working) "蓝牙直连中" else "蓝牙门锁",
                    fontSize = if (dense) 8.sp else if (compact) 9.sp else 10.sp,
                    color = foregroundColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    fontSize = if (dense) 15.sp else if (compact) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = foregroundColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (dense) denseSubtitle else subtitle,
                    fontSize = if (dense) 7.sp else if (compact) 8.sp else 9.sp,
                    lineHeight = if (dense) 9.sp else if (compact) 10.sp else 12.sp,
                    color = foregroundColor,
                    textAlign = TextAlign.Center,
                    maxLines = if (dense) 1 else 2,
                )
            }
        }
        if (!dense) {
            Text(
                if (hasCredential) "点击圆钮即可开门" else "添加一次钥匙，以后可离线开门",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeQuickWaterButton(
    compact: Boolean,
    darkTheme: Boolean,
    dense: Boolean,
    onManage: () -> Unit,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val catalogInteraction = rememberCatalogGlassInteraction()
    val haptic = LocalHapticFeedback.current
    var revision by remember { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }
    val account = remember(revision) { AccountStore.getCurrent(context) }
    val deviceId = account?.selectedDeviceId().orEmpty()
    val deviceName = account?.deviceDisplayName(deviceId).orEmpty()
    val control by WaterControlState.state.collectAsStateWithLifecycle()
    val controlState = control
    val starting = controlState?.deviceId == deviceId && controlState.starting
    val running = controlState?.deviceId == deviceId && !controlState.starting
    val title = when {
        starting -> "启动中"
        running -> "接水中"
        else -> "一键接水"
    }
    val subtitle = when {
        deviceId.isBlank() -> "点此配置设备"
        starting -> "正在连接饮水机"
        running -> "点击物理按键接水"
        else -> deviceName
    }
    val circleSize = if (dense) 96.dp else if (compact) 132.dp else 154.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 7.dp),
    ) {
        Box(
            Modifier
                .size(circleSize)
                .graphicsLayer {
                    catalogPressTransform(catalogInteraction, 1.04f)
                }
                .clip(CircleShape)
                .semantics {
                    contentDescription = "一键接水"
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = "启动默认饮水机",
                    onLongClickLabel = "更改默认饮水机",
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showPicker = true
                    },
                    onClick = {
                        if (deviceId.isBlank()) {
                            onManage()
                        } else {
                            when (
                                WaterService.start(
                                    context,
                                    deviceId,
                                    android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID,
                                )
                            ) {
                                WaterService.StartResult.STARTED -> Unit
                                WaterService.StartResult.ALREADY_RUNNING ->
                                    Toast.makeText(context, "饮水机已在运行", Toast.LENGTH_SHORT).show()
                                WaterService.StartResult.FAILED ->
                                    Toast.makeText(context, "启动失败，请检查登录与设备", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
                .then(catalogInteraction.gestureModifier),
        ) {
            LiquidGlassPanel(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    if (dense) 10.dp else 18.dp
                ),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    WaterDropGlyph(
                        if (darkTheme) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        title,
                        fontSize = if (dense) 15.sp else if (compact) 18.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        subtitle,
                        fontSize = if (dense) 7.sp else if (compact) 8.sp else 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Box(Modifier.matchParentSize().then(catalogInteraction.modifier))
        }
        if (!dense) {
            Text(
                "点击直接接水 · 长按换设备",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPicker) {
        val devices = account?.rememberedDevices().orEmpty()
        GlassPickerDialog(
            title = "更改默认饮水机",
            onDismiss = { showPicker = false },
        ) {
            if (devices.isEmpty()) {
                Text("还没有饮水机，请先在慧生活设备页点击“+”添加。")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    devices.forEach { id ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    account.selectDevice(id)
                                    AccountStore.updateCurrent(context, account)
                                    revision++
                                    showPicker = false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = id == deviceId, onClick = null)
                            Text(
                                account.deviceDisplayName(id),
                                Modifier.weight(1f),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockDefaultDialog(
    locks: List<LockCredential>,
    selectedAddress: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    GlassPickerDialog(
        title = "更改默认门锁",
        onDismiss = onDismiss,
    ) {
        if (locks.isEmpty()) {
            Text("还没有门锁凭证，请先进入“智能宿舍”添加。")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "首页开锁圆钮将使用选中的门锁。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                locks.forEach { lock ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(lock.address) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = lock.address == selectedAddress,
                            onClick = null,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(lock.name, fontWeight = FontWeight.Medium)
                            Text(
                                maskAddress(lock.address),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(20.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    content()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                }
            }
        }
    }
}

private fun maskAddress(address: String): String =
    if (address.length <= 8) "已保存门锁"
    else "${address.take(4)} ··· ${address.takeLast(4)}"

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
    dense: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val catalogInteraction = rememberCatalogGlassInteraction()
    val bottomColor = if (darkTheme) {
        lerp(container, Color.Black, 0.18f)
    } else {
        lerp(container, MaterialTheme.colorScheme.background, 0.16f)
    }
    val shape = RoundedCornerShape(if (dense) 20.dp else 28.dp)
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val useNativeGlass = glassEnabled &&
        backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val surfaceModifier = if (useNativeGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                vibrancy()
                blur(4.dp.toPx())
                lens(
                    refractionHeight = 16.dp.toPx(),
                    refractionAmount = 28.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true,
                )
            },
            highlight = { Highlight.Ambient },
            shadow = {
                Shadow.Default.copy(
                    radius = 15.dp,
                    color = Color.Black.copy(alpha = 0.16f),
                )
            },
            layerBlock = {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            },
            onDrawSurface = {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            container.copy(alpha = if (darkTheme) 0.34f else 0.34f),
                            bottomColor.copy(alpha = if (darkTheme) 0.18f else 0.2f),
                        )
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.18f, 0f),
                        radius = size.maxDimension * 0.85f,
                    )
                )
            },
        )
    } else if (glassEnabled) {
        Modifier
            .graphicsLayer {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(container, bottomColor)))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                shape,
            )
    } else {
        Modifier
            .graphicsLayer {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            }
            .clip(shape)
            .background(container)
    }
    Box(
        modifier
            .height(height)
            .then(surfaceModifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .then(catalogInteraction.clippedModifier(shape))
            .then(catalogInteraction.gestureModifier)
            .padding(if (dense) 10.dp else 18.dp),
    ) {
        if (dense) {
            val iconSize = if (height < 120.dp) 36.dp else 46.dp
            val titleSize = if (height < 120.dp) 15.sp else 17.sp
            val subtitleSize = if (height < 120.dp) 10.sp else 11.sp
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LiquidGlassIconDisc(discSize = iconSize) {
                    icon(accent)
                }
                Spacer(Modifier.height(if (height < 120.dp) 4.dp else 7.dp))
                Text(
                    title,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(if (height < 120.dp) 2.dp else 3.dp))
                Text(
                    subtitle,
                    fontSize = subtitleSize,
                    color = accent.copy(alpha = if (darkTheme) 0.9f else 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LiquidGlassIconDisc(discSize = 68.dp) {
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
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = accent.copy(alpha = if (darkTheme) 0.9f else 0.95f),
                )
            }
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
private fun IlifeGlyph(color: Color) {
    Canvas(Modifier.size(32.dp)) {
        val stroke = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.16f, size.height * 0.22f),
            size = Size(size.width * 0.68f, size.height * 0.56f),
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = stroke,
        )
        drawCircle(
            color = color,
            radius = 2.2.dp.toPx(),
            center = Offset(size.width * 0.34f, size.height * 0.58f),
        )
        drawLine(
            color = color.copy(alpha = 0.72f),
            start = Offset(size.width * 0.45f, size.height * 0.45f),
            end = Offset(size.width * 0.72f, size.height * 0.45f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color.copy(alpha = 0.72f),
            start = Offset(size.width * 0.45f, size.height * 0.6f),
            end = Offset(size.width * 0.64f, size.height * 0.6f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun UjingGlyph(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.16f, size.height * 0.12f),
            size = Size(size.width * 0.68f, size.height * 0.76f),
            cornerRadius = CornerRadius(size.width * 0.1f),
            style = stroke,
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.19f,
            center = Offset(size.width * 0.5f, size.height * 0.57f),
            style = stroke,
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.035f,
            center = Offset(size.width * 0.68f, size.height * 0.25f),
        )
    }
}

@Composable
private fun WaterDropGlyph(color: Color) {
    Canvas(Modifier.size(32.dp)) {
        val stroke = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val drop = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.12f)
            cubicTo(
                size.width * 0.24f,
                size.height * 0.42f,
                size.width * 0.18f,
                size.height * 0.58f,
                size.width * 0.18f,
                size.height * 0.7f,
            )
            cubicTo(
                size.width * 0.18f,
                size.height * 0.88f,
                size.width * 0.33f,
                size.height * 0.94f,
                size.width * 0.5f,
                size.height * 0.94f,
            )
            cubicTo(
                size.width * 0.67f,
                size.height * 0.94f,
                size.width * 0.82f,
                size.height * 0.88f,
                size.width * 0.82f,
                size.height * 0.7f,
            )
            cubicTo(
                size.width * 0.82f,
                size.height * 0.58f,
                size.width * 0.76f,
                size.height * 0.42f,
                size.width * 0.5f,
                size.height * 0.12f,
            )
            close()
        }
        drawPath(drop, color, style = stroke)
        drawArc(
            color = color.copy(alpha = 0.78f),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.3f, size.height * 0.56f),
            size = Size(size.width * 0.4f, size.height * 0.22f),
            style = stroke,
        )
    }
}

@Composable
private fun NfcGlyph(color: Color) {
    Canvas(Modifier.size(32.dp)) {
        val stroke = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(
            color = color,
            radius = 1.8.dp.toPx(),
            center = Offset(size.width * 0.27f, size.height * 0.54f),
        )
        listOf(0.48f, 0.66f, 0.84f).forEachIndexed { index, fraction ->
            drawArc(
                color = color.copy(alpha = 1f - index * 0.16f),
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(
                    size.width * (fraction - 0.42f),
                    size.height * (0.52f - fraction),
                ),
                size = Size(size.width * fraction * 0.72f, size.height * fraction * 0.96f),
                style = stroke,
            )
        }
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
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
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
private fun MyHubScreen(
    onTmall: () -> Unit,
    onIlife: () -> Unit,
    onUjing: () -> Unit,
    onPangguai: () -> Unit,
    onPerfectCampus: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(top = 88.dp, start = 20.dp, end = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HubPageHeader(
            title = "我的",
            subtitle = "选择要进入的账号与服务",
        )

        MyHubEntryCard(
            title = "天猫校园",
            subtitle = "登录态、订单、地址与 Shizuku 同步",
            badge = "天猫",
            container = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onTmall,
        )
        MyHubEntryCard(
            title = "慧生活",
            subtitle = "账户、积分、钱包、消费记录与自动任务",
            badge = "慧",
            container = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onIlife,
        )
        MyHubEntryCard(
            title = "U净账号",
            subtitle = "洗衣机账号、已收藏设备与进行中的订单",
            badge = "洗",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onUjing,
        )
        MyHubEntryCard(
            title = "胖乖生活",
            subtitle = "扫码洗护、最近设备与历史订单",
            badge = "胖",
            container = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onPangguai,
        )
        MyHubEntryCard(
            title = "完美校园",
            subtitle = "账号绑定房间与水电用量只读查询",
            badge = "完",
            container = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onPerfectCampus,
        )

        Text(
            "各平台分别登录、分别保存。敏感信息只保存在本机加密存储中。",
            modifier = Modifier.padding(horizontal = 4.dp),
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MyHubEntryCard(
    title: String,
    subtitle: String,
    badge: String,
    container: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val catalogInteraction = rememberCatalogGlassInteraction()
    val shape = RoundedCornerShape(24.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val backdrop = LocalLiquidGlassBackdrop.current
    val glassEnabled = LocalLiquidGlassEnabled.current
    val useNativeGlass = glassEnabled &&
        backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val surfaceModifier = if (useNativeGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                vibrancy()
                blur(4.dp.toPx())
                lens(
                    refractionHeight = 16.dp.toPx(),
                    refractionAmount = 28.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = true,
                )
            },
            highlight = { Highlight.Ambient },
            shadow = {
                Shadow.Default.copy(
                    radius = 16.dp,
                    color = Color.Black.copy(alpha = 0.18f),
                )
            },
            layerBlock = {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            },
            onDrawSurface = {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            container.copy(alpha = 0.3f),
                            lerp(container, surfaceColor, 0.42f)
                                .copy(alpha = 0.18f),
                        )
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.18f, 0f),
                        radius = size.maxDimension * 0.85f,
                    )
                )
            },
        )
    } else if (glassEnabled) {
        Modifier
            .graphicsLayer {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            }
            .shadow(
                elevation = 16.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = contentColor.copy(alpha = 0.18f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        container.copy(alpha = 0.92f),
                        lerp(container, surfaceColor, 0.3f)
                            .copy(alpha = 0.76f),
                    )
                )
            )
            .border(
                1.dp,
                Color.White.copy(alpha = 0.22f),
                shape,
            )
    } else {
        Modifier
            .graphicsLayer {
                catalogPressTransform(
                    catalogInteraction,
                    1f + 4.dp.toPx() / size.height.coerceAtLeast(1f),
                )
            }
            .clip(shape)
            .background(container)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .then(surfaceModifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .then(catalogInteraction.clippedModifier(shape))
            .then(catalogInteraction.gestureModifier),
    ) {
        if (glassEnabled) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.Transparent,
                        ),
                        endY = size.height * 0.64f,
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            contentColor.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.18f, size.height * 0.5f),
                        radius = size.height * 0.95f,
                    ),
                    radius = size.height * 0.95f,
                    center = Offset(size.width * 0.18f, size.height * 0.5f),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LiquidGlassIconDisc(discSize = 58.dp) {
                Text(
                    badge,
                    fontSize = if (badge.length == 1) 21.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = contentColor.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", fontSize = 28.sp, color = contentColor.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun IlifeProfileScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    appThemeMode: ThemeMode,
) {
    val context = LocalContext.current
    val viewModel: DashboardViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshData() {
        viewModel.reloadAccounts(resetScore = true)
        val account = AccountStore.getCurrent(context.applicationContext) ?: return
        val token = account.token?.takeIf(String::isNotBlank)
            ?: account.appToken?.takeIf(String::isNotBlank)
            ?: return
        val useApp = !account.hasToken()
        IlifeApi.missionLstWithToken(token, useApp) { json, _ ->
            LoginStatusStore.record(account, useApp, TokenValidity.from(json))
            val score = json?.takeIf { it.optInt("code", -1) == 0 }
                ?.optJSONObject("data")
                ?.optJSONObject("accScoreRsp")
                ?.optInt("validScore")
            viewModel.setCurrentScoreData(score = score)
        }
        IlifeApi.scoreLstWithToken(token) { json, _ ->
            viewModel.setCurrentScoreData(logs = json)
        }
        val walletToken = account.appToken?.takeIf(String::isNotBlank) ?: token
        IlifeApi.walletOwnerWithToken(walletToken) { json, error ->
            val balance = runCatching {
                json?.let { WalletResponseParser.dashboardBalance(it, account.eid) }
            }.getOrNull()
            viewModel.setCurrentWalletData(
                balance,
                if (balance == null) error ?: "钱包刷新失败，请稍后重试" else null,
            )
        }
    }

    LaunchedEffect(Unit) { refreshData() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshData()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val libraryTheme = when (appThemeMode) {
        ThemeMode.SYSTEM -> HuishengThemeMode.SYSTEM
        ThemeMode.LIGHT -> HuishengThemeMode.LIGHT
        ThemeMode.DARK -> HuishengThemeMode.DARK
    }
    LaunchedEffect(appThemeMode) {
        ThemeSettings.setMode(context.applicationContext, libraryTheme)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageHeader(title = "慧生活我的", onBack = onBack)
        HuishengMineContent(
            state = state.summary,
            onLogin = {
                val account = AccountStore.getCurrent(context.applicationContext)
                val intent = if (account?.phone.isNullOrBlank()) {
                    Intent(context, LoginActivity::class.java)
                } else {
                    LoginActivity.reloginIntent(context, account.phone, true)
                }
                context.startActivity(intent)
            },
            onAccounts = { context.startActivity(Intent(context, AccountsActivity::class.java)) },
            onScores = { context.startActivity(Intent(context, ScoreActivity::class.java)) },
            onWallet = { context.startActivity(Intent(context, WalletActivity::class.java)) },
            themeMode = libraryTheme,
            onOpenAppearance = onOpenAppearance,
            modifier = Modifier.weight(1f),
            liquidGlassBackdrop = LocalLiquidGlassBackdrop.current,
            liquidGlassEnabled = LocalLiquidGlassEnabled.current,
        )
    }
}

@Composable
private fun ProfileScreen(
    waterVm: WaterViewModel,
    onBack: () -> Unit,
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
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiquidGlassHeader(
            title = "天猫校园我的",
            subtitle = "登录、订单与地址",
            onBack = onBack,
        )

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

        LiquidGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
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
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
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
    glassEnabled: Boolean,
    onGlassEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onModuleToggle: (HomeModule, Boolean) -> Unit,
    onResetModules: () -> Unit,
    backgroundImageUri: String?,
    onPickBackgroundImage: () -> Unit,
    onClearBackgroundImage: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LiquidGlassHeader(
            title = "界面设置",
            subtitle = "主页、功能页与应用外观",
            onBack = onBack,
        )

        SettingsCard(
            title = "液态玻璃",
            subtitle = "开启后使用背景模糊、折射和高光；关闭后采用纯色平面。",
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onGlassEnabledChange(!glassEnabled) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("液态玻璃特效", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (glassEnabled) "已开启" else "已关闭",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CatalogLiquidSwitch(
                    checked = glassEnabled,
                    onCheckedChange = onGlassEnabledChange,
                )
            }
        }

        SettingsCard(
            title = "背景图片",
            subtitle = "选择本机图片作为主页、功能页和我的页背景；图片只在本机读取。",
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (backgroundImageUri == null) "当前使用默认背景" else "已设置自定义背景",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPickBackgroundImage,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("选择图片")
                    }
                    OutlinedButton(
                        onClick = onClearBackgroundImage,
                        enabled = backgroundImageUri != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("恢复默认")
                    }
                }
            }
        }

        SettingsCard(
            title = "主页模块",
            subtitle = "控制主页快捷动作与扫码入口；功能入口统一放在功能页。",
        ) {
            val homeModules = HomeModule.entries.filter { it.section == ModuleSection.HOME }
            homeModules.forEachIndexed { index, module ->
                ModuleToggleRow(
                    title = module.title,
                    subtitle = module.description,
                    checked = module in enabledModules,
                    onCheckedChange = {
                        onModuleToggle(module, it)
                    },
                )
                if (index != homeModules.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    )
                }
            }
        }

        SettingsCard(
            title = "功能页模块",
            subtitle = "关闭后功能页会隐藏对应入口；主页快捷动作不会在这里重复出现。",
        ) {
            val featureModules = HomeModule.entries.filter { it.section == ModuleSection.FEATURES }
            featureModules.forEachIndexed { index, module ->
                ModuleToggleRow(
                    title = module.title,
                    subtitle = module.description,
                    checked = module in enabledModules,
                    onCheckedChange = {
                        onModuleToggle(module, it)
                    },
                )
                if (index != featureModules.lastIndex) {
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
                Text("恢复全部模块")
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
            subtitle = "Cumpus ${BuildConfig.VERSION_NAME}",
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
    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
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
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
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
        CatalogLiquidSwitch(
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
    LiquidGlassHeader(
        title = title,
        subtitle = "返回上一级",
        onBack = onBack,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
