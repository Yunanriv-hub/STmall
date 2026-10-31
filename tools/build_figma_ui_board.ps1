param(
    [string]$SourceDir = (Join-Path $PSScriptRoot "..\figma-ui\source"),
    [string]$OutputDir = (Join-Path $PSScriptRoot "..\figma-ui")
)

Add-Type -AssemblyName System.Drawing

$titles = [ordered]@{
    "01-home" = "首页"
    "02-features" = "功能页"
    "03-my" = "我的页"
    "04-huisheng-mine" = "慧生活 · 我的"
    "05-huisheng-accounts" = "慧生活 · 账号管理"
    "06-water-control" = "饮水控制中心"
    "07-huisheng-add-device" = "添加慧生活设备"
    "08-huisheng-nfc" = "NFC 碰一碰"
    "09-smart-dorm-lock" = "智能宿舍 · 门锁"
    "10-smart-dorm-water" = "智能宿舍 · 饮水"
    "11-ujing" = "U净"
    "12-pangguai" = "胖乖生活"
    "13-perfect-campus" = "完美校园"
    "14-settings" = "界面设置"
}

$files = Get-ChildItem -LiteralPath $SourceDir -Filter "*.png" | Sort-Object Name
if ($files.Count -eq 0) {
    throw "No UI screenshots found in $SourceDir"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$screenWidth = 540
$screenHeight = 1206
$cellWidth = 580
$cellHeight = 1320
$margin = 40
$labelHeight = 64
$columns = 4
$rows = [math]::Ceiling($files.Count / $columns)
$boardWidth = $margin * 2 + $columns * $cellWidth
$boardHeight = 110 + $rows * $cellHeight + 72

$preview = New-Object System.Drawing.Bitmap($boardWidth, $boardHeight)
$graphics = [System.Drawing.Graphics]::FromImage($preview)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$graphics.Clear([System.Drawing.Color]::FromArgb(239, 243, 246))

$titleFont = New-Object System.Drawing.Font("Microsoft YaHei UI", 16, [System.Drawing.FontStyle]::Bold)
$metaFont = New-Object System.Drawing.Font("Microsoft YaHei UI", 9)
$headerFont = New-Object System.Drawing.Font("Microsoft YaHei UI", 28, [System.Drawing.FontStyle]::Bold)
$headerMetaFont = New-Object System.Drawing.Font("Microsoft YaHei UI", 11)
$ink = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(28, 37, 34))
$muted = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(91, 104, 99))
$cardBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(205, 214, 219), 2)

$graphics.DrawString("Cumpus 0.6.3 · UI 设计稿", $headerFont, $ink, 40, 24)
$graphics.DrawString(
    "Compose 界面参考 · 14 个页面 · 1080 × 2412 原始尺寸等比缩放 · 2026-10-27",
    $headerMetaFont,
    $muted,
    42,
    72
)

$svg = New-Object System.Text.StringBuilder
[void]$svg.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
[void]$svg.AppendLine("<svg xmlns=`"http://www.w3.org/2000/svg`" xmlns:xlink=`"http://www.w3.org/1999/xlink`" width=`"$boardWidth`" height=`"$boardHeight`" viewBox=`"0 0 $boardWidth $boardHeight`">")
[void]$svg.AppendLine('<rect width="100%" height="100%" fill="#EFF3F6"/>')
[void]$svg.AppendLine('<g id="board-title">')
[void]$svg.AppendLine('<text x="40" y="58" font-family="Microsoft YaHei UI, sans-serif" font-size="30" font-weight="700" fill="#1C2522">Cumpus 0.6.3 · UI 设计稿</text>')
[void]$svg.AppendLine('<text x="42" y="90" font-family="Microsoft YaHei UI, sans-serif" font-size="14" fill="#5B6863">Compose 界面参考 · 14 个页面 · 1080 × 2412 原始尺寸等比缩放 · 2026-10-27</text>')
[void]$svg.AppendLine('</g>')
[void]$svg.AppendLine('<defs>')

$encodedScreens = @()
for ($i = 0; $i -lt $files.Count; $i++) {
    $file = $files[$i]
    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    $title = if ($titles.Contains($baseName)) { $titles[$baseName] } else { $baseName }
    $scroll = $i % $columns
    $row = [math]::Floor($i / $columns)
    $x = $margin + $scroll * $cellWidth
    $y = 112 + $row * $cellHeight

    $source = [System.Drawing.Image]::FromFile($file.FullName)
    try {
        $scaled = New-Object System.Drawing.Bitmap($screenWidth, $screenHeight)
        $scaledGraphics = [System.Drawing.Graphics]::FromImage($scaled)
        try {
            $scaledGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $scaledGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $scaledGraphics.DrawImage($source, 0, 0, $screenWidth, $screenHeight)
        } finally {
            $scaledGraphics.Dispose()
        }

        $previewX = $x + 20
        $previewY = $y + $labelHeight
        $graphics.FillRectangle($cardBrush, $previewX - 2, $previewY - 2, $screenWidth + 4, $screenHeight + 4)
        $graphics.DrawImage($scaled, $previewX, $previewY, $screenWidth, $screenHeight)
        $graphics.DrawRectangle($borderPen, $previewX - 2, $previewY - 2, $screenWidth + 4, $screenHeight + 4)
        $graphics.DrawString($title, $titleFont, $ink, $previewX, $y + 12)
        $graphics.DrawString($baseName, $metaFont, $muted, $previewX, $y + 40)

        $stream = New-Object System.IO.MemoryStream
        try {
            $encoder = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
                Where-Object { $_.MimeType -eq "image/jpeg" }
            $encoderParams = New-Object System.Drawing.Imaging.EncoderParameters(1)
            $encoderParams.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter(
                [System.Drawing.Imaging.Encoder]::Quality,
                88L
            )
            $scaled.Save($stream, $encoder, $encoderParams)
            $base64 = [Convert]::ToBase64String($stream.ToArray())
        } finally {
            $stream.Dispose()
            $scaled.Dispose()
        }

        $safeId = $baseName -replace '[^A-Za-z0-9_-]', '-'
        $escapedTitle = [System.Security.SecurityElement]::Escape($title)
        [void]$svg.AppendLine("<clipPath id=`"clip-$safeId`"><rect x=`"$previewX`" y=`"$previewY`" width=`"$screenWidth`" height=`"$screenHeight`" rx=`"28`"/></clipPath>")
        $encodedScreens += [pscustomobject]@{
            Id = $safeId
            Title = $escapedTitle
            Base64 = $base64
            X = $previewX
            Y = $previewY
            FileName = $baseName
        }
    } finally {
        $source.Dispose()
    }
}

[void]$svg.AppendLine('</defs>')
foreach ($screen in $encodedScreens) {
    [void]$svg.AppendLine("<g id=`"$($screen.Id)`">")
    [void]$svg.AppendLine("<text x=`"$($screen.X)`" y=`"$($screen.Y - 42)`" font-family=`"Microsoft YaHei UI, sans-serif`" font-size=`"20`" font-weight=`"700`" fill=`"#1C2522`">$($screen.Title)</text>")
    [void]$svg.AppendLine("<text x=`"$($screen.X)`" y=`"$($screen.Y - 18)`" font-family=`"Microsoft YaHei UI, sans-serif`" font-size=`"12`" fill=`"#5B6863`">$($screen.FileName)</text>")
    [void]$svg.AppendLine("<image x=`"$($screen.X)`" y=`"$($screen.Y)`" width=`"$screenWidth`" height=`"$screenHeight`" preserveAspectRatio=`"none`" clip-path=`"url(#clip-$($screen.Id))`" xlink:href=`"data:image/jpeg;base64,$($screen.Base64)`"/>")
    [void]$svg.AppendLine("<rect x=`"$($screen.X)`" y=`"$($screen.Y)`" width=`"$screenWidth`" height=`"$screenHeight`" rx=`"28`" fill=`"none`" stroke=`"#CDD6DB`" stroke-width=`"2`"/>")
    [void]$svg.AppendLine('</g>')
}

[void]$svg.AppendLine("<text x=`"$margin`" y=`"$($boardHeight - 30)`" font-family=`"Microsoft YaHei UI, sans-serif`" font-size=`"12`" fill=`"#5B6863`">Source: android/app/src/main/java/com/fktm/app/ui · android/life798/src/main/kotlin/com/water/widget/ui</text>")
[void]$svg.AppendLine('</svg>')

$svgPath = Join-Path $OutputDir "Cumpus_0.6.3_UI_Board.svg"
[System.IO.File]::WriteAllText($svgPath, $svg.ToString(), [System.Text.UTF8Encoding]::new($false))

$previewPath = Join-Path $OutputDir "Cumpus_0.6.3_UI_Board_preview.png"
$preview.Save($previewPath, [System.Drawing.Imaging.ImageFormat]::Png)

$pluginDir = Join-Path $OutputDir "plugin"
New-Item -ItemType Directory -Force -Path $pluginDir | Out-Null
$templatePath = Join-Path $pluginDir "code-template.js"
$template = [System.IO.File]::ReadAllText($templatePath)
$screensJson = ConvertTo-Json -InputObject @($encodedScreens | ForEach-Object {
    [pscustomobject]@{
        id = $_.Id
        title = [System.Net.WebUtility]::HtmlDecode($_.Title)
        fileName = $_.FileName
        base64 = $_.Base64
    }
}) -Depth 3 -Compress
$pluginCode = $template.Replace("__SCREENS_JSON__", $screensJson).
    Replace("__BOARD_WIDTH__", $boardWidth.ToString()).
    Replace("__BOARD_HEIGHT__", $boardHeight.ToString())
[System.IO.File]::WriteAllText(
    (Join-Path $pluginDir "code.js"),
    $pluginCode,
    [System.Text.UTF8Encoding]::new($false)
)

$graphics.Dispose()
$preview.Dispose()
$titleFont.Dispose()
$metaFont.Dispose()
$headerFont.Dispose()
$headerMetaFont.Dispose()
$ink.Dispose()
$muted.Dispose()
$cardBrush.Dispose()
$borderPen.Dispose()

Get-Item $svgPath, $previewPath, (Join-Path $pluginDir "manifest.json"), (Join-Path $pluginDir "code.js") |
    Select-Object FullName, Length
