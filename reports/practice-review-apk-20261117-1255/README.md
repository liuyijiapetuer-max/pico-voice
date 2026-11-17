# 含 4a79fed 的首轮 assembleDebug 对账（不改功能）

记录时区：Asia/Shanghai（UTC+8）
机器：SPACE，仓库 `G:\minimaxworkspace\pico-sonosphere`，分支 `main`（未推送）

本轮只构建，不改功能、不重跑测试、不安装、不进设备。最新 69 例测试是此前 `reports/practice-review-indeterminate-advice-20261117/` 的结果，不是本轮执行。

## 构建前只读核对

构建前工作区（与预期一致）：

- 源码 HEAD（亦即本轮 **SourceCommit**）：`a8ba1365c72d10b4b6ec80a033ee16e44cb49688`（短 `a8ba136`）
- parent：`4a79fede0e28855e14e7969e17eca828dba08968`（短 `4a79fed`）
- `git status`：`main...origin/main [ahead 12]`；无已跟踪文件改动。未跟踪仅有应保留的 `reports/m2-ee1ab40-emulator-20261117-0028/logs/logcat-launch-smoke.txt` 与 `stability/aborted-20261112-080847-interrupted/`。无意外源码修改。

`a8ba136` 是「不可判定建议硬断言」文档提交，其父提交 `4a79fed` 为功能修复；本包源码含 `4a79fed`。

## 旧包独立副本（assemble 之前）

默认输出 `app/build/outputs/apk/debug/app-debug.apk` 在替换前已复制并核对：

| 项 | 值 |
|---|---|
| 独立副本 | `reports/practice-review-apk-20261117-1255/app-debug-ee1ab40.apk` |
| 字节数 | 31,688,250 |
| SHA256 | `EF774371B45129596F12F134CB196CBA811E1651235BDDE94D8159790D50AE3D` |
| mtime | 2026-11-17 00:26:57（Asia/Shanghai） |
| 对应 | ee1ab40 磁盘包 |

副本与当时默认输出一致，哈希和大小均与既定值相符。assemble 成功后再次核对该副本，仍完整未被改写。

## 首轮 assembleDebug

命令（未 `clean`、未 `--rerun-tasks`、未 install、未 adb、未碰共享模拟器）：

```powershell
$env:JAVA_HOME='E:\software\Dev\androidstudio\androidStudio\jbr'
$env:ANDROID_HOME='C:\Users\11574\AppData\Local\Android\Sdk'
$gradle='G:\minimaxworkspace\pico-spatial-2026\prototype\gradle-dist\gradle-8.13\bin\gradle.bat'
Set-Location 'G:\minimaxworkspace\pico-sonosphere'
& $gradle --no-daemon :app:assembleDebug --console=plain
```

- 开始：2026-11-17 12:55:33 +08:00（Asia/Shanghai）
- 结束：2026-11-17 12:55:54 +08:00（Asia/Shanghai）
- 退出码：0
- 日志原文：`BUILD SUCCESSFUL in 20s`
- 完整日志：`reports/practice-review-apk-20261117-1255/gradle-app-assembleDebug.txt`（第一次也是唯一一次 assemble 的全部输出；文末附记开始/结束时间与退出码）

Gradle 任务计数（照日志原文，未另行推算）：

- `38 actionable tasks: 6 executed, 32 up-to-date`
- 日志未出现 `from-cache` 字样，本报告不编造 from-cache 数量。

## 新包独立副本

默认输出已被本轮 build 替换。独立命名副本：

| 项 | 值 |
|---|---|
| 默认输出 | `app/build/outputs/apk/debug/app-debug.apk` |
| 独立副本 | `reports/practice-review-apk-20261117-1255/app-debug-a8ba136.apk` |
| 字节数 | 31,914,722 |
| SHA256 | `582A647D21DD49527554CFD4FE789EB0099F49DA5B1C1C9C93410F1EA36B070C` |
| mtime | 2026-11-17 12:55:54（Asia/Shanghai） |
| 源码 | SourceCommit `a8ba1365c72d10b4b6ec80a033ee16e44cb49688`（含父提交 `4a79fed`） |

默认输出与独立副本哈希、字节数一致。

## 提交说明（勿与 SourceCommit 混淆）

- **构建 SourceCommit**（包所对应的源码，构建时的 HEAD）：`a8ba1365c72d10b4b6ec80a033ee16e44cb49688`，parent `4a79fede0e28855e14e7969e17eca828dba08968`。
- **随后文档提交**：本对账报告与首轮日志的本地提交，不改变源码，也不包含 APK 二进制（仓库 `.gitignore` 已忽略 `*.apk` 与 `**/build/`）。该文档提交的哈希见入库后 `git log`，与 SourceCommit 不是同一次提交。

未 amend 旧提交，未推送，未改功能代码。

## 明确未做

- 新 APK **未安装**，未启动，未 force-stop，未碰共享模拟器
- 未做真实 WAV 设备导入，未头显验收，未真麦，未 DSP，未跑新的 30 分钟稳定性
- 不能继承 ee1ab40 或 8ba442c 的历史稳定性/验收结论
- 本轮未重跑测试

应保留的未跟踪项仍未入库：`reports/m2-ee1ab40-emulator-20261117-0028/logs/logcat-launch-smoke.txt`、`stability/aborted-20261112-080847-interrupted/`。
