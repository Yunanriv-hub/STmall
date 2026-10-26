# U净开源项目接入审查

审查日期：2026-10-26。仅做静态阅读，未运行上游工程。

## 项目身份

- `baijuqi/ujing-mini`：作者 `baijuqi`，MIT License，审查时 1 Star、0 fork、
  1 个 Release、9 次下载。
- `43aquarius/DUT-Ujing`：作者 `43aquarius`，无项目级许可证，审查时 1 Star、
  0 fork、3 个 Release、14 次下载。
- 同名搜索未发现冒充这两个作者的仓库；两个目录页均未显示归档或停用提醒。

## 数据处理

- 手机号、短信验证码、U净 Token、设备二维码、deviceId、MAC、订单号和
  BLE 上报数据会发送到 `https://phoenix.ujing.online`。
- 支付阶段由 U净服务端签发支付宝订单串，再交给支付宝官方 SDK；STmall
  不经手支付资金，也不持久化订单串。
- QR 校验只接受 `q.ujing.com.cn` 和 `app.littleswan.com` 的设备码。
- 上游 App 均读取相机、蓝牙和账户信息。`ujing-mini` 使用明文 DataStore，
  `DUT-Ujing` 使用明文 SharedPreferences；STmall 接入时改为 Android
  Keystore AES-GCM 加密保存。

## 执行与安装风险

- 未发现 `DexClassLoader`、加密后动态执行、WebView 远程脚本、开机广播、
  推送 SDK 或后台常驻服务。
- Android App 安装后不会自动下载或执行额外程序；订单运行时会短周期轮询
  服务端状态。
- `DUT-Ujing` 仓库公开包含 release keystore 和口令，其 Web 工程执行
  `bun install` 还会下载大量 npm 依赖。STmall 不复制签名密钥、不引入其
  Web 工程或依赖。

## 结论

可以按 MIT 许可选择性移植 `ujing-mini` 的网络和 BLE 代码，并使用
`DUT-Ujing` 的公开协议文档作交叉参考；不能直接复制或安装 `DUT-Ujing`
的源码、签名材料或 Web 服务。STmall 集成版可以安装，但应保持 U净自动刷新
默认关闭、仅在本人账号和本人有权使用的设备上授权相机与附近设备权限。
