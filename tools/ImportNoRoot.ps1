param(
    [string]$Adb = "adb",
    [string]$Serial = "",
    [switch]$SkipShizukuStart
)

$ErrorActionPreference = "Stop"
$addressUrl = "https://biz.confong.cn/app/tmall-xiaoyuan/page-m-webview/" +
    "deliveryAddress/list?hideNavigator=true&spm=a21srm.page_mine.0.0"
$deepLink = "tmallcampus://web/open?url=" + [Uri]::EscapeDataString($addressUrl)
$remoteImport = "/data/local/tmp/campus_lite_cookies.json"
$cookieExport = Join-Path $PSScriptRoot "ExportWebViewCookies.mjs"

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $prefix = @("-s", $Serial)
    & $Adb @prefix @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: $($Arguments -join ' ')"
    }
}

if (-not (Get-Command $Adb -ErrorAction SilentlyContinue) -and -not (Test-Path $Adb)) {
    throw "adb was not found. Pass -Adb <absolute path>."
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $devices = & $Adb devices -l | Select-String " device "
    if ($devices.Count -ne 1) {
        throw "Expected exactly one connected device. Pass -Serial."
    }
    $Serial = ($devices.Line -split "\s+")[0]
}

$official = (Invoke-Adb shell pm path com.tmall.campus.and) -join "`n"
if ($official -notmatch "^package:") {
    throw "Official Tmall Campus is not installed."
}

$campusLite = (Invoke-Adb shell pm path com.rea.campuslite) -join "`n"
if ($campusLite -notmatch "^package:") {
    throw "STmall is not installed."
}

if (-not $SkipShizukuStart) {
    $server = (Invoke-Adb shell "ps -A | grep shizuku_server") -join "`n"
    if ($server -notmatch "shizuku_server") {
        $abi = (Invoke-Adb shell getprop ro.product.cpu.abi).Trim()
        $libDir = if ($abi -eq "arm64-v8a") { "arm64" } else { "arm" }
        $shizukuApk = (Invoke-Adb shell pm path moe.shizuku.privileged.api) -replace "^package:", ""
        $starter = $shizukuApk -replace "/base.apk$", "/lib/$libDir/libshizuku.so"
        Invoke-Adb shell $starter | Out-Host
        Start-Sleep -Seconds 2
    }
}

# Open an official WebView page so its DevTools endpoint exists.
$startCommand = "am start -a android.intent.action.VIEW " +
    "-c android.intent.category.BROWSABLE -d '$deepLink' -p com.tmall.campus.and"
Invoke-Adb shell $startCommand | Out-Null
Start-Sleep -Seconds 4

$officialPid = (Invoke-Adb shell pidof com.tmall.campus.and).Trim()
if ([string]::IsNullOrWhiteSpace($officialPid)) {
    throw "Official Tmall Campus is not running."
}

$socket = "webview_devtools_remote_$officialPid"
$unix = (Invoke-Adb shell "cat /proc/net/unix") -join "`n"
if ($unix -notmatch [regex]::Escape($socket)) {
    throw "WebView debugging is unavailable. Open the official address page once and retry."
}

Invoke-Adb forward tcp:9222 "localabstract:$socket" | Out-Null
$pages = (Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:9222/json" -TimeoutSec 10).Content |
    ConvertFrom-Json
$page = $pages | Where-Object { $_.type -eq "page" } | Select-Object -First 1
if (-not $page) {
    throw "No official WebView page was found."
}

$temp = [IO.Path]::GetTempFileName()
try {
    & node $cookieExport $page.webSocketDebuggerUrl $temp
    if ($LASTEXITCODE -ne 0) {
        throw "Cookie export failed."
    }
    Invoke-Adb push $temp $remoteImport | Out-Null
    Invoke-Adb shell chmod 600 $remoteImport | Out-Null
} finally {
    Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
}

Invoke-Adb shell "monkey -p com.rea.campuslite -c android.intent.category.LAUNCHER 1" | Out-Null
Write-Host "No-root import file is ready."
Write-Host "In STmall, open My -> Sync official login."
Write-Host "After the account appears, delete $remoteImport through adb."
