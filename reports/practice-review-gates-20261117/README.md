# 导入代次门、缺/多音可判定性与 WAV 头时长预检

记录时间：2026-11-17 12:34（Asia/Shanghai）
机器：SPACE，仓库 `G:\minimaxworkspace\pico-sonosphere`，分支 `main`（未推送）

## 提交

| 项 | 值 |
|---|---|
| 内核提交 | `4c146e171d793ef3dd04dd3d69c637517f768e58` |
| 说明 | feat: 导入代次门、缺多音可判定性与 WAV 头时长预检 |
| 时间 | 2026-11-17 12:35:03 +08:00 |
| parent | `6b00a756524d624b9db11d11d0b17e7707c8d28c` |
| UI 提交 | `2fd7bd519cd51ff2cb832f1a22ac816682bddc76` |
| 说明 | feat: 练习窗接入导入代次门与不可判定缺多音文案 |
| 时间 | 2026-11-17 12:35:09 +08:00 |
| parent | `4c146e171d793ef3dd04dd3d69c637517f768e58` |
| 本文档提交 | 与本报告一并入库，见该次 `git log` |

未 amend 旧提交，未推送，未发布，未跑 `:app:assembleDebug`。

## 改动文件

内核：`UserImportCoordinator.kt`、`EventCountGate.kt`、`StrictUserWav.kt`、`NoteSegmenter.kt`、`NoteEvent.kt`、`PassageAnalyzer.kt`、`PracticeFragments.kt`、`TakeComparator.kt`、`ReviewText.kt`、`UserTakeFactory.kt`，以及测试 `UserImportCoordinatorTest.kt`、`EventCountabilityTest.kt`、`StrictUserWavHeaderTest.kt`、`UserWavImportTest.kt`、`PassageReviewTest.kt`

UI / 说明：`AnalysisBus.kt`、`UserWavImporter.kt`、`CoachWindowContent.kt`、`README.md`

未跟踪目录仍保留、均未入库：`stability/aborted-20261112-080847-interrupted/`，以及 `reports/m2-ee1ab40-emulator-20261117-0028/logs/logcat-launch-smoke.txt`。

## 测试与构建

命令（未跑 `:app:assembleDebug`）：

```powershell
$env:JAVA_HOME='E:\software\Dev\androidstudio\androidStudio\jbr'
$env:ANDROID_HOME='C:\Users\11574\AppData\Local\Android\Sdk'
$gradle='G:\minimaxworkspace\pico-spatial-2026\prototype\gradle-dist\gradle-8.13\bin\gradle.bat'
Set-Location 'G:\minimaxworkspace\pico-sonosphere'
& $gradle --no-daemon :audio-core:test --console=plain --rerun-tasks
& $gradle --no-daemon :app:compileDebugKotlin --console=plain
```

结果：

- `:audio-core:test` BUILD SUCCESSFUL（约 24s）。64 个测试，失败 0，错误 0，跳过 0。
  - AnalysisEngineTest 12
  - StageMappingTest 8
  - PassageReviewTest 15（滑音用例补充：缺/多音为 null）
  - UserWavImportTest 10
  - UserImportCoordinatorTest 6（新增）
  - EventCountabilityTest 5（新增）
  - StrictUserWavHeaderTest 8（新增）
- `:app:compileDebugKotlin` BUILD SUCCESSFUL（约 15s）
- 现有 debug APK 哈希仍为 `EF774371B45129596F12F134CB196CBA811E1651235BDDE94D8159790D50AE3D`（ee1ab40，31,688,250 字节，mtime 2026-11-17 00:26:57 Asia/Shanghai），编译未覆盖该包

日志：`reports/practice-review-gates-20261117/gradle-audio-core-test.txt`、`gradle-app-compileDebugKotlin.txt`、`tests/*.xml`

## 1. 导入请求代次 / 会话失效

纯 JVM `UserImportCoordinator` 作为唯一写槽门。`begin(slot)` 只提升该槽代次，清空该槽结果与比较，失败不把旧成功写回。`invalidateInFlight` 提升会话代次，使所有在途令牌失效，但不误杀另一槽已发布结果。App 路径：`AnalysisBus.userImports`，UI 在选中文件后调用 `beginUserImport`，IO 中检查 `isActive` 与 `isImportCurrent`，`finally`/use 释放描述符与流，取消时走 `onImportJobCancelled`（失效令牌不得改状态）。恢复示范、播放参考、取消确认参考都会 `invalidateInFlight` 并 cancel 该槽 Job。

测试：同槽 A 后开 B，B 先完成、A 迟到被拒且保留 B；B 失败后 A 迟到不能恢复旧成功；恢复示范期间迟到被拒；参考变更迟到被拒；不同槽互不误杀。

## 2. 缺/多音可判定性门

`EventCountGate` 不编造置信分数。可判定条件：分段无短无声同音合并、无滑音/不稳定事件，且谱面相邻同音能观察到足够无声或事件分隔。不可判定时 `missing`/`extra` 为 null，状态 `INDETERMINATE`，文案与 UI 不显示「缺音N/多音N」，比较结论为 `INCONCLUSIVE_INDETERMINATE_COUNTS`，不下改善结论。禁止按期望音数强行切成 4。

真实 PCM：

- 有间隔重复音（200 ms 音 + 200 ms 无声 ×4 A4）：严格断言 4 个事件、起点误差 ≤50 ms，缺=0 多=0，可判定
- 短间隔（160+20 ms）与首尾相接连奏：不可判定，计数为 null
- 慢滑音：不可判定，与重奏比较不下 `SPECIFIC_EVIDENCE`
- 官方互异音高缺中间 G4：仍可判定，缺音=1

门控依据：`NoteSegmenter` 记录的 `SHORT_SILENCE_SAME_PITCH` / `GLIDE_OR_UNSTABLE`、`observedSilences`，以及谱面相邻同 MIDI 是否满足至少 `minSilenceMs`（50 ms）分隔。不是另算的分数。

## 3. WAV 头时长预检

`decodeStream` 先读到 data 块大小，用 data 字节数 /（采样率×帧字节）得到精确时长，超过 20 s（含 20 s + 1 帧）立即拒绝且不读采样。3 MB 读入上限仍保留（knownLength 过大直接拒绝）。字节率与采样率×帧字节不一致视为损坏头。data 先于 fmt、截断 data、非法块大小均拒绝。

## 仍无法覆盖的边界

- 未在设备上点按选择器，也没有用真协程调度做 App 层竞态仪器测试；App 发布路径已接到同一 `UserImportCoordinator`，竞态规则由 JVM 门测试覆盖
- 取消检查在块边界/读循环处，不是每个字节
- 未把 OnsetTracker 能量凹点扩展为新的分隔器（本轮不扩 DSP）。只有频谱分析已给出的无声或换音；纯包络重起、中间几乎无无声的连奏，若谱面不是相邻同音，不会仅因长音本身标不可判定
- 未覆盖所有 WAV 变体（extensible fmt、大量 LIST/fact、非法超大未知块以外的奇异填充）
- 未安装、未头显、未真麦、未打新 APK、未跑新的 30 分钟稳定性

## 未验项（均明确未做）

未安装、未启动、未 force-stop，未碰共享模拟器，未头显，未实时麦，未 DSP 扩展，未打新 APK，未跑新的 30 分钟稳定性。
