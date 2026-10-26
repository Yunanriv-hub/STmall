# 完美校园参考来源安全审查

- 审查日期：2026-10-26
- Apifox 文档：`https://s.apifox.cn/ad1e6ba6-6cd2-4b6e-88b2-a1ca1577e14c/api-214004061`
- GitHub：`https://github.com/zhoudedi/WanMeiXiaoYuanCheckin`
- 审查方式：只读查看公共文档、README、MIT LICENSE、入口文件、网络代码和源码中的网址；未运行参考仓库代码。

## Apifox 只读接口

公共文档只包含以下业务能力：

- 学校代码表。
- `POST https://xqh5.17wanxiao.com/smartWaterAndElectricityService/SWAEServlet`
- `method=getbindroom`：按学校代码与学号/工号查询绑定房间。
- `method=h5_getstuindexpage`：按房间 `roomverify` 查询水电详情。

这两个 POST 都是查询，不修改水电余额、不提交签到，也不包含健康打卡。请求会向
`xqh5.17wanxiao.com` 发送学校代码、学号/工号和房间标识。

## GitHub 仓库

- 作者：`zhoudedi`
- 约 1 Star、1 Watcher、0 Fork，1 个 Release。
- 使用 MIT License。
- 不是 Fork，默认分支 `master`。
- 仓库主题直接写明“健康打卡、校内打卡、理论上支持所有学校自动打卡”。

## 数据与网址

参考仓库会访问或发送到：

- `https://app.17wanxiao.com`：密码/短信登录和 Token。
- `https://reportedh5.17wanxiao.com`：健康打卡、校内打卡和用户信息。
- `https://server.17wanxiao.com`、`https://server.59wanmei.com`：校园卡、粮票和成绩相关数据。
- `https://course.59wanmei.com`、`https://information.17wanxiao.com`、`https://api.xiaozhao365.com`：课表、资讯和任务。
- `https://smtp.qq.com`、`https://qyapi.weixin.qq.com`、`https://sctapi.ftqq.com`、`https://qmsg.zendee.cn`、`https://www.pipehub.net`、`https://api.day.app`：用户可选消息推送。
- `http://gm.zhoudedi.top:300`：非 HTTPS 的私人 QQ 推送通道。

## 文件、账号和密钥

- 从 `conf/user.json`、`conf/push.json` 读取手机号、密码、`device_id`、邮箱授权码、推送密钥等敏感信息。
- 设备只允许一个登录来源。README 明确警告：使用脚本后不要再用手机 App 登录，否则 `device_id` 会失效。
- 获取 `device_id` 需要抓包或 Xposed Hook。
- 登录代码使用 `verify=False` 关闭 TLS 证书校验。
- 没有发现远程下载并执行代码、`eval`、`subprocess` 或自动安装程序。
- 配置中的 HTTPS 推送和 SMTP 在程序运行时才会使用；README 通过外部 `crontab` 每天启动。

## 封号与违规风险

参考仓库包含自动健康打卡、校内打卡、模拟设备 ID、批量用户、定时任务、查看课表/头条赚粮票和自动领取活跃奖励。这些行为可能违反学校和服务条款，也可能触发设备、账号或风控限制。

STmall 没有接入以下行为：

- 密码或虚拟 `device_id` 登录。
- 健康打卡、校内签到、定位打卡。
- 自动课表/头条任务、粮票或活跃奖励。
- 定时自动提交、批量账号和推送。
- 关闭 TLS 校验。

## 结论

完美校园模块只独立实现 Apifox 文档中的学校代码、绑定房间和房间水电查询；不复制 GitHub 登录、打卡、任务或推送代码。结论：STmall 可以加入该只读模块，GitHub 自动打卡仓库不要安装；若单独安装该仓库，必须关闭自动执行/定时任务、通知与云备份，并不要填写真实密码、设备 ID、邮箱授权码或推送密钥。
