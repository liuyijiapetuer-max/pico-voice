<#
.SYNOPSIS
    准入门槛第 3 条自检：连续运行 N 分钟，盯崩溃、ANR、内存增长。

.DESCRIPTION
    官方原文：「连续运行 30 分钟无崩溃、卡死或数据丢失；
    对于短时体验，可通过重复完整体验进行验证。」

    这个脚本做前一半（硬跑 30 分钟），因为它更硬、更省事：
    跑一次就有一次结论，不用靠「重复完整体验」的口头解释。

    它盯三件事：
      1. **崩溃**：logcat 的 crash buffer + FATAL EXCEPTION
      2. **卡死**：进程还活着吗 + 有没有 ANR
      3. **内存增长**：PSS 是不是一路涨 —— 这是「长时间视频处理必须有界缓冲」
         那条坑的直接证据（缓冲无界 = 内存单调上涨 = 跑不久就 OOM）

    输出：一份 Markdown 报告 + 逐次采样 CSV。报告可以直接作为材料附上。

.EXAMPLE
    pwsh -File tools\stability-30min.ps1 -Minutes 30
    pwsh -File tools\stability-30min.ps1 -Minutes 5 -SkipInstall   # 快速试跑
#>
[CmdletBinding()]
param(
    # 跑多久（分钟）。门槛要求 30。
    [int]$Minutes = 30,

    # 采样间隔（秒）。
    [int]$IntervalSeconds = 15,

    # 包名。
    [string]$Package = 'com.spatialcoach.soundarchitecture',

    # APK 路径。默认取 app 模块的 debug 产物。
    [string]$Apk,

    # adb 可执行文件。
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",

    # 目标设备 serial。留空则自动选第一台在线设备。
    [string]$Serial,

    # 报告输出目录。
    [string]$OutDir = "$PSScriptRoot\..\stability",

    # 跳过安装（用设备上已装的版本）。
    [switch]$SkipInstall,

    # 打出被测 APK 的代码 commit。留空则记录当前仓库 HEAD（对账文档提交后 HEAD 会再前移，
    # 所以跑稳定性时应显式传入打包用的那个 commit，不要把对账 commit 写成 APK 源 commit）。
    [string]$SourceCommit
)

$ErrorActionPreference = 'Stop'

if (-not $Apk) {
    $Apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk'
}
$Apk = [System.IO.Path]::GetFullPath($Apk)

function Invoke-Adb {
    param([string[]]$AdbArgs)
    & $Adb @AdbArgs 2>&1
}

function Get-TargetSerial {
    if ($Serial) { return $Serial }
    # ⚠️ 必须用 @(...) 包起来：
    # 只有一台设备时，ForEach-Object 的输出会被 PowerShell 拆成**标量字符串**，
    # 于是 $devices[0] 取到的是第一个**字符**（"emulator-5554" → "e"），
    # 后面 adb -s e ... 全错，而且不报错 —— 这种坑最难查。
    $devices = @(
        (Invoke-Adb @('devices')) |
            Select-String -Pattern '\tdevice$' |
            ForEach-Object { ($_ -split "`t")[0] }
    )
    if ($devices.Count -eq 0) { throw '没有在线的 Android 设备。先启动 PICO 模拟器。' }
    return $devices[0]
}

# ── 前置检查 ────────────────────────────────────────────────────────────
if (-not (Test-Path $Adb)) {
    $picoAdb = Join-Path $env:LOCALAPPDATA 'PICO\sdk\custom\emulator\platform-tools\adb.exe'
    if (Test-Path $picoAdb) { $Adb = $picoAdb }
    else { throw "找不到 adb：$Adb" }
}
$target = Get-TargetSerial
Write-Host "[stability] 目标设备：$target"

if (-not $SkipInstall) {
    if (-not (Test-Path $Apk)) { throw "找不到 APK：$Apk（先构建）" }
    Write-Host "[stability] 安装 $Apk"
    Invoke-Adb @('-s', $target, 'install', '-r', '-g', $Apk) | Out-Null
}

# ★ 记下被测 APK 的字节数 + SHA256。
#   「稳定性证据必须对应最终 APK」这条对账链就写在这一行：
#   报告里只有带上哈希，才能证明「跑满 30 分钟的」和「交上去的」是同一个二进制。
$apkBytes = (Get-Item $Apk).Length
$apkSha = (Get-FileHash $Apk -Algorithm SHA256).Hash
if (-not $SourceCommit) {
    $repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $SourceCommit = (git -C $repoRoot rev-parse HEAD).Trim()
}
Write-Host "[stability] 被测 APK：$apkBytes 字节  SHA256=$apkSha"
Write-Host "[stability] 打包用的代码 commit：$SourceCommit"

# 清掉旧日志，保证这次测的日志干净
# 必须连 events 一起清：只 logcat -c 只会清 main，events 里上次的 am_anr 会在收尾时被再次计入，
# 把「本轮没有新 ANR」写成未通过（本次 2026-11-12 复测就踩了这个坑）。
Invoke-Adb @('-s', $target, 'logcat', '-b', 'all', '-c') | Out-Null

# ── 应用日志落盘（必须放在启动之前）──────────────────────────────────
# ⚠ 为什么不能等跑完再 `logcat -d` 捞：
#   PICO 模拟器的系统日志 spam 极猛（半小时 14 万行），
#   main buffer 会被冲掉 —— 实测跑满 30 分钟后，
#   `logcat -d | grep ExposureTerrain` 一条都搜不到，
#   而应用本身好好地跑着（Stage 与三扇副窗仍在）。
#   ⇒ 改成**边跑边流式落盘**，按 tag 过滤，只收应用自己那几个 tag。
$OutDirEarly = $OutDir
New-Item -ItemType Directory -Force -Path $OutDirEarly | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$appLog = Join-Path $OutDirEarly "applog-$stamp.txt"

Write-Host '[stability] 启动应用日志流式抓取'
$logProc = Start-Process -FilePath $Adb -NoNewWindow -PassThru `
    -RedirectStandardOutput $appLog `
    -ArgumentList @('-s', $target, 'logcat', '-v', 'time', '-s',
        'SoundArchitecture:V', 'SoundField:V', 'FileDemoPlayer:V', 'AndroidRuntime:E', 'ActivityManager:I', 'SpatialPack:I')

$runStart = Get-Date
Write-Host "[stability] 启动应用（先停掉可能占着 Stage 的《曝光暗房》，切勿覆盖那个包）"
Invoke-Adb @('-s', $target, 'shell', 'am', 'force-stop', 'com.spatialcoach.sonosphere') | Out-Null
Invoke-Adb @('-s', $target, 'shell', 'am', 'force-stop', $Package) | Out-Null
Start-Sleep -Seconds 2
Invoke-Adb @('-s', $target, 'shell', 'monkey', '-p', $Package, '-c', 'android.intent.category.LAUNCHER', '1') | Out-Null

# 等应用起来
$appPid = ''
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    $p = (Invoke-Adb @('-s', $target, 'shell', 'pidof', $Package)) | Out-String
    $p = $p.Trim()
    if ($p) { $appPid = $p; break }
}
if (-not $appPid) { throw '应用启动后拿不到 pid —— 先手动确认能装能跑。' }
Write-Host "[stability] pid=$appPid"

# ── 采样循环 ────────────────────────────────────────────────────────────
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$csv = Join-Path $OutDir "samples-$stamp.csv"
$report = Join-Path $OutDir "report-$stamp.md"
$crashLog = Join-Path $OutDir "logcat-$stamp.txt"

"timestamp,elapsed_sec,pss_kb,pid_alive" | Set-Content -Path $csv -Encoding UTF8

$total = $Minutes * 60
$elapsed = 0
$samples = @()
$start = Get-Date

Write-Host "[stability] 开始计时 $Minutes 分钟，每 $IntervalSeconds 秒采样一次"
while ($elapsed -lt $total) {
    Start-Sleep -Seconds $IntervalSeconds
    $elapsed = [int]((Get-Date) - $start).TotalSeconds

    $alive = $false
    $p = (Invoke-Adb @('-s', $target, 'shell', 'pidof', $Package)) | Out-String
    if ($p.Trim()) { $alive = $true }

    $pss = 0
    if ($alive) {
        $mem = (Invoke-Adb @('-s', $target, 'shell', 'dumpsys', 'meminfo', $Package)) | Out-String
        $m = [regex]::Match($mem, 'TOTAL PSS:\s*(\d+)')
        if ($m.Success) { $pss = [int]$m.Groups[1].Value }
    }

    $samples += [pscustomobject]@{ elapsed = $elapsed; pss = $pss; alive = $alive }
    "$(Get-Date -Format 's'),$elapsed,$pss,$alive" | Add-Content -Path $csv -Encoding UTF8

    $flag = if ($alive) { ' ' } else { 'X' }
    Write-Host ("[stability] {0,5}s  pss={1,7} KB  alive={2}" -f $elapsed, $pss, $alive)

    if (-not $alive) {
        Write-Host "[stability] ⚠ 进程消失了，提前结束"
        break
    }
}

# ── 收尾：抓日志、统计 ──────────────────────────────────────────────────
Write-Host '[stability] 停止应用日志抓取'
if ($logProc -and -not $logProc.HasExited) {
    try { Stop-Process -Id $logProc.Id -Force -ErrorAction Stop } catch { }
    # 等 adb 真正把管道写完
    Start-Sleep -Milliseconds 800
}

Write-Host '[stability] 抓取日志'
Invoke-Adb @('-s', $target, 'logcat', '-d', '-v', 'time') | Set-Content -Path $crashLog -Encoding UTF8

$crashText = (Invoke-Adb @('-s', $target, 'logcat', '-d', '-b', 'crash')) | Out-String
$fatal = ([regex]::Matches($crashText, 'FATAL EXCEPTION')).Count
$anr = ((Invoke-Adb @('-s', $target, 'logcat', '-d', '-b', 'events', '-v', 'time')) | Out-String)
$anrAll = @([regex]::Matches($anr, '(?m)^.*am_anr.*$') | ForEach-Object { $_.Value })
# 只计本应用、且时间不早于本次启动的 am_anr，避免把 events 里上次残留算进本轮。
$startToken = $runStart.ToString('MM-dd HH:mm:ss')
$anrThisRun = @($anrAll | Where-Object {
    ($_ -match [regex]::Escape($Package)) -and ($_.Length -ge 17) -and ($_.Substring(0, 17) -ge $startToken)
})
$anrCount = $anrThisRun.Count

# 应用自报帧数：从**落盘的应用日志**里读，不是从整机 logcat 里捞
$appLogText = if (Test-Path $appLog) { Get-Content $appLog -Raw } else { '' }
$frames = ([regex]::Matches($appLogText, 'frames=(\d+)'))
$lastFrames = if ($frames.Count -gt 0) { $frames[$frames.Count - 1].Groups[1].Value } else { '—' }
$appLogLines = if ($appLogText) { ($appLogText -split "`n").Count } else { 0 }
$hotSwaps = ([regex]::Matches($appLogText, 'terrain hot-swapped')).Count

$pssList = @($samples | Where-Object { $_.pss -gt 0 } | ForEach-Object { $_.pss })
$minPss = if ($pssList.Count) { ($pssList | Measure-Object -Minimum).Minimum } else { 0 }
$maxPss = if ($pssList.Count) { ($pssList | Measure-Object -Maximum).Maximum } else { 0 }
$avgPss = if ($pssList.Count) { [int](($pssList | Measure-Object -Average).Average) } else { 0 }
$growth = if ($pssList.Count -gt 1) { $pssList[-1] - $pssList[0] } else { 0 }
$aliveAll = (@($samples | Where-Object { -not $_.alive }).Count -eq 0)

$pass = ($fatal -eq 0) -and ($anrCount -eq 0) -and $aliveAll

$md = @"
# 稳定性自检报告 · 准入门槛第 3 条

| 项 | 值 |
|---|---|
| 生成时间 | $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')（Asia/Shanghai） |
| 开始时间 | $($runStart.ToString('yyyy-MM-dd HH:mm:ss'))（Asia/Shanghai） |
| 结束时间 | $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')（Asia/Shanghai） |
| 目标设备 | $target |
| 设备类型 | $(if ($target -like 'emulator-*') { '模拟器，不是头显' } else { "设备 serial=$target（未按头显/真机声明已验）" }) |
| 包名 | $Package |
| **打包用的代码 commit** | ``$($SourceCommit)`` |
| **被测 APK 字节数** | $apkBytes |
| **被测 APK SHA256** | ``$($apkSha)`` |
| 计划时长 | $Minutes 分钟 |
| 实际时长 | $([int]((Get-Date) - $start).TotalSeconds) 秒 |
| 采样次数 | $($samples.Count) |
| 进程全程存活 | $aliveAll |
| 崩溃（FATAL EXCEPTION） | $fatal |
| ANR | $anrCount |
| PSS 最小 / 平均 / 最大 | $minPss / $avgPss / $maxPss KB |
| PSS 首尾增长 | $growth KB |
| 应用自报处理帧数 | $lastFrames |
| 应用日志行数（落盘） | $appLogLines |
| 演示输入 | 文件回放（合成音轨）；模拟器无真麦 |
| 实时拾音 / 头显验收 | 未做，不写入路演 |
| **结论** | $(if ($pass) { '✅ 通过' } else { '❌ 未通过' }) |

## 判读

- **崩溃 $fatal、ANR $anrCount、进程全程存活 $aliveAll**$(if ($pass) { ' ⇒ 满足「连续运行无崩溃、卡死」。结论：通过。' } else { ' ⇒ 未满足准入门槛（30 分钟无崩溃、卡死）。结论：未通过。' })
- 判读必须按实际崩溃 / ANR / 进程存活计数书写，不得写死 0。本报告结论以脚本判据为准（崩溃=0 且 ANR=0 且进程全程存活才算通过）。
- 本轮计入的 ANR 行（已过滤包名，且时间不早于本次启动；空则表示本轮没有新的 am_anr）：$(if ($anrThisRun.Count) { ($anrThisRun -join ' / ') } else { '无' })
- **PSS 首尾增长**：正数说明内存还在涨。短时增长正常（缓冲填充），
  但**持续单调上涨**说明缓冲无界 —— 这正是「长时间视频处理必须有界缓冲」那条坑。
  判据：增长幅度应远小于 PSS 绝对值（本应用默认文件回放循环分析，PSS 若持续单调大涨需单独排查）。
- **数据丢失**：应用自报的处理帧数应随时间单调增长；若卡在某个数不动，
  说明管线停了（不等于崩溃，但等于「卡死」）。

## 附件

- 逐次采样：``$(Split-Path $csv -Leaf)``
- **应用自身日志（流式落盘）：** ``$(Split-Path $appLog -Leaf)``
- 完整 logcat：``$(Split-Path $crashLog -Leaf)``

> ⚠ 为什么应用日志要单独流式落盘，不能跑完再 `logcat -d` 捞：
> PICO 模拟器的系统日志 spam 极猛（半小时约 14 万行），
> main buffer 会被冲掉 —— 实测跑满 30 分钟后
> `logcat -d | grep ExposureTerrain` 一条都搜不到，
> 而应用好好地跑着（Stage 与三扇副窗仍在、PSS 可对照采样表）。
> 所以脚本在**启动应用之前**就开一条按 tag 过滤的流式抓取，
> 只收本应用相关 tag；本应用默认是文件回放，没有头显实时拾音日志。

> 官方退路：「对于短时体验，可通过重复完整体验进行验证。」
> 本报告是更硬的证据：一次性连续跑满，且有逐次采样留档。
"@
Set-Content -Path $report -Value $md -Encoding UTF8

Write-Host ''
Write-Host "[stability] ===== 结果 ====="
Write-Host "[stability] 报告：$report"
Write-Host "[stability] 采样：$csv"
Write-Host "[stability] 日志：$crashLog"
Write-Host "[stability] 崩溃=$fatal ANR=$anrCount 全程存活=$aliveAll PSS $minPss..$maxPss KB 增长=$growth KB"
Write-Host "[stability] 结论：$(if ($pass) { '通过' } else { '未通过' })"

if (-not $pass) { exit 1 }
exit 0
