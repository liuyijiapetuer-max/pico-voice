# 声之建筑

实时声音可视化：同一套分析引擎，服务专业调音和乐器初学者。

这是 PICO Space Program 2026 Spatial 赛道的独立参赛应用。仓库：https://github.com/liuyijiapetuer-max/pico-voice

包名必须是 `com.spatialcoach.soundarchitecture`，与《曝光暗房》分开，不修改也不搬用那边的工程。仓库里的《声之建筑-落地方案.pdf》是历史文档，不再作为实现目标。

## 当前范围（M2 + V3.0 首期增量）

- 可单测的分析内核（`:audio-core`）：单音音高（音分）、全频谱、动态、起音/节奏提示。不做复调识别，不做自动跟谱。
- 默认演示走**文件回放**（应用私有目录里的合成音轨，不含商业歌曲）。麦克风权限和 Android 录音路径已预留，但没有接进默认启动链路，也**不能**写成已在头显上验证的实时拾音。
- 同一套分析结果，一个开关两种模式。专业模式给出音分、频谱、动态的刻度或数值；练习模式只保留音准、节奏、强弱和一句人话。
- 主舞台：前方扇面随频谱和力度起伏；目标音是空间里的位置，音准误差用上下和左右的空间偏移表示。V3 另在默认桌面尺度（约 1.2 米宽）上叠合参考白线框与实际青色实体，偏差用香槟金提示。默认摆放**不是**已完成的实体乐器真机锚定，禁止写成已遮挡或已对准真实琴键。右侧频谱窗、左侧动态窗、下方练习提示窗，启动后会错开打开。不是 24 柱回放，也不是纯灯光秀。
- 评价口径：MIDI 只核对准键音符及时间，不评价声学音准；麦克风单音模式才评价声学音准。本轮没有接入经验收的 MIDI 设备或真麦，只把口径写进内核、叠合和文案。
- 选中构件后才展开音分或毫秒；可对参考与实际做 A/B 试听。用户导入的 WAV 当前只保存分析结果，实际演奏片段不能从原音频切片回放，会明确说明。重奏会保留前次轮廓。漏音显示为空缺，低置信度为虚线并标「未知」。禁止用时间拉伸掩盖节奏错误。
- 一键正交视图只旋转轨道根节点（侧视 / 俯视），不是已验证的相机锚定。交互默认控制器射线；手势跟随尚未通过稳定性测试。
- 专业修音预览仍是实验室夹具上的保守处理，`claimFixed` 保持为 false，不是已完成的专业自动修音。

## 演奏复验

练习窗区分两件事：用户把自己演奏的 WAV 导入为第一遍/重奏并比较；以及独立的内置合成示范（不能当作用户成果）。

- 参考固定为 `practice-melody-v1`（120 BPM，原点 0 ms，四拍谱面目标 A4–C5–G4–E4）。不支持其他曲目。导入前必须确认同一参考；未确认不会把任意文件标成该片段。
- 用户路径：确认参考 →「导入第一遍」/「导入重奏」（系统文档选择器，只读用户授权的文件）→「比较第一遍与重奏」。界面显示文件名、来源（录音文件分析，不是实时采集）、参考和时间窗口。开头静音不会自动裁掉，也不会平移对齐。同一槽位新的导入会作废在途旧请求（按请求代次，迟到结果不能覆盖新文件）；失败不会把旧成功写回。恢复示范、播放参考、取消确认参考也会使在途导入失效。不同槽位互不影响。
- 只接受 PCM16 单声道 44.1 kHz 的 WAV。解码先根据 WAV 头的 data 大小和帧率检查 20 秒时长，超时不读采样；另外仍保留 3 MB 读入上限，避免声明时长短但容器很大的文件占满内存。格式不支持、文件过大或过长、无法对齐时给出原因，**不会回退到合成示范**。系统选择器沿用 PICO 官方样例的 `ActivityResultContracts.OpenDocument()`，本机尚未做设备点按验收。
- 「第几拍」只在有显式 BPM 和原点时填写。判断赶拍/拖拍请看「相对同处理参考」的差值：参考自身经同一分析后，后三音大约早 36–38 ms，节奏平均偏差约 28 ms。相对谱面的原始时差会保留，不偷偷减整段统一延迟，也不能把绝对时差直接写成用户赶拍。
- 最近律音（当前帧音准靠近哪个十二平均律音）和谱面目标（练习谱既定音）在界面上分开标注。错音与匹配覆盖率单列；缺音/多音仅在分段可判定时显示确定数字，不可判定时不显示数字，建议也不写确定缺/多音、错漏推断或改善结论，并说明无法判定及重录条件。对准音的平均音分不含错音，不能当作整体改善。缺音过多时拒绝改善结论。
- 只做单旋律。相邻同音需要可观察的起音或足够无声分隔才能给出确定缺/多音；短间隔合并、连续同音重新起奏、滑音或不够稳定时标为不可判定，不会按期望音数强行切开。不做复调，也不自动跟谱。舞台拖动只是查看，不是 DSP 修音。不做真麦。
- 内置合成示范有独立按钮（含漏音示范），只用于预览分析，不会写入用户第一遍/重奏。预录音必须在画面明确标注。
- 验证方式（不要跑 `assembleDebug`，以免覆盖现有 ee1ab40 APK）：


```powershell
$env:JAVA_HOME='E:\software\Dev\androidstudio\androidStudio\jbr'
$env:ANDROID_HOME='C:\Users\11574\AppData\Local\Android\Sdk'
$gradle='G:\minimaxworkspace\pico-spatial-2026\prototype\gradle-dist\gradle-8.13\bin\gradle.bat'
Set-Location 'G:\minimaxworkspace\pico-sonosphere'
& $gradle --no-daemon :audio-core:test :app:compileDebugKotlin --console=plain
```

证据见 [reports/practice-review-20261117/](reports/practice-review-20261117/)（内核/示范）、[reports/practice-review-user-wav-20261117/](reports/practice-review-user-wav-20261117/)（用户 WAV 导入）与 [reports/practice-review-gates-20261117/](reports/practice-review-gates-20261117/)（导入代次门、缺/多音可判定性、WAV 头时长预检）、[reports/practice-review-indeterminate-advice-20261117/](reports/practice-review-indeterminate-advice-20261117/)（不可判定建议不得写确定缺/多音）。V3 叠合与口径单测见 `:audio-core` 的 `SpatialOverlayTest`、`EvaluationPolicyTest`。本项未装机、未头显、未实时麦、未出新 APK、未跑新的 30 分钟稳定性，也未做用户对照实验。官方 30 分钟稳定性仍以历史提交 `8ba442c` 为准。

文案把「能演示」和「已真机验证」分开。下面的操作说明是模拟器上的文件回放演示，模拟器没有真麦。

## 构建与安装

需要本机已安装 Android SDK 与 PICO Spatial SDK 6.1。若 `gradlew` 下载发行版超时，改用本机已有的 Gradle 8.13。

```powershell
$env:JAVA_HOME='E:\software\Dev\androidstudio\androidStudio\jbr'
$gradle='G:\minimaxworkspace\pico-spatial-2026\prototype\gradle-dist\gradle-8.13\bin\gradle.bat'
Set-Location 'G:\minimaxworkspace\pico-sonosphere'
& $gradle --no-daemon :audio-core:test :app:assembleDebug --console=plain

$apk='G:\minimaxworkspace\pico-sonosphere\app\build\outputs\apk\debug\app-debug.apk'
Get-FileHash -Algorithm SHA256 $apk
git rev-parse HEAD
```

把上面的 Commit 和 SHA256 记在一起，作为 Commit ↔ APK 对账。然后安装启动：

```powershell
$env:PICO_HOME='C:\Users\11574\AppData\Local\PICO\sdk'
$cli='G:\minimaxworkspace\pico-spatial-2026\tools\node_modules\.bin\pico-cli.cmd'
# 若同一台模拟器上《曝光暗房》（com.spatialcoach.sonosphere）已在 Stage 中，先强制停止，切勿用本包装到那个包名上。
& $cli app install $apk -r
& $cli app launch com.spatialcoach.soundarchitecture
```

## 操作说明（模拟器 · 文件回放）

1. 启动后进入 SOUNDARCHITECTURE Stage。画面会自动开始回放合成音轨，前方扇面随频谱起伏，中央音高尺上有目标音（绿）和当前音（琥珀）两个位置。前方另有一条约 1.2 米宽的参考轨道（白色线框，A4–C5–G4–E4）。画面会标明这是预录音 / 文件回放。
2. 约两秒后依次打开三扇副窗：右边是频谱（专业模式带 Hz 刻度），左边是动态（专业模式带 dBFS 刻度和数值），下边是练习提示（音准、节奏、强弱、一句人话，以及叠合选中后的音分/毫秒与 A/B 试听）。
3. 点舞台下方「专业 / 练习」或练习窗里的切换，在两种模式之间转换。练习模式会收起专业刻度，只保留更简单的提示。
4. 当前输入是文件回放（合成音轨）。模拟器没有真麦。实时拾音要另开头显验收，通过前路演不得说成已验证。可用控制器射线点选轨道构件，再试听参考或实际；「一键正交视图」只改变轨道摆放。
5. 无需把手离开「乐器」去频繁操作：演示会自动播；模式开关是一次性切换。手势跟随不是已通过稳定性测试的交互。

若同一台模拟器上《曝光暗房》（`com.spatialcoach.sonosphere`）已在 Stage 中，请先强制停止该应用再启动本应用，否则启动检查可能失败。两个应用包名不同，不要互相覆盖安装。

## Commit ↔ APK 对账（M2）

历史官方通过仍以已推送的 M2 提交 [`8ba442c148c2b7c3b6d9992bca212347a9e949a3`](https://github.com/liuyijiapetuer-max/pico-voice/commit/8ba442c148c2b7c3b6d9992bca212347a9e949a3) 与 [stability/report-20261112-085904.md](stability/report-20261112-085904.md) 为准（emulator-5554，文件回放，不是头显，也不是继承任何旧通过）。当时对账的 SHA256 是 `287009CBC2764E2F2C7E37E2019C0BC8893549F68DE032C85E71DC49356E5D1A`，完整表格见 [reports/m2-commit-apk.md](reports/m2-commit-apk.md)。

**当前磁盘上的** `app/build/outputs/apk/debug/app-debug.apk` 既不是 8ba442c 那一包，也不是 ee1ab40 那一包：2026-11-17 12:55:54（Asia/Shanghai）那次 `assembleDebug` 已把它换成 SourceCommit `a8ba1365c72d10b4b6ec80a033ee16e44cb49688`（含父提交 `4a79fed`）对应的包，字节数 31,914,722，SHA256 为 `582A647D21DD49527554CFD4FE789EB0099F49DA5B1C1C9C93410F1EA36B070C`，完整对账见 [reports/practice-review-apk-20261117-1255/README.md](reports/practice-review-apk-20261117-1255/README.md)（2026-11-26 实读复核字节数、SHA256、mtime 三项均与该对账一致）。

- **这个磁盘包不含 V3.0 首期增量**：V3 提交是 `d304342`（2026-11-25 20:50），晚于本包。交卷必须重新打包并重做 Commit ↔ APK 对账，不能拿它当交卷包。
- ee1ab40 旧包仍有独立副本，未被覆盖：`reports/practice-review-apk-20261117-1255/app-debug-ee1ab40.apk` 与 `reports/m2-ee1ab40-emulator-20261117-0028/apk/app-debug-ee1ab40.apk`（31,688,250 B，SHA256 `EF774371B45129596F12F134CB196CBA811E1651235BDDE94D8159790D50AE3D`）。重打包前先确认这两份副本是否还要留。
- 这些 APK 副本都是 `.gitignore` 忽略项（`*.apk`），仓库里没有任何二进制。

本轮 V3.0 首期增量只有新源码与单测/编译证据，**未打新 APK，未跑新的 30 分钟稳定性，未做用户对照或真机录屏**，不能继承上述历史通过结论，也不能声称已完成 W4。
