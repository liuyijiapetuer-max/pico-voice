# 用户 WAV 演奏复验增量报告

记录时间：2026-11-17 12:14（Asia/Shanghai）
机器：SPACE，仓库 `G:\minimaxworkspace\pico-sonosphere`，分支 `main`（未推送）

## 提交

| 项 | 值 |
|---|---|
| 内核提交 | `f3fdb071e797f71a8ed0a703240a9a4d3b480d55` |
| 说明 | feat: 用户 WAV 导入与相对同处理参考的演奏比较 |
| 时间 | 2026-11-17 12:15:27 +08:00 |
| parent | `2f1fb5e3ea1539d818cfedec1c93b7c24c2fa0ba` |
| UI 提交 | `ce982f983b076f00c1a178b9a445de2d755647d8` |
| 说明 | feat: 练习窗导入第一遍/重奏与谱面目标文案 |
| 时间 | 2026-11-17 12:15:36 +08:00 |
| parent | `f3fdb071e797f71a8ed0a703240a9a4d3b480d55` |
| 本文档提交 | 与本报告一并入库，见该次 `git log` |

未 amend 旧提交，未推送，未发布，未跑 `:app:assembleDebug`。

## 改动文件

内核：`StrictUserWav.kt`、`UserTakeFactory.kt`、`NoteEvent.kt`、`PassageAnalyzer.kt`、`PracticeFragments.kt`、`ReviewText.kt`、`TakeComparator.kt`、`UserWavImportTest.kt`

UI / 依赖 / 说明：`UserWavImporter.kt`、`AnalysisBus.kt`、`CoachWindowContent.kt`、`StageContent.kt`、`app/build.gradle.kts`、`README.md`

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

- `:audio-core:test` BUILD SUCCESSFUL（约 22s）。45 个测试，失败 0，错误 0，跳过 0。
  - AnalysisEngineTest 12（旧）
  - StageMappingTest 8（旧）
  - PassageReviewTest 15（上一轮）
  - UserWavImportTest 10（本轮新增，全部经真实解码或 `AnalysisEngine` 再分段）
- `:app:compileDebugKotlin` BUILD SUCCESSFUL（约 17s）
- 现有 debug APK 哈希仍为 `EF774371B45129596F12F134CB196CBA811E1651235BDDE94D8159790D50AE3D`（ee1ab40，31,688,250 字节，mtime 2026-11-17 00:26:57 Asia/Shanghai），编译未覆盖该包

日志：

- `reports/practice-review-user-wav-20261117/gradle-audio-core-test.txt`
- `reports/practice-review-user-wav-20261117/gradle-app-compileDebugKotlin.txt`
- `reports/practice-review-user-wav-20261117/tests/*.xml`
- 样例输入输出：`reports/practice-review-user-wav-20261117/sample-io.txt`

本轮新测试：官方 WAV 往返两遍可比较；未确认不得绑定/不可比；开头 500 ms 静音不裁不平移；跨段 `engine.reset()` 无起音泄漏；立体声/48 kHz/24 bit/截断/非 RIFF/过长拒绝；流由调用方关闭且超限在完整解码前拒绝；低覆盖拒绝改善结论；同音短间隔分段并在不可靠时给出限制；±50 音分按 `Tuning.nearestMidi` 边界（+49 对准，+51 错音）。

## 对齐代价修正

原先错音配对代价 0.45，同音且整体偏晚约 500 ms 的时间代价约 1.0，Needleman–Wunsch 会把正确的四音延迟演奏配到「缺第一音 + 后三音错配邻近目标 + 多出最后一音」。本轮把错音代价提高到 1.25（仍低于缺音+多音的 2.0），使检测窗内（700 ms）的同音延迟保持为对准并报告时差，而不是误判错音。原始时差保留；相对同处理参考按槽位减去官方参考的分析时差，不减用户整段统一延迟。

## 具体操作（应用内，尚未装机）

默认启动仍是舞台合成演示回放。打开下方「练习提示」窗：

1. 先确认这是固定练习谱 `practice-melody-v1`（120 BPM，原点 0 ms，四拍谱面目标 A4–C5–G4–E4）。未确认不能导入或比较。
2. 「导入第一遍」「导入重奏」走系统文档选择器（`ActivityResultContracts.OpenDocument()`，MIME 含 `audio/wav` 等）。只读用户授权的文件，不扫盘、不绕过权限。源文案为「录音文件分析，不是实时采集」。
3. 界面展示文件名、来源、已确认参考、时间窗口（默认 0 到文件时长，不裁开头静音）。
4. 「比较第一遍与重奏」：按同一参考列出音准/节奏、错音/缺音/多音与覆盖率，以及「相对谱面时差」和「相对同处理参考」。对准音平均音分不含错音，不能当整体改善；覆盖不足则结论为 `INCONCLUSIVE_LOW_COVERAGE`。
5. 练习窗把「当前帧 · 最近律音」和复验里的「谱面目标」分开标注。
6. 内置合成示范按钮独立，只预览分析，不写入用户第一遍/重奏，失败也不会回退到合成示范。

文档选择器已编译进源码，未在模拟器或头显上实际点按验收。

## 样例输入输出摘要

由同一套解码/分析引擎得到（详见 `sample-io.txt`）。官方参考自身：A4 0 ms，C5 −36 ms，G4 −37 ms，E4 −38 ms，节奏平均 28 ms。这是检测窗偏差，不能把逐音绝对时差直接写成用户赶拍。

- 内置第一遍（示范，不是用户演奏）：A4 +25 音分；G4 相对谱面 +44 ms、相对同处理参考 +81 ms；第 4 拍错音（谱面目标 E4，最近律音 F4）；覆盖 4/4，对准 3、错音 1。对准音音分平均 8（n=3，不含错音）。
- 内置重奏：A4 +5 音分；G4 相对同处理参考 +12 ms；E4 对准；覆盖 4/4，错音 0。对准音音分平均 1（n=4）。比较结论 `SPECIFIC_EVIDENCE`，建议同时列出错/缺/多音与覆盖率，并写明 8→1 音分不能当作整体音准结论。
- 用户 WAV 官方四音往返：与参考一致，来源为「录音文件分析（不是实时采集）」。
- 开头 500 ms 静音：事件 A4@464、C5@963、G4@1462、E4@1962；时间窗口 0–2650 ms；四音均对准；相对谱面时差约 +462–+464 ms；相对同处理参考 +464 / +499 / +499 / +500 ms。没有被裁成从 0 起，也没有把延迟减掉。
- 未确认参考：拒绝绑定，不改已有结果。
- ±50 音分（目标 E4）：−49 / +49 仍为 E4（对准），−51 为 D#4、+51 为 F4（错音），跟随 `roundToInt` 最近律音，不是另写一套盖章规则。
- 同音短间隔 4×A4（160 ms 音、100 ms 间隔）：本次检出 4 个事件（起点约 0 / 220 / 487 / 743 ms）。分段并非在所有同音短间隔下都可靠，文案保留显式限制：不可靠时不作确定缺音或多音结论。

## 未验项（均明确未做）

- 未安装、未启动、未 force-stop，未碰共享模拟器
- 未头显验收
- 未实时麦
- 未在设备上点按文档选择器（仅编译通过）
- 未 DSP 修音（本项也未实现修音），未扩展 DSP 或设备路径
- 未打新 APK，未跑新的 30 分钟稳定性（历史官方通过仍以 8ba442c 为准，SHA256 `287009CBC2764E2F2C7E37E2019C0BC8893549F68DE032C85E71DC49356E5D1A`；磁盘现有包仍是 ee1ab40，哈希见上）
- 舞台拖动跟手仍只是查看，未在设备上再次点选验收

## 限制

单旋律，且只对照 `practice-melody-v1` 既定四拍。分析窗偏差见上，比较时用相对同处理参考，不把绝对时差写成赶拍。相邻同音且间隔过短时分段可能合并或漏计。最近律音不是谱面目标。无显式 BPM+原点时拍号为空。严格 WAV：RIFF/WAVE PCM format 1、单声道、16 bit、44.1 kHz，最大约 3 MB / 20 s。
