# Cumpus 0.6.3 Figma UI

Figma 设计文件：

- `Cumpus_0.6.3_UI.fig`

画布内包含 14 个独立页面 Frame：

1. 首页
2. 功能页
3. 我的页
4. 慧生活 · 我的
5. 慧生活 · 账号管理
6. 饮水控制中心
7. 添加慧生活设备
8. NFC 碰一碰
9. 智能宿舍 · 门锁
10. 智能宿舍 · 饮水
11. U净
12. 胖乖生活
13. 完美校园
14. 界面设置

每个 Frame 包含真实界面截图、页面名称和源截图名。截图作为单独的图片填充图层，可用于覆盖重绘、标注、切图和视觉调整。Compose / Kotlin 源码不能直接转换为 Figma 原生组件，因此若要继续修改控件结构，可在截图上重建组件，或按 Frame 对照源码修改 Compose。

重新生成：

```powershell
.\tools\build_figma_ui_board.ps1
```

生成物：

- `Cumpus_0.6.3_UI_Board.svg`：可导入的分组 SVG
- `Cumpus_0.6.3_UI_Board_preview.png`：全部页面预览
- `plugin/manifest.json`、`plugin/code.js`：本地 Figma 开发插件，用于把 14 页创建成独立 Frame 图层
