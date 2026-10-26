# 胖乖生活参考仓库安全审查

- 审查日期：2026-10-26
- 仓库：`https://github.com/Night-stars-1/wash`
- 审查方式：只读检查 GitHub 仓库页、Releases、README、Gradle 清单、入口文件和源码中的网址；未运行仓库代码。

## 仓库来源

- 作者：`Night-stars-1`
- 默认分支：`master`
- 不是 Fork，未归档，没有 LICENSE 文件。
- 约 6 个 Star、0 个 Fork。
- 有 1 个 Release `v1.1`，资产为约 2.47 MB 的 `app-release.apk`；页面未显示可核对的下载量。
- GitHub 搜索还能找到 `wzs0512/qiekj-android`、`3ryng1um/qiekj` 等不同作者的同主题客户端，不能把它们的来源混在一起。

## 数据和网址

运行时代码中唯一的业务网址是：

- `https://userapi.qiekj.com`

会发生网络发送的数据包括：

- 手机号、短信验证码和登录 Token。
- 扫码得到的设备参数 `NQT`、设备详情和可选服务。
- 最近使用设备、历史订单和订单详情。
- 创建交易、支付渠道和支付宝预支付参数。

README 中的 GitHub 图片、LINUX DO 链接、Android/Gradle 文档链接均不参与业务数据上传。

## 文件、密钥和账号信息

- 没有发现读取通讯录、短信、相册、外部存储或任意文件的代码。
- 登录 Token 被直接保存在明文 SharedPreferences `washPrefs` 中。
- `SigningInterceptor` 会把带 Token 的完整签名请求写入 logcat。
- 构建脚本公开写入固定 release keystore 别名和口令，且仓库未包含对应 `wuhua.jks`。
- 源码公开包含 API `appSecret`。
- Manifest 开启 `allowBackup`，备份规则没有排除 `washPrefs`。

## 下载与执行行为

- 没有发现远程下载 DEX/JAR、反射执行下载文件、JavaScript 桥或脚本引擎。
- `Class.forName` 只查找本机的 `com.srap.elecbill.module.ElecBillModule`。
- Gradle 构建会从 Google、Maven Central 和 Gradle 官方服务下载标准依赖与 Gradle 发行包。
- `settings.gradle.kts` 和 `app/build.gradle.kts` 引用了 `:elecbill` 动态模块，但目录中没有该模块，源码无法按仓库原样完整构建。

## 启动和自行发送

- 没有开机广播、定时任务自动发请求或自动后台登录逻辑。
- `CountdownService` 只在用户进入相关倒计时操作后启动；使用 `START_STICKY`，启动后可能被系统重启。
- 倒计时服务只更新通知并发送本机广播，不向业务服务器自行发送数据。

## 结论

仓库没有恶意远控代码，但它缺少许可证，并公开了签名口令和 API 密钥；Token 明文存储且被日志输出，备份规则也未保护 Token。因此 STmall 不复制、不安装该仓库实现，只依据公开接口行为独立实现功能，并使用 Android Keystore AES-GCM 加密 Token、关闭应用备份和避免记录敏感请求头。

不建议原样安装该参考仓库 APK；如果仍要安装，至少关闭云备份和自动恢复，关闭通知与后台数据，且不要授予存储权限。
