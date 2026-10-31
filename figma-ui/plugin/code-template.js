const SCREENS = __SCREENS_JSON__;
const BOARD_WIDTH = __BOARD_WIDTH__;
const BOARD_HEIGHT = __BOARD_HEIGHT__;
const CELL_WIDTH = 580;
const CELL_HEIGHT = 1320;
const MARGIN = 40;
const SCREEN_WIDTH = 540;
const SCREEN_HEIGHT = 1206;
const COLUMNS = 4;

function rgb(hex) {
  const value = hex.replace("#", "");
  return {
    r: parseInt(value.slice(0, 2), 16) / 255,
    g: parseInt(value.slice(2, 4), 16) / 255,
    b: parseInt(value.slice(4, 6), 16) / 255,
  };
}

function decodeBase64(value) {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

async function loadFont(style) {
  const available = await figma.listAvailableFontsAsync();
  const preferredFamilies = [
    "Microsoft YaHei",
    "Microsoft YaHei UI",
    "Noto Sans SC",
    "Source Han Sans SC",
    "PingFang SC",
    "Inter",
  ];
  for (const family of preferredFamilies) {
    const match = available.find(
      (font) =>
        font.fontName.family === family &&
        font.fontName.style.toLowerCase() === style.toLowerCase(),
    );
    if (match) {
      await figma.loadFontAsync(match.fontName);
      return match.fontName;
    }
  }
  const fallback = available.find(
    (font) => font.fontName.style.toLowerCase() === style.toLowerCase(),
  );
  if (!fallback) throw new Error(`No font available for style ${style}`);
  await figma.loadFontAsync(fallback.fontName);
  return fallback.fontName;
}

function addText(parent, name, value, x, y, size, color, fontName) {
  const node = figma.createText();
  parent.appendChild(node);
  node.name = name;
  node.fontName = fontName;
  node.characters = value;
  node.fontSize = size;
  node.fills = [{ type: "SOLID", color: rgb(color) }];
  node.x = x;
  node.y = y;
  return node;
}

(async () => {
  try {
    console.log("Cumpus UI import started", SCREENS.length);
    const regular = await loadFont("Regular");
    const bold = await loadFont("Bold");
    console.log("Cumpus UI fonts loaded", regular.family, bold.family);
    figma.currentPage.name = "Cumpus 0.6.3 · UI";

    const board = figma.createFrame();
    board.name = "Cumpus 0.6.3 · 14 个界面";
    board.resize(BOARD_WIDTH, BOARD_HEIGHT);
    board.x = 0;
    board.y = 0;
    board.fills = [{ type: "SOLID", color: rgb("#EFF3F6") }];
    board.clipsContent = false;
    if (board.parent !== figma.currentPage) {
      figma.currentPage.appendChild(board);
    }
    console.log("Cumpus UI board created", board.id);

    addText(
      board,
      "画板标题",
      "Cumpus 0.6.3 · UI 设计稿",
      MARGIN,
      22,
      30,
      "#1C2522",
      bold,
    );
    addText(
      board,
      "画板说明",
      "Compose 界面参考 · 14 个页面 · 1080 × 2412 原始尺寸等比缩放 · 2026-10-27",
      MARGIN + 2,
      67,
      14,
      "#5B6863",
      regular,
    );

    for (let i = 0; i < SCREENS.length; i += 1) {
      const screen = SCREENS[i];
      const column = i % COLUMNS;
      const row = Math.floor(i / COLUMNS);
      const frame = figma.createFrame();
      frame.name = `${screen.id} · ${screen.title}`;
      frame.resize(SCREEN_WIDTH, SCREEN_HEIGHT + 64);
      frame.x = MARGIN + column * CELL_WIDTH;
      frame.y = 112 + row * CELL_HEIGHT;
      frame.fills = [];
      frame.clipsContent = false;
      board.appendChild(frame);

      addText(
        frame,
        "页面名称",
        screen.title,
        0,
        0,
        20,
        "#1C2522",
        bold,
      );
      addText(
        frame,
        "源文件",
        screen.fileName,
        0,
        28,
        12,
        "#5B6863",
        regular,
      );

      const image = figma.createImage(decodeBase64(screen.base64));
      const shot = figma.createRectangle();
      shot.name = `${screen.id} · 真实界面截图`;
      shot.resize(SCREEN_WIDTH, SCREEN_HEIGHT);
      shot.x = 0;
      shot.y = 64;
      shot.cornerRadius = 28;
      shot.fills = [{ type: "IMAGE", scaleMode: "FILL", imageHash: image.hash }];
      frame.appendChild(shot);

      const outline = figma.createRectangle();
      outline.name = "设备画板边框";
      outline.resize(SCREEN_WIDTH, SCREEN_HEIGHT);
      outline.x = 0;
      outline.y = 64;
      outline.cornerRadius = 28;
      outline.fills = [];
      outline.strokes = [
        { type: "SOLID", color: rgb("#CDD6DB"), opacity: 1 },
      ];
      outline.strokeWeight = 2;
      frame.appendChild(outline);
      console.log("Cumpus UI screen imported", screen.id);
    }

    addText(
      board,
      "源码说明",
      "Source: android/app/src/main/java/com/fktm/app/ui · android/life798/src/main/kotlin/com/water/widget/ui",
      MARGIN,
      BOARD_HEIGHT - 42,
      12,
      "#5B6863",
      regular,
    );

    figma.currentPage.selection = [board];
    figma.viewport.scrollAndZoomIntoView([board]);
    console.log("Cumpus UI import complete", SCREENS.length);
    figma.notify(`Cumpus UI 已导入：${SCREENS.length} 个页面`, {
      timeout: 5000,
    });
    figma.closePlugin();
  } catch (error) {
    console.error("Cumpus UI import failed", error && error.stack ? error.stack : error);
    figma.notify(`Cumpus UI 导入失败：${error.message}`, {
      error: true,
      timeout: 12000,
    });
    figma.closePlugin();
  }
})();
