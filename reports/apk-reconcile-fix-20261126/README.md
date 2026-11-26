# README 对账订正与 V3 自检证据（2026-11-26 Asia/Shanghai）

工程目录：`G:\minimaxworkspace\pico-sonosphere`（公开仓库 `pico-voice`，分支 `main`）。
本轮开始时 HEAD = `d304342`（V3.0 首期增量，2026-11-25 20:50），parent = `0145545`。

## 一、本轮唯一待办项

**订正 README「Commit ↔ APK 对账（M2）」段的过期事实**，并写明当前磁盘包不含 V3.0 首期增量。

选它的依据：

- 里程碑 gate：2026-11-26 ≤ 2026-12-05 ⇒ 功能面冻结期，只修阻塞交卷的问题，不加功能。本轮**没有任何功能改动**。
- 交卷材料（说明文档／演示脚本／清单）要求 2026-12-12 前齐备。README 是交卷说明的第一页，它的对账段三项数值全错（见下），按文档核对会直接对错包与错源码 commit。
- 该错是 2026-11-17 12:55 那轮 `assembleDebug` 之后未回写 README 造成的，跨了整整一代 APK。

## 二、实读结论（2026-11-26 10:3x 实机复核）

| 项 | README 原记载（错） | 2026-11-26 实读 | 权威出处 |
|---|---|---|---|
| 磁盘默认输出 `app/build/outputs/apk/debug/app-debug.apk` 字节数 | 31,688,250 | **31,914,722** | `reports/practice-review-apk-20261117-1255/README.md` |
| 同上 SHA256 | `EF774371…0AE3D` | **`582A647D21DD49527554CFD4FE789EB0099F49DA5B1C1C9C93410F1EA36B070C`** | 同上 |
| 同上 mtime | 2026-11-17 00:26:57 | **2026-11-17 12:55:54** | 同上 |
| 同上对应源码 | ee1ab40 | **`a8ba1365c72d10b4b6ec80a033ee16e44cb49688`（含父提交 `4a79fed`）** | 同上 |
| ee1ab40 旧包副本 | 未提 | 仍在磁盘两处，各 31,688,250 B / `EF774371…0AE3D` | 本轮实读列目录 |

补充实读结论：

1. **磁盘包不含 V3**。V3 提交 `d304342`（2026-11-25 20:50）晚于该包（11-17 12:55），故磁盘默认输出里没有 V3 的空间叠合、试听重奏与评价口径。交卷必须重新打包并重做 Commit ↔ APK 对账，不能拿它当交卷包。
2. **ee1ab40 旧包未被覆盖**：`reports/practice-review-apk-20261117-1255/app-debug-ee1ab40.apk` 与 `reports/m2-ee1ab40-emulator-20261117-0028/apk/app-debug-ee1ab40.apk`，字节数与 SHA256 与 11-17 对账件一致。
3. **包名未漂移**：`app/build.gradle.kts:12` 的 `applicationId` 仍为 `com.spatialcoach.soundarchitecture`，`namespace` 同值。
4. **仓库内无二进制**：这些 `.apk` 副本都被 `.gitignore` 的 `*.apk` 忽略，不入库。

## 三、本轮改动

- `README.md`「Commit ↔ APK 对账（M2）」段：删掉写错的那一句，换成上表的真实包身份，并补三条要点（磁盘包不含 V3／旧包副本仍在／仓库无二进制）。净增 4 行。
- 未改任何源码，未改任何测试，未改 `applicationId`。
- 未 `assembleDebug`（会覆盖当前磁盘包），未安装，未启动，未 force-stop，未碰模拟器与头显。

## 四、自检（命令与日志均在本目录）

| 检查 | 结果 | 证据 |
|---|---|---|
| `:audio-core:test :app:compileDebugKotlin --rerun-tasks` | **BUILD SUCCESSFUL in 34s**，`20 actionable tasks: 20 executed`，退出码 0 | `gradle-selfcheck-rerun.log` |
| JUnit 汇总 | **91 例 / 11 个测试类，failures 0、errors 0、skipped 0**，执行时间 2026-11-26 10:33:46 | `tests/*.xml`（11 份已入库） |
| 首次复跑（不带 `--rerun-tasks`） | 全任务 UP-TO-DATE ⇒ V3 源码自 2026-11-25 20:36 起未变更 | `gradle-selfcheck-uptodate.log` |
| 磁盘 APK 复核 | 仍为 31,914,722 B / `582A647D…B070C` / 11-17 12:55:54，本轮未变 | 本轮 `Get-FileHash` 输出 |

测试类分布：AnalysisEngineTest 12、CorrectionPreviewTest 8、EvaluationPolicyTest 4、EventCountabilityTest 5、IndeterminateSuggestionTest 5、PassageReviewTest 15、SpatialOverlayTest 10、StageMappingTest 8、StrictUserWavHeaderTest 8、UserImportCoordinatorTest 6、UserWavImportTest 10。

## 五、明确未做（不得据此推断）

- 未打新 APK、未安装、未启动、未碰共享模拟器与头显，未做真实 WAV 设备导入、未真麦、未录屏。
- 未跑新的 30 分钟稳定性，未做用户对照实验。**不能继承 `8ba442c` 的官方稳定性结论，也不能声称已完成 W4。**
- 未改功能代码，未 `push`（公开仓库，未经 hommi 明确指令不推送）。

## 六、工作区遗留（历史遗留，非本轮产生，本轮未处理）

`git status` 仍显示两处未跟踪项，均为 2026-11-17 对账件写明「应保留」的遗留物：

- `reports/m2-ee1ab40-emulator-20261117-0028/logs/logcat-launch-smoke.txt`
- `stability/aborted-20261112-080847-interrupted/`

本轮不删、不入库，也未改 `.gitignore`（属另一件事，另轮处理）。

## 七、下一步候选（按优先级，≤3 条）

1. **补 V3.0 首期增量的落盘件**：`reports/` 目前没有 `d304342` 对应的落地报告与证据，属交卷材料缺口（V3 源码与单测今天已复核为绿，但没入库）。
2. **重新打包交卷 APK 并重做 Commit ↔ APK 对账**：在 `d304342` 之后跑 `assembleDebug`；打包前先确认两份旧包副本是否还要留。
3. **交卷清单／演示脚本补齐（12-12 前）**：与 README 口径同步复核，确保「能演示」与「已真机验证」在每一处都对得上。
