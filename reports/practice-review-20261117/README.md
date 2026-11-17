# 演奏复验增量报告

记录时间：2026-11-17 11:54（Asia/Shanghai）
机器：SPACE，仓库 `G:\minimaxworkspace\pico-sonosphere`，分支 `main`（未推送）

## 提交

| 项 | 值 |
|---|---|
| 内核提交 | `734d8a8f88c1b248911f5ce676bc84f59ba0e86b` |
| 说明 | feat: 单旋律音符事件与同参考演奏分项比较 |
| 时间 | 2026-11-17 11:53:53 +08:00 |
| parent | `75d50d2118723620989d1fb815d5247344011dcb` |
| UI 提交 | `7b4220e236c9d153dfa017b750b50b450606eef8` |
| 说明 | feat: 练习窗演奏复验与起音文案、回放跨圈重置 |
| 时间 | 2026-11-17 11:54:03 +08:00 |
| parent | `734d8a8f88c1b248911f5ce676bc84f59ba0e86b` |
| 本文档提交 | 与本报告一并入库，见该次 `git log` |

未 amend 旧提交，未推送，未发布。

## 改动文件

内核：`NoteEvent.kt`、`NoteSegmenter.kt`、`PassageAnalyzer.kt`、`PracticeFragments.kt`、`ReviewText.kt`、`RhythmReference.kt`、`TakeComparator.kt`、`PracticeCoach.kt`、`PassageReviewTest.kt`

UI / 文案 / 说明：`AnalysisBus.kt`、`PracticeSessionPlayer.kt`、`FileDemoPlayer.kt`、`StageContent.kt`、`CoachWindowContent.kt`、`DynamicsWindowContent.kt`、`README.md`

未跟踪目录仍保留：`stability/aborted-20261112-080847-interrupted/`，以及 `reports/m2-ee1ab40-emulator-20261117-0028/logs/logcat-launch-smoke.txt`。二者均未入库。

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

- `:audio-core:test` BUILD SUCCESSFUL（约 23s）。35 个测试，失败 0，错误 0，跳过 0。
  - AnalysisEngineTest 12（旧）
  - StageMappingTest 8（旧）
  - PassageReviewTest 15（新，全部经真实 AnalysisEngine 再分段）
- `:app:compileDebugKotlin` BUILD SUCCESSFUL（约 19s）
- 现有 debug APK 哈希仍为 `EF774371B45129596F12F134CB196CBA811E1651235BDDE94D8159790D50AE3D`（ee1ab40），未被覆盖

日志：

- `reports/practice-review-20261117/gradle-audio-core-test.txt`
- `reports/practice-review-20261117/gradle-compileDebugKotlin.txt`
- `reports/practice-review-20261117/tests/*.xml`
- 样例输入输出：`reports/practice-review-20261117/sample-io.txt`

新测试覆盖：多音参考旋律、有/无节拍网格、无目标不判错音、±25 音分、整段 80 ms 延迟且不减统一偏差、缺音不导致后续错位、多音、错音、片段/节拍不可比拒绝、噪声/低于门限、慢滑音边界、第一遍 vs 重奏分项改善。

## 具体操作（应用内，尚未装机）

默认启动仍是舞台合成演示回放。打开下方「练习提示」窗：

1. 可见片段 `practice-melody-v1`、120 BPM、原点 0 ms、四拍 A4 C5 G4 E4，以及「文件回放（合成音轨）。不是真麦采集，也不是自动修音。」
2. 「播放参考（对准、准点）」：只分析，不记入比较。
3. 「记为第一遍（内置含偏差示范）」：A4 +25 音分，G4 晚 80 ms，第 4 拍弹成 F4。
4. 「记为重奏（内置已改善示范）」：A4 +5 音分，G4 约晚 12 ms，E4 对准。
5. 「比较第一遍与重奏」：按同一参考列出音准/节奏分项、缺音/多音/错音/未对齐，以及有证据的建议。不用总分，不称 DSP 修音。
6. 「恢复舞台演示回放」：回到原来的循环演示；循环跨圈会重置分析引擎。

当前只支持上述内置合成示范，不能加载用户文件或真麦。

## 样例输入输出摘要

由同一套 `PassageAnalyzer` + `TakeComparator` 从合成 PCM 得到（详见 `sample-io.txt`）。窗口定位可能整体偏早约 36 ms，这是分析偏差，不是把整段延迟减掉：第一遍 G4 时差 +44 ms，相对参考的 -37 ms，差值约 +81 ms，与写入的 80 ms 延迟相符。

- 参考：四音均对准，音准平均 0 音分
- 第一遍：A4 +25 音分；G4 +44 ms；第 4 拍错音 F4；音准平均 8 音分（n=3，错音不计入音准均值）
- 重奏：A4 +5 音分；G4 -25 ms；E4 对准；音准平均 1 音分（n=4）
- 建议明确写出音准改善、第 4 拍错音已对准，并声明不是自动修音或 DSP

## 未验项（均明确未做）

- 未安装、未启动、未 force-stop，未碰共享模拟器
- 未头显验收
- 未实时麦 / 未用户文件输入
- 未 DSP 修音（本项也未实现修音）
- 未打新 APK，未跑 30 分钟稳定性（旧 8ba442c 通过不继承；磁盘上现有包仍是 ee1ab40）
- 舞台拖动跟手仍只是查看，未在设备上再次点选验收
- 练习窗按钮路径已编译进源码，但未在模拟器或头显上实际点按

## 限制

单旋律 only。静音/换音分段，并抑制短帧抖动；慢滑音标为不够稳定，不伪装成确定音列。不做复调，不做自动跟谱。无显式 BPM+原点时 beat 为 null。最近律音不等于谱面目标。
