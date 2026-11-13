<#
.SYNOPSIS
    在「正式包」和「debug 测试包」之间切换启用状态（不卸载、不动数据）。

.DESCRIPTION
    - dev     : 停用正式包 com.slideindex.app，启用测试包 com.slideindex.app.debug
    - release : 停用测试包，启用正式包

    为什么需要切换：两个包都有无障碍服务 / 悬浮窗 / 剪贴板监听，
    同时启用会重复触发（两套悬浮把手、两次监听）。停用只是「隐藏 + 停服务」，
    数据与安装都不受影响，切回来即可用。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/dev/switch-app.ps1 -Mode dev
    powershell -ExecutionPolicy Bypass -File scripts/dev/switch-app.ps1 -Mode release
#>
param(
    [ValidateSet("dev", "release")]
    [string]$Mode = "dev"
)

$releasePkg = "com.slideindex.app"
$debugPkg = "com.slideindex.app.debug"

$adb = @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "C:\Android\Sdk\platform-tools\adb.exe",
    "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $adb) {
    Write-Error "未找到 adb，请先在 Android Studio 安装 platform-tools，或把 adb 加到 PATH。"
    exit 1
}

function Set-PackageEnabled {
    param([string]$Package, [bool]$Enabled)

    if ($Enabled) {
        $result = & $adb shell pm enable $Package 2>&1
    } else {
        $result = & $adb shell pm disable-user --user 0 $Package 2>&1
    }
    "{0,-32} {1} -> {2}" -f $Package, $(if ($Enabled) { "enable" } else { "disable" }), ($result -join ' ')
}

if ($Mode -eq "dev") {
    Set-PackageEnabled -Package $releasePkg -Enabled $false
    Set-PackageEnabled -Package $debugPkg -Enabled $true
    "`n已切到 debug 包：桌面上只剩「X手势 Dev」，正式包被停用（数据仍在）。"
} else {
    Set-PackageEnabled -Package $debugPkg -Enabled $false
    Set-PackageEnabled -Package $releasePkg -Enabled $true
    "`n已切回正式包。"
}
