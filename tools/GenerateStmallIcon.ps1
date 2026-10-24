param(
    [Parameter(Mandatory = $true)]
    [string]$Source,
    [string]$ResDirectory
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if ([string]::IsNullOrWhiteSpace($ResDirectory)) {
    $ResDirectory = Join-Path $PSScriptRoot '..\android\app\src\main\res'
}

$sourcePath = (Resolve-Path -LiteralPath $Source).Path
$resPath = (Resolve-Path -LiteralPath $ResDirectory).Path
$sourceImage = [System.Drawing.Bitmap]::new($sourcePath)
$background = $sourceImage.GetPixel(0, 0)
$backgroundColour = [System.Drawing.Color]::FromArgb(
    255,
    $background.R,
    $background.G,
    $background.B
)

function New-SquareCanvas {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [bool]$Transparent
    )

    $side = [Math]::Max($Bitmap.Width, $Bitmap.Height)
    $canvas = [System.Drawing.Bitmap]::new(
        $side,
        $side,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = [System.Drawing.Graphics]::FromImage($canvas)
    try {
        $clearColour = if ($Transparent) {
            [System.Drawing.Color]::Transparent
        } else {
            $backgroundColour
        }
        $graphics.Clear($clearColour)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.DrawImageUnscaled(
            $Bitmap,
            [int](($side - $Bitmap.Width) / 2),
            [int](($side - $Bitmap.Height) / 2)
        )
    }
    finally {
        $graphics.Dispose()
    }
    return $canvas
}

function Save-ScaledPng {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [int]$Size,
        [string]$Path
    )

    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $scaled = [System.Drawing.Bitmap]::new(
        $Size,
        $Size,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = [System.Drawing.Graphics]::FromImage($scaled)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage(
            $Bitmap,
            [System.Drawing.Rectangle]::new(0, 0, $Size, $Size),
            0,
            0,
            $Bitmap.Width,
            $Bitmap.Height,
            [System.Drawing.GraphicsUnit]::Pixel
        )
        $scaled.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $graphics.Dispose()
        $scaled.Dispose()
    }
}

$legacySquare = New-SquareCanvas -Bitmap $sourceImage -Transparent $false
$foregroundSquare = [System.Drawing.Bitmap]::new(
    $sourceImage.Width,
    $sourceImage.Height,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
)

try {
    for ($y = 0; $y -lt $sourceImage.Height; $y++) {
        for ($x = 0; $x -lt $sourceImage.Width; $x++) {
            $pixel = $sourceImage.GetPixel($x, $y)
            $luminance = ($pixel.R + $pixel.G + $pixel.B) / 3.0
            $alpha = [Math]::Max(
                0,
                [Math]::Min(255, [int](($luminance - 18.0) * 255.0 / 222.0))
            )
            $foregroundSquare.SetPixel(
                $x,
                $y,
                [System.Drawing.Color]::FromArgb($alpha, 255, 255, 255)
            )
        }
    }

    $foregroundPadded = New-SquareCanvas -Bitmap $foregroundSquare -Transparent $true
    try {
        foreach ($density in @{
            'mdpi' = 48
            'hdpi' = 72
            'xhdpi' = 96
            'xxhdpi' = 144
            'xxxhdpi' = 192
        }.GetEnumerator()) {
            Save-ScaledPng `
                -Bitmap $legacySquare `
                -Size $density.Value `
                -Path (Join-Path $resPath "mipmap-$($density.Key)\ic_launcher.png")
        }

        Save-ScaledPng `
            -Bitmap $foregroundPadded `
            -Size 432 `
            -Path (Join-Path $resPath 'drawable-nodpi\stmall_launcher_foreground.png')
    }
    finally {
        $foregroundPadded.Dispose()
    }
}
finally {
    $foregroundSquare.Dispose()
    $legacySquare.Dispose()
    $sourceImage.Dispose()
}

Write-Host "Launcher assets generated for STmall from $sourcePath"
