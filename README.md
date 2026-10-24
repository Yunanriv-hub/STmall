<div align="center">
  <img src="docs/images/stmall-icon.png" width="112" alt="STmall icon">

  <h1>STmall</h1>

  <p>面向校园高频场景的独立 Android 客户端</p>

  <p>
    <a href="https://github.com/Yunanriv-hub/STmall/releases/latest">下载最新版</a> ·
    <a href="docs/USAGE.md">使用教程</a> ·
    <a href="docs/CHANGELOG.md">更新记录</a> ·
    <a href="DISCLAIMER.md">免责声明</a>
  </p>
</div>

> **STmall 是非官方个人项目，与天猫、淘宝、阿里巴巴及其关联公司没有隶属、
> 授权或合作关系。请仅使用自己的账号，并只操作自己有权使用的门锁和设备。**

STmall 将智能宿舍、校园饮水、扫一扫和蓝牙门锁等高频入口集中到一个无广告
首页，同时保留官方天猫校园的账号、订单与地址能力。项目不会修改、注入或
重新签名官方应用。

## 界面预览

<p align="center">
  <img src="docs/images/home-dark.png" width="320" alt="STmall 首页纯黑模式">
</p>

## 主要功能

- 首页只保留“首页”和“我的”两个主入口，支持日间、跟随系统和纯黑暗黑模式。
- “智能宿舍”“扫一扫”“一键开门”模块可独立显示或隐藏，首页自动重排。
- 自绘矢量图标与自适应布局，兼容不同屏幕尺寸、字体缩放和系统栏高度。
- 底部液态玻璃导航栏使用单层纵向玻璃背景、圆角高光、弹性胶囊和按压反馈。
- 支持从本机官方天猫校园导入已有登录态，用于校园身份校验与饮水服务。
- 支持校园饮水设备识别、只读状态查询与接水操作。
- 支持抓取自己门锁的凭证、Keystore 加密保存、离线蓝牙开门和电量查询。
- “我的订单”“地址管理”通过官方深链打开对应官方页面。
- 提供下拉快捷磁贴和桌面开锁快捷方式。
- 登录 Cookie 与门锁凭证不进入系统备份，源码和 Release 不包含真实凭证。

## 系统要求

- Android 8.0 或更高版本，`minSdk 26`。
- 设备支持 Bluetooth LE；门锁功能还要求手机蓝牙已开启并位于门锁附近。
- 官方天猫校园客户端现有可用账号。
- Shizuku 用于首次同步登录态、读取日志或抓取门锁凭证。
- 不需要已保存凭证后的日常离线开门权限，也不要求修改 Root 或系统分区。

## 快速开始

1. 从仓库右侧的 **Releases** 下载最新版 `STmall_*.apk` 并安装。
2. 安装并登录官方天猫校园。
3. 安装 Shizuku，根据系统版本通过 USB ADB 或无线调试启动它。
4. 在 Shizuku 中授权包名 `com.rea.campuslite`。
5. 打开 STmall，在“我的”中同步官方登录。
6. 进入“智能宿舍”，按需配置门锁凭证或选择饮水设备。

完整的无 Root 登录导入、门锁首次录入、饮水扫码和故障排查步骤见
[使用教程](docs/USAGE.md)。

## 无 Root 登录态导入

部分厂商的 WebView 不生成可由 Shizuku 直接读取的 Cookie 数据库。此时可运行
仓库内的辅助脚本，从官方 WebView 的只读 DevTools 通道导入登录态：

```powershell
.\tools\ImportNoRoot.ps1 `
  -Adb "$env:ANDROID_HOME\platform-tools\adb.exe" `
  -Serial "<手机序列号>"
```

脚本不会在终端输出 Cookie 值，但会在手机临时路径写入导入文件。同步完成后
应立即删除：

```powershell
adb -s <手机序列号> shell rm -f /data/local/tmp/campus_lite_cookies.json
```

## 门锁凭证与离线开门

首次使用门锁时：

1. 打开“智能宿舍 → 门锁”，点“开始抓取”。
2. 切回官方天猫校园，在蓝牙已开启且靠近自己门锁时，完成一次合法开锁。
3. 回到 STmall，确认抓到的门锁地址和 `activationKey` 已自动填入。
4. 保存凭证。之后即可在本机离线连接并开锁。

`activationKey` 等同于门锁钥匙，不应截图、公开、上传或发送给他人。请勿向
不属于自己的设备发送开锁帧。

## 构建

构建环境：

- JDK 21
- Android SDK 35 或更高
- Gradle Wrapper 8.11.1
- Node.js 仅在运行 WebView Cookie 导入工具时需要

```powershell
git clone https://github.com/Yunanriv-hub/STmall.git
Set-Location STmall

$env:JAVA_HOME = '<JDK 21 安装目录>'
$env:ANDROID_HOME = '<Android SDK 目录>'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-home'

.\android\gradlew.bat `
  -p android `
  testReleaseUnitTest `
  assembleRelease `
  --no-daemon `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

产物路径：

```text
android/app/build/outputs/apk/release/app-release.apk
```

仓库不包含签名密钥。自行构建发布包时，请创建本地
`android/keystore.properties` 并按自己的密钥签名；该文件、JKS/Keystore
和构建产物均已加入 `.gitignore`。

## 当前版本

| 项目 | 值 |
|---|---|
| 版本 | `0.2.6` |
| versionCode | `14` |
| 包名 | `com.rea.campuslite` |
| minSdk | `26` |
| targetSdk | `36` |
| Release APK | `STmall_0.2.6.apk` |
| SHA-256 | `B4793226D11372C422CDC5D7AF29A3740D3AAC78DE3915A39BD45D29F92CEF70` |

该版本已通过 67 项 JVM 单元测试、release 编译、Android Lint 和 v2 签名校验。

## 兼容性说明

- 已在 OnePlus PLQ110、Android 16、1080×2378 和 720×1280 小屏参数下验证
  首页布局、主题切换、底部导航、模块开关和官方页面跳转。
- 使用系统字体缩放 1.3 验证过文字不重叠、不被截断。
- 已在 HONOR `Hera-BD00`、Android 11、无 Root 环境验证 Shizuku 启动、
  登录态导入、校园身份检查、饮水只读查询和门锁结果回传。
- 不同厂商的窗口管理、状态栏高度、后台限制和 WebView 实现可能不同；
  本项目不能保证覆盖所有 Android ROM。

## 隐私与安全

- 本项目不附带账号、Cookie、`activationKey`、手机号、签名密钥或设备数据。
- 登录 Cookie 和门锁凭证由 Android Keystore 加密，并明确排除系统备份。
- 抓取服务只面向本机官方天猫校园和用户已授权的 Shizuku 通道。
- 请勿在 Issue、讨论区或截图中公开 Cookie、`activationKey`、完整手机号、
  门锁序列号或设备日志。安全问题请遵循 [SECURITY.md](SECURITY.md)。
- “清除本机登录态”只删除 STmall 本地保存的会话，不修改官方应用。

## 已知限制

- STmall 不提供伪造登录、支付、下单或绕过官方权限的能力。
- 订单页要求官方账号登录态有效；失效时可能先进入官方登录页。
- 校园饮水和订单接口可能随服务端更新而变化。
- 蓝牙连接会受到距离、电量、系统后台策略和门锁固件状态影响。
- 已保存凭证可用于离线开门，但手机重启后若需重新同步或抓取，仍要重新
  启动 Shizuku。

## 参考项目与地址

门锁协议、日志抓取方式和校园饮水接口的研究参考：

- TMallKey：<https://github.com/lmc2007/TMallKey>
- TMallKey 文档：<https://lmc2007.github.io/TMallKey/>

液态玻璃交互和底栏视觉原则参考：

- AndroidLiquidGlass：<https://github.com/Kyant0/AndroidLiquidGlass>

应用图标由项目提供者提供，图标来源标注为：

- Unoline Icons Set：<https://iconstore.co/icons/unoline-icons-set>

## 第三方许可

- TMallKey 参考项目采用 MIT License，许可文本见
  [LICENSE-TMallKey](LICENSE-TMallKey)。
- AndroidLiquidGlass 参考项目采用 Apache License 2.0，许可文本见
  [LICENSE-AndroidLiquidGlass](LICENSE-AndroidLiquidGlass)。
- 第三方项目名称、商标和图标权利归各自权利人所有。
- 本仓库未对 STmall 自有代码额外授予项目级开源许可证；如需再分发或商用，
  请先确认你自己拥有相应授权，并遵守所有第三方许可及服务条款。

## 免责声明

本项目仅用于个人学习和本人设备管理。使用者必须遵守所在地法律法规、官方
平台服务条款、学校宿舍管理规定以及设备所有者的授权范围。

本项目与天猫、淘宝、阿里巴巴、学校、门锁厂商及 Shizuku 项目无隶属或授权
关系。作者不保证功能的持续性、准确性、安全性或适销性，也不对因使用或无法
使用本项目造成的账号受限、数据丢失、门锁故障、财产损失或人身伤害承担任何
责任。请勿将本项目用于未授权访问、破解、绕过访问控制或其他违法用途。

完整免责声明见 [DISCLAIMER.md](DISCLAIMER.md)。
